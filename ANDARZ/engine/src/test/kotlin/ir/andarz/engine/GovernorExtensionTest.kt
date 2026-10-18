package ir.andarz.engine

import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Behaviors without a vector (10.4 items 1, 2, 9) and the snapshot properties of 10.3. */
class GovernorExtensionTest {
    private val zone = ZoneOffset.ofHoursMinutes(3, 30)
    private val p = Params.DEFAULTS
    private fun t(s: String): Instant = OffsetDateTime.parse("$s+03:30").toInstant()
    private val ledger = listOf(LedgerEntry(t("2026-09-01T00:00:00"), LedgerType.BASELINE, 2000.0))
    private val setups = listOf(Setup("s1", "B"), Setup("sA", "A"), Setup("sNew", "B"))

    private fun trade(
        id: String, entry: String, exit: String?, price: Double, reason: String = "MANUAL",
        imported: Boolean = false, account: Account = Account.REAL, violations: List<String> = emptyList(),
        violationAt: Instant? = null, setup: String = "s1", notional: Double = 500.0,
    ) = TradeRaw(
        id = id, account = account, setupId = setup, side = Side.LONG, entry = 100.0, sl = 96.0, tp = 108.0, lev = 5.0,
        eq = 2000.0, chk = 8, gateAllowed = true, notional = notional, entryTime = t(entry), exitTime = exit?.let { t(it) },
        legs = if (exit != null) listOf(Leg(price, 1.0)) else emptyList(), fee = 0.0, exitReason = if (exit != null) reason else null,
        violations = violations, violationAt = violationAt, imported = imported,
    )

    private fun snap(now: String, trades: List<TradeRaw>, reviews: List<Review> = emptyList(), setupId: String? = null, s: List<Setup> = setups) =
        Governor.snapshot(t(now), zone, p, ledger, trades, reviews, s, setupId)

    // ---- 10.4 item 1: persisted derived fields win (D-24)

    @Test
    fun storedVerdictSurvivesATighterRiskMax() {
        val raw = trade("a", "2026-10-14T09:00:00", "2026-10-14T10:00:00", 108.0, notional = 750.0)
            .copy(storedVerdict = SizeVerdict.GRADE_A_ONLY)
        val tighter = p.with("Risk_Max", 0.012)
        assertEquals(SizeVerdict.RED_LINE, Trades.resolve(raw.copy(storedVerdict = null), tighter).verdict)
        assertEquals(SizeVerdict.GRADE_A_ONLY, Trades.resolve(raw, tighter).verdict)
        assertTrue(Trades.resolve(raw, tighter).ok!!)
    }

    // ---- 10.4 item 2: imported history (D-14)

    @Test
    fun importedLosingWeekCreatesNoLockButCountsForEquity() {
        val trades = listOf(
            trade("a", "2026-10-10T09:00:00", "2026-10-10T10:00:00", 90.0, imported = true),
            trade("b", "2026-10-12T09:00:00", "2026-10-12T10:00:00", 90.0, imported = true),
            trade("c", "2026-10-14T09:00:00", "2026-10-14T10:00:00", 96.0, "STOP_LOSS", imported = true),
            trade("d", "2026-10-14T10:30:00", "2026-10-14T11:00:00", 96.0, "STOP_LOSS", imported = true),
        )
        val s = snap("2026-10-14T11:10:00", trades)
        assertTrue(s.governor.locks.isEmpty(), "locks: ${s.governor.locks}")
        assertEquals(0, s.governor.tradesToday)
        assertEquals(2000.0 - 50 - 50 - 20 - 20, s.equity.now, 1e-9)
        // the same rows as live trades lock
        val live = snap("2026-10-14T11:10:00", trades.map { it.copy(imported = false) })
        assertTrue(live.governor.locks.map { it.code }.containsAll(listOf(LockCode.WEEKLY_LOSS, LockCode.DAY_OVER, LockCode.COOLDOWN)))
    }

    @Test
    fun importedViolationsWithoutReportsCreateNoLockOrReduction() {
        val trades = (1..3).map { n ->
            trade("v$n", "2026-10-0${n}T09:00:00", "2026-10-0${n}T10:00:00", 104.0, imported = true,
                violations = listOf("V06"), violationAt = t("2026-10-0${n}T10:00:00"))
        }
        val s = snap("2026-10-04T12:00:00", trades)
        assertTrue(s.governor.locks.isEmpty()); assertTrue(s.governor.reductions.isEmpty())
        val live = snap("2026-10-04T12:00:00", trades.map { it.copy(imported = false) })
        assertTrue(live.governor.locks.any { it.code == LockCode.VIOLATION })
        assertTrue(live.governor.reductions.any { it.code == ReductionCode.VIOLATIONS })
    }

    @Test
    fun importedDrawdownBreachDoesNotLockButKeepsTheCurve() {
        // 11 losses of 20 USD = 220 USD = 11% of 2000
        val trades = (1..11).map { n ->
            val d = "2026-09-%02d".format(n + 1)
            trade("x$n", "${d}T09:00:00", "${d}T10:00:00", 96.0, "STOP_LOSS", imported = true)
        }
        val s = snap("2026-10-14T12:00:00", trades)
        assertTrue(s.governor.locks.none { it.code == LockCode.DRAWDOWN })
        assertTrue(s.governor.reductions.isEmpty())
        assertEquals(0.11, s.governor.ddNow, 1e-9)
        assertEquals(DashStatus.DD_RED, s.dashboard.status.drawdown, "the dashboard still shows the real drawdown")
    }

    @Test
    fun importedBigWinStartsNoHoldButCountsForSetupTestAndGradeA() {
        val big = trade("w", "2026-10-14T09:00:00", "2026-10-14T10:00:00", 120.0, imported = true, setup = "sNew")
        val s = snap("2026-10-14T12:00:00", listOf(big), setupId = "sNew")
        assertNull(s.governor.bigWinHoldUntil)
        assertEquals(19, s.governor.reductions.single { it.code == ReductionCode.SETUP_TEST }.remaining)
    }

    // ---- D-21 testWaived (P1)

    @Test
    fun testWaivedNeedsEnoughDemoTrades() {
        val demos = (1..20).map { n ->
            trade("d$n", "2026-10-01T09:%02d:00".format(n), "2026-10-01T10:%02d:00".format(n), 104.0, account = Account.DEMO, setup = "sNew")
        }
        val waived = setups.map { if (it.id == "sNew") it.copy(testWaived = true) else it }
        assertTrue(snap("2026-10-14T12:00:00", demos, setupId = "sNew", s = waived).governor.reductions.isEmpty())
        val few = snap("2026-10-14T12:00:00", demos.take(19), setupId = "sNew", s = waived)
        assertEquals(ReductionCode.SETUP_TEST, few.governor.reductions.single().code)
        val notWaived = snap("2026-10-14T12:00:00", demos, setupId = "sNew")
        assertEquals(ReductionCode.SETUP_TEST, notWaived.governor.reductions.single().code)
    }

    // ---- sessions and changes

    @Test
    fun paramsAtIgnoresFutureChangesAndOrdersByEffectiveThenApplied() {
        val c1 = ParamChange("Risk_Pct", 0.008, t("2026-10-10T10:00:00"), t("2026-10-10T10:00:00"))
        val c2 = ParamChange("Risk_Pct", 0.006, t("2026-10-09T10:00:00"), t("2026-10-10T10:00:00"))
        val c3 = ParamChange("Risk_Pct", 0.02, t("2026-10-12T10:00:00"), t("2026-10-13T10:00:00"))
        assertEquals(0.008, Governor.paramsAt(p, listOf(c3, c1, c2), t("2026-10-12T00:00:00")).riskPct)
        assertEquals(0.02, Governor.paramsAt(p, listOf(c3, c1, c2), t("2026-10-13T10:00:00")).riskPct)
    }

    @Test
    fun noneAndNeutralChangesApplyAtOnce() {
        val at = t("2026-10-10T10:00:00")
        assertEquals(at to ChangeKind.NONE, Governor.changeEffectiveAt("Risk_Pct", 0.01, 0.01, at, p))
        assertEquals(at to ChangeKind.NEUTRAL, Governor.changeEffectiveAt("Withdraw_Pct", 0.3, 0.4, at, p))
    }

    // ---- 10.3 snapshot properties

    @Test
    fun snapshotIsShuffleInvariantAndDemoNeutral() {
        val rnd = Random(42)
        repeat(300) { iter ->
            val n = rnd.nextInt(1, 12)
            val trades = (0 until n).map { k ->
                val day = 1 + rnd.nextInt(14)
                val h = 8 + rnd.nextInt(10)
                val px = listOf(96.0, 108.0, 100.0, 102.0, 97.5)[rnd.nextInt(5)]
                val open = rnd.nextInt(6) == 0
                trade("t$iter-$k", "2026-10-%02dT%02d:00:00".format(day, h), if (open) null else "2026-10-%02dT%02d:30:00".format(day, h), px,
                    if (px == 96.0) "STOP_LOSS" else "MANUAL",
                    violations = if (rnd.nextInt(8) == 0) listOf("V01") else emptyList(),
                    violationAt = t("2026-10-%02dT%02d:10:00".format(day, h)))
            }
            val reviews = listOf(Review(ReviewType.WEEKLY, t("2026-10-09T20:00:00"), java.time.LocalDate.parse("2026-10-03")))
            val base = snap("2026-10-14T20:00:00", trades, reviews)
            val shuffled = Governor.snapshot(t("2026-10-14T20:00:00"), zone, p, ledger.shuffled(rnd), trades.shuffled(rnd), reviews, setups)
            assertEquals(base.governor.locks, shuffled.governor.locks)
            assertEquals(base.equity.now, shuffled.equity.now, 1e-9)
            assertEquals(base.dashboard.status, shuffled.dashboard.status)
            assertEquals(base.governor.canEnter, base.governor.locks.isEmpty())
            assertTrue(base.governor.riskCap <= p.riskPct)
            val withDemo = snap("2026-10-14T20:00:00", trades + trade("demo$iter", "2026-10-14T09:00:00", "2026-10-14T09:30:00", 96.0, "STOP_LOSS", account = Account.DEMO), reviews)
            assertEquals(base.governor, withDemo.governor)
            assertEquals(base.equity, withDemo.equity)
            assertEquals(base.dashboard, withDemo.dashboard)
        }
    }

    @Test
    fun fiveThousandTradeSnapshotIsFast() {
        val trades = (0 until 5_000).map { k ->
            val day = java.time.LocalDate.of(2020, 1, 1).plusDays((k / 3).toLong())
            trade("p$k", "${day}T09:0${k % 3}:00", "${day}T10:0${k % 3}:00", if (k % 3 == 0) 96.0 else 104.0)
        }
        Governor.snapshot(t("2026-10-14T12:00:00"), zone, p, ledger, trades, emptyList(), setups)  // warm up
        val start = System.nanoTime()
        Governor.snapshot(t("2026-10-14T12:00:00"), zone, p, ledger, trades, emptyList(), setups)
        val ms = (System.nanoTime() - start) / 1e6
        println("5,000-trade snapshot: ${"%.0f".format(java.util.Locale.ROOT, ms)} ms")
        assertTrue(ms < 5_000, "snapshot took $ms ms")
    }
}
