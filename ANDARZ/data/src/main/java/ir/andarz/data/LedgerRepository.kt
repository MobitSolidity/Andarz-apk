package ir.andarz.data

import androidx.room.withTransaction
import ir.andarz.data.Audit.log
import ir.andarz.data.db.AndarzDatabase
import ir.andarz.data.db.LedgerEntryEntity
import ir.andarz.data.db.ReviewEntity
import ir.andarz.data.db.SetupEntity
import ir.andarz.engine.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

enum class LedgerError { NEGATIVE_EQUITY, BASELINE_LOCKED, BASELINE_EXISTS, NOT_POSITIVE }

/** Ledger of 5.12 and onboarding (5.1). Exactly one BASELINE exists; every edit is audited. */
class LedgerRepository(
    private val db: AndarzDatabase,
    private val clock: Clock,
    private val params: ParamsRepository,
    private val facade: EngineFacade,
) {
    private fun now() = clock.now().toEpochMilli()

    /** Onboarding: BASELINE at 00:00 local of today plus NEUTRAL Start_Equity and Risk_Pct (5.1, D-18). */
    suspend fun onboard(equity: Double, riskPct: Double, zone: ZoneId): LedgerError? {
        require(equity.isFinite() && riskPct.isFinite())
        if (equity <= 0) return LedgerError.NOT_POSITIVE
        if (db.ledger().baselines().isNotEmpty()) return LedgerError.BASELINE_EXISTS
        val at = clock.now()
        val midnight = at.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        db.withTransaction {
            db.ledger().insert(LedgerEntryEntity(UUID.randomUUID().toString(), midnight.toEpochMilli(), "BASELINE", equity, "", at.toEpochMilli()))
            params.recordOnboarding("Start_Equity", equity, at)
            params.recordOnboarding("Risk_Pct", riskPct, at)
        }
        return null
    }

    /** DEPOSIT, WITHDRAWAL (stored positive) or ADJUSTMENT (signed). A withdrawal above equity is refused. */
    suspend fun add(type: String, amount: Double, time: Instant, note: String): LedgerError? {
        require(amount.isFinite())
        require(type in setOf("DEPOSIT", "WITHDRAWAL", "ADJUSTMENT"))
        if (type != "ADJUSTMENT" && amount <= 0) return LedgerError.NOT_POSITIVE
        if (type == "WITHDRAWAL" && amount > facade.snapshot(clock.now()).equity.now) return LedgerError.NEGATIVE_EQUITY
        val e = LedgerEntryEntity(UUID.randomUUID().toString(), time.toEpochMilli(), type, amount, note, now())
        db.withTransaction {
            db.ledger().insert(e)
            db.audit().log(now(), AuditType.LEDGER_EDIT, e.id, mapOf("op" to "ADD", "type" to type, "amount" to amount))
        }
        return null
    }

    suspend fun delete(id: String): LedgerError? {
        val e = db.ledger().get(id) ?: return null
        if (e.type == "BASELINE") return LedgerError.BASELINE_LOCKED
        db.withTransaction {
            db.ledger().delete(e)
            db.audit().log(now(), AuditType.LEDGER_DELETE, id, mapOf("type" to e.type, "amount" to e.amount))
        }
        return null
    }

    /** BASELINE edits only inside a review session; Start_Equity changes with it, one transaction (D-18). */
    suspend fun editBaseline(amount: Double, time: Instant? = null, reason: String): LedgerError? {
        require(amount.isFinite())
        if (amount <= 0) return LedgerError.NOT_POSITIVE
        if (params.openSession() == null) return LedgerError.BASELINE_LOCKED
        val b = db.ledger().baselines().single()
        var err: LedgerError? = null
        db.withTransaction {
            db.ledger().update(b.copy(amount = amount, time = time?.toEpochMilli() ?: b.time))
            if (params.apply("Start_Equity", amount, reason, viaLedger = true) is ChangeResult.Rejected) err = LedgerError.BASELINE_LOCKED
            db.audit().log(now(), AuditType.LEDGER_EDIT, b.id, mapOf("op" to "BASELINE", "old" to b.amount, "new" to amount))
            if (err != null) throw IllegalStateException("rollback")
        }
        return err
    }
}

/** Setups (5.11) and reviews (5.9): thin writers, rules stay in the engine. */
class SetupRepository(private val db: AndarzDatabase, private val clock: Clock) {
    suspend fun create(name: String, entryRule: String = "", invalidation: String = "", target: String = "", notes: String = ""): SetupEntity {
        val n = clock.now().toEpochMilli()
        val s = SetupEntity(UUID.randomUUID().toString(), name.trim(), entryRule, invalidation, target, notes, "B", false, false, n, n)
        db.setups().upsert(s)
        return s
    }

    suspend fun update(s: SetupEntity) = db.setups().upsert(s.copy(updatedAt = clock.now().toEpochMilli()))
}

class ReviewRepository(private val db: AndarzDatabase, private val clock: Clock) {
    /** [periodStart]: the day, the week's Saturday, the month start or the breach date (4.2). */
    suspend fun save(type: String, periodStart: LocalDate, periodEnd: LocalDate, payload: String, kpiSnapshot: String): ReviewEntity {
        val r = ReviewEntity(UUID.randomUUID().toString(), type, clock.now().toEpochMilli(), periodStart.toString(), periodEnd.toString(), payload, kpiSnapshot)
        db.reviews().insert(r)
        return r
    }
}
