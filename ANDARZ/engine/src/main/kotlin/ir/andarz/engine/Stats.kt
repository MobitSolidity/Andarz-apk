package ir.andarz.engine

import java.time.LocalDate
import kotlin.math.abs

/** P&L curve and KPIs (3.8). Port of `curve`, `kpis` and `tally` of the reference. */
object Stats {

    /** P&L-only curve in exit order. Deposits and withdrawals never enter. */
    fun curve(nets: List<Double>, start: Double): List<CurvePoint> {
        val out = ArrayList<CurvePoint>(nets.size)
        var peak = start
        var run = 0.0
        for (n in nets) {
            run += n
            val cum = start + run
            peak = maxOf(peak, cum)
            out += CurvePoint(cum, peak, if (peak != 0.0) (peak - cum) / peak else 0.0)
        }
        return out
    }

    /** KPIs for the rows whose exit date is inside `[from, to]` (inclusive). */
    fun kpis(closed: List<ClosedRow>, from: LocalDate, to: LocalDate, p: Params): Kpis {
        val rows = closed.filter { !it.exitDate.isBefore(from) && !it.exitDate.isAfter(to) }
        val n = rows.size
        val rs = rows.mapNotNull { it.r }
        val wins = rs.filter { it > 0 }
        val loss = rs.filter { it < 0 }
        val wsum = pySum(rows.filter { it.net > 0 }.map { it.net })
        val lsum = pySum(rows.filter { it.net < 0 }.map { it.net })

        val compliance = if (n > 0) rows.count { it.ok }.toDouble() / n else null
        val expectancy = if (n > 0) pySum(rs) / n else null
        val avgWinR = if (wins.isNotEmpty()) pySum(wins) / wins.size else null
        val avgLossR = if (loss.isNotEmpty()) pySum(loss) / loss.size else null
        val realRR = if (avgWinR != null && avgLossR != null) avgWinR / abs(avgLossR) else null
        val winRate = if (n > 0) rows.count { it.net > 0 }.toDouble() / n else null
        val profitFactor = if (n > 0 && lsum != 0.0) wsum / abs(lsum) else null
        val maxDD = if (n > 0) rows.maxOf { it.dd } else null
        val avgChk = if (n > 0) rows.sumOf { it.chk }.toDouble() / n else null
        val net = pySum(rows.map { it.net })
        val sumR = pySum(rs)

        val st = LinkedHashMap<String, KpiStatus>()
        st["n"] = if (n >= p.minTrades) KpiStatus.ENOUGH_DATA else KpiStatus.LOW_DATA
        compliance?.let { st["compliance"] = if (it >= p.complianceTarget) KpiStatus.ON_TARGET else KpiStatus.BELOW_TARGET }
        expectancy?.let { st["expectancy"] = if (it > 0) KpiStatus.POSITIVE else KpiStatus.NON_POSITIVE }
        realRR?.let { st["realRR"] = if (it >= p.rrMin) KpiStatus.ON_TARGET else KpiStatus.BELOW_TARGET }
        profitFactor?.let { st["profitFactor"] = if (it >= p.pfMin) KpiStatus.ON_TARGET else KpiStatus.BELOW_TARGET }
        maxDD?.let { st["maxDD"] = if (it < p.ddCap) KpiStatus.IN_RANGE else KpiStatus.OVER_CAP }
        avgChk?.let { st["avgChk"] = if (it >= p.chkN) KpiStatus.COMPLETE else KpiStatus.INCOMPLETE }

        return Kpis(
            n = n, compliance = compliance, expectancy = expectancy, avgWinR = avgWinR, avgLossR = avgLossR,
            realRR = realRR, winRate = winRate, profitFactor = profitFactor, maxDD = maxDD, avgChk = avgChk,
            net = net, sumR = sumR, withdrawSuggest = maxOf(0.0, net) * p.withdrawPct, status = st,
            mistakeTally = tally(rows) { it.mistakes }, violationTally = tally(rows) { it.violations },
        )
    }

    /** Count of each id over the rows, in first-seen order. */
    fun tally(rows: List<ClosedRow>, key: (ClosedRow) -> List<String>): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        for (r in rows) for (v in key(r)) out[v] = (out[v] ?: 0) + 1
        return out
    }

    /** Break-even win rate for a reward-to-risk of [r]: `1 / (1 + R)`. */
    fun breakEvenWinRate(r: Double): Double? = if (1 + r != 0.0) 1 / (1 + r) else null

    /** Left-to-right sum, the order Python's `sum()` uses, so results are bit-identical to the reference. */
    internal fun pySum(xs: List<Double>): Double {
        var s = 0.0
        for (x in xs) s += x
        return s
    }
}

/** The six limits of the Today screen (3.8). Port of `dashboard`. */
object Limits {
    fun dashboard(
        tradesToday: Int,
        dayNet: Double,
        weekNet: Double,
        equityNow: Double,
        equitySod: Double,
        equitySow: Double,
        ddNow: Double,
        openCount: Int,
        openRiskUsd: Double,
        p: Params,
    ): Dashboard {
        val dp = if (equitySod != 0.0) dayNet / equitySod else 0.0
        val wp = if (equitySow != 0.0) weekNet / equitySow else 0.0
        val orisk = if (equityNow != 0.0) openRiskUsd / equityNow else 0.0
        val status = DashboardStatus(
            trades = if (tradesToday >= p.maxTradesDay) DashStatus.TRADES_CAP else DashStatus.OK,
            daily = if (XlMath.round(-dp, 6) >= p.dailyLossCap) DashStatus.DAILY_RED else DashStatus.OK,
            weekly = if (XlMath.round(-wp, 6) >= p.weeklyLossCap) DashStatus.WEEKLY_RED else DashStatus.OK,
            drawdown = if (XlMath.round(ddNow, 6) >= p.ddCap) DashStatus.DD_RED else DashStatus.OK,
            positions = when {
                openCount > p.maxPositions -> DashStatus.POSITIONS_OVER
                openCount.toDouble() == p.maxPositions -> DashStatus.POSITIONS_FULL
                else -> DashStatus.OK
            },
            openRisk = if (orisk + p.riskPct > p.openRiskMax + 0.000001) DashStatus.RISK_FULL else DashStatus.OK,
        )
        return Dashboard(tradesToday, dp, wp, ddNow, openCount, orisk, status)
    }
}
