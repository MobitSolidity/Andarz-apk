package ir.andarz.data.settings

import ir.andarz.data.backup.BackupCrypto
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * App-lock PIN (8.2): PBKDF2-HMAC-SHA256, 16-byte salt, 120,000 iterations, 32-byte hash, compared in
 * constant time. Only hash, salt and iteration count are stored; the PIN is never stored or logged.
 * Attempt policy (D-33): 5 free attempts, then 30 s doubling up to 30 min.
 */
object PinLock {
    const val ITERATIONS = 120_000
    const val FREE_ATTEMPTS = 5
    const val BASE_WAIT_MS = 30_000L
    const val MAX_WAIT_MS = 30 * 60_000L

    data class Hashed(val hash: String, val salt: String, val iterations: Int)

    fun valid(pin: String): Boolean = pin.length in 4..6 && pin.all { it in '0'..'9' }

    fun hash(pin: String, iterations: Int = ITERATIONS, random: SecureRandom = SecureRandom()): Hashed {
        require(valid(pin))
        val salt = ByteArray(16).also(random::nextBytes)
        val h = BackupCrypto.pbkdf2(pin.toByteArray(Charsets.US_ASCII), salt, iterations)
        return Hashed(b64(h), b64(salt), iterations)
    }

    fun verify(pin: String, stored: Hashed): Boolean {
        if (!valid(pin)) return false
        val salt = Base64.getDecoder().decode(stored.salt)
        val h = BackupCrypto.pbkdf2(pin.toByteArray(Charsets.US_ASCII), salt, stored.iterations)
        return MessageDigest.isEqual(h, Base64.getDecoder().decode(stored.hash))
    }

    /** The lock-out after the [failCount]-th consecutive failure; 0 while free attempts remain. */
    fun waitAfter(failCount: Int): Long {
        if (failCount < FREE_ATTEMPTS) return 0
        val steps = failCount - FREE_ATTEMPTS
        return (BASE_WAIT_MS shl steps.coerceAtMost(10)).coerceAtMost(MAX_WAIT_MS)
    }

    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)
}
