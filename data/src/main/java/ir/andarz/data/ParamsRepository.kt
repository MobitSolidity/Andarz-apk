package ir.andarz.data

import androidx.room.withTransaction
import ir.andarz.data.Audit.log
import ir.andarz.data.db.AndarzDatabase
import ir.andarz.data.db.ParamChangeEntity
import ir.andarz.data.db.ReviewSessionEntity
import ir.andarz.engine.ChangeKind
import ir.andarz.engine.Clock
import ir.andarz.engine.Governor
import ir.andarz.engine.Params
import ir.andarz.engine.SessionCheck
import ir.andarz.engine.Trades
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** `min ≤ value ≤ max` per parameter, from `params[]` of the content JSON (3.9). Built by `:app`. */
data class ParamBounds(val min: Double, val max: Double, val locked: Boolean = false)

sealed interface ChangeResult {
    data class Applied(val change: ParamChangeEntity) : ChangeResult
    data class Rejected(val reason: ChangeError) : ChangeResult
}

enum class ChangeError { NO_SESSION, LOCKED, BELOW_MIN, ABOVE_MAX, RISK_ORDER, NO_CHANGE, REASON_REQUIRED, NOT_PENDING }

/**
 * Parameters are never stored as current values: `params(now) = paramsAt(DEFAULTS, live changes, now)`
 * (4.2). Changes happen only inside an open review session (law 7, D-06) and each one writes an audit row.
 */
class ParamsRepository(
    private val db: AndarzDatabase,
    private val clock: Clock,
    private val bounds: Map<String, ParamBounds>,
) {
    suspend fun paramsAt(t: Instant): Params =
        Governor.paramsAt(Params.DEFAULTS, EngineMapping.toChanges(db.paramChanges().all()), t)

    suspend fun current(): Params = paramsAt(clock.now())

    // ---------------------------------------------------------------- review sessions (D-06)

    suspend fun openSession(): ReviewSessionEntity? {
        val s = db.sessions().open() ?: return null
        val now = clock.now().toEpochMilli()
        if (now - s.startedAt >= SESSION_MAX.toMillis()) {
            db.sessions().update(s.copy(endedAt = s.startedAt + SESSION_MAX.toMillis(), endReason = "TIMEOUT"))
            return null
        }
        return s
    }

    suspend fun canStartSession(): SessionCheck {
        val p = current()
        val trades = db.trades().engineRows().mapNotNull(EngineMapping::toRaw).map { Trades.resolve(it, p) }
        return Governor.canStartSession(clock.now(), trades, p)
    }

    /** Starts a session when allowed; returns the check either way. */
    suspend fun startSession(): Pair<SessionCheck, ReviewSessionEntity?> {
        openSession()?.let { return SessionCheck(true, null, null) to it }
        val check = canStartSession()
        if (!check.allowed) return check to null
        val s = ReviewSessionEntity(UUID.randomUUID().toString(), clock.now().toEpochMilli(), null, null)
        db.sessions().insert(s)
        return check to s
    }

    suspend fun endSession(reason: String = "USER") {
        val s = db.sessions().open() ?: return
        db.sessions().update(s.copy(endedAt = clock.now().toEpochMilli(), endReason = reason))
    }

    // ---------------------------------------------------------------- changes (3.9)

    suspend fun apply(key: String, newValue: Double, reason: String, requireSession: Boolean = true, viaLedger: Boolean = false): ChangeResult {
        val session = openSession()
        if (requireSession && session == null) return ChangeResult.Rejected(ChangeError.NO_SESSION)
        val b = bounds[key]
        // Start_Equity is locked in the Rules screen and changes only with the BASELINE row (D-18).
        if (key == "Chk_N" || (b?.locked == true && !(viaLedger && key == "Start_Equity"))) return ChangeResult.Rejected(ChangeError.LOCKED)
        if (reason.isBlank()) return ChangeResult.Rejected(ChangeError.REASON_REQUIRED)
        if (b != null && newValue < b.min) return ChangeResult.Rejected(ChangeError.BELOW_MIN)
        if (b != null && newValue > b.max) return ChangeResult.Rejected(ChangeError.ABOVE_MAX)
        val now = clock.now()
        val p = paramsAt(now)
        val old = p[key]
        val after = p.with(key, newValue)
        if (after.riskMax < after.riskPct) return ChangeResult.Rejected(ChangeError.RISK_ORDER)
        val (effectiveAt, kind) = Governor.changeEffectiveAt(key, old, newValue, now, p)
        if (kind == ChangeKind.NONE) return ChangeResult.Rejected(ChangeError.NO_CHANGE)
        val row = ParamChangeEntity(
            id = UUID.randomUUID().toString(), key = key, oldValue = old, newValue = newValue, kind = kind.name,
            appliedAt = now.toEpochMilli(), effectiveAt = effectiveAt.toEpochMilli(), reason = reason.trim(),
            sessionId = session?.id, cancelledAt = null, createdAt = now.toEpochMilli(),
        )
        db.withTransaction {
            db.paramChanges().insert(row)
            db.audit().log(now.toEpochMilli(), AuditType.PARAM_CHANGE, row.id, mapOf("key" to key, "old" to old, "new" to newValue, "kind" to kind.name))
        }
        return ChangeResult.Applied(row)
    }

    /** Onboarding choices are NEUTRAL changes effective at once, outside a session (D-18, 5.1). */
    suspend fun recordOnboarding(key: String, value: Double, at: Instant) {
        val row = ParamChangeEntity(
            UUID.randomUUID().toString(), key, Params.DEFAULTS[key], value, ChangeKind.NEUTRAL.name,
            at.toEpochMilli(), at.toEpochMilli(), ONBOARDING_REASON, null, null, at.toEpochMilli(),
        )
        db.paramChanges().insert(row)
        db.audit().log(at.toEpochMilli(), AuditType.PARAM_CHANGE, row.id, mapOf("key" to key, "new" to value, "kind" to "NEUTRAL"))
    }

    /** A pending change can be cancelled before it takes effect. */
    suspend fun cancel(id: String): ChangeError? {
        val c = db.paramChanges().get(id) ?: return ChangeError.NOT_PENDING
        val now = clock.now().toEpochMilli()
        if (c.cancelledAt != null || c.effectiveAt <= now) return ChangeError.NOT_PENDING
        db.withTransaction {
            db.paramChanges().update(c.copy(cancelledAt = now))
            db.audit().log(now, AuditType.PARAM_CANCEL, id, mapOf("key" to c.key))
        }
        return null
    }

    suspend fun pending(): List<ParamChangeEntity> {
        val now = clock.now().toEpochMilli()
        return db.paramChanges().all().filter { it.cancelledAt == null && it.effectiveAt > now }
    }

    companion object {
        val SESSION_MAX: Duration = Duration.ofMinutes(60)
        /** A code, not wording: the UI renders it through content. */
        const val ONBOARDING_REASON = "ONBOARDING"
    }
}
