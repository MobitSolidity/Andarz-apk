package ir.andarz.app.ui

import ir.andarz.app.ui.journal.JournalFilter
import ir.andarz.app.ui.journal.JournalSort
import ir.andarz.app.ui.wizard.WizardCalc
import ir.andarz.app.ui.wizard.WizardDraft
import ir.andarz.data.db.PlanSnapshot
import ir.andarz.data.db.TradeEntity
import ir.andarz.engine.GateReason
import ir.andarz.engine.Side
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 10.4 item 8 (wizard: items 6–7 derived, emotion blocks item 8, counter-trend half risk) and the journal filter. */
class WizardCalcTest {
    private val calc = WizardCalc()

    private val worked = WizardDraft(
        equity = "2000", entry = "100", sl = "96", tp = "108", lev = "5", riskPct = "1", isolated = true,
        manualMask = 0b1001_1111, htfTrend = "UP",
    )

    @Test
    fun workedExampleIsAllowedWithDerivedItems() {
        val c = calc.of(worked, null)
        assertTrue(c.item6); assertTrue(c.item7); assertEquals(8, c.chk)
        assertTrue(c.allowed, "reasons ${c.reasons}")
        assertEquals(500.0, c.plan!!.notionalMax!!, 1e-9)
    }

    @Test
    fun itemSixFollowsTheRewardToRisk() {
        val c = calc.of(worked.copy(tp = "105"), null) // R:R 1.25
        assertFalse(c.item6)
        assertEquals(listOf(GateReason.CHECKLIST, GateReason.RR), c.reasons)
    }

    @Test
    fun itemSevenNeedsIsolatedAndASafeLeverage() {
        assertFalse(calc.of(worked.copy(isolated = false), null).item7)
        assertFalse(calc.of(worked.copy(lev = "20"), null).item7)
    }

    @Test
    fun aNonCalmEmotionMakesItemEightUntickable() {
        val c = calc.of(worked.copy(emotion = "FOMO"), null)
        assertEquals(7, c.chk)
        assertTrue(GateReason.CHECKLIST in c.reasons)
    }

    @Test
    fun counterTrendCapsTheRiskAtHalf() {
        val c = calc.of(worked.copy(htfTrend = "DOWN"), null)
        assertTrue(c.counterTrend)
        assertEquals(0.005, c.riskPct, 1e-12)
        assertEquals(250.0, c.plan!!.notionalMax!!, 1e-9)
    }

    @Test
    fun persianDigitsAreAccepted() {
        val c = calc.of(worked.copy(entry = "\u06F1\u06F0\u06F0", sl = "\u06F9\u06F6"), null)
        assertTrue(c.allowed)
    }

    @Test
    fun entryEqualToStopIsReported() {
        val c = calc.of(worked.copy(sl = "100"), null)
        assertTrue(c.entryEqSl); assertFalse(c.allowed)
    }

    private fun row(id: String, status: String, r: Double?, time: Long, violations: List<String> = emptyList()) = TradeEntity(
        id = id, account = "REAL", status = status, symbol = "BTCUSDT", side = Side.LONG.name, kind = "INTRADAY", setupId = null,
        timeframe = "15m", htfTrend = "UP", counterTrendReason = null, emotion = "CALM", checklistMask = 255, chk = 8,
        equity = 2000.0, plannedEntry = 100.0, slInitial = 96.0, tp = 108.0, lev = 5.0, riskPctChosen = 0.01,
        riskPctCap = 0.01, gradeA = false, entryOrder = "LIMIT", isolated = true, slTpOnExchange = true, canMonitor = true,
        plan = PlanSnapshot(0.04, 2.0, 20.0, 500.0, 100.0, 0.05, 80.5, 0.195, 4.875), gateReasons = "", gatePlanOk = true,
        entryAllowed = true, gateAllowed = true, entryTime = time, r = r, violations = violations, createdAt = time, updatedAt = time,
    )

    @Test
    fun journalFilterAndSort() {
        val rows = listOf(row("a", "CLOSED", 1.0, 1), row("b", "CLOSED", -1.0, 2, listOf("V04")), row("c", "OPEN", null, 3))
        assertEquals(listOf("c", "b", "a"), JournalFilter().apply(rows) { "" }.map { it.id })
        assertEquals(listOf("a", "b"), JournalFilter(status = "CLOSED", sort = JournalSort.BEST_R).apply(rows) { "" }.map { it.id })
        assertEquals(listOf("b"), JournalFilter(hasViolation = true).apply(rows) { "" }.map { it.id })
        assertEquals(3, JournalFilter(query = "btc").apply(rows) { "" }.size)
    }
}
