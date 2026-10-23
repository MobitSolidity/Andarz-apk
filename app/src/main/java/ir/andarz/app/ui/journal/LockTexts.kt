package ir.andarz.app.ui.journal

import ir.andarz.app.ui.Ui
import ir.andarz.engine.Lock
import ir.andarz.engine.LockCode
import ir.andarz.engine.LockNeed
import ir.andarz.engine.Params
import ir.andarz.engine.Reduction
import java.time.Duration
import java.time.Instant

/** Lock, need and reduction wording with their `uiArgs` filled from live parameters (5.2). */
object LockTexts {
    fun title(u: Ui, l: Lock) = u.t("lock.${l.code.name}.title")

    fun body(u: Ui, l: Lock, p: Params, tradesToday: Int): String {
        val int = { x: Double -> u.int(x.toInt()) }
        return when (l.code) {
            LockCode.VIOLATION -> u.t("lock.VIOLATION.body", "hours" to int(p.violationPauseH))
            LockCode.DRAWDOWN -> u.t(
                "lock.DRAWDOWN.body", "cap" to u.pct(p.ddCap, 0), "days" to int(p.ddLockDays),
                "risk" to u.pct(p.recoveryRisk, 2), "n" to int(p.recoveryTrades),
            )
            LockCode.WEEKLY_LOSS -> u.t("lock.WEEKLY_LOSS.body", "cap" to u.pct(p.weeklyLossCap, 0))
            LockCode.DAILY_LOSS -> u.t("lock.DAILY_LOSS.body", "cap" to u.pct(p.dailyLossCap, 0))
            LockCode.DAY_OVER -> u.t("lock.DAY_OVER.body", "n" to int(p.consecStops))
            LockCode.TRADE_CAP -> u.t("lock.TRADE_CAP.body", "n" to u.int(tradesToday), "cap" to int(p.maxTradesDay))
            LockCode.COOLDOWN -> u.t("lock.COOLDOWN.body", "minutes" to int(p.cooldownMin))
        }
    }

    /** Countdown line; re-rendered every second by the caller (5.2). */
    fun until(u: Ui, l: Lock, now: Instant): String? {
        val until = l.until ?: return null
        if (!now.isBefore(until)) return if (l.needs.isNotEmpty()) u.t("lock.untilDone") else null
        val left = Duration.between(now, until)
        if (l.code == LockCode.COOLDOWN) return u.t("lock.COOLDOWN.left", "m" to u.int(((left.seconds + 59) / 60).toInt()))
        return u.t("lock.until", "until" to u.dateTime(until))
    }

    fun needText(u: Ui, n: LockNeed, p: Params) = when (n) {
        LockNeed.REPORT -> u.t("need.REPORT", "min" to u.int(p.reportMinChars.toInt()))
        else -> u.t("need.${n.name}")
    }

    fun needAction(u: Ui, n: LockNeed) = when (n) {
        LockNeed.REPORT -> u.t("need.action.report")
        LockNeed.REVIEW, LockNeed.WEEKLY_REVIEW -> u.t("need.action.review")
        LockNeed.CLOSE_POSITION -> u.t("need.action.close")
    }

    fun reduction(u: Ui, r: Reduction) = u.t(
        "reduction.${r.code.name}", "risk" to u.pct(r.riskPct, 2), "n" to u.int(r.remaining),
    )

}
