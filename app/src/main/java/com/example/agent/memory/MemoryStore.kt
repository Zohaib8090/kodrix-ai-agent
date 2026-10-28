package com.example.agent.memory

import com.example.agent.Risk
import com.example.agent.Tool
import com.example.agent.ToolResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLDecoder

class MemoryException(message: String) : Exception(message)

/**
 * File-based agent memory, command-compatible with Anthropic's client-side memory tool
 * (view / create / str_replace / insert / delete / rename under /memories).
 * Everything lives under [root]; paths that try to escape it are rejected.
 */
class MemoryStore(private val root: File, private val maxFileBytes: Int = 64 * 1024) {

    init {
        root.mkdirs()
    }

    private val canonicalRoot: File = root.canonicalFile

    /** Maps a model-supplied "/memories/..." path to a real file inside [root], or throws. */
    fun resolve(path: String): File {
        var p = path.trim()
        // Decode percent-encoding repeatedly so %2e%2e%2f and double-encoded variants are caught.
        repeat(3) {
            val decoded = try {
                URLDecoder.decode(p.replace("+", "%2B"), "UTF-8")
            } catch (e: Exception) {
                throw MemoryException("Invalid path encoding")
            }
            if (decoded == p) return@repeat
            p = decoded
        }
        p = p.replace('\\', '/')
        if (p != "/memories" && !p.startsWith("/memories/")) {
            throw MemoryException("Path must start with /memories")
        }
        if (p.split('/').any { it == ".." }) throw MemoryException("Path traversal is not allowed")
        val rel = p.removePrefix("/memories").trimStart('/')
        val target = if (rel.isEmpty()) canonicalRoot else File(canonicalRoot, rel)
        val canonical = target.canonicalFile
        if (canonical != canonicalRoot && !canonical.path.startsWith(canonicalRoot.path + File.separator)) {
            throw MemoryException("Path escapes the memory directory")
        }
        return canonical
    }

    /** Resolves [path] and requires it to be an existing regular file. */
    private fun fileOnly(path: String): File {
        val f = resolve(path)
        if (!f.exists()) throw MemoryException("The path $path does not exist.")
        if (f.isDirectory) throw MemoryException("$path is a directory, not a file.")
        return f
    }

    fun view(path: String, viewRange: Pair<Int, Int>? = null): String {
        val f = resolve(path)
        if (!f.exists()) throw MemoryException("The path $path does not exist.")
        if (f.isDirectory) {
            val sb = StringBuilder("Directory listing of $path (2 levels):\n")
            f.walkTopDown().maxDepth(2).filter { it != f && !it.name.startsWith(".") }.sortedBy { it.path }.forEach {
                val rel = it.relativeTo(canonicalRoot).path.replace(File.separatorChar, '/')
                sb.append(if (it.isDirectory) "-\t/memories/$rel/\n" else "${it.length()}\t/memories/$rel\n")
            }
            return sb.toString()
        }
        val lines = f.readLines()
        val (from, to) = viewRange ?: (1 to lines.size)
        val sb = StringBuilder()
        for (i in from.coerceAtLeast(1)..to.coerceAtMost(lines.size)) {
            sb.append(String.format("%6d\t%s\n", i, lines[i - 1]))
        }
        return sb.toString()
    }

    fun create(path: String, text: String): String {
        if (text.toByteArray().size > maxFileBytes) throw MemoryException("File too large (max $maxFileBytes bytes)")
        val f = resolve(path)
        if (f == canonicalRoot || f.isDirectory) throw MemoryException("$path is a directory")
        f.parentFile?.mkdirs()
        f.writeText(text)
        return "File created successfully at: $path"
    }

    fun strReplace(path: String, oldStr: String, newStr: String): String {
        val f = fileOnly(path)
        val content = f.readText()
        if (oldStr.isEmpty()) throw MemoryException("old_str must not be empty")
        val first = content.indexOf(oldStr)
        if (first < 0) throw MemoryException("No replacement was performed, old_str did not appear verbatim in $path.")
        if (content.indexOf(oldStr, first + 1) >= 0) {
            throw MemoryException("No replacement was performed. Multiple occurrences of old_str in $path. Make it unique.")
        }
        val updated = content.replaceFirst(oldStr, newStr)
        if (updated.toByteArray().size > maxFileBytes) throw MemoryException("File would exceed $maxFileBytes bytes")
        f.writeText(updated)
        return "The memory file has been edited."
    }

    fun insert(path: String, line: Int, text: String): String {
        val f = fileOnly(path)
        val lines = f.readLines().toMutableList()
        if (line < 0 || line > lines.size) throw MemoryException("Invalid insert_line $line; file has ${lines.size} lines")
        lines.addAll(line, text.split('\n'))
        val out = lines.joinToString("\n")
        if (out.toByteArray().size > maxFileBytes) throw MemoryException("File would exceed $maxFileBytes bytes")
        f.writeText(out)
        return "The file $path has been edited."
    }

    fun delete(path: String): String {
        val f = resolve(path)
        if (f == canonicalRoot) throw MemoryException("Cannot delete the /memories root")
        if (!f.exists()) throw MemoryException("The path $path does not exist.")
        f.deleteRecursively()
        return "Successfully deleted $path"
    }

    fun rename(oldPath: String, newPath: String): String {
        val a = resolve(oldPath)
        val b = resolve(newPath)
        if (a == canonicalRoot || b == canonicalRoot) throw MemoryException("Cannot rename the /memories root")
        if (!a.exists()) throw MemoryException("The path $oldPath does not exist.")
        if (b.exists()) throw MemoryException("The destination $newPath already exists.")
        b.parentFile?.mkdirs()
        if (!a.renameTo(b)) throw MemoryException("Rename failed")
        return "Successfully renamed $oldPath to $newPath"
    }

    /** Short digest of what is stored, used to prime the system prompt at session start. */
    fun index(maxChars: Int = 3000): String {
        val sb = StringBuilder()
        canonicalRoot.walkTopDown().filter { it.isFile && !it.name.startsWith(".") }.sortedBy { it.path }.forEach {
            val rel = it.relativeTo(canonicalRoot).path.replace(File.separatorChar, '/')
            sb.append("/memories/$rel (${it.length()} bytes)\n")
        }
        return sb.toString().take(maxChars)
    }
}

/** Exposes [MemoryStore] to the model as a single `memory` tool. */
class MemoryTool(private val store: MemoryStore) : Tool {
    override val name = "memory"
    override val description =
        "Persistent memory across conversations, stored as files under /memories. " +
            "Commands: view, create, str_replace, insert, delete, rename. " +
            "Check memory at the start of a task and save durable facts about the user and their preferences."
    override val parameters: JSONObject = JSONObject(
        """
        {"type":"object","properties":{
          "command":{"type":"string","enum":["view","create","str_replace","insert","delete","rename"]},
          "path":{"type":"string","description":"Path under /memories"},
          "file_text":{"type":"string"},
          "old_str":{"type":"string"},
          "new_str":{"type":"string"},
          "insert_line":{"type":"integer"},
          "insert_text":{"type":"string"},
          "old_path":{"type":"string"},
          "new_path":{"type":"string"},
          "view_range":{"type":"array","items":{"type":"integer"}}
        },"required":["command"]}
        """.trimIndent()
    )

    override suspend fun run(args: JSONObject): ToolResult = try {
        val out = when (val cmd = args.optString("command")) {
            "view" -> {
                val r: JSONArray? = args.optJSONArray("view_range")
                val range = if (r != null && r.length() == 2) r.getInt(0) to r.getInt(1) else null
                store.view(args.optString("path", "/memories"), range)
            }
            "create" -> store.create(args.getString("path"), args.optString("file_text"))
            "str_replace" -> store.strReplace(args.getString("path"), args.getString("old_str"), args.optString("new_str"))
            "insert" -> store.insert(args.getString("path"), args.getInt("insert_line"), args.optString("insert_text"))
            "delete" -> store.delete(args.getString("path"))
            "rename" -> store.rename(args.getString("old_path"), args.getString("new_path"))
            else -> throw MemoryException("Unknown memory command '$cmd'")
        }
        ToolResult(out)
    } catch (e: MemoryException) {
        ToolResult(e.message ?: "memory error", isError = true)
    } catch (e: org.json.JSONException) {
        ToolResult("Missing or invalid argument: ${e.message}", isError = true)
    } catch (e: java.io.IOException) {
        ToolResult("Storage error: ${e.message}", isError = true)
    }
}
