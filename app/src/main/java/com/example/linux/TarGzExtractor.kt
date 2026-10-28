package com.example.linux

import java.io.BufferedInputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.GZIPInputStream

class TarException(message: String) : Exception(message)

/** Filesystem operations that differ between Android (android.system.Os) and the JVM (used by unit tests). */
interface FsOps {
    fun symlink(target: String, link: File)
    fun chmod(file: File, mode: Int)
}

object JvmFsOps : FsOps {
    override fun symlink(target: String, link: File) {
        Files.createSymbolicLink(link.toPath(), java.nio.file.Paths.get(target))
    }

    override fun chmod(file: File, mode: Int) {
        val perms = mutableSetOf<PosixFilePermission>()
        val all = PosixFilePermission.values() // OWNER_READ..OTHERS_EXECUTE, in rwx rwx rwx order
        for (i in 0 until 9) if (mode and (1 shl (8 - i)) != 0) perms += all[i]
        Files.setPosixFilePermissions(file.toPath(), perms)
    }
}

object AndroidFsOps : FsOps {
    override fun symlink(target: String, link: File) = android.system.Os.symlink(target, link.absolutePath)
    override fun chmod(file: File, mode: Int) = android.system.Os.chmod(file.absolutePath, mode)
}

data class ExtractStats(val files: Int, val dirs: Int, val symlinks: Int, val hardlinks: Int, val skipped: List<String>)

/**
 * Minimal, dependency-free tar.gz extractor (ustar, GNU long names, PAX headers) for unpacking Linux root filesystems.
 *
 * Safety: entries may not contain '..' and may not be written through a symlink that leaves [dest]
 * (the "zip-slip" attack). Directories are forced owner-writable and files owner-readable, because proot
 * emulates root but the host filesystem still enforces the app's real permissions.
 */
class TarGzExtractor(private val fs: FsOps = AndroidFsOps) {

    fun extract(gz: InputStream, dest: File, onEntry: (String) -> Unit = {}): ExtractStats {
        dest.mkdirs()
        val root = dest.canonicalFile
        val input = GZIPInputStream(BufferedInputStream(gz, 64 * 1024))
        var files = 0; var dirs = 0; var symlinks = 0; var hardlinks = 0
        val skipped = mutableListOf<String>()

        var longName: String? = null
        var longLink: String? = null
        var paxPath: String? = null
        var paxLink: String? = null
        var zeroBlocks = 0

        val header = ByteArray(512)
        while (true) {
            if (!readFully(input, header)) break
            if (header.all { it == 0.toByte() }) {
                if (++zeroBlocks >= 2) break
                continue
            }
            zeroBlocks = 0
            val size = parseSize(header)
            val type = header[156].toInt().toChar()

            when (type) {
                'x' -> { // PAX extended header for the next entry
                    val rec = parsePax(readBytes(input, size))
                    paxPath = rec["path"]; paxLink = rec["linkpath"]
                    continue
                }
                'g' -> { readBytes(input, size); continue }
                'L' -> { longName = cString(readBytes(input, size)); continue }
                'K' -> { longLink = cString(readBytes(input, size)); continue }
            }

            val name = paxPath ?: longName ?: ustarName(header)
            val link = paxLink ?: longLink ?: cString(header, 157, 100)
            paxPath = null; longName = null; paxLink = null; longLink = null

            val rel = name.trimStart('/').removeSuffix("/")
            val mode = (parseOctal(header, 100, 8) and 0x1FF).toInt()
            if (rel.isEmpty() || rel == ".") { skip(input, size); continue }
            if (rel.split('/').any { it == ".." }) throw TarException("Unsafe path in archive: $name")

            val target = File(root, rel)
            val parent = target.parentFile ?: root
            parent.mkdirs()
            if (!isInside(root, parent.canonicalFile)) {
                // Would be written through a symlink pointing outside the root.
                skipped += name
                skip(input, size)
                continue
            }
            onEntry(rel)

            when (type) {
                '5' -> {
                    if (!target.isDirectory) target.mkdirs()
                    fs.chmod(target, mode or 0b111_000_000) // rwx for owner
                    dirs++
                }
                '2' -> {
                    removeExisting(target)
                    fs.symlink(link, target)
                    symlinks++
                }
                '1' -> {
                    val src = File(root, link.trimStart('/'))
                    if (!src.isFile || !isInside(root, src.canonicalFile)) { skipped += name; continue }
                    removeExisting(target)
                    src.copyTo(target, overwrite = true)
                    fs.chmod(target, mode or 0b110_000_000)
                    hardlinks++
                }
                '0', 0.toChar(), '7' -> {
                    removeExisting(target)
                    target.outputStream().use { out -> copy(input, out, size) }
                    skipPadding(input, size)
                    fs.chmod(target, mode or 0b110_000_000) // rw for owner, keep exec bits
                    files++
                }
                else -> { // devices, fifos: not creatable by an app, and not needed
                    skipped += name
                    skip(input, size)
                }
            }
        }
        return ExtractStats(files, dirs, symlinks, hardlinks, skipped)
    }

    // ---- helpers

    private fun isInside(root: File, f: File) = f == root || f.path.startsWith(root.path + File.separator)

    private fun removeExisting(f: File) {
        if (Files.isSymbolicLink(f.toPath()) || f.exists()) {
            if (f.isDirectory && !Files.isSymbolicLink(f.toPath())) f.deleteRecursively() else f.delete()
        }
    }

    private fun ustarName(h: ByteArray): String {
        val name = cString(h, 0, 100)
        val isUstar = String(h, 257, 5, Charsets.US_ASCII) == "ustar"
        val prefix = if (isUstar) cString(h, 345, 155) else ""
        return if (prefix.isNotEmpty()) "$prefix/$name" else name
    }

    private fun cString(b: ByteArray, off: Int = 0, len: Int = b.size - off): String {
        var end = off
        while (end < off + len && b[end] != 0.toByte()) end++
        return String(b, off, end - off, Charsets.UTF_8)
    }

    private fun parseOctal(b: ByteArray, off: Int, len: Int): Long {
        if (b[off].toInt() and 0x80 != 0) { // GNU base-256 for very large values
            var v = (b[off].toInt() and 0x7F).toLong()
            for (i in 1 until len) v = (v shl 8) or (b[off + i].toLong() and 0xFF)
            return v
        }
        val s = cString(b, off, len).trim()
        return if (s.isEmpty()) 0 else s.toLong(8)
    }

    private fun parseSize(h: ByteArray) = parseOctal(h, 124, 12)

    private fun parsePax(data: ByteArray): Map<String, String> {
        val out = HashMap<String, String>()
        var i = 0
        while (i < data.size) {
            var sp = i
            while (sp < data.size && data[sp] != ' '.code.toByte()) sp++
            val len = String(data, i, sp - i, Charsets.US_ASCII).toIntOrNull() ?: break
            if (len <= 0 || i + len > data.size) break
            val rec = String(data, sp + 1, i + len - sp - 2, Charsets.UTF_8) // strip trailing \n
            val eq = rec.indexOf('=')
            if (eq > 0) out[rec.substring(0, eq)] = rec.substring(eq + 1)
            i += len
        }
        return out
    }

    private fun readFully(input: InputStream, buf: ByteArray): Boolean {
        var n = 0
        while (n < buf.size) {
            val r = input.read(buf, n, buf.size - n)
            if (r < 0) { if (n == 0) return false else throw EOFException("Truncated tar header") }
            n += r
        }
        return true
    }

    private fun readBytes(input: InputStream, size: Long): ByteArray {
        if (size > 1_000_000) throw TarException("Metadata block too large")
        val buf = ByteArray(size.toInt())
        var n = 0
        while (n < buf.size) {
            val r = input.read(buf, n, buf.size - n)
            if (r < 0) throw EOFException("Truncated tar data")
            n += r
        }
        skipPadding(input, size)
        return buf
    }

    private fun copy(input: InputStream, out: java.io.OutputStream, size: Long) {
        val buf = ByteArray(64 * 1024)
        var left = size
        while (left > 0) {
            val r = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (r < 0) throw EOFException("Truncated tar data")
            out.write(buf, 0, r)
            left -= r
        }
    }

    private fun skipPadding(input: InputStream, size: Long) {
        val pad = (512 - size % 512) % 512
        skipExactly(input, pad)
    }

    private fun skip(input: InputStream, size: Long) = skipExactly(input, size + (512 - size % 512) % 512)

    private fun skipExactly(input: InputStream, n: Long) {
        var left = n
        val buf = ByteArray(8192)
        while (left > 0) {
            val r = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (r < 0) throw EOFException("Truncated tar data")
            left -= r
        }
    }
}
