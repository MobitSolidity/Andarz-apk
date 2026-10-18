package ir.andarz.data

import ir.andarz.data.db.AndarzDatabase
import ir.andarz.engine.Clock
import ir.andarz.engine.Governor
import ir.andarz.engine.Params
import ir.andarz.engine.Snapshot
import ir.andarz.engine.Trades
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * `snapshot(now)` as a pure function over the tables (4.5), and its reactive form. The zone comes from
 * settings; parameters are `paramsAt(now)`.
 */
class EngineFacade(
    private val db: AndarzDatabase,
    private val clock: Clock,
    private val zone: suspend () -> ZoneId,
) {
    suspend fun paramsAt(t: Instant): Params =
        Governor.paramsAt(Params.DEFAULTS, EngineMapping.toChanges(db.paramChanges().all()), t)

    suspend fun snapshot(now: Instant = clock.now(), setupId: String? = null): Snapshot {
        val p = paramsAt(now)
        return Governor.snapshot(
            now = now, zone = zone(), p = p,
            ledger = db.ledger().all().map(EngineMapping::toLedger),
            tradesRaw = db.trades().engineRows().mapNotNull(EngineMapping::toRaw),
            reviews = db.reviews().all().map(EngineMapping::toReview),
            setups = db.setups().all().map(EngineMapping::toSetup),
            setupId = setupId,
        )
    }

    /** The next instant at which the snapshot can change without a write (4.5 "boundaries"). */
    suspend fun nextBoundary(s: Snapshot, now: Instant): Instant {
        val z = zone()
        val today = now.atZone(z).toLocalDate()
        val candidates = ArrayList<Instant>()
        s.governor.locks.mapNotNullTo(candidates) { it.until }
        s.governor.bigWinHoldUntil?.let { candidates += it }
        candidates += today.plusDays(1).atStartOfDay(z).toInstant()
        candidates += ir.andarz.engine.Jalali.weekStart(today).plusDays(7).atStartOfDay(z).toInstant()
        return candidates.filter { it > now }.minOrNull() ?: now.plus(TICK)
    }

    /**
     * Recomputed on any change of the rule-relevant tables (Room invalidation), when a boundary passes, and
     * every 60 s while collected. Bursts of writes are debounced by 50 ms.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun observe(setupId: String? = null): Flow<Snapshot> {
        val tables = arrayOf("trade", "ledger_entry", "review", "param_change", "setup")
        val writes: Flow<Unit> = callbackFlow {
            val observer = object : androidx.room.InvalidationTracker.Observer(tables) {
                override fun onInvalidated(tables: Set<String>) { trySend(Unit) }
            }
            db.invalidationTracker.addObserver(observer)
            awaitClose { db.invalidationTracker.removeObserver(observer) }
        }
        val ticks: Flow<Unit> = flow {
            while (true) {
                val now = clock.now()
                val next = nextBoundary(snapshot(now, setupId), now)
                val wait = Duration.between(now, next).coerceAtMost(TICK).coerceAtLeast(Duration.ofMillis(200))
                delay(wait.toMillis())
                emit(Unit)
            }
        }
        return merge(writes, ticks).onStart { emit(Unit) }.debounce(50).mapLatest { snapshot(clock.now(), setupId) }
    }

    /** Resolved trades for list screens (no rule logic). */
    suspend fun resolvedTrades() = paramsAt(clock.now()).let { p ->
        db.trades().engineRows().mapNotNull(EngineMapping::toRaw).map { Trades.resolve(it, p) }
    }

    companion object {
        val TICK: Duration = Duration.ofSeconds(60)
    }
}

@Suppress("unused")
private fun <A, B> Flow<A>.unusedCombine(o: Flow<B>) = combine(this, o) { a, _ -> a }
