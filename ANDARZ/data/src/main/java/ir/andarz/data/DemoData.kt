package ir.andarz.data

import androidx.room.withTransaction
import ir.andarz.data.Audit.log
import ir.andarz.data.db.AndarzDatabase
import ir.andarz.data.db.LegRow
import ir.andarz.data.db.TradeEntity
import ir.andarz.engine.Clock
import ir.andarz.engine.Leg
import ir.andarz.engine.Params
import ir.andarz.engine.Planner
import ir.andarz.engine.Side
import java.time.Duration
import java.time.Instant

/**
 * Demo data (5.14, P1, D-68): twelve closed `DEMO` trades flagged `imported = false, account = DEMO`, so every
 * screen can be explored without touching real statistics (D-15: demo rows are ignored by every rule, limit
 * and KPI). The rows follow the shape of vector K01 (entry 100, stop 96, target 108, equity 2000) but are
 * dated over the twelve days before [load]. Ids start with [PREFIX], so [remove] deletes exactly these rows and
 * never a demo trade the user logged by hand.
 */
class DemoData(private val db: AndarzDatabase, private val clock: Clock, private val params: ParamsRepository) {

    /** One scripted row: exit legs, fee, exit reason, mistake id, checklist score. */
    data class Script(val legs: List<Pair<Double, Double>>, val fee: Double, val reason: String, val mistake: String, val chk: Int = 8, val side: Side = Side.LONG)

    suspend fun loaded(): Boolean = db.trades().all().any { it.id.startsWith(PREFIX) }

    /** Inserts the twelve rows; a second call is a no-op. Returns the number inserted. */
    suspend fun load(): Int {
        if (loaded()) return 0
        val now = clock.now()
        val p = params.paramsAt(now)
        val rows = SCRIPT.mapIndexed { k, s -> row(k, s, now.minus(Duration.ofDays((SCRIPT.size - k).toLong())), p) }
        db.withTransaction {
            db.trades().insertAll(rows)
            db.audit().log(now.toEpochMilli(), AuditType.DEMO_LOAD, null, mapOf("rows" to rows.size))
        }
        return rows.size
    }

    /** Deletes the rows [load] inserted (events and attachments cascade). Returns the number removed. */
    suspend fun remove(): Int {
        val rows = db.trades().all().filter { it.id.startsWith(PREFIX) }
        db.withTransaction {
            rows.forEach { db.trades().delete(it) }
            db.audit().log(clock.now().toEpochMilli(), AuditType.DEMO_REMOVE, null, mapOf("rows" to rows.size))
        }
        return rows.size
    }

    private fun row(k: Int, s: Script, entryAt: Instant, p: Params): TradeEntity {
        val long = s.side == Side.LONG
        val entry = 100.0
        val sl = if (long) 96.0 else 104.0
        val tp = if (long) 108.0 else 92.0
        val eq = 2000.0
        val lev = 5.0
        val notional = 500.0
        val plan = Planner.plan(s.side, eq, entry, sl, tp, lev, p, p.riskPct)
        val reasons = Planner.gate(s.side, entry, sl, tp, lev, s.chk, plan, p)
        val gateOk = reasons.isEmpty()
        val rrisk = notional * kotlin.math.abs(entry - sl) / entry
        val rriskp = rrisk / eq
        val verdict = Planner.sizeVerdict(rriskp, p, p.riskPct, true)
        val exitAt = entryAt.plus(Duration.ofHours(2))
        val legs = s.legs.map { (px, f) -> LegRow(px, f, exitAt.toEpochMilli()) }
        val fill = Planner.fill(s.side, entry, sl, eq, notional, legs.map { Leg(it.price, it.fraction) }, s.fee)
        val violations = if (s.chk < p.chkN) listOf("V06") else emptyList()
        val ok = Planner.compliant(violations, gateOk, verdict)
        val ms = entryAt.toEpochMilli()
        val mask = (1 shl s.chk) - 1
        return TradeEntity(
            id = PREFIX + (k + 1).toString().padStart(2, '0'), account = "DEMO", status = "CLOSED", symbol = SYMBOLS[k % SYMBOLS.size],
            side = s.side.name, kind = "INTRADAY", setupId = null, timeframe = "15m", htfTrend = if (long) "UP" else "DOWN",
            counterTrendReason = null, emotion = "CALM", checklistMask = mask, chk = s.chk, equity = eq, plannedEntry = entry,
            slInitial = sl, tp = tp, lev = lev, riskPctChosen = p.riskPct, riskPctCap = p.riskPct, gradeA = true,
            entryOrder = "LIMIT", isolated = true, slTpOnExchange = true, canMonitor = true, plan = EngineMapping.snapshotOf(plan),
            gateReasons = reasons.joinToString("|") { it.name }, gatePlanOk = gateOk, entryAllowed = gateOk, gateAllowed = gateOk,
            entry = entry, notional = notional, entryTime = ms, slCurrent = sl, rrisk = rrisk, rriskp = rriskp, verdict = verdict.name,
            exitTime = exitAt.toEpochMilli(), legs = legs, fee = s.fee, exitReason = s.reason, gross = fill.gross, net = fill.net,
            r = fill.r, result = fill.net?.let { if (it > 0) "WIN" else if (it < 0) "LOSS" else "EVEN" }, equityAfter = fill.equityAfter,
            ok = ok, violations = violations, violationAt = if (violations.isNotEmpty()) exitAt.toEpochMilli() else null,
            mistake = s.mistake, reviewedAt = exitAt.toEpochMilli(), imported = false, createdAt = ms, updatedAt = ms,
        )
    }

    companion object {
        const val PREFIX = "demo-"
        private val SYMBOLS = listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT")

        /** The twelve rows: the result mix of K01 (wins, stop-outs, a partial exit, a manual exit, a V06). */
        val SCRIPT: List<Script> = listOf(
            Script(listOf(108.0 to 1.0), 0.6, "TAKE_PROFIT", "M00"),
            Script(listOf(96.0 to 1.0), 0.6, "STOP_LOSS", "M02"),
            Script(listOf(96.0 to 1.0), 0.6, "STOP_LOSS", "M01"),
            Script(listOf(108.0 to 1.0), 0.6, "TAKE_PROFIT", "M00"),
            Script(listOf(96.0 to 1.0), 0.6, "STOP_LOSS", "M07", chk = 7),
            Script(listOf(104.0 to 0.5, 108.0 to 0.5), 0.5, "TAKE_PROFIT", "M00"),
            Script(listOf(101.0 to 1.0), 0.5, "MANUAL", "M05"),
            Script(listOf(92.0 to 1.0), 0.6, "TAKE_PROFIT", "M00", side = Side.SHORT),
            Script(listOf(104.0 to 1.0), 0.6, "STOP_LOSS", "M03", side = Side.SHORT),
            Script(listOf(108.0 to 1.0), 0.6, "TAKE_PROFIT", "M00"),
            Script(listOf(99.0 to 1.0), 0.5, "MANUAL", "M06"),
            Script(listOf(106.0 to 1.0), 0.6, "MANUAL", "M00"),
        )
    }
}
