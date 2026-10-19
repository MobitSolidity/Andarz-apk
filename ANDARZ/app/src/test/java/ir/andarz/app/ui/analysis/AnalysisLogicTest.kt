package ir.andarz.app.ui.analysis

import ir.andarz.data.db.PlanSnapshot
import ir.andarz.data.db.TradeEntity
import ir.andarz.engine.ChangeKind
import ir.andarz.engine.GateReason
import ir.andarz.engine.Params
import ir.andarz.engine.Side
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalysisLogicTest {
    private val p = Params.DEFAULTS
    private val zone = ZoneOffset.ofHoursMinutes(3, 30)

    // ---------------------------------------------------------------- calculator (5.7, D-36)

    @Test
    fun `worked example and the fee impact`() {
        val r = CalcLogic.of(CalcDraft(Side.LONG, "2000", "100", "96", "108", "5", "1", "0.12"), p, p.riskPct)
        assertTrue(r.allowed)
        assertEquals(500.0, r.plan!!.notionalMax!!, 1e-9)
        assertEquals(2.0, r.plan!!.rr!!, 1e-12)
        assertEquals(39.4 / 20.6, r.rrAfterFees!!, 1e-12)
        assertEquals(5, r.advisor!!.recommended)
    }

    @Test
    fun `zero fee equals rr and no fee shows nothing`() {
        val zero = CalcLogic.of(CalcDraft(Side.LONG, "2000", "100", "96", "108", "5", "", "0"), p, p.riskPct)
        assertEquals(zero.plan!!.rr!!, zero.rrAfterFees!!, 1e-12)
        val none = CalcLogic.of(CalcDraft(Side.LONG, "2000", "100", "96", "108", "5", "", ""), p, p.riskPct)
        assertNull(none.rrAfterFees)
    }

    @Test
    fun `persian digits parse, entry equal to stop blanks the plan, wrong direction is flagged`() {
        val fa = CalcLogic.of(CalcDraft(Side.LONG, "\u06F2\u06F0\u06F0\u06F0", "\u06F1\u06F0\u06F0", "\u06F9\u06F6", "\u06F1\u06F0\u06F8", "\u06F5"), p, p.riskPct)
        assertTrue(fa.valid)
        val eq = CalcLogic.of(CalcDraft(Side.LONG, "2000", "100", "100", "108", "5"), p, p.riskPct)
        assertTrue(eq.entryEqSl); assertFalse(eq.valid)
        val wrong = CalcLogic.of(CalcDraft(Side.SHORT, "2000", "100", "96", "108", "5"), p, p.riskPct)
        assertTrue(wrong.slWrong && wrong.tpWrong)
        assertTrue(GateReason.DIRECTION in wrong.reasons)
    }

    @Test
    fun `low reward is denied with RR`() {
        val r = CalcLogic.of(CalcDraft(Side.LONG, "2000", "100", "96", "105.2", "5"), p, p.riskPct)
        assertEquals(listOf(GateReason.RR), r.reasons)
    }

    // ---------------------------------------------------------------- periods (D-31)

    @Test
    fun `week starts on Saturday and month follows the display calendar`() {
        val wed = LocalDate.of(2026, 10, 14)
        assertEquals(LocalDate.of(2026, 10, 10) to wed, Periods.range(Period.WEEK, wed, true))
        // 1 Aban 1405 = 23 October 2026, so on 14 October the Jalali month (Mehr) began on 23 September
        assertEquals(LocalDate.of(2026, 9, 23), Periods.range(Period.MONTH, wed, true).first)
        assertEquals(LocalDate.of(2026, 10, 1), Periods.range(Period.MONTH, wed, false).first)
        assertEquals(LocalDate.of(2026, 9, 15), Periods.range(Period.LAST30, wed, true).first)
        val c = Periods.range(Period.CUSTOM, wed, true, LocalDate.of(2026, 10, 20) to LocalDate.of(2026, 10, 1))
        assertEquals(LocalDate.of(2026, 10, 1) to LocalDate.of(2026, 10, 20), c)
        assertEquals(LocalDate.of(2026, 8, 23) to LocalDate.of(2026, 9, 22), Periods.previousMonth(wed, true))
    }

    // ---------------------------------------------------------------- insights

    private fun closed(id: String, exit: String, net: Double, r: Double, ok: Boolean = true, mistake: String = "M00", symbol: String = "BTCUSDT", setup: String = "s1"): TradeEntity {
        val exitT = OffsetDateTime.parse(exit).toInstant().toEpochMilli()
        return TradeEntity(
            id = id, account = "REAL", status = "CLOSED", symbol = symbol, side = "LONG", kind = "INTRADAY", setupId = setup,
            timeframe = "15m", htfTrend = "UP", counterTrendReason = null, emotion = "CALM", checklistMask = 0xFF, chk = 8,
            equity = 2000.0, plannedEntry = 100.0, slInitial = 96.0, tp = 108.0, lev = 5.0, riskPctChosen = 0.01, riskPctCap = 0.01,
            gradeA = true, entryOrder = "LIMIT", isolated = true, slTpOnExchange = true, canMonitor = true,
            plan = PlanSnapshot(0.04, 2.0, 20.0, 500.0, 100.0, 0.05, 80.5, 0.195, 4.875), gateReasons = "", gatePlanOk = true,
            entryAllowed = true, gateAllowed = true, entry = 100.0, notional = 500.0, entryTime = exitT - 3_600_000, slCurrent = 96.0,
            rrisk = 20.0, rriskp = 0.01, verdict = "YES", exitTime = exitT, net = net, r = r, ok = ok, mistake = mistake,
            createdAt = exitT - 3_700_000, updatedAt = exitT,
        )
    }

    @Test
    fun `insights for a period`() {
        val ts = listOf(
            closed("a", "2026-10-11T10:00:00+03:30", 40.0, 2.0),
            closed("b", "2026-10-12T15:00:00+03:30", -20.0, -1.0, ok = false, mistake = "M01", symbol = "ETHUSDT"),
            closed("c", "2026-10-13T09:00:00+03:30", -10.0, -0.5, mistake = "M01"),
            closed("old", "2026-09-01T10:00:00+03:30", 100.0, 3.5),
        )
        val ins = InsightsLogic.of(ts, LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 14), p, zone)
        assertEquals(3, ins.kpis.n)
        // the period's curve starts at the equity after the September trade
        assertEquals(listOf(2100.0, 2140.0, 2120.0, 2110.0), ins.equity)
        assertEquals(0.0, ins.drawdown[0], 1e-12)
        assertEquals(30.0 / 2140.0, ins.drawdown[2], 1e-12)
        assertEquals(listOf(0, 0, 2, 0, 0, 1, 0), ins.rHistogram.map { it.count })
        assertEquals("M01" to 2, ins.topMistake)
        assertEquals(1, ins.weeklyCompliance.size)
        assertEquals(2.0 / 3.0, ins.weeklyCompliance[0].value, 1e-12)
        assertEquals(listOf("BTCUSDT", "ETHUSDT"), ins.bySymbol.map { it.label })
        assertEquals(1.5 / 2, ins.bySymbol[0].value, 1e-12)
        val s = ins.setups.single()
        assertEquals(3, s.n); assertEquals(40.0 / 30.0, s.profitFactor!!, 1e-12)
        assertTrue(InsightsLogic.of(ts, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21), p, zone).empty)
    }

    @Test
    fun `R bins`() {
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), listOf(-2.5, -2.0, -0.1, 0.0, 1.0, 2.99, 3.0).map(InsightsLogic::bin))
    }

    // ---------------------------------------------------------------- rules (F5 logic)

    private val riskPct = ParamDef("Risk_Pct", "", "", "pct", 0.01, 0.001, 0.02, 0.0005, "core", "3-1", false)
    private val riskMax = ParamDef("Risk_Max", "", "", "pct", 0.02, 0.005, 0.03, 0.0025, "core", "3-1", false)
    private val cooldown = ParamDef("Cooldown_Min", "", "", "min", 30.0, 5.0, 240.0, 5.0, "governor", "5-1", false)

    @Test
    fun `tightening, loosening and bounds`() {
        val t = RulesLogic.check(riskPct, "0.5", p)
        assertEquals(0.005, t.value!!, 1e-15); assertNull(t.invalid); assertEquals(ChangeKind.TIGHTEN, t.kind)
        assertEquals(ChangeKind.LOOSEN, RulesLogic.check(riskPct, "1.5", p).kind)
        assertEquals(ChangeKind.NONE, RulesLogic.check(riskPct, "1", p).kind)
        assertEquals(ParamInvalid.ABOVE_MAX, RulesLogic.check(riskPct, "3", p).invalid)
        assertEquals(ParamInvalid.BELOW_MIN, RulesLogic.check(cooldown, "1", p).invalid)
        assertEquals(ChangeKind.TIGHTEN, RulesLogic.check(cooldown, "60", p).kind)
        assertEquals(ParamInvalid.RISK_ORDER, RulesLogic.check(riskMax, "0.75", p).invalid)
        assertEquals(ParamInvalid.NUMBER, RulesLogic.check(riskPct, "abc", p).invalid)
        assertEquals("1", RulesLogic.fieldText(riskPct, 0.01))
        assertEquals(0.007, RulesLogic.check(riskPct, "0.7", p).value!!, 0.0)
    }

    @Test
    fun `reset applies tightening before loosening`() {
        val cur = p.with(mapOf("Risk_Pct" to 0.005, "Risk_Max" to 0.03, "Cooldown_Min" to 30.0))
        val changes = RulesLogic.resetOrder(RulesLogic.resetChanges(listOf(riskPct, riskMax, cooldown), cur), cur)
        assertEquals(listOf("Risk_Max", "Risk_Pct"), changes.map { it.first.key })
    }

    // ---------------------------------------------------------------- watch-list and reviews

    @Test
    fun `liquidity filter and recheck`() {
        assertEquals(true, WatchLogic.liquidity(2e8, 1.0, 1e8, 0.7, 3.0).pass)
        assertEquals(false, WatchLogic.liquidity(5e7, 1.0, 1e8, 0.7, 3.0).pass)
        assertEquals(false, WatchLogic.liquidity(2e8, 3.5, 1e8, 0.7, 3.0).pass)
        assertNull(WatchLogic.liquidity(null, null, 1e8, 0.7, 3.0).pass)
        val now = OffsetDateTime.parse("2026-10-14T09:00:00+03:30").toInstant()
        assertTrue(WatchLogic.needsRecheck(null, now))
        assertTrue(WatchLogic.needsRecheck(now.toEpochMilli() - 31 * 86_400_000L, now))
        assertFalse(WatchLogic.needsRecheck(now.toEpochMilli() - 86_400_000L, now))
    }

    @Test
    fun `due reviews`() {
        val wed = LocalDate.of(2026, 10, 14)
        val none = ReviewLogic.due(wed, true, emptyList())
        assertTrue(none.daily)
        assertEquals(LocalDate.of(2026, 10, 3), none.weekly)
        assertEquals(LocalDate.of(2026, 8, 23), none.monthly)
        val done = ReviewLogic.due(wed, true, listOf("DAILY" to wed, "WEEKLY" to LocalDate.of(2026, 10, 3), "MONTHLY" to LocalDate.of(2026, 8, 23)))
        assertFalse(done.daily); assertNull(done.weekly); assertNull(done.monthly)
        // on Friday the current week is the one due
        assertEquals(LocalDate.of(2026, 10, 10), ReviewLogic.due(LocalDate.of(2026, 10, 16), true, emptyList()).weekly)
        val choices = ReviewLogic.weekChoices(wed, setOf(LocalDate.of(2026, 10, 3)))
        assertEquals(LocalDate.of(2026, 10, 10), choices.first())
        assertFalse(LocalDate.of(2026, 10, 3) in choices)
        assertNotNull(choices.lastOrNull())
    }
}
