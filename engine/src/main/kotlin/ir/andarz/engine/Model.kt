package ir.andarz.engine

import java.time.Instant
import java.time.LocalDate

enum class Side { LONG, SHORT }

enum class Account { REAL, DEMO }

/** Gate reasons in their fixed order (3.4). */
enum class GateReason { DIRECTION, CHECKLIST, LEVERAGE, RR, MARGIN, LIQUIDATION }

enum class SizeVerdict { YES, GRADE_A_ONLY, OVER_CAP, RED_LINE }

enum class TradeResult { WIN, LOSS, EVEN }

/**
 * Planning numbers of one trade (3.3). Every field is nullable because the reference never divides by
 * zero: entry = SL gives `rr`, `notionalMax`, `margin`, `marginPct` and `liqX` = null (3.1 Guards).
 */
data class Plan(
    val sld: Double?,
    val rr: Double?,
    val riskUsd: Double,
    val notionalMax: Double?,
    val margin: Double?,
    val marginPct: Double?,
    val liq: Double?,
    val liqDist: Double?,
    val liqX: Double?,
)

/** One exit leg: a price and the fraction of the original position it closed (3.6). */
data class Leg(val price: Double, val fraction: Double)

/** Actual risk and, when legs exist, the result (3.5, 3.6). */
data class Fill(
    val rrisk: Double,
    val rriskp: Double?,
    val gross: Double?,
    val net: Double?,
    val r: Double?,
    val result: TradeResult?,
    val equityAfter: Double?,
)

/** Leverage advisor output (3.7). */
data class Advisor(
    val lMinReal: Double,
    val lMaxLiqReal: Double,
    val feasibleMin: Int?,
    val feasibleMax: Int?,
    val recommended: Int?,
)

/** One point of the P&L curve (3.8). */
data class CurvePoint(val cum: Double, val peak: Double, val dd: Double)

/** Input row of the KPI computation (3.8), one per closed REAL trade in exit order. */
data class ClosedRow(
    val id: String,
    val exitDate: LocalDate,
    val net: Double,
    val r: Double?,
    val ok: Boolean,
    val chk: Int,
    val dd: Double,
    val cum: Double = 0.0,
    val peak: Double = 0.0,
    val mistakes: List<String> = emptyList(),
    val violations: List<String> = emptyList(),
)

enum class KpiStatus {
    ENOUGH_DATA, LOW_DATA, ON_TARGET, BELOW_TARGET, POSITIVE, NON_POSITIVE, IN_RANGE, OVER_CAP, COMPLETE, INCOMPLETE,
}

/** The KPIs of 3.8. Statuses are omitted (absent from the map) for null KPIs. */
data class Kpis(
    val n: Int,
    val compliance: Double?,
    val expectancy: Double?,
    val avgWinR: Double?,
    val avgLossR: Double?,
    val realRR: Double?,
    val winRate: Double?,
    val profitFactor: Double?,
    val maxDD: Double?,
    val avgChk: Double?,
    val net: Double,
    val sumR: Double,
    val withdrawSuggest: Double,
    val status: Map<String, KpiStatus>,
    val mistakeTally: Map<String, Int> = emptyMap(),
    val violationTally: Map<String, Int> = emptyMap(),
)

/** Dashboard status codes per gauge (3.8). */
enum class DashStatus { OK, TRADES_CAP, DAILY_RED, WEEKLY_RED, DD_RED, POSITIONS_FULL, POSITIONS_OVER, RISK_FULL }

data class DashboardStatus(
    val trades: DashStatus,
    val daily: DashStatus,
    val weekly: DashStatus,
    val drawdown: DashStatus,
    val positions: DashStatus,
    val openRisk: DashStatus,
) {
    val allOk: Boolean
        get() = listOf(trades, daily, weekly, drawdown, positions, openRisk).all { it == DashStatus.OK }
}

/** The six limits of the Today screen plus the overall verdict (3.8). */
data class Dashboard(
    val tradesToday: Int,
    val dailyPct: Double,
    val weeklyPct: Double,
    val drawdown: Double,
    val openPositions: Int,
    val openRiskPct: Double,
    val status: DashboardStatus,
) {
    val allowed: Boolean get() = status.allOk
}

/** Ledger entry types (3.8). WITHDRAWAL amounts are stored positive and subtract. */
enum class LedgerType { BASELINE, DEPOSIT, WITHDRAWAL, ADJUSTMENT }

data class LedgerEntry(val time: Instant, val type: LedgerType, val amount: Double)
