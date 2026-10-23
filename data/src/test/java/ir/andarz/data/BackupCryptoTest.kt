package ir.andarz.data

import ir.andarz.data.backup.BackupCrypto
import ir.andarz.data.backup.BackupError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.security.SecureRandom
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The `crypto` vectors and the container rules of 8.3 / 10.5, on the JVM. */
class BackupCryptoTest {
    private val crypto: JsonObject by lazy {
        val text = javaClass.getResource("/andarz_golden_vectors.json")!!.readText(Charsets.UTF_8)
        Json.parseToJsonElement(text).jsonObject["crypto"]!!.jsonObject
    }

    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun JsonObject.s(k: String) = this[k]!!.jsonPrimitive.content

    @Test
    fun pbkdf2Vector() {
        val v = crypto["pbkdf2"]!!.jsonObject
        val key = BackupCrypto.pbkdf2(BackupCrypto.passwordBytes(v.s("password")), hex(v.s("saltHex")), v.s("iterations").toInt(), v.s("dkLen").toInt())
        assertContentEquals(hex(v.s("expectedKeyHex")), key)
    }

    @Test
    fun aesGcmVector() {
        val v = crypto["aesGcm"]!!.jsonObject
        assertEquals("andarz-backup-v1", v.s("aadUtf8"))
        val ct = BackupCrypto.aesGcmEncrypt(hex(v.s("keyHex")), hex(v.s("nonceHex")), v.s("plaintextUtf8").toByteArray(Charsets.UTF_8))
        assertContentEquals(hex(v.s("expectedCiphertextAndTagHex")), ct)
        val back = BackupCrypto.aesGcmDecrypt(hex(v.s("keyHex")), hex(v.s("nonceHex")), ct)
        assertEquals(v.s("plaintextUtf8"), String(back, Charsets.UTF_8))
    }

    @Test
    fun productionDefaultsMatch() {
        val d = crypto["productionDefaults"]!!.jsonObject
        assertEquals(d.s("pbkdf2Iterations").toInt(), BackupCrypto.ITERATIONS)
        assertEquals(d.s("saltBytes").toInt(), BackupCrypto.SALT_BYTES)
        assertEquals(d.s("nonceBytes").toInt(), BackupCrypto.NONCE_BYTES)
    }

    private val payload = """{"format":"andarz-backup","version":1}""".toByteArray()

    @Test
    fun roundTripAndHeader() {
        val f = BackupCrypto.seal(payload, "correct horse", withShots = true, iterations = 10_000)
        assertContentEquals(BackupCrypto.MAGIC, f.copyOfRange(0, 4))
        assertEquals(1, f[4].toInt()); assertEquals(1, f[5].toInt())
        assertEquals(3, f[38].toInt())
        val o = BackupCrypto.open(f, "correct horse")
        assertContentEquals(payload, o.payloadJson); assertTrue(o.withShots); assertEquals(10_000, o.iterations)
    }

    @Test
    fun saltAndNonceAreFreshEveryTime() {
        val a = BackupCrypto.seal(payload, "correct horse", false, 10_000, SecureRandom())
        val b = BackupCrypto.seal(payload, "correct horse", false, 10_000, SecureRandom())
        assertTrue(!a.copyOfRange(10, 38).contentEquals(b.copyOfRange(10, 38)))
    }

    @Test
    fun failuresHaveTheirOwnErrors() {
        val f = BackupCrypto.seal(payload, "correct horse", false, 10_000)
        assertFailsWith<BackupError.WrongPassword> { BackupCrypto.open(f, "wrong horse!") }
        val flipped = f.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertFailsWith<BackupError.WrongPassword> { BackupCrypto.open(flipped, "correct horse") }
        assertFailsWith<BackupError.Corrupt> { BackupCrypto.open(f.copyOf(40), "correct horse") }
        val bad = f.copyOf().also { it[0] = 'X'.code.toByte() }
        assertFailsWith<BackupError.NotAndarz> { BackupCrypto.open(bad, "correct horse") }
        val newer = f.copyOf().also { it[4] = 2 }
        assertFailsWith<BackupError.Version> { BackupCrypto.open(newer, "correct horse") }
        val lowIter = f.copyOf().also { it[6] = 0; it[7] = 0; it[8] = 0; it[9] = 1 }
        assertFailsWith<BackupError.Corrupt> { BackupCrypto.open(lowIter, "correct horse") }
    }

    @Test
    fun productionIterationsRoundTrip() {
        val start = System.nanoTime()
        val f = BackupCrypto.seal(payload, "andarz-test-password", false)
        assertContentEquals(payload, BackupCrypto.open(f, "andarz-test-password").payloadJson)
        println("600k PBKDF2 seal+open: ${(System.nanoTime() - start) / 1_000_000} ms")
    }

    @Test
    fun nfcAndDecomposedPasswordsDeriveTheSameKey() {
        // U+00E9 versus e + U+0301: one password typed two ways
        assertContentEquals(BackupCrypto.passwordBytes("caf\u00E9!!!!"), BackupCrypto.passwordBytes("cafe\u0301!!!!"))
    }
}
