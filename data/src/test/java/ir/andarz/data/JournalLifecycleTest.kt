package ir.andarz.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.andarz.data.db.AndarzDatabase
import ir.andarz.engine.Clock
import ir.andarz.engine.LockCode
import ir.andarz.engine.LockNeed
import ir.andarz.engine.Side
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Lifecycle and persistence through the repositories on an in-memory Room database (10.5, M3 exit). */
@RunWith(RobolectricTestRunner::class)
class JournalLifecycleTest {
    private class FakeClock(var t: Instant) : Clock { override fun now() = t; fun plus(d: Duration) { t = t.plus(d) } }

    private val zone: ZoneId = ZoneOffset.ofHoursMinutes(3, 30)
    private val clock = FakeClock(OffsetDateTime.parse("2026-10-14T09:00:00+03:30").toInstant())
    private lateinit var db: AndarzDatabase
    private lateinit var facade: EngineFacade
    private lateinit var params: ParamsRepository
    private lateinit var journal: JournalRepository
    private lateinit var ledger: LedgerRepository
    private lateinit var setups: SetupRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AndarzDatabase::class.java).allowMainThreadQueries().build()
        facade = EngineFacade(db, clock) { zone }
        params = ParamsRepository(db, clock, mapOf("Risk_Pct" to ParamBounds(0.001, 0.02), "Start_Equity" to ParamBounds(1.0, 1e9)))
        journal = JournalRepository(db, clock, params, facade)
        ledger = LedgerRepository(db, clock, params, facade)
        setups = SetupRepository(db, clock)
    }

    @After fun tearDown() = db.close()

    private fun draft(setupId: String, mask: Int = 0xFF, entry: Double = 100.0, sl: Double = 96.0, tp: Double = 108.0, lev: Double = 5.0) = PlanDraft(
        account = "REAL", symbol = "btcusdt", side = Side.LONG, kind = "INTRADAY", setupId = setupId, timeframe = "15m",
        htfTrend = "UP", counterTrendReason = null, emotion = "CALM", checklistMask = mask, equity = 2000.0,
        plannedEntry = entry, sl = sl, tp = tp, lev = lev, riskPctChosen = 0.01, entryOrder = "LIMIT",
        isolated = true, slTpOnExchange = true, canMonitor = true,
    )

    private fun onboarded(): String = runBlocking {
        assertNull(ledger.onboard(2000.0, 0.01, zone))
        // a setup with enough history would skip SETUP_TEST; for the worked example the cap is what it is
        setups.create("retest").id
    }

    @Test
    fun workedExampleRunsHeadlessThroughTheRepositories() = runBlocking {
        val s = onboarded()
        val planned = journal.savePlan(draft(s))
        assertEquals("PLANNED", planned.status)
        assertEquals("BTCUSDT", planned.symbol)
        assertEquals("", planned.gateReasons); assertTrue(planned.gatePlanOk)
        assertEquals(500.0, planned.plan.notionalMax!!, 1e-9); assertEquals(4.875, planned.plan.liqX!!, 1e-9)
        // a new setup trades under SETUP_TEST (0.25%), so the governor cap is stored, not the chosen risk
        assertEquals(0.0025, planned.riskPctCap, 1e-12)

        clock.plus(Duration.ofMinutes(5))
        val open = journal.confirmEntry(planned.id, EntryConfirm(100.0, 500.0, clock.now()))
        assertEquals("OPEN", open.status); assertEquals(20.0, open.rrisk!!, 1e-9); assertEquals(96.0, open.slCurrent)
        // 1% actual risk against a 0.25% cap without grade A: OVER_CAP (D-03), recorded, not recomputed later
        assertEquals("OVER_CAP", open.verdict)
        assertTrue(open.gateAllowed)

        clock.plus(Duration.ofHours(1))
        val closed = journal.close(planned.id, listOf(ExitLeg(108.0, 1.0)), 0.6, "TAKE_PROFIT", clock.now())
        assertEquals(40.0, closed.gross!!, 1e-9); assertEquals(39.4, closed.net!!, 1e-9); assertEquals(1.97, closed.r!!, 1e-9)
        assertEquals(2039.4, closed.equityAfter!!, 1e-9); assertEquals("WIN", closed.result)
        assertFalse(closed.ok!!, "OVER_CAP is non-compliant")

        val reviewed = journal.saveReview(planned.id, ReviewInput(emptyList(), null, "M00", "CALM", "patience"), 20)
        assertNotNull(reviewed.reviewedAt)
        assertEquals(2039.4, facade.snapshot().equity.now, 1e-9)
    }

    /** D-98: Today reads open trades through an indexed query that returns exactly the OPEN rows, newest first. */
    @Test
    fun observeOpenReturnsOnlyOpenTradesNewestFirstInALargeJournal() = runBlocking {
        val s = onboarded()
        val base = journal.savePlan(draft(s))
        db.trades().delete(base)
        val statuses = listOf("CLOSED", "PLANNED", "REJECTED", "CANCELLED")
        val rows = (0 until 2000).map { k ->
            val t = clock.now().minus(Duration.ofMinutes(2000L - k)).toEpochMilli()
            base.copy(id = "r$k", status = if (k % 100 == 7) "OPEN" else statuses[k % statuses.size], entryTime = t, createdAt = t)
        }
        db.trades().insertAll(rows)
        val open = db.trades().observeOpen().first()
        val expected = rows.filter { it.status == "OPEN" }.sortedByDescending { it.entryTime }.map { it.id }
        assertEquals(20, open.size)
        assertEquals(expected, open.map { it.id })
        assertTrue(open.all { it.status == "OPEN" })
        // The query is served by the status index, not a full scan of the journal.
        val plan = db.openHelper.readableDatabase.query(
            "EXPLAIN QUERY PLAN SELECT * FROM trade WHERE status = 'OPEN' ORDER BY COALESCE(entryTime, createdAt) DESC",
        ).use { c -> buildString { while (c.moveToNext()) append(c.getString(c.columnCount - 1)).append('\n') } }
        assertTrue("index_trade_status" in plan, plan)
    }

    @Test
    fun workedExampleWithAnEstablishedSetupIsYesAndCompliant() = runBlocking {
        val s = onboarded()
        // 20 earlier entries of this setup end its test period (they are imported history, so no locks)
        repeat(20) { k ->
            val day = clock.now().minus(Duration.ofDays(30L - k))
            db.trades().insert(
                journal.savePlan(draft(s)).copy(
                    status = "CLOSED", entry = 100.0, notional = 500.0, entryTime = day.toEpochMilli(),
                    exitTime = day.plusSeconds(60).toEpochMilli(), legs = listOf(ir.andarz.data.db.LegRow(100.0, 1.0)),
                    verdict = "YES", net = 0.0, r = 0.0, ok = true, imported = true, id = "h$k",
                ),
            )
        }
        val planned = journal.savePlan(draft(s))
        assertEquals(0.01, planned.riskPctCap, 1e-12)
        val open = journal.confirmEntry(planned.id, EntryConfirm(100.0, 500.0, clock.now()))
        assertEquals("YES", open.verdict)
        clock.plus(Duration.ofHours(1))
        val closed = journal.close(planned.id, listOf(ExitLeg(108.0, 1.0)), 0.6, "TAKE_PROFIT", clock.now())
        assertTrue(closed.ok!!)
        assertEquals(2039.4, facade.snapshot().equity.now, 1e-9)
    }

    @Test
    fun stopAwayAddsV04AndTheViolationLockNeedsCloseThenReport() = runBlocking {
        val s = onboarded()
        val t = journal.savePlan(draft(s))
        journal.confirmEntry(t.id, EntryConfirm(100.0, 500.0, clock.now()))
        val toward = journal.moveStop(t.id, 98.0)
        assertTrue(toward.violations.isEmpty())
        val away = journal.moveStop(t.id, 95.0)
        assertEquals(listOf("V04"), away.violations)
        assertEquals(96.0, away.slInitial)
        val lock = facade.snapshot().governor.locks.single { it.code == LockCode.VIOLATION }
        assertEquals(setOf(LockNeed.REPORT, LockNeed.CLOSE_POSITION), lock.needs.toSet())
        assertEquals(2, db.events().forTrade(t.id).count { it.type == "SL_MOVE" })

        clock.plus(Duration.ofMinutes(30))
        journal.close(t.id, listOf(ExitLeg(97.0, 1.0)), 0.0, "MANUAL", clock.now())
        assertEquals(listOf(LockNeed.REPORT), facade.snapshot().governor.locks.single { it.code == LockCode.VIOLATION }.needs)
        journal.saveReview(t.id, ReviewInput(emptyList(), "moved the stop out of fear", "M03", null, null), 20)
        val after = facade.snapshot().governor.locks.single { it.code == LockCode.VIOLATION }
        assertTrue(after.needs.isEmpty()); assertNotNull(after.until)
        assertFalse(journal.delete(t.id), "no deletion while a lock is active (D-23)")
    }

    @Test
    fun partialExitAppendsALegAndCloseUsesAllLegs() = runBlocking {
        val s = onboarded()
        val t = journal.savePlan(draft(s))
        journal.confirmEntry(t.id, EntryConfirm(100.0, 500.0, clock.now()))
        journal.partialExit(t.id, 104.0, 0.5, 0.2)
        clock.plus(Duration.ofHours(2))
        val c = journal.close(t.id, listOf(ExitLeg(108.0, 0.5)), 0.3, "TAKE_PROFIT", clock.now())
        assertEquals(2, c.legs.size); assertEquals(0.5, c.fee, 1e-12)
        assertEquals(30.0, c.gross!!, 1e-9); assertEquals(29.5, c.net!!, 1e-9)
    }

    @Test
    fun deniedPlanEnteredAnywayRecordsViolationsAndIsNonCompliant() = runBlocking {
        val s = onboarded()
        val t = journal.savePlan(draft(s, mask = 0x7F), rejected = true)
        assertEquals("REJECTED", t.status); assertEquals("CHECKLIST", t.gateReasons)
        val open = journal.confirmEntry(t.id, EntryConfirm(100.0, 500.0, clock.now()))
        assertTrue("V06" in open.violations); assertFalse(open.gateAllowed)
    }

    @Test
    fun plansExpireAfterTwelveHours() = runBlocking {
        val s = onboarded()
        val t = journal.savePlan(draft(s))
        clock.plus(Duration.ofHours(11))
        assertEquals(0, journal.expirePlans())
        clock.plus(Duration.ofHours(2))
        assertEquals(1, journal.expirePlans())
        assertEquals("CANCELLED", db.trades().get(t.id)!!.status)
    }

    @Test
    fun parameterChangesNeedASessionAndLooseningIsDelayed() = runBlocking {
        onboarded()
        assertIs<ChangeResult.Rejected>(params.apply("Risk_Pct", 0.005, "tighter"))
        val (check, session) = params.startSession()
        assertTrue(check.allowed); assertNotNull(session)
        val tight = params.apply("Risk_Pct", 0.005, "tighter") as ChangeResult.Applied
        assertEquals("TIGHTEN", tight.change.kind)
        assertEquals(0.005, params.current().riskPct)
        val loose = params.apply("Risk_Pct", 0.015, "looser") as ChangeResult.Applied
        assertEquals("LOOSEN", loose.change.kind)
        assertEquals(0.005, params.current().riskPct, "loosening waits 24 h")
        assertEquals(1, params.pending().size)
        assertNull(params.cancel(loose.change.id))
        clock.plus(Duration.ofHours(25))
        assertEquals(0.005, params.current().riskPct, "the cancelled change never applies")
        assertNull(params.openSession(), "sessions time out after 60 minutes")
        assertEquals(ChangeError.BELOW_MIN, (params.apply("Risk_Pct", 0.0001, "x", requireSession = false) as ChangeResult.Rejected).reason)
        assertEquals(ChangeError.LOCKED, (params.apply("Chk_N", 7.0, "x", requireSession = false) as ChangeResult.Rejected).reason)
        assertTrue(db.audit().all().count { it.type == "PARAM_CHANGE" } >= 4)
        assertEquals(1, db.audit().all().count { it.type == "PARAM_CANCEL" })
    }

    @Test
    fun anOpenPositionBlocksTheSessionAndARealEntryEndsIt() = runBlocking {
        val s = onboarded()
        params.startSession()
        val t = journal.savePlan(draft(s))
        journal.confirmEntry(t.id, EntryConfirm(100.0, 500.0, clock.now()))
        assertNull(params.openSession())
        assertEquals("TRADE_OPENED", db.sessions().all().single().endReason)
        assertFalse(params.startSession().first.allowed)
    }

    @Test
    fun ledgerRefusesOverdrawnWithdrawalsAndKeepsOneBaseline() = runBlocking {
        onboarded()
        assertEquals(LedgerError.BASELINE_EXISTS, ledger.onboard(3000.0, 0.01, zone))
        assertEquals(LedgerError.NEGATIVE_EQUITY, ledger.add("WITHDRAWAL", 2500.0, clock.now(), ""))
        assertNull(ledger.add("WITHDRAWAL", 500.0, clock.now(), ""))
        clock.plus(Duration.ofSeconds(1))
        assertEquals(1500.0, facade.snapshot().equity.now, 1e-9)
        val baseline = db.ledger().baselines().single()
        assertEquals(LedgerError.BASELINE_LOCKED, ledger.delete(baseline.id))
        assertEquals(LedgerError.BASELINE_LOCKED, ledger.editBaseline(2500.0, null, "fix"))
        params.startSession()
        assertNull(ledger.editBaseline(2500.0, null, "fix"))
        assertEquals(2500.0, params.current().startEquity)
    }

    @Test
    fun cascadeDeletesEventsAndAuditsTheDeletion() = runBlocking {
        val s = onboarded()
        val t = journal.savePlan(draft(s))
        journal.confirmEntry(t.id, EntryConfirm(100.0, 500.0, clock.now()))
        journal.addEvent(t.id, "NOTE", "note")
        clock.plus(Duration.ofMinutes(1))
        journal.close(t.id, listOf(ExitLeg(104.0, 1.0)), 0.0, "MANUAL", clock.now())
        assertTrue(journal.delete(t.id))
        assertTrue(db.events().forTrade(t.id).isEmpty())
        assertEquals(1, db.audit().all().count { it.type == "TRADE_DELETE" })
    }

    @Test
    fun gradeAOnlyWhenEligibleAndTheWatchListHasItsLimit() = runBlocking {
        val s = onboarded()
        assertFalse(setups.setGrade(s, "A", eligibleA = false))
        assertEquals("B", db.setups().get(s)!!.grade)
        assertTrue(setups.setGrade(s, "A", eligibleA = true))
        assertTrue(setups.setGrade(s, "B", eligibleA = false), "demotion is always allowed")
        setups.setArchived(s, true)
        assertTrue(db.setups().get(s)!!.archived)

        val watch = WatchRepository(db, clock, params)
        assertEquals(WatchError.EMPTY_SYMBOL, watch.add("  ", null, null, "", ""))
        assertNull(watch.add("btcusdt", 2e8, 1.2, "100k", ""))
        assertEquals(WatchError.DUPLICATE, watch.add("BTCUSDT", null, null, "", ""))
        assertNotNull(db.watch().all().single().checkedAt)
        for (i in 1 until 10) assertNull(watch.add("C${i}USDT", null, null, "", ""))
        assertEquals(WatchError.FULL, watch.add("XUSDT", null, null, "", ""), "Watchlist_Max is 10")
    }

    // ---- M7: late logging (D-26, D-67) and demo data (D-68)

    @Test
    fun lateEntryIsJudgedAtItsOwnEntryTimeAndFlagged(): Unit = runBlocking {
        val s = onboarded()
        val t0 = clock.now()
        // a stop-out right now starts a cool-down
        val a = journal.savePlan(draft(s))
        journal.confirmEntry(a.id, EntryConfirm(100.0, 500.0, t0))
        clock.plus(Duration.ofMinutes(5))
        journal.close(a.id, listOf(ExitLeg(96.0, 1.0)), 0.0, "STOP_LOSS", clock.now())
        clock.plus(Duration.ofMinutes(10))
        assertFalse(facade.snapshot().mayEnter, "cool-down is active now")

        // logged now, entered two hours ago (before the stop-out): judged then, so the entry was allowed
        val b = journal.savePlan(draft(s))
        val late = journal.confirmEntry(b.id, EntryConfirm(100.0, 500.0, t0.minus(Duration.ofHours(2))))
        assertTrue(late.loggedLate)
        assertTrue(late.entryAllowed, "the governor at the entry time had no lock")

        // an entry confirmed on time is judged now and the cool-down counts
        val c = journal.savePlan(draft(s))
        val onTime = journal.confirmEntry(c.id, EntryConfirm(100.0, 500.0, clock.now().minus(Duration.ofMinutes(10))))
        assertFalse(onTime.loggedLate)
        assertFalse(onTime.entryAllowed)

        // the entry may not be in the future
        val d = journal.savePlan(draft(s))
        assertIs<IllegalArgumentException>(runCatching { journal.confirmEntry(d.id, EntryConfirm(100.0, 500.0, clock.now().plusSeconds(60))) }.exceptionOrNull())
        assertTrue(JournalRepository.isLate(t0, t0.plus(Duration.ofMinutes(16))))
        assertFalse(JournalRepository.isLate(t0, t0.plus(Duration.ofMinutes(15))))
    }

    @Test
    fun demoDataLoadsTwelveDemoTradesThatNeverTouchRealNumbers(): Unit = runBlocking {
        onboarded()
        val before = facade.snapshot()
        val demo = DemoData(db, clock, params)
        assertEquals(12, demo.load())
        assertEquals(0, demo.load(), "a second load is a no-op")
        val rows = db.trades().all().filter { it.id.startsWith(DemoData.PREFIX) }
        assertEquals(12, rows.size)
        assertTrue(rows.all { it.account == "DEMO" && !it.imported && it.status == "CLOSED" && it.net != null })
        assertTrue(rows.any { it.net!! > 0 } && rows.any { it.net!! < 0 }, "a mix of wins and losses")
        assertTrue(rows.all { kotlin.math.abs(it.legs.sumOf { l -> l.fraction } - 1.0) < 1e-9 })
        val after = facade.snapshot()
        assertEquals(before.equity.now, after.equity.now, 1e-9)
        assertEquals(before.governor.locks, after.governor.locks)

        // a demo trade the user logged by hand survives the removal
        val mine = journal.savePlan(draft(db.setups().all().first().id).copy(account = "DEMO"))
        assertEquals(12, demo.remove())
        assertFalse(demo.loaded())
        assertNotNull(db.trades().get(mine.id))
        assertEquals(1, db.audit().all().count { it.type == "DEMO_LOAD" })
        assertEquals(1, db.audit().all().count { it.type == "DEMO_REMOVE" })
    }
}
