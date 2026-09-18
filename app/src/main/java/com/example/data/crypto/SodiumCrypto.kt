package com.example.data.crypto

import android.util.Base64
import java.security.SecureRandom

/**
 * Pure Kotlin implementation of Libsodium crypto_box_seal compatible with GitHub Actions Secrets API.
 * Encrypts a plaintext secret using Curve25519, XSalsa20, Poly1305, and Blake2b-24.
 */
object SodiumCrypto {

    private val random = SecureRandom()

    fun sealSecret(plaintext: String, recipientPublicKeyBase64: String): String {
        val messageBytes = plaintext.toByteArray(Charsets.UTF_8)
        val recipientPublicKey = Base64.decode(recipientPublicKeyBase64, Base64.DEFAULT)
        require(recipientPublicKey.size == 32) { "Recipient public key must be 32 bytes" }

        // 1. Generate ephemeral Curve25519 keypair
        val ephemeralSecretKey = ByteArray(32)
        random.nextBytes(ephemeralSecretKey)
        clamp(ephemeralSecretKey)

        val ephemeralPublicKey = ByteArray(32)
        cryptoScalarMultBase(ephemeralPublicKey, ephemeralSecretKey)

        // 2. Nonce = Blake2b-24(ephemeralPublicKey || recipientPublicKey)
        val nonceInput = ByteArray(64)
        System.arraycopy(ephemeralPublicKey, 0, nonceInput, 0, 32)
        System.arraycopy(recipientPublicKey, 0, nonceInput, 32, 32)
        val nonce = Blake2b.digest(nonceInput, 24)

        // 3. Shared secret = crypto_scalarmult(ephemeralSecretKey, recipientPublicKey)
        val sharedSecret = ByteArray(32)
        cryptoScalarMult(sharedSecret, ephemeralSecretKey, recipientPublicKey)

        // 4. crypto_box: XSalsa20-Poly1305
        val cipherWithMac = cryptoBoxAfterNm(messageBytes, nonce, sharedSecret)

        // 5. Sealed box = ephemeralPublicKey (32 bytes) || cipherWithMac
        val result = ByteArray(32 + cipherWithMac.size)
        System.arraycopy(ephemeralPublicKey, 0, result, 0, 32)
        System.arraycopy(cipherWithMac, 0, result, 32, cipherWithMac.size)

        return Base64.encodeToString(result, Base64.NO_WRAP)
    }

    private fun clamp(key: ByteArray) {
        key[0] = (key[0].toInt() and 248).toByte()
        key[31] = (key[31].toInt() and 127).toByte()
        key[31] = (key[31].toInt() or 64).toByte()
    }

    // --- Curve25519 implementation ---
    private val BASE_POINT = ByteArray(32).apply { this[0] = 9 }

    private fun cryptoScalarMultBase(q: ByteArray, n: ByteArray) {
        cryptoScalarMult(q, n, BASE_POINT)
    }

    private fun cryptoScalarMult(q: ByteArray, n: ByteArray, p: ByteArray) {
        val z = LongArray(32)
        val x = LongArray(80)
        val a = LongArray(16)
        val b = LongArray(16)
        val c = LongArray(16)
        val d = LongArray(16)
        val e = LongArray(16)
        val f = LongArray(16)

        for (i in 0 until 31) z[i] = (n[i].toInt() and 0xFF).toLong()
        z[31] = ((n[31].toInt() and 127) or 64).toLong()
        z[0] = (z[0] and 248)

        unpack25519(x, p)
        for (i in 0 until 16) {
            b[i] = x[i]
            d[i] = 0
            a[i] = 0
            c[i] = 0
        }
        a[0] = 1
        d[0] = 1

        for (i in 254 downTo 0) {
            val r = ((z[i ushr 3] ushr (i and 7)) and 1).toInt()
            sel25519(a, b, r)
            sel25519(c, d, r)
            add(e, a, c)
            sub(a, a, c)
            add(c, b, d)
            sub(b, b, d)
            sqr(d, e)
            sqr(f, a)
            mul(a, c, a)
            mul(c, b, e)
            add(e, a, c)
            sub(a, a, c)
            sqr(b, a)
            sub(c, d, f)
            mul121666(a, c)
            add(a, a, d)
            mul(c, c, a)
            mul(a, d, f)
            mul(d, b, x)
            sqr(b, e)
            sel25519(a, b, r)
            sel25519(c, d, r)
        }
        inv25519(c, c)
        mul(a, a, c)
        pack25519(q, a)
    }

    private fun unpack25519(o: LongArray, n: ByteArray) {
        for (i in 0 until 16) {
            o[i] = ((n[2 * i].toInt() and 0xFF).toLong() or ((n[2 * i + 1].toInt() and 0xFF).toLong() shl 8))
        }
        o[15] = o[15] and 0x7FFF
    }

    private fun pack25519(o: ByteArray, n: LongArray) {
        val t = LongArray(16)
        val m = LongArray(16)
        for (i in 0 until 16) t[i] = n[i]
        car25519(t)
        car25519(t)
        car25519(t)
        for (j in 0 until 2) {
            m[0] = t[0] - 0xFFED
            for (i in 1 until 15) {
                m[i] = t[i] - 0xFFFF - ((m[i - 1] shr 16) and 1)
                m[i - 1] = m[i - 1] and 0xFFFF
            }
            m[15] = t[15] - 0x7FFF - ((m[14] shr 16) and 1)
            val b = ((m[15] shr 16) and 1).toInt() xor 1
            m[14] = m[14] and 0xFFFF
            sel25519(t, m, b)
        }
        for (i in 0 until 16) {
            o[2 * i] = (t[i] and 0xFF).toByte()
            o[2 * i + 1] = ((t[i] shr 8) and 0xFF).toByte()
        }
    }

    private fun car25519(o: LongArray) {
        var c: Long
        for (i in 0 until 16) {
            o[i] += (1L shl 16)
            c = o[i] shr 16
            o[(i + 1) * (if (i < 15) 1 else 0)] += (c - 1) * (if (i < 15) 1 else 38)
            o[i] -= c shl 16
        }
    }

    private fun sel25519(p: LongArray, q: LongArray, b: Int) {
        val mask = (-(b.toLong()))
        for (i in 0 until 16) {
            val t = mask and (p[i] xor q[i])
            p[i] = p[i] xor t
            q[i] = q[i] xor t
        }
    }

    private fun add(o: LongArray, a: LongArray, b: LongArray) {
        for (i in 0 until 16) o[i] = a[i] + b[i]
    }

    private fun sub(o: LongArray, a: LongArray, b: LongArray) {
        for (i in 0 until 16) o[i] = a[i] - b[i]
    }

    private fun mul(o: LongArray, a: LongArray, b: LongArray) {
        val t = LongArray(31)
        for (i in 0 until 16) {
            for (j in 0 until 16) {
                t[i + j] += a[i] * b[j]
            }
        }
        for (i in 0 until 15) {
            t[i] += 38 * t[i + 16]
        }
        for (i in 0 until 16) o[i] = t[i]
        car25519(o)
        car25519(o)
    }

    private fun sqr(o: LongArray, a: LongArray) {
        mul(o, a, a)
    }

    private fun mul121666(o: LongArray, a: LongArray) {
        val t = LongArray(16)
        for (i in 0 until 16) t[i] = a[i] * 121666L
        car25519(t)
        car25519(t)
        for (i in 0 until 16) o[i] = t[i]
    }

    private fun inv25519(o: LongArray, i: LongArray) {
        val a = LongArray(16)
        val b = LongArray(16)
        val c = LongArray(16)
        for (j in 0 until 16) a[j] = i[j]
        for (j in 253 downTo 0) {
            sqr(a, a)
            if (j != 2 && j != 4) mul(a, a, i)
        }
        for (j in 0 until 16) o[j] = a[j]
    }

    // --- XSalsa20 + Poly1305 ---
    private fun cryptoBoxAfterNm(m: ByteArray, nonce: ByteArray, k: ByteArray): ByteArray {
        // HSalsa20 on first 16 bytes of nonce to get Salsa20 subkey
        val subkey = ByteArray(32)
        hsalsa20(subkey, nonce, k)

        // Salsa20 on remaining 8 bytes of nonce
        val s20Nonce = ByteArray(8)
        System.arraycopy(nonce, 16, s20Nonce, 0, 8)

        // Pad message with 32 zeros per tweetnacl / libsodium
        val padded = ByteArray(32 + m.size)
        System.arraycopy(m, 0, padded, 32, m.size)

        val cipherPadded = ByteArray(padded.size)
        salsa20Xor(cipherPadded, padded, padded.size, s20Nonce, subkey)

        // Poly1305 MAC generated using first 32 bytes of salsa20 stream
        val polyKey = ByteArray(32)
        System.arraycopy(cipherPadded, 0, polyKey, 0, 32)

        val mac = Poly1305.mac(cipherPadded, 32, cipherPadded.size - 32, polyKey)

        // Output: 16 bytes MAC + ciphertext (starts at offset 32)
        val result = ByteArray(16 + m.size)
        System.arraycopy(mac, 0, result, 0, 16)
        System.arraycopy(cipherPadded, 32, result, 16, m.size)
        return result
    }

    private fun hsalsa20(out: ByteArray, in16: ByteArray, k: ByteArray) {
        val x = IntArray(16)
        val constants = intArrayOf(0x61707865, 0x33312064, 0x79622d32, 0x6b206574)

        for (i in 0 until 4) x[i * 5] = constants[i]
        for (i in 0 until 4) {
            x[1 + i] = loadLittleEndian32(k, i * 4)
            x[6 + i] = loadLittleEndian32(in16, i * 4)
            x[11 + i] = loadLittleEndian32(k, 16 + i * 4)
        }

        for (i in 0 until 10) {
            quarterRound(x, 0, 4, 8, 12)
            quarterRound(x, 5, 9, 13, 1)
            quarterRound(x, 10, 14, 2, 6)
            quarterRound(x, 15, 3, 7, 11)
            quarterRound(x, 0, 1, 2, 3)
            quarterRound(x, 5, 6, 7, 4)
            quarterRound(x, 10, 11, 12, 9)
            quarterRound(x, 15, 12, 13, 14)
        }

        storeLittleEndian32(out, 0, x[0])
        storeLittleEndian32(out, 4, x[5])
        storeLittleEndian32(out, 8, x[10])
        storeLittleEndian32(out, 12, x[15])
        storeLittleEndian32(out, 16, x[6])
        storeLittleEndian32(out, 20, x[7])
        storeLittleEndian32(out, 24, x[8])
        storeLittleEndian32(out, 28, x[9])
    }

    private fun salsa20Xor(out: ByteArray, inBytes: ByteArray, len: Int, nonce8: ByteArray, k32: ByteArray) {
        val block = ByteArray(64)
        val x = IntArray(16)
        val constants = intArrayOf(0x61707865, 0x33312064, 0x79622d32, 0x6b206574)

        var pos = 0
        var counter = 0L

        while (pos < len) {
            for (i in 0 until 4) x[i * 5] = constants[i]
            for (i in 0 until 4) {
                x[1 + i] = loadLittleEndian32(k32, i * 4)
                x[11 + i] = loadLittleEndian32(k32, 16 + i * 4)
            }
            x[6] = loadLittleEndian32(nonce8, 0)
            x[7] = loadLittleEndian32(nonce8, 4)
            x[8] = (counter and 0xFFFFFFFFL).toInt()
            x[9] = (counter ushr 32).toInt()

            val z = x.clone()
            for (i in 0 until 10) {
                quarterRound(z, 0, 4, 8, 12)
                quarterRound(z, 5, 9, 13, 1)
                quarterRound(z, 10, 14, 2, 6)
                quarterRound(z, 15, 3, 7, 11)
                quarterRound(z, 0, 1, 2, 3)
                quarterRound(z, 5, 6, 7, 4)
                quarterRound(z, 10, 11, 12, 9)
                quarterRound(z, 15, 12, 13, 14)
            }

            for (i in 0 until 16) storeLittleEndian32(block, i * 4, z[i] + x[i])

            val chunk = minOf(64, len - pos)
            for (i in 0 until chunk) {
                out[pos + i] = (inBytes[pos + i].toInt() xor block[i].toInt()).toByte()
            }
            pos += chunk
            counter++
        }
    }

    private fun quarterRound(x: IntArray, a: Int, b: Int, c: Int, d: Int) {
        x[b] = x[b] xor rotl(x[a] + x[d], 7)
        x[c] = x[c] xor rotl(x[b] + x[a], 9)
        x[d] = x[d] xor rotl(x[c] + x[b], 13)
        x[a] = x[a] xor rotl(x[d] + x[c], 18)
    }

    private fun rotl(v: Int, c: Int) = (v shl c) or (v ushr (32 - c))

    private fun loadLittleEndian32(b: ByteArray, off: Int): Int {
        return (b[off].toInt() and 0xFF) or
                ((b[off + 1].toInt() and 0xFF) shl 8) or
                ((b[off + 2].toInt() and 0xFF) shl 16) or
                ((b[off + 3].toInt() and 0xFF) shl 24)
    }

    private fun storeLittleEndian32(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v ushr 8) and 0xFF).toByte()
        b[off + 2] = ((v ushr 16) and 0xFF).toByte()
        b[off + 3] = ((v ushr 24) and 0xFF).toByte()
    }
}

// Poly1305 authenticator
internal object Poly1305 {
    fun mac(m: ByteArray, offset: Int, length: Int, key: ByteArray): ByteArray {
        var r0 = load16(key, 0) and 0x3ffffffL
        var r1 = (load16(key, 3) ushr 2) and 0x3ffff03L
        var r2 = (load16(key, 6) ushr 4) and 0x3ffc0ffL
        var r3 = (load16(key, 9) ushr 6) and 0x3f03fffL
        var r4 = (load16(key, 12) ushr 8) and 0x00fffffL

        var h0 = 0L
        var h1 = 0L
        var h2 = 0L
        var h3 = 0L
        var h4 = 0L

        var pos = offset
        var remaining = length

        while (remaining > 0) {
            val chunk = minOf(16, remaining)
            val block = ByteArray(17)
            System.arraycopy(m, pos, block, 0, chunk)
            block[chunk] = 1 // append 1 bit

            h0 += load16(block, 0) and 0x3ffffffL
            h1 += (load16(block, 3) ushr 2) and 0x3ffffffL
            h2 += (load16(block, 6) ushr 4) and 0x3ffffffL
            h3 += (load16(block, 9) ushr 6) and 0x3ffffffL
            h4 += (load16(block, 12) ushr 8)

            // Poly1305 multiplication modulo 2^130 - 5
            val d0 = h0 * r0 + h1 * (5 * r4) + h2 * (5 * r3) + h3 * (5 * r2) + h4 * (5 * r1)
            val d1 = h0 * r1 + h1 * r0 + h2 * (5 * r4) + h3 * (5 * r3) + h4 * (5 * r2)
            val d2 = h0 * r2 + h1 * r1 + h2 * r0 + h3 * (5 * r4) + h4 * (5 * r3)
            val d3 = h0 * r3 + h1 * r2 + h2 * r1 + h3 * r0 + h4 * (5 * r4)
            val d4 = h0 * r4 + h1 * r3 + h2 * r2 + h3 * r1 + h4 * r0

            var c = d0 shr 26; h0 = d0 and 0x3ffffffL
            var d = d1 + c; c = d shr 26; h1 = d and 0x3ffffffL
            d = d2 + c; c = d shr 26; h2 = d and 0x3ffffffL
            d = d3 + c; c = d shr 26; h3 = d and 0x3ffffffL
            d = d4 + c; c = d shr 26; h4 = d and 0x3ffffffL
            h0 += c * 5
            c = h0 shr 26
            h0 = h0 and 0x3ffffffL
            h1 += c

            pos += chunk
            remaining -= chunk
        }

        // Add s
        val s0 = load16(key, 16)
        val s1 = load16(key, 20)
        val s2 = load16(key, 24)
        val s3 = load16(key, 28)

        val f0 = (h0 or (h1 shl 26)) + s0
        val f1 = ((h1 ushr 6) or (h2 shl 20)) + s1
        val f2 = ((h2 ushr 12) or (h3 shl 14)) + s2
        val f3 = ((h3 ushr 18) or (h4 shl 8)) + s3

        val out = ByteArray(16)
        store32(out, 0, f0)
        store32(out, 4, f1)
        store32(out, 8, f2)
        store32(out, 12, f3)
        return out
    }

    private fun load16(b: ByteArray, o: Int): Long {
        return (b[o].toLong() and 0xff) or
                ((b[o + 1].toLong() and 0xff) shl 8) or
                ((b[o + 2].toLong() and 0xff) shl 16) or
                ((b[o + 3].toLong() and 0xff) shl 24)
    }

    private fun store32(b: ByteArray, o: Int, v: Long) {
        b[o] = (v and 0xff).toByte()
        b[o + 1] = ((v ushr 8) and 0xff).toByte()
        b[o + 2] = ((v ushr 16) and 0xff).toByte()
        b[o + 3] = ((v ushr 24) and 0xff).toByte()
    }
}

// RFC 7693 Blake2b implementation
internal object Blake2b {
    private val IV = longArrayOf(
        0x6a09e667f3bcc908L, -0x4498517a7b3558c0L, 0x3c6ef372fe94f82bL, -0x5ab00ac560aa0061L,
        0x510e527fade682d1L, -0x64fa9773d4c193e4L, 0x1f83d9abfb41bd6bL, 0x5be0cd19137e2179L
    )

    private val SIGMA = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
        intArrayOf(14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3),
        intArrayOf(11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4),
        intArrayOf(7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8),
        intArrayOf(9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13),
        intArrayOf(2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9),
        intArrayOf(12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11),
        intArrayOf(13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10),
        intArrayOf(6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5),
        intArrayOf(10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0),
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
        intArrayOf(14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3)
    )

    fun digest(data: ByteArray, outLen: Int): ByteArray {
        val h = IV.clone()
        h[0] = h[0] xor 0x01010000L xor (outLen.toLong())

        val block = ByteArray(128)
        val m = LongArray(16)
        val v = LongArray(16)

        var pos = 0
        var remaining = data.size

        while (remaining > 128) {
            System.arraycopy(data, pos, block, 0, 128)
            compress(h, block, 128L * (pos / 128 + 1), false, m, v)
            pos += 128
            remaining -= 128
        }

        val lastBlock = ByteArray(128)
        System.arraycopy(data, pos, lastBlock, 0, remaining)
        compress(h, lastBlock, data.size.toLong(), true, m, v)

        val out = ByteArray(outLen)
        for (i in 0 until outLen) {
            out[i] = ((h[i / 8] ushr (8 * (i % 8))) and 0xFF).toByte()
        }
        return out
    }

    private fun compress(h: LongArray, block: ByteArray, bytesTotal: Long, isLast: Boolean, m: LongArray, v: LongArray) {
        for (i in 0 until 16) {
            var w = 0L
            for (j in 0 until 8) {
                w = w or ((block[i * 8 + j].toLong() and 0xFF) shl (8 * j))
            }
            m[i] = w
        }

        System.arraycopy(h, 0, v, 0, 8)
        System.arraycopy(IV, 0, v, 8, 8)
        v[12] = v[12] xor bytesTotal
        if (isLast) v[14] = v[14] xor -1L

        for (round in 0 until 12) {
            val s = SIGMA[round]
            g(v, 0, 4, 8, 12, m[s[0]], m[s[1]])
            g(v, 1, 5, 9, 13, m[s[2]], m[s[3]])
            g(v, 2, 6, 10, 14, m[s[4]], m[s[5]])
            g(v, 3, 7, 11, 15, m[s[6]], m[s[7]])
            g(v, 0, 5, 10, 15, m[s[8]], m[s[9]])
            g(v, 1, 6, 11, 12, m[s[10]], m[s[11]])
            g(v, 2, 7, 8, 13, m[s[12]], m[s[13]])
            g(v, 3, 4, 9, 14, m[s[14]], m[s[15]])
        }

        for (i in 0 until 8) {
            h[i] = h[i] xor v[i] xor v[i + 8]
        }
    }

    private fun g(v: LongArray, a: Int, b: Int, c: Int, d: Int, x: Long, y: Long) {
        v[a] = v[a] + v[b] + x
        v[d] = rotr64(v[d] xor v[a], 32)
        v[c] = v[c] + v[d]
        v[b] = rotr64(v[b] xor v[c], 24)
        v[a] = v[a] + v[b] + y
        v[d] = rotr64(v[d] xor v[a], 16)
        v[c] = v[c] + v[d]
        v[b] = rotr64(v[b] xor v[c], 63)
    }

    private fun rotr64(w: Long, c: Int): Long = (w ushr c) or (w shl (64 - c))
}
