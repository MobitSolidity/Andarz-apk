package ir.andarz.app.ui.analysis

import ir.andarz.data.EngineMapping
import ir.andarz.data.db.TradeEntity
import ir.andarz.engine.Advisor
import ir.andarz.engine.ChangeKind
import ir.andarz.engine.ClosedRow
import ir.andarz.engine.Fmt
import ir.andarz.engine.GateReason
import ir.andarz.engine.Governor
import ir.andarz.engine.Jalali
import ir.andarz.engine.Kpis
import ir.andarz.engine.Params
import ir.andarz.engine.Plan
import ir.andarz.engine.Planner
import ir.andarz.engine.Side
import ir.andarz.engine.Trades
import ir.andarz.engine.XlMath
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.round

// ==================================================================== calculator (5.7)

/** Raw text of the calculator's fields; nothing here is saved (calc.demoNote). */
data class CalcDraft(
    val side: Side = Side.LONG,
    val equity: String = "",
    val entry: String = "",
    val sl: String = "",
    val tp: String = "",
    val lev: String = "5",
    val riskPct: String = "",
    val feePct: String = "",
) : java.io.Serializable

data class CalcResult(
    val equity: Double?,
    val entry: Double?,
    val sl: Double?,
    val tp: Double?,
    val lev: Double?,
    val riskPct: Double,
    val plan: Plan?,
    /** Gate reasons with a full checklist: the calculator judges the numbers, not the ticks. */
    val reasons: List<GateReason>,
    val advisor: Advisor?,
    val feePct: Double?,
    val rrAfterFees: Double?,
    val entryEqSl: Boolean,
    val slWrong: Boolean,
    val tpWrong: Boolean,
    /** Margin share at the advisor's recommended leverage. */
    val recommendedMarginPct: Double? = null,
) {
    val valid: Boolean get() = plan != null
    val allowed: Boolean get() = valid && reasons.isEmpty()
}

/** The calculator's numbers, all from the engine (trap 16). */
object CalcLogic {
    fun of(d: CalcDraft, p: Params, riskCap: Double): CalcResult {
        val equity = Fmt.normalizeInput(d.equity)?.takeIf { it > 0 }
        val entry = Fmt.normalizeInput(d.entry)?.takeIf { it > 0 }
        val sl = Fmt.normalizeInput(d.sl)?.takeIf { it > 0 }
        val tp = Fmt.normalizeInput(d.tp)?.takeIf { it > 0 }
        val lev = Fmt.normalizeInput(d.lev)?.takeIf { it > 0 }
        val risk = (Fmt.normalizeInput(d.riskPct)?.takeIf { it > 0 }?.div(100)) ?: riskCap
        val fee = Fmt.normalizeInput(d.feePct)?.takeIf { it >= 0 }?.div(100)
        val entryEqSl = entry != null && sl != null && entry == sl
        val slWrong = entry != null && sl != null && !entryEqSl && (if (d.side == Side.LONG) sl >= entry else sl <= entry)
        val tpWrong = entry != null && tp != null && (if (d.side == Side.LONG) tp <= entry else tp >= entry)
        if (equity == null || entry == null || sl == null || tp == null || lev == null || entryEqSl) {
            return CalcResult(equity, entry, sl, tp, lev, risk, null, emptyList(), null, fee, null, entryEqSl, slWrong, tpWrong)
        }
        val plan = Planner.plan(d.side, equity, entry, sl, tp, lev, p, risk)
        val chk = p.chkN.toInt()
        val reasons = Planner.gate(d.side, entry, sl, tp, lev, chk, plan, p)
        val adv = runCatching { Planner.leverageAdvisor(d.side, equity, entry, sl, tp, chk, p, risk) }.getOrNull()
        val rrAfter = if (fee != null && plan.notionalMax != null) Planner.rrAfterFees(plan.notionalMax!!, entry, sl, tp, fee) else null
        val recMargin = adv?.recommended?.let { Planner.plan(d.side, equity, entry, sl, tp, it.toDouble(), p, risk).marginPct }
        return CalcResult(equity, entry, sl, tp, lev, risk, plan, reasons, adv, fee, rrAfter, false, slWrong, tpWrong, recMargin)
    }

    /** Text form of a number for prefilling a field (Latin digits, no grouping: the field accepts both). */
    fun field(x: Double): String = Fmt.number(x, 8, fa = false, group = false, trim = true)
}

// ==================================================================== periods (5.8)

enum class Period { WEEK, MONTH, LAST30, ALL, CUSTOM }

object Periods {
    /** `[from, to]` of a period, inclusive, by exit date in the display calendar (D-31). */
    fun range(period: Period, today: LocalDate, jalali: Boolean, custom: Pair<LocalDate, LocalDate>? = null): Pair<LocalDate, LocalDate> = when (period) {
        Period.WEEK -> Jalali.weekStart(today) to today
        Period.MONTH -> (if (jalali) Jalali.monthStart(today) else today.withDayOfMonth(1)) to today
        Period.LAST30 -> today.minusDays(29) to today
        Period.ALL -> LocalDate.of(1970, 1, 1) to LocalDate.of(9999, 12, 31)
        Period.CUSTOM -> custom?.let { (a, b) -> if (a <= b) a to b else b to a } ?: (today to today)
    }

    /** The previous display-calendar month: `[start, end]`. */
    fun previousMonth(today: LocalDate, jalali: Boolean): Pair<LocalDate, LocalDate> {
        val thisStart = if (jalali) Jalali.monthStart(today) else today.withDayOfMonth(1)
        val prevEnd = thisStart.minusDays(1)
        val prevStart = if (jalali) Jalali.monthStart(prevEnd) else prevEnd.withDayOfMonth(1)
        return prevStart to prevEnd
    }
}

// ==================================================================== insights (5.8)

/** One bar or point of a chart: a label key or literal plus a value. */
data class Bar(val label: String, val value: Double, val count: Int = 0)

data class SetupRow(val setupId: String?, val n: Int, val winRate: Double?, val expectancy: Double?, val profitFactor: Double?)

data class Insights(
    val kpis: Kpis,
    /** Cumulative P&L-only equity of the period's trades, in exit order (start = equity before the first). */
    val equity: List<Double>,
    /** Drawdown from the all-time peak at each of the period's exits (0..1). */
    val drawdown: List<Double>,
    val rHistogram: List<Bar>,
    /** Compliance share per week start (Saturday). */
    val weeklyCompliance: List<Bar>,
    /** Mean R per 4-hour entry bucket: labels "0", "4", … "20" (the starting hour). */
    val byHour: List<Bar>,
    val bySymbol: List<Bar>,
    val bySetup: List<Bar>,
    val mistakes: List<Bar>,
    val violations: List<Bar>,
    val setups: List<SetupRow>,
    val topMistake: Pair<String, Int>?,
) {
    val empty: Boolean get() = kpis.n == 0
}

object InsightsLogic {
    /** The R histogram bins of 5.8, as label codes rendered by the screen. */
    val R_BINS = listOf("<-2", "[-2,-1)", "[-1,0)", "[0,1)", "[1,2)", "[2,3)", ">=3")

    fun bin(r: Double): Int = when {
        r < -2 -> 0
        r < -1 -> 1
        r < 0 -> 2
        r < 1 -> 3
        r < 2 -> 4
        r < 3 -> 5
        else -> 6
    }

    fun of(trades: List<TradeEntity>, from: LocalDate, to: LocalDate, p: Params, zone: ZoneId): Insights {
        val raw = trades.mapNotNull(EngineMapping::toRaw)
        val (kpis, allRows) = Trades.kpis(raw, from, to, p, zone)
        val rows = allRows.filter { !it.exitDate.isBefore(from) && !it.exitDate.isAfter(to) }
        val byId = trades.associateBy { it.id }
        val ent = rows.mapNotNull { byId[it.id]?.let { t -> it to t } }

        val firstIdx = allRows.indexOfFirst { it.id == rows.firstOrNull()?.id }
        val before = if (firstIdx > 0) allRows[firstIdx - 1].cum else p.startEquity
        val equity = if (rows.isEmpty()) emptyList() else listOf(before) + rows.map { it.cum }
        val dd = rows.map { it.dd }

        val hist = IntArray(R_BINS.size)
        rows.mapNotNull { it.r }.forEach { hist[bin(it)]++ }

        val weekly = rows.groupBy { Jalali.weekStart(it.exitDate) }.toSortedMap()
            .map { (w, list) -> Bar(w.toString(), list.count { it.ok }.toDouble() / list.size, list.size) }

        fun meanR(groups: Map<String, List<ClosedRow>>) = groups.mapNotNull { (k, list) ->
            val rs = list.mapNotNull { it.r }
            if (rs.isEmpty()) null else Bar(k, rs.sum() / rs.size, rs.size)
        }

        val byHour = meanR(ent.groupBy({ (_, t) -> (((t.entryTime ?: 0L).let { Instant.ofEpochMilli(it).atZone(zone).hour } / 4) * 4).toString() }, { it.first }))
            .sortedBy { it.label.toInt() }
        val bySymbol = meanR(ent.groupBy({ it.second.symbol }, { it.first })).sortedByDescending { it.count }
        val bySetup = meanR(ent.groupBy({ it.second.setupId ?: "" }, { it.first })).sortedByDescending { it.count }

        val mistakes = kpis.mistakeTally.filterKeys { it != "M00" }.map { (k, v) -> Bar(k, v.toDouble(), v) }.sortedByDescending { it.value }
        val violations = kpis.violationTally.map { (k, v) -> Bar(k, v.toDouble(), v) }.sortedBy { it.label }

        val setups = ent.groupBy { it.second.setupId }.map { (sid, list) ->
            val rs = list.map { it.first }
            val wsum = rs.filter { it.net > 0 }.sumOf { it.net }
            val lsum = rs.filter { it.net < 0 }.sumOf { it.net }
            SetupRow(
                sid, rs.size,
                rs.count { it.net > 0 }.toDouble() / rs.size,
                rs.mapNotNull { it.r }.sum() / rs.size,
                if (lsum != 0.0) wsum / abs(lsum) else null,
            )
        }.sortedByDescending { it.n }

        val top = mistakes.firstOrNull()?.let { it.label to it.count }
        return Insights(kpis, equity, dd, R_BINS.mapIndexed { i, l -> Bar(l, hist[i].toDouble(), hist[i]) }, weekly, byHour, bySymbol, bySetup, mistakes, violations, setups, top)
    }
}

// ==================================================================== rules (5.10)

/** One parameter definition from `params[]` of the content JSON. */
data class ParamDef(
    val key: String,
    val label: String,
    val note: String,
    val kind: String,
    val default: Double,
    val min: Double,
    val max: Double,
    val step: Double,
    val group: String,
    val article: String,
    val locked: Boolean,
) {
    /** Percent parameters are typed as percent numbers (1 for 1%). */
    val inputScale: Double get() = if (kind == "pct") 100.0 else 1.0
}

enum class ParamInvalid { NUMBER, BELOW_MIN, ABOVE_MAX, RISK_ORDER, LOCKED }

data class ParamCheck(val value: Double?, val invalid: ParamInvalid?, val kind: ChangeKind)

object RulesLogic {
    /** Validation of one edit (5.10): bounds, `Risk_Max ≥ Risk_Pct`, the live change kind. */
    fun check(def: ParamDef, text: String, p: Params): ParamCheck {
        if (def.locked || def.key == "Chk_N" || def.key == "Start_Equity") return ParamCheck(null, ParamInvalid.LOCKED, ChangeKind.NONE)
        val typed = Fmt.normalizeInput(text) ?: return ParamCheck(null, ParamInvalid.NUMBER, ChangeKind.NONE)
        // Round away float noise of the percent scaling (0.7 / 100 = 0.006999…).
        val v = XlMath.round(typed / def.inputScale, 10)
        val kind = Governor.classifyChange(def.key, p[def.key], v)
        val invalid = when {
            v < def.min - 1e-12 -> ParamInvalid.BELOW_MIN
            v > def.max + 1e-12 -> ParamInvalid.ABOVE_MAX
            p.with(def.key, v).let { it.riskMax < it.riskPct } -> ParamInvalid.RISK_ORDER
            else -> null
        }
        return ParamCheck(v, invalid, kind)
    }

    /** Changes of a reset to defaults: every non-locked parameter whose value differs (5.10). */
    fun resetChanges(defs: List<ParamDef>, p: Params): List<Pair<ParamDef, Double>> =
        defs.filter { !it.locked && it.key != "Start_Equity" && it.key != "Chk_N" && p[it.key] != it.default }.map { it to it.default }

    /**
     * Reset order: tightening and neutral changes first, so `Risk_Max ≥ Risk_Pct` holds after each single
     * step when both move; loosening last (it is delayed anyway).
     */
    fun resetOrder(changes: List<Pair<ParamDef, Double>>, p: Params): List<Pair<ParamDef, Double>> =
        changes.sortedBy { (d, v) ->
            when (Governor.classifyChange(d.key, p[d.key], v)) { ChangeKind.TIGHTEN -> 0; ChangeKind.NEUTRAL -> 1; else -> 2 }
        }

    /** The field text for the current value (percent parameters as percent numbers). */
    fun fieldText(def: ParamDef, v: Double): String = Fmt.number(XlMath.round(v * def.inputScale, 6), 6, fa = false, group = false, trim = true)
}

// ==================================================================== watch-list (5.11, D-22)

data class Liquidity(val volumeOk: Boolean?, val relOk: Boolean?) {
    val pass: Boolean? get() = if (volumeOk == null && relOk == null) null else (volumeOk != false && relOk != false)
}

object WatchLogic {
    const val RECHECK_DAYS = 30L

    fun liquidity(volume: Double?, rel: Double?, volMin: Double, relLo: Double, relHi: Double) =
        Liquidity(volume?.let { it >= volMin }, rel?.let { it in relLo..relHi })

    fun needsRecheck(checkedAt: Long?, now: Instant): Boolean =
        checkedAt == null || now.toEpochMilli() - checkedAt >= RECHECK_DAYS * 86_400_000L
}

// ==================================================================== reviews (5.9)

data class ReviewDue(val daily: Boolean, val weekly: LocalDate?, val monthly: LocalDate?)

object ReviewLogic {
    /**
     * Due items of the Reviews hub. Daily: today has no DAILY review. Weekly: the just-ended week (or the
     * current one from Friday on) has no WEEKLY review. Monthly: the previous month has no MONTHLY review.
     */
    fun due(today: LocalDate, jalali: Boolean, reviews: List<Pair<String, LocalDate>>): ReviewDue {
        fun has(type: String, d: LocalDate) = reviews.any { it.first == type && it.second == d }
        val ws = Jalali.weekStart(today)
        val week = if (Jalali.weekdayIndexSat0(today) == 6) ws else ws.minusDays(7)
        val prevMonth = Periods.previousMonth(today, jalali).first
        return ReviewDue(
            daily = !has("DAILY", today),
            weekly = week.takeIf { !has("WEEKLY", it) },
            monthly = prevMonth.takeIf { !has("MONTHLY", it) },
        )
    }

    /** Weeks the user may review: the current one and the last eight, unreviewed first-in-time last. */
    fun weekChoices(today: LocalDate, reviewed: Set<LocalDate>): List<LocalDate> {
        val ws = Jalali.weekStart(today)
        return (0..8).map { ws.minusDays(7L * it) }.filter { it !in reviewed }
    }

    /** Rounded to whole cents, the amount a monthly review offers to record as a withdrawal. */
    fun withdrawAmount(k: Kpis): Double = round(k.withdrawSuggest * 100) / 100
}
