package ir.andarz.data

import androidx.room.withTransaction
import ir.andarz.data.Audit.log
import ir.andarz.data.db.AndarzDatabase
import ir.andarz.data.db.LegRow
import ir.andarz.data.db.TradeEntity
import ir.andarz.data.db.TradeEventEntity
import ir.andarz.engine.Clock
import ir.andarz.engine.Leg
import ir.andarz.engine.Management
import ir.andarz.engine.Planner
import ir.andarz.engine.Side
import ir.andarz.engine.SizeVerdict
import ir.andarz.engine.TradeResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.math.abs

/** Everything the wizard collected before a plan is saved (5.3, 4.4 "Plan saved"). */
data class PlanDraft(
    val account: String,
    val symbol: String,
    val side: Side,
    val kind: String,
    val setupId: String?,
    val timeframe: String,
    val htfTrend: String,
    val counterTrendReason: String?,
    val emotion: String,
    val checklistMask: Int,
    val equity: Double,
    val plannedEntry: Double,
    val sl: Double,
    val tp: Double,
    val lev: Double,
    val riskPctChosen: Double,
    val entryOrder: String,
    val isolated: Boolean,
    val slTpOnExchange: Boolean,
    val canMonitor: Boolean,
)

/** The confirmation sheet (5.3). */
data class EntryConfirm(
    val entry: Double,
    val notional: Double,
    val entryTime: Instant,
    /** Confirmed with `slTpOnExchange` off: records V01 (5.3). */
    val withoutExchangeStops: Boolean = false,
)

/** A closing leg typed in the close sheet (5.5). */
data class ExitLeg(val price: Double, val fraction: Double, val fee: Double = 0.0)

/** The post-trade review (5.5, 4.4 "Review saved"). */
data class ReviewInput(
    val selfViolations: List<String>,
    val violationReport: String?,
    val mistake: String,
    val emotionAfter: String?,
    val lesson: String?,
)

class TradeStateException(msg: String) : IllegalStateException(msg)

/**
 * The trade lifecycle of 4.3, persisting exactly what 4.4 lists at each moment. Derived fields are written
 * when they become known and never recomputed later (law 6, D-24). Every numeric correction and deletion
 * writes an audit row (4.1).
 */
class JournalRepository(
    private val db: AndarzDatabase,
    private val clock: Clock,
    private val params: ParamsRepository,
    private val facade: EngineFacade,
) {
    private fun now() = clock.now().toEpochMilli()
    private fun checkFinite(vararg xs: Double) = xs.forEach { require(it.isFinite()) { "numbers must be finite" } }

    private fun popcount(mask: Int) = Integer.bitCount(mask and 0xFF)

    /** Save a plan (PLANNED) or a denied plan (REJECTED). Gate and governor are evaluated now (4.4). */
    suspend fun savePlan(d: PlanDraft, rejected: Boolean = false): TradeEntity {
        checkFinite(d.equity, d.plannedEntry, d.sl, d.tp, d.lev, d.riskPctChosen)
        val at = clock.now()
        val p = params.paramsAt(at)
        val snap = facade.snapshot(at, d.setupId)
        val plan = Planner.plan(d.side, d.equity, d.plannedEntry, d.sl, d.tp, d.lev, p, d.riskPctChosen)
        val chk = popcount(d.checklistMask)
        val reasons = Planner.gate(d.side, d.plannedEntry, d.sl, d.tp, d.lev, chk, plan, p)
        val gatePlanOk = reasons.isEmpty()
        val entryAllowed = gatePlanOk && snap.mayEnter
        val t = TradeEntity(
            id = UUID.randomUUID().toString(), account = d.account, status = if (rejected) "REJECTED" else "PLANNED",
            symbol = d.symbol.trim().uppercase(java.util.Locale.ROOT), side = d.side.name, kind = d.kind, setupId = d.setupId,
            timeframe = d.timeframe, htfTrend = d.htfTrend, counterTrendReason = d.counterTrendReason, emotion = d.emotion,
            checklistMask = d.checklistMask, chk = chk, equity = d.equity, plannedEntry = d.plannedEntry, slInitial = d.sl,
            tp = d.tp, lev = d.lev, riskPctChosen = d.riskPctChosen, riskPctCap = snap.governor.riskCap,
            gradeA = snap.governor.gradeAAllowed, entryOrder = d.entryOrder, isolated = d.isolated,
            slTpOnExchange = d.slTpOnExchange, canMonitor = d.canMonitor, plan = EngineMapping.snapshotOf(plan),
            gateReasons = reasons.joinToString("|") { it.name }, gatePlanOk = gatePlanOk, entryAllowed = entryAllowed,
            gateAllowed = false, createdAt = at.toEpochMilli(), updatedAt = at.toEpochMilli(),
        )
        db.trades().insert(t)
        return t
    }

    /**
     * Confirm the entry: PLANNED → OPEN (or the D-28 "entered anyway" path from a denied plan). Persists the
     * execution fields, the size verdict with `paramsAt(entryTime)`, auto violations and `gateAllowed`.
     */
    suspend fun confirmEntry(id: String, c: EntryConfirm): TradeEntity {
        checkFinite(c.entry, c.notional)
        require(c.entry > 0 && c.notional > 0) { "entry and notional must be positive" }
        val t = db.trades().get(id) ?: throw TradeStateException("no trade $id")
        if (t.status != "PLANNED" && t.status != "REJECTED") throw TradeStateException("trade $id is ${t.status}")
        if (!t.canMonitor) throw TradeStateException("canMonitor is off (article 6-7)")
        val p = params.paramsAt(c.entryTime)
        // re-evaluate the governor at confirmation: a lock that arrived since the plan counts (D-08)
        val snap = facade.snapshot(clock.now(), t.setupId)
        val entryAllowed = t.gatePlanOk && snap.mayEnter
        val sld = abs(c.entry - t.slInitial) / c.entry
        val rrisk = c.notional * sld
        val rriskp = rrisk / t.equity
        val verdict = Planner.sizeVerdict(rriskp, p, t.riskPctCap, t.gradeA)
        val auto = LinkedHashSet<String>()
        if (verdict == SizeVerdict.RED_LINE) auto += "V02"
        if (t.lev > p.levMax || !t.isolated) auto += "V03"
        if (t.chk < p.chkN) auto += "V06"
        if (c.withoutExchangeStops || !t.slTpOnExchange) auto += "V01"
        val n = now()
        val out = t.copy(
            status = "OPEN", entry = c.entry, notional = c.notional, entryTime = c.entryTime.toEpochMilli(),
            slCurrent = t.slInitial, rrisk = rrisk, rriskp = rriskp, verdict = verdict.name,
            entryAllowed = entryAllowed, gateAllowed = t.gatePlanOk && entryAllowed,
            violations = (t.violations + auto).distinct(),
            violationAt = if (auto.isNotEmpty()) (t.violationAt ?: n) else t.violationAt,
            updatedAt = n,
        )
        db.withTransaction {
            db.trades().update(out)
            // a REAL entry ends an open review session (D-06)
            if (out.account == "REAL") params.endSession("TRADE_OPENED")
        }
        return out
    }

    /** Abandon a plan; also the 12-hour expiry sweep (4.3, D-37). */
    suspend fun cancelPlan(id: String) {
        val t = db.trades().get(id) ?: return
        if (t.status == "PLANNED") db.trades().update(t.copy(status = "CANCELLED", updatedAt = now()))
    }

    suspend fun expirePlans(): Int {
        val before = now() - PLAN_EXPIRY.toMillis()
        val rows = db.trades().plannedBefore(before)
        rows.forEach { db.trades().update(it.copy(status = "CANCELLED", updatedAt = now())) }
        return rows.size
    }

    private suspend fun openTrade(id: String): TradeEntity {
        val t = db.trades().get(id) ?: throw TradeStateException("no trade $id")
        if (t.status != "OPEN") throw TradeStateException("trade $id is ${t.status}")
        return t
    }

    private fun event(tradeId: String, type: String, at: Long, payload: Map<String, Any?>) = TradeEventEntity(
        UUID.randomUUID().toString(), tradeId, at, type,
        JsonObject(payload.mapValues { (_, v) -> if (v == null) kotlinx.serialization.json.JsonNull else if (v is Number) JsonPrimitive(v) else if (v is Boolean) JsonPrimitive(v) else JsonPrimitive(v.toString()) }).toString(),
    )

    /** Move or remove the stop (5.4). Away or removed adds V04; `slInitial` never changes. */
    suspend fun moveStop(id: String, newSl: Double?): TradeEntity {
        newSl?.let { checkFinite(it) }
        val t = openTrade(id)
        val side = Side.valueOf(t.side)
        val away = Management.isAwayMove(side, t.slCurrent, newSl)
        val n = now()
        val out = t.copy(
            slCurrent = newSl,
            violations = if (away) (t.violations + "V04").distinct() else t.violations,
            violationAt = if (away) (t.violationAt ?: n) else t.violationAt,
            updatedAt = n,
        )
        db.withTransaction {
            db.trades().update(out)
            db.events().insert(event(id, "SL_MOVE", n, mapOf("from" to t.slCurrent, "to" to newSl, "away" to away)))
        }
        return out
    }

    /** Partial exit of a fraction of the original position (5.4); never more than what remains. */
    suspend fun partialExit(id: String, price: Double, fraction: Double, fee: Double = 0.0): TradeEntity {
        checkFinite(price, fraction, fee)
        val t = openTrade(id)
        val remaining = 1.0 - t.legs.sumOf { it.fraction }
        require(fraction > 0 && fraction < remaining - 1e-9) { "a partial exit must leave a remainder" }
        val n = now()
        val out = t.copy(legs = t.legs + LegRow(price, fraction, n, fee), updatedAt = n)
        db.withTransaction {
            db.trades().update(out)
            db.events().insert(event(id, "PARTIAL_EXIT", n, mapOf("price" to price, "fraction" to fraction, "fee" to fee)))
        }
        return out
    }

    /** Free events: NEWS, EMOTION, PRAYER, NOTE (5.4). ADD_REPORTED goes through [reportAdd]. */
    suspend fun addEvent(id: String, type: String, text: String? = null) {
        require(type in setOf("NEWS", "EMOTION", "PRAYER", "NOTE"))
        val t = db.trades().get(id) ?: throw TradeStateException("no trade $id")
        db.events().insert(event(t.id, type, now(), if (text != null) mapOf(if (type == "EMOTION") "emotion" to text else "text" to text) else emptyMap()))
    }

    /** "I added to the position": V05 and an ADD_REPORTED event; the VIOLATION lock appears at once (5.4). */
    suspend fun reportAdd(id: String): TradeEntity {
        val t = openTrade(id)
        val n = now()
        val out = t.copy(violations = (t.violations + "V05").distinct(), violationAt = t.violationAt ?: n, updatedAt = n)
        db.withTransaction { db.trades().update(out); db.events().insert(event(id, "ADD_REPORTED", n, emptyMap())) }
        return out
    }

    suspend fun monitorCheck(id: String) {
        val t = openTrade(id)
        db.trades().update(t.copy(monitorChecks = t.monitorChecks + now(), updatedAt = now()))
    }

    /**
     * Close: OPEN → CLOSED. The final legs plus the recorded partial legs must sum to 1. Persists
     * `exitTime, legs, fee, exitReason` then `gross, net, r, result, equityAfter, ok` from the stored verdict.
     */
    suspend fun close(id: String, finalLegs: List<ExitLeg>, extraFee: Double, exitReason: String, exitTime: Instant): TradeEntity {
        finalLegs.forEach { checkFinite(it.price, it.fraction, it.fee) }
        checkFinite(extraFee)
        val t = openTrade(id)
        require(exitTime.toEpochMilli() >= t.entryTime!!) { "exit before entry" }
        require(!exitTime.isAfter(clock.now())) { "exit in the future" }
        val legs = t.legs + finalLegs.map { LegRow(it.price, it.fraction, exitTime.toEpochMilli(), it.fee) }
        require(abs(legs.sumOf { it.fraction } - 1.0) < 1e-9) { "leg fractions must sum to 100%" }
        val fee = legs.sumOf { it.fee } + extraFee
        val f = Planner.fill(Side.valueOf(t.side), t.entry!!, t.slInitial, t.equity, t.notional!!, legs.map { Leg(it.price, it.fraction) }, fee)
        val verdict = SizeVerdict.valueOf(t.verdict!!)
        val ok = Planner.compliant(t.violations, t.gateAllowed, verdict)
        val out = t.copy(
            status = "CLOSED", exitTime = exitTime.toEpochMilli(), legs = legs, fee = fee, exitReason = exitReason,
            gross = f.gross, net = f.net, r = f.r, result = (f.result ?: TradeResult.EVEN).name, equityAfter = f.equityAfter,
            ok = ok, updatedAt = now(),
        )
        db.withTransaction {
            db.trades().update(out)
            // MK3: trade-linked price alerts vanish when the trade closes (K9.1 b).
            db.tradeAlerts().removeAll(id)
        }
        return out
    }

    /**
     * Post-trade review (5.5). Self-reported violations join the auto ones (`violationAt` set when the first is
     * added); `ok` follows the violations, the stored gate flag and the stored verdict.
     */
    suspend fun saveReview(id: String, r: ReviewInput, reportMinChars: Int): TradeEntity {
        val t = db.trades().get(id) ?: throw TradeStateException("no trade $id")
        val violations = (t.violations + r.selfViolations).distinct()
        if (violations.isNotEmpty() && (r.violationReport ?: "").trim().length < reportMinChars) {
            throw TradeStateException("violation report too short")
        }
        val n = now()
        val ok = if (t.status == "CLOSED" && t.verdict != null) Planner.compliant(violations, t.gateAllowed, SizeVerdict.valueOf(t.verdict)) else t.ok
        val out = t.copy(
            violations = violations, violationAt = if (violations.isNotEmpty()) (t.violationAt ?: n) else null,
            violationReport = r.violationReport?.trim(), mistake = r.mistake, emotionAfter = r.emotionAfter,
            lesson = r.lesson?.trim(), reviewedAt = n, ok = ok, updatedAt = n,
        )
        db.trades().update(out)
        return out
    }

    /**
     * Delete a trade. Refused while any lock is active (D-23); audited. Attachments and events cascade.
     */
    suspend fun delete(id: String): Boolean {
        val t = db.trades().get(id) ?: return false
        if (t.status == "OPEN" || t.status == "CLOSED") {
            if (facade.snapshot(clock.now(), null).governor.locks.isNotEmpty()) return false
        }
        db.withTransaction {
            db.trades().delete(t)
            db.audit().log(now(), AuditType.TRADE_DELETE, id, mapOf("status" to t.status, "net" to t.net))
        }
        return true
    }

    companion object {
        val PLAN_EXPIRY: Duration = Duration.ofHours(12)
    }
}
