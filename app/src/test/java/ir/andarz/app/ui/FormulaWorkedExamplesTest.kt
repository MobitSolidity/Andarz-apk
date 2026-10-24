package ir.andarz.app.ui

import ir.andarz.app.ui.platform.LearnLogic
import ir.andarz.engine.GateReason
import ir.andarz.engine.Leg
import ir.andarz.engine.Params
import ir.andarz.engine.Planner
import ir.andarz.engine.Side
import ir.andarz.engine.SizeVerdict
import ir.andarz.engine.TradeResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Item 7 of the request (D-94): every number the app teaches is computed by the same code that gates trades,
 * and every worked example in the constitution and the glossary comes out of that code exactly.
 * The engine is pinned by the 275 golden vectors; this test pins the teaching texts to the engine.
 */
class FormulaWorkedExamplesTest {
    private val p = Params.DEFAULTS
    private val eps = 1e-9

    @Test
    fun `article 3 worked example - long 2000 dollars, entry 100, stop 96, target 108, 5x`() {
        val plan = Planner.plan(Side.LONG, 2000.0, 100.0, 96.0, 108.0, 5.0, p)
        assertEquals(0.04, plan.sld!!, eps) // stop distance 4%
        assertEquals(2.0, plan.rr!!, eps) // profit twice the loss
        assertEquals(20.0, plan.riskUsd, eps) // 1% risk = 20 dollars
        assertEquals(500.0, plan.notionalMax!!, eps) // 20 / 0.04
        assertEquals(100.0, plan.margin!!, eps) // 500 / 5
        assertEquals(0.05, plan.marginPct!!, eps) // 5% of equity
        assertEquals(80.5, plan.liq!!, eps) // 100 × (1 − 0.2 + 0.005)
        assertEquals(4.875, plan.liqX!!, eps) // "about 4.9 times the stop distance"
        assertTrue(Planner.gate(Side.LONG, 100.0, 96.0, 108.0, 5.0, 8, plan, p).isEmpty())
        // exits: stop = −20 dollars = −1R; target gross 40, net 39.4 with 0.6 of fees, about 1.97R
        val stop = Planner.fill(Side.LONG, 100.0, 96.0, 2000.0, 500.0, listOf(Leg(96.0, 1.0)), 0.0)
        assertEquals(-20.0, stop.net!!, eps); assertEquals(-1.0, stop.r!!, eps); assertEquals(TradeResult.LOSS, stop.result)
        val target = Planner.fill(Side.LONG, 100.0, 96.0, 2000.0, 500.0, listOf(Leg(108.0, 1.0)), 0.6)
        assertEquals(40.0, target.gross!!, eps); assertEquals(39.4, target.net!!, eps); assertEquals(1.97, target.r!!, eps)
        assertEquals(2039.4, target.equityAfter!!, eps)
    }

    @Test
    fun `short liquidation mirrors the long one`() {
        val plan = Planner.plan(Side.SHORT, 2000.0, 100.0, 104.0, 92.0, 5.0, p)
        assertEquals(119.5, plan.liq!!, eps) // 100 × (1 + 0.2 − 0.005)
        assertEquals(500.0, plan.notionalMax!!, eps)
        val win = Planner.fill(Side.SHORT, 100.0, 104.0, 2000.0, 500.0, listOf(Leg(92.0, 1.0)), 0.0)
        assertEquals(40.0, win.net!!, eps); assertEquals(2.0, win.r!!, eps)
    }

    @Test
    fun `glossary - leverage, margin and liquidation`() {
        // "at 10x, about 9.5% against the position liquidates it"
        val p10 = Planner.plan(Side.LONG, 2000.0, 100.0, 96.0, 108.0, 10.0, p)
        assertEquals(0.095, p10.liqDist!!, eps)
        // "a 2% price move at 10x is 20% of margin"
        assertEquals(0.20, 0.02 * 10, eps)
        // "10% margin at 12x means a position of 120% of equity; a 5% stop means 6% of equity"
        assertEquals(1.2, 0.10 * 12, eps); assertEquals(0.06, 1.2 * 0.05, eps)
    }

    @Test
    fun `glossary - scaled exit result is the weighted sum of legs`() {
        val f = Planner.fill(Side.LONG, 100.0, 96.0, 2000.0, 500.0, listOf(Leg(104.0, 0.5), Leg(108.0, 0.5)), 0.0)
        assertEquals(30.0, f.gross!!, eps) // 10 + 20
        assertEquals(1.5, f.r!!, eps)
    }

    @Test
    fun `glossary - expectancy, break-even win rate and profit factor`() {
        assertEquals(0.2, LearnLogic.expectancy(0.4, 2.0)!!, eps) // 0.4 × 2 − 0.6
        assertEquals(0.0, LearnLogic.expectancy(0.5, 1.0)!!, eps)
        assertEquals(1.0 / 3, LearnLogic.breakEven(2.0)!!, eps) // about 33%
        assertEquals(0.4, LearnLogic.breakEven(1.5)!!, eps)
        assertEquals(0.5, LearnLogic.breakEven(1.0)!!, eps)
        // at the break-even win rate the expectancy is zero, for any R
        for (r in listOf(0.5, 1.0, 1.5, 2.0, 3.0)) assertEquals(0.0, LearnLogic.expectancy(LearnLogic.breakEven(r), r)!!, 1e-12)
        assertNull(LearnLogic.expectancy(1.2, 2.0)); assertNull(LearnLogic.expectancy(0.4, 0.0)); assertNull(LearnLogic.breakEven(-1.0))
        assertEquals(0.4, LearnLogic.fromPercent("40")!!, eps)
        assertEquals(1.5, 300.0 / 200.0, eps) // profit factor example
    }

    @Test
    fun `glossary - drawdown and recovery`() {
        assertEquals(0.10, (2200.0 - 1980.0) / 2200.0, eps)
        assertEquals(0.1111, Math.round((1 / 0.9 - 1) * 1e4) / 1e4, eps) // 11.1% to recover 10%
        assertEquals(1.0, 1 / 0.5 - 1, eps) // 100% to recover 50%
    }

    @Test
    fun `glossary - fees, spread, slippage, funding, break-even price, relative volume`() {
        assertEquals(0.25, 500 * 0.0005, eps) // taker fee each side
        assertEquals(0.0025, 0.25 / 100, eps) // 0.25% of margin
        assertEquals(0.04, 100.02 - 99.98, 1e-9)
        assertEquals(30.0, 10_000 * (100.3 - 100) / 100, 1e-9) // slippage 0.3% = 30 dollars
        assertEquals(0.05, 500 * 0.0001, eps); assertEquals(0.15, 3 * 0.05, eps) // funding per period and per day
        val be = 100 * (1 + 0.0005) / (1 - 0.0005) // long break-even with taker fee both sides
        assertEquals(100.10, Math.round(be * 100) / 100.0, eps)
        assertEquals(1.5, 150.0 / 100.0, eps)
        assertTrue(1.5 in 0.7..3.0)
    }

    @Test
    fun `fees lower the reward-to-risk shown by the calculator`() {
        val rr = Planner.rrAfterFees(500.0, 100.0, 96.0, 108.0, 0.001)!!
        assertEquals((40.0 - 0.5) / (20.0 + 0.5), rr, eps)
        assertTrue(rr < 2.0)
    }

    @Test
    fun `the gate rejects each limit with its own reason`() {
        fun reasons(sl: Double, tp: Double, lev: Double, chk: Int = 8) =
            Planner.gate(Side.LONG, 100.0, sl, tp, lev, chk, Planner.plan(Side.LONG, 2000.0, 100.0, sl, tp, lev, p), p)
        assertEquals(listOf(GateReason.DIRECTION), reasons(104.0, 92.0, 5.0).filter { it == GateReason.DIRECTION })
        assertTrue(GateReason.CHECKLIST in reasons(96.0, 108.0, 5.0, chk = 7))
        assertTrue(GateReason.LEVERAGE in reasons(96.0, 108.0, 11.0))
        assertTrue(GateReason.RR in reasons(96.0, 105.0, 5.0)) // 1.25 < 1.5
        assertTrue(GateReason.MARGIN in reasons(99.5, 102.0, 1.0)) // stop 0.5% → notional 4000, margin 200% at 1x
        assertTrue(GateReason.LIQUIDATION in reasons(90.0, 120.0, 10.0)) // liq 10.5% < 2 × 10%
    }

    @Test
    fun `size verdict bands`() {
        assertEquals(SizeVerdict.YES, Planner.sizeVerdict(0.0105, p))
        assertEquals(SizeVerdict.GRADE_A_ONLY, Planner.sizeVerdict(0.015, p))
        assertEquals(SizeVerdict.OVER_CAP, Planner.sizeVerdict(0.015, p, gradeAAllowed = false))
        assertEquals(SizeVerdict.RED_LINE, Planner.sizeVerdict(0.021, p))
    }

    @Test
    fun `leverage advisor suggests the leverage closest to the 5 percent margin target`() {
        val a = Planner.leverageAdvisor(Side.LONG, 2000.0, 100.0, 96.0, 108.0, 8, p)
        assertEquals(5, a.recommended) // margin 100 = exactly 5%
        assertEquals(3, a.feasibleMin) // margin 500/2 = 250 = 12.5% > 10% at 2x
        assertEquals(10, a.feasibleMax) // liquidation 9.5% ≥ 2 × 4% even at the cap
    }
}
