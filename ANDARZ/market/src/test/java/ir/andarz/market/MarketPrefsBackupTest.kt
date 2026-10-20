package ir.andarz.market

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import ir.andarz.market.model.AlertWindow
import ir.andarz.market.model.CoinRule
import ir.andarz.market.model.RingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** K6.12 / K9.1 (a): market settings round trip; consent and the master switch never travel. */
class MarketPrefsBackupTest {
    private fun prefs(scope: CoroutineScope): MarketPrefs {
        val f = File.createTempFile("market", ".preferences_pb").also { it.delete() }
        return MarketPrefs(PreferenceDataStoreFactory.create(scope = scope) { f })
    }

    @Test
    fun `round trip without consent or switch`(): Unit = runBlocking {
        val scopeA = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val scopeB = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val a = prefs(scopeA)
            a.acceptConsent(1_000L)
            a.toggleWatch("dogecoin")
            a.setToman(true)
            a.updateSettings { it.copy(enabled = true, window = AlertWindow.H1, ring = RingMode.TARGETS, muteInQuiet = true, name = "x") }
            a.setRule(CoinRule("bitcoin", pct = 3f, above = 100_000.0))
            val json = a.exportJson()
            val o = JSONObject(json)
            assertFalse(o.has("consent_at") || o.has("consentAt") || o.has("market_enabled") || o.has("enabled"))

            val b = prefs(scopeB)
            b.importJson(json)
            assertTrue("dogecoin" in b.watch.first())
            assertTrue(b.toman.first())
            val s = b.alertSettings.first()
            assertTrue(s.enabled); assertEquals(AlertWindow.H1, s.window); assertEquals(RingMode.TARGETS, s.ring); assertTrue(s.muteInQuiet)
            assertEquals(100_000.0, b.rules.first()["bitcoin"]!!.above)
            val gate = b.gate.first()
            assertFalse(gate.enabled); assertNull(gate.consentAt)
        } finally { scopeA.cancel(); scopeB.cancel() }
    }

    @Test
    fun `unknown fields are ignored and invalid values keep the current ones`(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val p = prefs(scope)
            p.importJson("""{"version":9,"future":true,"alerts":{"pct":-5,"window":"NOPE","cooldown":0},"watch":["solana"]}""")
            val s = p.alertSettings.first()
            assertEquals(5f, s.thresholdPct); assertEquals(AlertWindow.M15, s.window); assertEquals(30, s.cooldownMin)
            assertEquals(setOf("solana"), p.watch.first())
            assertFailsWith<org.json.JSONException> { p.importJson("not json") }
        } finally { scope.cancel() }
    }
}
