package ir.andarz.data

import ir.andarz.data.db.LedgerEntryEntity
import ir.andarz.data.db.ParamChangeEntity
import ir.andarz.data.db.PlanSnapshot
import ir.andarz.data.db.ReviewEntity
import ir.andarz.data.db.SetupEntity
import ir.andarz.data.db.TradeEntity
import ir.andarz.engine.Account
import ir.andarz.engine.LedgerEntry
import ir.andarz.engine.LedgerType
import ir.andarz.engine.Leg
import ir.andarz.engine.ParamChange
import ir.andarz.engine.Plan
import ir.andarz.engine.Review
import ir.andarz.engine.ReviewType
import ir.andarz.engine.Setup
import ir.andarz.engine.Side
import ir.andarz.engine.SizeVerdict
import ir.andarz.engine.TradeRaw
import java.time.Instant
import java.time.LocalDate

/** Rows ↔ engine types (4.2 "Engine mapping"). Pure functions, tested on the JVM. */
object EngineMapping {
    private fun ms(v: Long) = Instant.ofEpochMilli(v)

    /** Only OPEN and CLOSED rows have an entry time and reach the engine; anything else returns null. */
    fun toRaw(t: TradeEntity): TradeRaw? {
        if (t.status != "OPEN" && t.status != "CLOSED") return null
        val entryTime = t.entryTime ?: return null
        val closed = t.status == "CLOSED" && t.exitTime != null
        return TradeRaw(
            id = t.id,
            account = Account.valueOf(t.account),
            setupId = t.setupId,
            side = Side.valueOf(t.side),
            entry = t.entry ?: t.plannedEntry,
            sl = t.slInitial,
            tp = t.tp,
            lev = t.lev,
            eq = t.equity,
            chk = t.chk,
            gateAllowed = t.gateAllowed,
            notional = t.notional ?: 0.0,
            entryTime = ms(entryTime),
            exitTime = if (closed) ms(t.exitTime!!) else null,
            legs = if (closed) t.legs.map { Leg(it.price, it.fraction) } else emptyList(),
            fee = t.fee,
            exitReason = t.exitReason,
            violations = t.violations,
            violationAt = t.violationAt?.let(::ms),
            violationReport = t.violationReport,
            mistake = t.mistake,
            ctxRiskCap = t.riskPctCap,
            ctxGradeA = t.gradeA,
            imported = t.imported,
            storedVerdict = t.verdict?.let(SizeVerdict::valueOf),
            storedRrisk = t.rrisk,
            storedRriskp = t.rriskp,
            storedNet = if (closed) t.net else null,
            storedR = if (closed) t.r else null,
            storedOk = if (closed) t.ok else null,
        )
    }

    fun toLedger(e: LedgerEntryEntity) = LedgerEntry(ms(e.time), LedgerType.valueOf(e.type), e.amount)

    fun toReview(r: ReviewEntity) = Review(
        type = ReviewType.valueOf(r.type),
        completedAt = ms(r.completedAt),
        weekStart = if (r.type == "WEEKLY") LocalDate.parse(r.periodStart) else null,
    )

    fun toSetup(s: SetupEntity) = Setup(s.id, s.grade, s.testWaived)

    /** Cancelled changes never apply (4.2 `param_change`). */
    fun toChanges(rows: List<ParamChangeEntity>): List<ParamChange> =
        rows.filter { it.cancelledAt == null }.map { ParamChange(it.key, it.newValue, ms(it.appliedAt), ms(it.effectiveAt)) }

    fun snapshotOf(p: Plan) = PlanSnapshot(p.sld, p.rr, p.riskUsd, p.notionalMax, p.margin, p.marginPct, p.liq, p.liqDist, p.liqX)
}
