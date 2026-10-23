package ir.andarz.engine

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/**
 * A trade as the engine sees it (4.2 "Engine mapping"). Only OPEN and CLOSED rows reach the engine, so
 * [entryTime] is always set. The derived fields at the end are the values *persisted* by `:data`; when
 * present they win over recomputation (D-24, law 6). The golden vectors pass none of them.
 */
data class TradeRaw(
    val id: String,
    val account: Account = Account.REAL,
    val setupId: String? = null,
    val side: Side,
    val entry: Double,
    val sl: Double,
    val tp: Double,
    val lev: Double,
    val eq: Double,
    val chk: Int,
    val gateAllowed: Boolean,
    val notional: Double,
    val entryTime: Instant,
    val exitTime: Instant? = null,
    val legs: List<Leg> = emptyList(),
    val fee: Double = 0.0,
    val exitReason: String? = null,
    val violations: List<String> = emptyList(),
    val violationAt: Instant? = null,
    val violationReport: String? = null,
    val mistake: String? = null,
    /** `ctx.riskCap` (stored `riskPctCap`); null = the default `Risk_Pct`. */
    val ctxRiskCap: Double? = null,
    /** `ctx.gradeA`; the reference defaults it to true. */
    val ctxGradeA: Boolean = true,
    /** History row from CSV (D-14): counts for statistics, never creates a lock. */
    val imported: Boolean = false,
    // ---- persisted derived values (D-24)
    val storedVerdict: SizeVerdict? = null,
    val storedRrisk: Double? = null,
    val storedRriskp: Double? = null,
    val storedNet: Double? = null,
    val storedR: Double? = null,
    val storedOk: Boolean? = null,
)

/** A trade with its derived fields resolved (`resolve_trade` of the reference). */
data class ResolvedTrade(
    val raw: TradeRaw,
    val rrisk: Double,
    val rriskp: Double?,
    val verdict: SizeVerdict,
    val gross: Double?,
    val net: Double?,
    val r: Double?,
    val result: TradeResult?,
    val ok: Boolean?,
) {
    val id: String get() = raw.id
    val isReal: Boolean get() = raw.account == Account.REAL
    val entryTime: Instant get() = raw.entryTime
    val exitTime: Instant? get() = raw.exitTime
}

object Trades {
    /** Raw trade → derived fields. Stored values win; recomputation only fills gaps (D-24). */
    fun resolve(t: TradeRaw, p: Params): ResolvedTrade {
        val sld = abs(t.entry - t.sl) / t.entry
        val rrisk = t.storedRrisk ?: (t.notional * sld)
        val rriskp = t.storedRriskp ?: (if (t.eq != 0.0) rrisk / t.eq else null)
        val verdict = t.storedVerdict ?: Planner.sizeVerdict(rriskp ?: 0.0, p, t.ctxRiskCap, t.ctxGradeA)
        if (t.exitTime == null) return ResolvedTrade(t, rrisk, rriskp, verdict, null, null, null, null, null)
        val f = Planner.fill(t.side, t.entry, t.sl, t.eq, t.notional, t.legs, t.fee)
        val net = t.storedNet ?: f.net
        val r = t.storedR ?: f.r
        val result = net?.let { if (it > 0) TradeResult.WIN else if (it < 0) TradeResult.LOSS else TradeResult.EVEN }
        val ok = t.storedOk ?: Planner.compliant(t.violations, t.gateAllowed, verdict)
        return ResolvedTrade(t, rrisk, rriskp, verdict, f.gross, net, r, result, ok)
    }

    /** Closed REAL trades in exit order, ties by `(exitTime, entryTime, id)` (3.8). */
    fun closedInExitOrder(trades: List<ResolvedTrade>): List<ResolvedTrade> =
        trades.filter { it.isReal && it.exitTime != null }
            .sortedWith(compareBy<ResolvedTrade>({ it.exitTime }, { it.entryTime }, { it.id }))

    /**
     * `kpi_from_trades` of the reference: resolve, build the all-time curve (maxDD is taken from it, not
     * re-based per period), then KPIs and tallies for exits inside `[from, to]`.
     */
    fun kpis(raw: List<TradeRaw>, from: java.time.LocalDate, to: java.time.LocalDate, p: Params, zone: ZoneId): Pair<Kpis, List<ClosedRow>> {
        val closed = closedInExitOrder(raw.map { resolve(it, p) })
        val cv = Stats.curve(closed.map { it.net!! }, p.startEquity)
        val rows = closed.mapIndexed { i, t ->
            ClosedRow(
                id = t.id,
                exitDate = t.exitTime!!.atZone(zone).toLocalDate(),
                net = t.net!!,
                r = t.r,
                ok = t.ok!!,
                chk = t.raw.chk,
                dd = cv[i].dd,
                cum = cv[i].cum,
                peak = cv[i].peak,
                mistakes = listOfNotNull(t.raw.mistake?.takeIf { it.isNotEmpty() }),
                violations = t.raw.violations,
            )
        }
        return Stats.kpis(rows, from, to, p) to rows
    }
}
