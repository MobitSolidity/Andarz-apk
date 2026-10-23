package ir.andarz.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.text.Normalizer
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

sealed class BackupError(msg: String) : Exception(msg) {
    class NotAndarz : BackupError("not an Andarz backup")
    class Version : BackupError("newer backup format")
    class WrongPassword : BackupError("wrong password or damaged file")
    class Corrupt(why: String) : BackupError(why)
}

/**
 * The `ANDZ` v1 container of 8.3 (D-35): PBKDF2-HMAC-SHA256 → AES-256-GCM with AAD `andarz-backup-v1`.
 *
 * PBKDF2 is implemented over `Mac("HmacSHA256")` (RFC 8018 5.2) rather than `SecretKeyFactory`, because
 * Android's providers handle non-ASCII passwords differently from the JDK's (10.5); the password is the
 * UTF-8 of its NFC form, which pins the encoding on every platform (D-46).
 */
object BackupCrypto {
    val MAGIC = byteArrayOf('A'.code.toByte(), 'N'.code.toByte(), 'D'.code.toByte(), 'Z'.code.toByte())
    const val VERSION: Byte = 1
    const val KDF_PBKDF2: Byte = 1
    const val ITERATIONS = 600_000
    const val MIN_ITERATIONS = 10_000
    const val MAX_ITERATIONS = 5_000_000
    const val SALT_BYTES = 16
    const val NONCE_BYTES = 12
    const val TAG_BITS = 128
    const val HEADER = 39
    const val MAX_FILE_BYTES = 100L * 1024 * 1024
    const val FLAG_GZIP = 1
    const val FLAG_SHOTS = 2
    val AAD = "andarz-backup-v1".toByteArray(Charsets.US_ASCII)

    fun passwordBytes(password: String): ByteArray =
        Normalizer.normalize(password, Normalizer.Form.NFC).toByteArray(Charsets.UTF_8)

    /** PBKDF2-HMAC-SHA256 (RFC 8018 5.2). */
    fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, dkLen: Int = 32): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        // SecretKeySpec rejects an empty key; passwords are at least 8 characters (8.3), so it never is.
        require(password.isNotEmpty()) { "empty password" }
        mac.init(SecretKeySpec(password, "HmacSHA256"))
        val hLen = mac.macLength
        val blocks = (dkLen + hLen - 1) / hLen
        val out = ByteArray(dkLen)
        for (i in 1..blocks) {
            mac.update(salt)
            mac.update(ByteBuffer.allocate(4).putInt(i).array())
            var u = mac.doFinal()
            val t = u.copyOf()
            for (k in 2..iterations) {
                u = mac.doFinal(u)
                for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
            val off = (i - 1) * hLen
            System.arraycopy(t, 0, out, off, minOf(hLen, dkLen - off))
        }
        return out
    }

    fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray = AAD): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        c.updateAAD(aad)
        return c.doFinal(plaintext)
    }

    fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, ciphertextAndTag: ByteArray, aad: ByteArray = AAD): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        c.updateAAD(aad)
        return try { c.doFinal(ciphertextAndTag) } catch (e: AEADBadTagException) { throw BackupError.WrongPassword() }
    }

    /** Builds a container. [iterations] is a parameter so tests can use the vector's 10,000. */
    fun seal(payloadJson: ByteArray, password: String, withShots: Boolean, iterations: Int = ITERATIONS, random: SecureRandom = SecureRandom()): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val key = pbkdf2(passwordBytes(password), salt, iterations)
        val gz = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(payloadJson) } }.toByteArray()
        val ct = aesGcmEncrypt(key, nonce, gz)
        val flags = FLAG_GZIP or (if (withShots) FLAG_SHOTS else 0)
        return ByteBuffer.allocate(HEADER + ct.size)
            .put(MAGIC).put(VERSION).put(KDF_PBKDF2).putInt(iterations).put(salt).put(nonce).put(flags.toByte()).put(ct)
            .array()
    }

    data class Opened(val payloadJson: ByteArray, val withShots: Boolean, val iterations: Int)

    fun open(file: ByteArray, password: String): Opened {
        if (file.size > MAX_FILE_BYTES) throw BackupError.Corrupt("file too large")
        if (file.size < 4 || !file.copyOfRange(0, 4).contentEquals(MAGIC)) throw BackupError.NotAndarz()
        if (file.size < HEADER + 16) throw BackupError.Corrupt("truncated")
        val b = ByteBuffer.wrap(file)
        b.position(4)
        val version = b.get()
        if (version > VERSION) throw BackupError.Version()
        if (version != VERSION) throw BackupError.Corrupt("unknown version")
        if (b.get() != KDF_PBKDF2) throw BackupError.Corrupt("unknown kdf")
        val iterations = b.int
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) throw BackupError.Corrupt("iterations out of range")
        val salt = ByteArray(SALT_BYTES).also { b.get(it) }
        val nonce = ByteArray(NONCE_BYTES).also { b.get(it) }
        val flags = b.get().toInt()
        val ct = ByteArray(b.remaining()).also { b.get(it) }
        val key = pbkdf2(passwordBytes(password), salt, iterations)
        val plain = aesGcmDecrypt(key, nonce, ct)
        val json = if (flags and FLAG_GZIP != 0) GZIPInputStream(ByteArrayInputStream(plain)).use { it.readBytes() } else plain
        return Opened(json, flags and FLAG_SHOTS != 0, iterations)
    }
}
