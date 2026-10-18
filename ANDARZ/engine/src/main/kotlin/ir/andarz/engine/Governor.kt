package ir.andarz.engine

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class LockCode { VIOLATION, DRAWDOWN, WEEKLY_LOSS, DAILY_LOSS, DAY_OVER, TRADE_CAP, COOLDOWN }

enum class LockNeed { REPORT, REVIEW, WEEKLY_REVIEW, CLOSE_POSITION }

enum class ReductionCode { DD_RECOVERY, VIOLATIONS, SETUP_TEST }

enum class ReviewType { DAILY, WEEKLY, MONTHLY, DRAWDOWN }

enum class ChangeKind { NONE, NEUTRAL, TIGHTEN, LOOSEN }

enum class SessionBlock { OPEN_POSITION, RECENT_LOSS }

/** [until] null = no end yet (an open violating trade, `INF` in the reference). */
data class Lock(val code: LockCode, val until: Instant?, val needs: List<LockNeed>, val tradeId: String? = null)

data class Reduction(val code: ReductionCode, val start: Instant?, val riskPct: Double, val remaining: Int)

data class Review(val type: ReviewType, val completedAt: Instant, val weekStart: LocalDate? = null)

/** [testWaived]: extension D-21, exempt from SETUP_TEST once the setup has `Setup_Test_Trades` demo trades. */
data class Setup(val id: String, val grade: String, val testWaived: Boolean = false)

data class GovernorResult(
    val canEnter: Boolean,
    val locks: List<Lock>,
    val riskCap: Double,
    val reductions: List<Reduction>,
    val gradeAAllowed: Boolean,
    val bigWinHoldUntil: Instant?,
    val tradesToday: Int,
    val openPositions: Int,
    val ddNow: Double,
    val dailyNet: Double,
)

data class Equity(val now: Double, val startOfDay: Double, val startOfWeek: Double)

data class Snapshot(
    val equity: Equity,
    val dashboard: Dashboard,
    val governor: GovernorResult,
    val trades: List<ResolvedTrade>,
) {
    /** `dashboard.allowed ∧ governor.canEnter`: the overall verdict without a plan (5.2). */
    val mayEnter: Boolean get() = dashboard.allowed && governor.canEnter
}

data class SessionCheck(val allowed: Boolean, val reason: SessionBlock?, val until: Instant?)

data class ParamChange(val key: String, val value: Double, val appliedAt: Instant, val effectiveAt: Instant)

data class GradeA(val eligible: Boolean, val n: Int, val expectancy: Double?)

/**
 * The governor of 3.9: locks, drawdown walk, risk reductions, big-win hold, review sessions, parameter
 * changes and grade A. A literal port of `andarz_ref.py`, plus the imported-history extension (D-14):
 * an imported row counts for equity, the curve and the setup counter, but never starts a lock, a
 * cool-down, a violation event, a drawdown breach, a reduction or a hold, and the daily, weekly,
 * day-over and trade-cap scans ignore it.
 */
object Governor {
    private val PRIORITY = LockCode.entries

    private fun localDate(t: Instant, zone: ZoneId): LocalDate = t.atZone(zone).toLocalDate()
    private fun dayStart(d: LocalDate, zone: ZoneId): Instant = d.atStartOfDay(zone).toInstant()

    private fun real(ts: List<ResolvedTrade>) = ts.filter { it.isReal }

    /** Closed REAL trades with exit ≤ now, in exit order. */
    private fun closed(ts: List<ResolvedTrade>, now: Instant) =
        real(ts).filter { it.exitTime != null && it.exitTime!! <= now }
            .sortedWith(compareBy<ResolvedTrade>({ it.exitTime }, { it.entryTime }, { it.id }))

    private fun entered(ts: List<ResolvedTrade>, now: Instant) = real(ts).filter { it.entryTime <= now }

    /**
     * Equity just before an instant: ledger flows before it plus the net of REAL trades closed before it
     * (3.8). Built once per snapshot as sorted prefix sums, so each query is a binary search instead of a
     * pass over every row (4.5: 5,000 trades in under 50 ms). The order of addition is by time, not the
     * reference's list order; the results agree to far below the 1e-9 tolerance (D-42).
     */
    class EquityIndex(ledger: List<LedgerEntry>, trades: List<ResolvedTrade>) {
        private val times: LongArray
        private val nanos: IntArray
        private val prefix: DoubleArray

        init {
            val flows = ArrayList<Pair<Instant, Double>>(ledger.size + trades.size)
            for (e in ledger) flows += e.time to when (e.type) {
                LedgerType.BASELINE, LedgerType.DEPOSIT, LedgerType.ADJUSTMENT -> e.amount
                LedgerType.WITHDRAWAL -> -e.amount
            }
            for (tr in trades) if (tr.exitTime != null && tr.isReal) flows += tr.exitTime!! to (tr.net ?: 0.0)
            flows.sortBy { it.first }
            times = LongArray(flows.size) { flows[it].first.epochSecond }
            nanos = IntArray(flows.size) { flows[it].first.nano }
            prefix = DoubleArray(flows.size + 1)
            for (i in flows.indices) prefix[i + 1] = prefix[i] + flows[i].second
        }

        /** Sum of every flow strictly before [t]. */
        fun before(t: Instant): Double {
            var lo = 0
            var hi = times.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                val c = times[mid].compareTo(t.epochSecond).let { if (it != 0) it else nanos[mid].compareTo(t.nano) }
                if (c < 0) lo = mid + 1 else hi = mid
            }
            return prefix[lo]
        }

        fun now(now: Instant): Double = before(now.plusNanos(1_000))
    }

    /** One-off form of [EquityIndex.before]. */
    fun equityBefore(t: Instant, ledger: List<LedgerEntry>, trades: List<ResolvedTrade>): Double =
        EquityIndex(ledger, trades).before(t)

    fun isStopOut(t: ResolvedTrade, p: Params): Boolean {
        if (t.exitTime == null) return false
        if (t.raw.exitReason == "STOP_LOSS") return true
        val net = t.net ?: return false
        return net < 0 && t.r != null && t.r <= p.stopoutR
    }

    // ------------------------------------------------------------------ drawdown

    private class DdState(val lock: Lock?, val recoveryStarts: List<Instant>, val ddNow: Double)

    private fun qualifyingReviews(reviews: List<Review>, after: Instant): List<Instant> =
        reviews.filter { it.type in DD_REVIEW_TYPES && it.completedAt >= after }.map { it.completedAt }.sorted()

    private val DD_REVIEW_TYPES = setOf(ReviewType.WEEKLY, ReviewType.MONTHLY, ReviewType.DRAWDOWN)

    private fun drawdownState(closed: List<ResolvedTrade>, start: Double, p: Params, reviews: List<Review>, now: Instant): DdState {
        var cum = start
        var peak = start
        var locked = false
        var breachAt: Instant? = null
        val releases = ArrayList<Instant>()
        val lockDays = Duration.ofSeconds((p.ddLockDays * 86_400).toLong())

        fun tryRelease(limit: Instant): Boolean {
            if (!locked) return false
            val revs = qualifyingReviews(reviews, breachAt!!)
            val rel = if (revs.isNotEmpty()) maxOf(breachAt!!.plus(lockDays), revs[0]) else null
            if (rel != null && rel <= limit) {
                peak = cum
                releases += rel
                locked = false
                breachAt = null
                return true
            }
            return false
        }

        var i = 0
        while (true) {
            val nxt = if (i < closed.size) closed[i].exitTime else null
            val limit = nxt ?: now
            while (tryRelease(limit)) { /* apply every release due before the next trade */ }
            if (nxt == null) break
            val t = closed[i]
            cum += t.net!!
            peak = maxOf(peak, cum)
            val dd = if (peak != 0.0) (peak - cum) / peak else 0.0
            // D-14: an imported row advances cum and peak but never starts a breach.
            if (!locked && !t.raw.imported && XlMath.round(dd, 6) >= p.ddCap) {
                locked = true
                breachAt = nxt
            }
            i++
        }
        val ddNow = if (peak != 0.0) (peak - cum) / peak else 0.0
        val lock = if (locked) {
            val revs = qualifyingReviews(reviews, breachAt!!)
            Lock(LockCode.DRAWDOWN, breachAt!!.plus(lockDays), if (revs.isEmpty()) listOf(LockNeed.REVIEW) else emptyList())
        } else null
        return DdState(lock, releases, ddNow)
    }

    // ------------------------------------------------------------------ violations

    private class ViolationEvent(val id: String, val start: Instant, val open: Boolean, val reportOk: Boolean)

    private fun violationEvents(trades: List<ResolvedTrade>, now: Instant, p: Params): List<ViolationEvent> {
        val out = ArrayList<ViolationEvent>()
        for (t in real(trades)) {
            if (t.raw.imported) continue
            if (t.raw.violations.isEmpty()) continue
            val va = t.raw.violationAt ?: continue
            if (va > now) continue
            val ex = t.exitTime?.takeIf { it <= now }
            val start = if (ex != null) maxOf(va, ex) else va
            val reportOk = (t.raw.violationReport ?: "").trim().length >= p.reportMinChars
            out += ViolationEvent(t.id, start, ex == null, reportOk)
        }
        return out.sortedWith(compareBy<ViolationEvent>({ it.start }, { it.id }))
    }

    private fun violationTriggers(events: List<ViolationEvent>, p: Params): List<Instant> {
        val windowLen = Duration.ofSeconds((p.violationWindowD * 86_400).toLong())
        var window = ArrayList<Instant>()
        val fired = ArrayList<Instant>()
        for (e in events) {
            window = ArrayList(window.filter { Duration.between(it, e.start) <= windowLen })
            window += e.start
            if (window.size >= p.violationTrigger) {
                fired += e.start
                window = ArrayList()
            }
        }
        return fired
    }

    // ------------------------------------------------------------------ big win

    private fun bigWinHold(closed: List<ResolvedTrade>, eq: EquityIndex, p: Params, now: Instant, zone: ZoneId): Instant? {
        var hold: Instant? = null
        val dayNet = HashMap<LocalDate, Double>()
        val holdLen = Duration.ofSeconds((p.bigWinHoldH * 3_600).toLong())
        for (t in closed) {
            if (t.raw.imported) continue
            val te = t.exitTime!!
            val starts = ArrayList<Instant>()
            if (t.r != null && t.r > p.bigWinR) starts += te
            val d = localDate(te, zone)
            dayNet[d] = (dayNet[d] ?: 0.0) + t.net!!
            val sod = eq.before(dayStart(d, zone))
            if (sod != 0.0 && XlMath.round(dayNet[d]!! / sod, 6) > p.bigWinDayPct) starts += te
            for (s in starts) {
                val end = s.plus(holdLen)
                if (hold == null || end > hold) hold = end
            }
        }
        return hold?.takeIf { it > now }
    }

    // ------------------------------------------------------------------ governor

    fun governor(
        now: Instant, zone: ZoneId, trades: List<ResolvedTrade>, ledger: List<LedgerEntry>, reviews: List<Review>,
        setups: List<Setup>, p: Params, setupId: String? = null, equity: EquityIndex = EquityIndex(ledger, trades),
    ): GovernorResult {
        val closed = closed(trades, now)
        val live = closed.filter { !it.raw.imported }          // rows that may create locks (D-14)
        val entered = entered(trades, now)
        val today = localDate(now, zone)
        val tomorrow = dayStart(today.plusDays(1), zone)
        val locks = ArrayList<Lock>()

        // 1) violations
        val pause = Duration.ofSeconds((p.violationPauseH * 3_600).toLong())
        val events = violationEvents(trades, now, p)
        for (e in events) {
            val until = e.start.plus(pause)
            val needs = if (e.reportOk) emptyList() else listOf(LockNeed.REPORT)
            if (e.open) locks += Lock(LockCode.VIOLATION, null, needs + LockNeed.CLOSE_POSITION, e.id)
            else if (now < until || needs.isNotEmpty()) locks += Lock(LockCode.VIOLATION, until, needs, e.id)
        }

        // 2) drawdown cap
        val dd = drawdownState(closed, p.startEquity, p, reviews, now)
        dd.lock?.let { locks += it }

        // 3) weekly loss cap
        val wkNet = HashMap<LocalDate, Double>()
        val wkSeen = java.util.TreeMap<LocalDate, Instant>()
        for (t in live) {
            val te = t.exitTime!!
            val w = Jalali.weekStart(localDate(te, zone))
            wkNet[w] = (wkNet[w] ?: 0.0) + t.net!!
            val sow = equity.before(dayStart(w, zone))
            if (sow != 0.0 && w !in wkSeen && XlMath.round(-wkNet[w]!! / sow, 6) >= p.weeklyLossCap) wkSeen[w] = te
        }
        for (w in wkSeen.keys) {
            val nextWeek = dayStart(w.plusDays(7), zone)
            val hasRev = reviews.any { it.type == ReviewType.WEEKLY && it.weekStart == w }
            if (now < nextWeek || !hasRev) {
                locks += Lock(LockCode.WEEKLY_LOSS, nextWeek, if (hasRev) emptyList() else listOf(LockNeed.WEEKLY_REVIEW))
            }
        }

        // 4) daily loss cap (today only)
        val todays = live.filter { localDate(it.exitTime!!, zone) == today }
        val dnet = Stats.pySum(closed.filter { localDate(it.exitTime!!, zone) == today }.map { it.net!! })
        val sod = equity.before(dayStart(today, zone))
        var dayRun = 0.0
        var breachToday = false
        for (t in todays) {
            dayRun += t.net!!
            if (sod != 0.0 && XlMath.round(-dayRun / sod, 6) >= p.dailyLossCap) breachToday = true
        }
        if (breachToday) locks += Lock(LockCode.DAILY_LOSS, tomorrow, emptyList())

        // 5) consecutive stop-outs today
        var run = 0
        for (t in todays) {
            run = if (isStopOut(t, p)) run + 1 else 0
            if (run >= p.consecStops) {
                locks += Lock(LockCode.DAY_OVER, tomorrow, emptyList())
                break
            }
        }

        // 6) trades per day, by local entry date
        val nToday = entered.count { !it.raw.imported && localDate(it.entryTime, zone) == today }
        if (nToday >= p.maxTradesDay) locks += Lock(LockCode.TRADE_CAP, tomorrow, emptyList())

        // 7) cool-down after the latest stop-out
        val stops = live.filter { isStopOut(it, p) }.map { it.exitTime!! }
        if (stops.isNotEmpty()) {
            val until = stops.max().plusSeconds((p.cooldownMin * 60).toLong())
            if (now < until) locks += Lock(LockCode.COOLDOWN, until, emptyList())
        }

        locks.sortWith(compareBy<Lock>({ PRIORITY.indexOf(it.code) }, { it.until ?: Instant.MAX }))

        // risk reductions
        val reductions = ArrayList<Reduction>()
        fun addReduction(code: ReductionCode, start: Instant, risk: Double, n: Double) {
            val used = entered.count { it.entryTime >= start }
            if (used < n) reductions += Reduction(code, start, risk, (n - used).toInt())
        }
        for (s in dd.recoveryStarts) addReduction(ReductionCode.DD_RECOVERY, s, p.recoveryRisk, p.recoveryTrades)
        for (s in violationTriggers(events, p)) addReduction(ReductionCode.VIOLATIONS, s, p.reducedRisk, p.reducedTrades)
        val setup = setupId?.let { id -> setups.firstOrNull { it.id == id } }
        if (setupId != null) {
            val used = entered.count { it.raw.setupId == setupId }
            val demo = trades.count { !it.isReal && it.raw.setupId == setupId && it.entryTime <= now }
            val waived = setup?.testWaived == true && demo >= p.setupTestTrades
            if (used < p.setupTestTrades && !waived) {
                reductions += Reduction(ReductionCode.SETUP_TEST, null, p.setupTestRisk, (p.setupTestTrades - used).toInt())
            }
        }
        val riskCap = (listOf(p.riskPct) + reductions.map { it.riskPct }).min()
        val hold = bigWinHold(closed, equity, p, now, zone)
        val gradeAAllowed = setup?.grade == "A" && reductions.isEmpty() && hold == null
        val openTrades = entered.filter { it.exitTime == null || it.exitTime!! > now }
        return GovernorResult(
            canEnter = locks.isEmpty(), locks = locks, riskCap = riskCap, reductions = reductions,
            gradeAAllowed = gradeAAllowed, bigWinHoldUntil = hold, tradesToday = nToday,
            openPositions = openTrades.size, ddNow = dd.ddNow, dailyNet = dnet,
        )
    }

    /** Equity, the six dashboard limits and the governor state at [now] (3.13). */
    fun snapshot(
        now: Instant, zone: ZoneId, p: Params, ledger: List<LedgerEntry>, tradesRaw: List<TradeRaw>,
        reviews: List<Review>, setups: List<Setup>, setupId: String? = null,
    ): Snapshot {
        val trades = tradesRaw.map { Trades.resolve(it, p) }
        val closed = closed(trades, now)
        val today = localDate(now, zone)
        val wk = Jalali.weekStart(today)
        val eq = EquityIndex(ledger, trades)
        val eqNow = eq.now(now)
        val sod = eq.before(dayStart(today, zone))
        val sow = eq.before(dayStart(wk, zone))
        val dnet = Stats.pySum(closed.filter { localDate(it.exitTime!!, zone) == today }.map { it.net!! })
        val wnet = Stats.pySum(
            closed.filter { val d = localDate(it.exitTime!!, zone); !d.isBefore(wk) && !d.isAfter(today) }.map { it.net!! },
        )
        val g = governor(now, zone, trades, ledger, reviews, setups, p, setupId, eq)
        val openReal = entered(trades, now).filter { it.exitTime == null || it.exitTime!! > now }
        val d = Limits.dashboard(
            g.tradesToday, dnet, wnet, eqNow, sod, sow, g.ddNow, openReal.size, Stats.pySum(openReal.map { it.rrisk }), p,
        )
        return Snapshot(Equity(eqNow, sod, sow), d, g, trades)
    }

    // ------------------------------------------------------------------ sessions and parameter changes

    fun canStartSession(now: Instant, trades: List<ResolvedTrade>, p: Params): SessionCheck {
        val r = real(trades)
        if (r.any { it.exitTime == null && it.entryTime <= now }) return SessionCheck(false, SessionBlock.OPEN_POSITION, null)
        val losses = r.filter { it.exitTime != null && it.exitTime!! <= now && (it.net ?: 0.0) < 0 }.map { it.exitTime!! }
        if (losses.isNotEmpty()) {
            val until = losses.max().plusSeconds((p.reviewAfterLossH * 3_600).toLong())
            if (now < until) return SessionCheck(false, SessionBlock.RECENT_LOSS, until)
        }
        return SessionCheck(true, null, null)
    }

    fun classifyChange(key: String, old: Double, new: Double): ChangeKind {
        if (new == old) return ChangeKind.NONE
        return when (Params.TIGHTER[key] ?: throw IllegalArgumentException("unknown parameter $key")) {
            Tighter.NEUTRAL -> ChangeKind.NEUTRAL
            Tighter.LOWER -> if (new < old) ChangeKind.TIGHTEN else ChangeKind.LOOSEN
            Tighter.HIGHER -> if (new > old) ChangeKind.TIGHTEN else ChangeKind.LOOSEN
        }
    }

    fun changeEffectiveAt(key: String, old: Double, new: Double, appliedAt: Instant, p: Params): Pair<Instant, ChangeKind> {
        val kind = classifyChange(key, old, new)
        val at = if (kind == ChangeKind.LOOSEN) appliedAt.plusSeconds((p.loosenDelayH * 3_600).toLong()) else appliedAt
        return at to kind
    }

    /** Parameters in force at [t]: changes with `effectiveAt ≤ t`, ordered by `(effectiveAt, appliedAt)`. */
    fun paramsAt(base: Params, changes: List<ParamChange>, t: Instant): Params {
        val over = LinkedHashMap<String, Double>()
        for (c in changes.sortedWith(compareBy<ParamChange>({ it.effectiveAt }, { it.appliedAt }))) {
            if (c.effectiveAt <= t) over[c.key] = c.value
        }
        return base.with(over)
    }

    /** A setup may be promoted to grade A with ≥ `Min_Trades` closed REAL trades and mean R > 0 (3.9). */
    fun gradeAEligible(closedRs: List<Double?>, p: Params): GradeA {
        val n = closedRs.size
        if (n < p.minTrades) return GradeA(false, n, null)
        val e = Stats.pySum(closedRs.filterNotNull()) / n
        return GradeA(e > 0, n, e)
    }
}
