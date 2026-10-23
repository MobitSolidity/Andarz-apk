package ir.andarz.engine

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Property tests of 10.3 for the M1 engine (seeded `Random(42)`, at least 5,000 iterations each). */
class PropertyTest {
    private val n = 5_000
    private val p = Params.DEFAULTS

    private fun Random.price(): Double = XlMath.round(nextDouble(0.01, 100_000.0), 4)

    @Test
    fun xlRoundIsIdempotentAndOdd() {
        val rnd = Random(42)
        repeat(n) {
            val x = (rnd.nextDouble() - 0.5) * 10.0.pow(rnd.nextInt(-3, 8))
            val d = rnd.nextInt(0, 7)
            val r = XlMath.round(x, d)
            assertEquals(r, XlMath.round(r, d), "idempotent $x,$d")
            assertEquals(-r + 0.0, XlMath.round(-x, d) + 0.0, "odd $x,$d")
        }
    }

    @Test
    fun xlRoundTiesGoAwayFromZero() {
        val rnd = Random(42)
        repeat(n) {
            val k = rnd.nextLong(0, 1_000_000)
            val d = rnd.nextInt(0, 4)
            // k.5 at d decimals, e.g. k = 12, d = 1 -> 1.25: its shortest repr is the tie itself.
            val tie = BigDecimal("${k}5").movePointLeft(d + 1).toDouble()
            val away = BigDecimal(k + 1).movePointLeft(d).toDouble()
            assertEquals(away, XlMath.round(tie, d), "tie $tie,$d")
            assertEquals(-away, XlMath.round(-tie, d), "tie -$tie,$d")
        }
    }

    @Test
    fun planIdentitiesHold() {
        val rnd = Random(42)
        repeat(n) {
            val side = if (rnd.nextBoolean()) Side.LONG else Side.SHORT
            val entry = rnd.price()
            val dist = rnd.nextDouble(0.001, 0.2)
            val sl = if (side == Side.LONG) entry * (1 - dist) else entry * (1 + dist)
            val tp = if (side == Side.LONG) entry * (1 + dist * 2) else entry * (1 - dist * 2)
            val eq = rnd.nextDouble(100.0, 100_000.0)
            val lev = rnd.nextInt(1, 20).toDouble()
            val pl = Planner.plan(side, eq, entry, sl, tp, lev, p)
            assertTrue(abs(pl.notionalMax!! * pl.sld!! - pl.riskUsd) <= 1e-9 * pl.riskUsd + 1e-9)
            assertTrue(abs(pl.margin!! * lev - pl.notionalMax!!) <= 1e-9 * pl.notionalMax!! + 1e-9)
            if (side == Side.LONG) assertTrue(pl.liq!! < entry) else assertTrue(pl.liq!! > entry)
            val higher = Planner.plan(side, eq, entry, sl, tp, lev + 1, p)
            assertTrue(higher.liqX!! < pl.liqX!!, "liqX decreases with leverage")
        }
    }

    @Test
    fun advisorFeasibleSetIsAnIntervalAndRecommendedIsBest() {
        val rnd = Random(42)
        repeat(n) {
            val side = if (rnd.nextBoolean()) Side.LONG else Side.SHORT
            val entry = rnd.price()
            val dist = rnd.nextDouble(0.002, 0.15)
            val sl = if (side == Side.LONG) entry * (1 - dist) else entry * (1 + dist)
            val tp = if (side == Side.LONG) entry * (1 + dist * 3) else entry * (1 - dist * 3)
            val eq = rnd.nextDouble(100.0, 50_000.0)
            val a = Planner.leverageAdvisor(side, eq, entry, sl, tp, 8, p)
            val plans = (1..p.levMax.toInt()).associateWith { Planner.plan(side, eq, entry, sl, tp, it.toDouble(), p) }
            val feasible = plans.keys.filter { l ->
                val r = Planner.gate(side, entry, sl, tp, l.toDouble(), 8, plans.getValue(l), p)
                GateReason.MARGIN !in r && GateReason.LIQUIDATION !in r
            }
            if (feasible.isEmpty()) {
                assertNull(a.recommended); assertNull(a.feasibleMin)
            } else {
                assertEquals((feasible.first()..feasible.last()).toList(), feasible, "interval")
                assertEquals(feasible.first(), a.feasibleMin); assertEquals(feasible.last(), a.feasibleMax)
                val best = feasible.minWith(
                    compareBy<Int> { abs(plans.getValue(it).marginPct!! - p.marginTarget) }.thenBy { it },
                )
                assertEquals(best, a.recommended)
            }
        }
    }

    @Test
    fun fillProperties() {
        val rnd = Random(42)
        repeat(n) {
            val side = if (rnd.nextBoolean()) Side.LONG else Side.SHORT
            val entry = rnd.price()
            val dist = rnd.nextDouble(0.001, 0.2)
            val sl = if (side == Side.LONG) entry * (1 - dist) else entry * (1 + dist)
            val notional = rnd.nextDouble(10.0, 100_000.0)
            val eq = rnd.nextDouble(100.0, 100_000.0)
            val even = Planner.fill(side, entry, sl, eq, notional, listOf(Leg(entry, 1.0)), 0.0)
            assertEquals(TradeResult.EVEN, even.result); assertEquals(0.0, even.net!! + 0.0)
            val stop = Planner.fill(side, entry, sl, eq, notional, listOf(Leg(sl, 1.0)), 0.0)
            assertTrue(abs(stop.r!! + 1.0) <= 1e-9, "stop = -1R, got ${stop.r}")
            val px = entry * rnd.nextDouble(0.8, 1.2)
            val one = Planner.fill(side, entry, sl, eq, notional, listOf(Leg(px, 1.0)), 0.0)
            val split = Planner.fill(side, entry, sl, eq, notional, listOf(Leg(px, 0.5), Leg(px, 0.5)), 0.0)
            assertTrue(abs(one.gross!! - split.gross!!) <= 1e-9 * abs(one.gross!!) + 1e-9)
        }
    }

    @Test
    fun curveProperties() {
        val rnd = Random(42)
        repeat(n) {
            val start = rnd.nextDouble(500.0, 10_000.0)
            val nets = List(rnd.nextInt(1, 40)) { rnd.nextDouble(-start / 30, start / 20) }
            val cv = Stats.curve(nets, start)
            var prev = start
            var sum = 0.0
            cv.forEachIndexed { i, pt ->
                sum += nets[i]
                assertTrue(pt.peak >= prev); prev = pt.peak
                assertTrue(pt.dd >= 0 && pt.dd < 1)
                assertTrue(abs(pt.cum - (start + sum)) <= 1e-9 * start)
            }
        }
    }

    @Test
    fun formatRoundTripsThroughNormalizeInput() {
        val rnd = Random(42)
        repeat(n) {
            val x = (rnd.nextDouble() - 0.5) * 10.0.pow(rnd.nextInt(0, 9))
            val d = rnd.nextInt(0, 5)
            for (fa in listOf(true, false)) for (group in listOf(true, false)) {
                val text = Fmt.number(x, d, fa, group)
                val back = Fmt.normalizeInput(text)
                assertNotNull(back, text)
                assertEquals(XlMath.round(x, d) + 0.0, back + 0.0, "$x $d $fa $group -> $text")
                if (XlMath.round(x, d) == 0.0) assertTrue(!text.startsWith(Fmt.MINUS) && !text.startsWith("+"), text)
            }
        }
    }

    @Test
    fun jalaliRoundTripsEveryDayOf1300To1500() {
        var d = Jalali.toGregorian(1300, 1, 1)
        val end = Jalali.toGregorian(1500, 12, Jalali.monthLength(1500, 12))
        var prev: JDate? = null
        var days = 0
        while (!d.isAfter(end)) {
            val j = Jalali.toJalali(d)
            assertEquals(d, Jalali.toGregorian(j))
            if (prev != null) {
                val next = when {
                    prev.day < Jalali.monthLength(prev.year, prev.month) -> JDate(prev.year, prev.month, prev.day + 1)
                    prev.month < 12 -> JDate(prev.year, prev.month + 1, 1)
                    else -> JDate(prev.year + 1, 1, 1)
                }
                assertEquals(next, j)
            }
            prev = j
            d = d.plusDays(1)
            days++
        }
        assertEquals(73_414, days)
    }

    // ---- 3.1 guards: no vector has a null plan value, so these are ours.

    @Test
    fun entryEqualsStopGivesNullsAndOnlyDirection() {
        val pl = Planner.plan(Side.LONG, 2000.0, 100.0, 100.0, 108.0, 5.0, p)
        assertNull(pl.rr); assertNull(pl.notionalMax); assertNull(pl.margin); assertNull(pl.marginPct); assertNull(pl.liqX)
        assertEquals(listOf(GateReason.DIRECTION), Planner.gate(Side.LONG, 100.0, 100.0, 108.0, 5.0, 8, pl, p))
    }

    @Test
    fun zeroLeverageAndZeroEquityNeverProduceInfinity() {
        val pl = Planner.plan(Side.SHORT, 0.0, 100.0, 104.0, 92.0, 0.0, p)
        assertNull(pl.margin); assertNull(pl.marginPct); assertNull(pl.liq); assertNull(pl.liqDist); assertNull(pl.liqX)
        listOfNotNull(pl.sld, pl.rr, pl.riskUsd, pl.notionalMax).forEach { assertTrue(it.isFinite()) }
        assertNull(Planner.fill(Side.LONG, 100.0, 96.0, 0.0, 500.0, listOf(Leg(108.0, 1.0)), 0.0).rriskp)
    }

    @Test
    fun workedExample10_2() {
        val pl = Planner.plan(Side.LONG, 2000.0, 100.0, 96.0, 108.0, 5.0, p)
        assertEquals(500.0, pl.notionalMax!!, 1e-9); assertEquals(4.875, pl.liqX!!, 1e-9)
        assertTrue(Planner.gate(Side.LONG, 100.0, 96.0, 108.0, 5.0, 8, pl, p).isEmpty())
        val win = Planner.fill(Side.LONG, 100.0, 96.0, 2000.0, 500.0, listOf(Leg(108.0, 1.0)), 0.6)
        assertEquals(39.4, win.net!!, 1e-9); assertEquals(1.97, win.r!!, 1e-9); assertEquals(2039.4, win.equityAfter!!, 1e-9)
        val loss = Planner.fill(Side.LONG, 100.0, 96.0, 2000.0, 500.0, listOf(Leg(96.0, 1.0)), 0.6)
        assertEquals(-20.6, loss.net!!, 1e-9); assertEquals(-1.03, loss.r!!, 1e-9)
    }

    @Test
    fun feeImpactD36() {
        assertEquals(39.4 / 20.6, Planner.rrAfterFees(500.0, 100.0, 96.0, 108.0, 0.0012)!!, 1e-9)
        assertEquals(2.0, Planner.rrAfterFees(500.0, 100.0, 96.0, 108.0, 0.0)!!, 1e-12)
    }

    @Test
    fun everyVectorCategoryIsPresentAndDefaultsMatch() {
        val expected = setOf(
            "xlRound", "plan", "leverageAdvisor", "sizeVerdict", "fill", "curve", "kpi", "scenarios", "reviewSession",
            "paramChange", "paramsAt", "gradeA", "jalali", "format", "renderClause", "crypto",
        )
        assertEquals(expected, Vectors.root.keys - "meta")
        val defaults = Vectors.root["meta"]!!.jsonObject["defaultParams"] as JsonObject
        assertEquals(Params.KEYS.toSet(), defaults.keys)
        Params.KEYS.forEach { assertEquals(defaults[it]!!.jsonPrimitive.content.toDouble(), Params.DEFAULTS[it], it) }
    }

    @Test
    fun weekStartTrap5() {
        assertEquals(LocalDate.of(2026, 10, 10), Jalali.weekStart(LocalDate.of(2026, 10, 16)))
    }
}
