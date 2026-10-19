package ir.andarz.app.platform

import ir.andarz.data.db.ReviewEntity
import ir.andarz.data.db.TradeEntity
import ir.andarz.data.settings.CalendarMode
import ir.andarz.data.settings.Settings
import ir.andarz.engine.Jalali
import ir.andarz.engine.LockCode
import ir.andarz.engine.Management
import ir.andarz.engine.Side
import ir.andarz.engine.Snapshot
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Kinds of discipline reminders (7); `toggle` names the settings switch that gates each. */
enum class ReminderKind(val channel: String) {
    COOLDOWN_END("discipline"), LOCK_END("discipline"), TIME_STOP("discipline"), OPEN("discipline"), REPORT_DUE("discipline"),
    DAILY_CLOSE("reviews"), WEEKLY("reviews"), MONTHLY("reviews"), BACKUP("system"), SECURITY("system"), ADVICE("system"),
}

/**
 * One desired reminder: the unique work name of 7, the instant (already moved out of quiet hours), the
 * route it opens, and the ids its text needs (a trade id or a lock code; wording is resolved at fire time).
 */
data class Reminder(val name: String, val kind: ReminderKind, val at: Instant, val route: String, val ref: String? = null)

/**
 * The desired set of 7, derived from state. Pure: the reconciler diffs it against WorkManager, and the
 * worker calls [stillDue] at fire time so a stale reminder is suppressed.
 */
object ReminderPlanner {
    private val OPEN_EVERY: Duration = Duration.ofHours(4)
    private val REPORT_EVERY: Duration = Duration.ofHours(6)
    private const val DAY_START = 8
    private const val DAY_END = 23

    fun plan(snap: Snapshot, trades: List<TradeEntity>, reviews: List<ReviewEntity>, s: Settings, now: Instant): List<Reminder> {
        val z = s.zone
        val out = ArrayList<Reminder>()
        val p = snap.params
        val locks = snap.governor.locks
        val today = now.atZone(z).toLocalDate()

        if (s.notifCooldown) {
            locks.firstOrNull { it.code == LockCode.COOLDOWN }?.until?.let { out += Reminder("cooldown-end", ReminderKind.COOLDOWN_END, it, ROUTE_TODAY) }
            for (l in locks) {
                if (l.code == LockCode.COOLDOWN || l.until == null || l.needs.isNotEmpty()) continue
                out += Reminder("lock-end-${l.code.name}-${l.until!!.toEpochMilli()}", ReminderKind.LOCK_END, l.until!!, ROUTE_TODAY, l.code.name)
            }
        }
        val open = trades.filter { it.status == "OPEN" && it.entryTime != null }
        if (s.notifTimeStop) for (t in open) {
            val ts = Management.timeStop(Side.valueOf(t.side), t.entry ?: t.plannedEntry, t.slCurrent, Instant.ofEpochMilli(t.entryTime!!), t.timeframe, t.legs.isNotEmpty(), now, p)
            // Scheduled for the due instant; once due it has fired (or would fire now) and is not repeated.
            val protects = t.slCurrent?.let { sl -> if (t.side == "LONG") sl >= (t.entry ?: t.plannedEntry) else sl <= (t.entry ?: t.plannedEntry) } == true
            if (ts.dueAt > now && t.legs.isEmpty() && !protects) out += Reminder("time-stop-${t.id}", ReminderKind.TIME_STOP, ts.dueAt, "trade/${t.id}", t.id)
        }
        if (s.notifOpen) for (t in open.filter { it.account == "REAL" }) {
            out += Reminder("open-reminder-${t.id}", ReminderKind.OPEN, nextOpenSlot(Instant.ofEpochMilli(t.entryTime!!), now, z), "trade/${t.id}", t.id)
        }
        // report reminders are part of the lock and have no own switch: a VIOLATION lock is the strongest rule
        for (t in trades.filter { it.status == "CLOSED" && it.violations.isNotEmpty() && (it.violationReport?.trim()?.length ?: 0) < p.reportMinChars }) {
            val from = Instant.ofEpochMilli(t.exitTime ?: t.updatedAt)
            out += Reminder("report-due-${t.id}", ReminderKind.REPORT_DUE, nextEvery(from, REPORT_EVERY, now), "trade/${t.id}", t.id)
        }
        if (s.notifReviews) {
            val todayTrades = trades.any { t -> t.entryTime?.let { Instant.ofEpochMilli(it).atZone(z).toLocalDate() == today } == true }
            val dailyDone = reviews.any { it.type == "DAILY" && it.periodStart == today.toString() }
            if ((todayTrades && !dailyDone) || open.isNotEmpty()) out += Reminder("daily-close", ReminderKind.DAILY_CLOSE, nextAt(now, z, LocalTime.of(21, 0)), ROUTE_REVIEWS)
            val ws = Jalali.weekStart(today)
            if (reviews.none { it.type == "WEEKLY" && it.periodStart == ws.toString() }) {
                val fri = ws.plusDays(6).atTime(18, 0).atZone(z).toInstant()
                out += Reminder("weekly-review", ReminderKind.WEEKLY, if (fri > now) fri else now.plusSeconds(60), ROUTE_REVIEWS)
            }
            val jalali = s.calendar == CalendarMode.JALALI
            val monthStart = if (jalali) Jalali.monthStart(today) else today.withDayOfMonth(1)
            val prevStart = if (jalali) Jalali.monthStart(monthStart.minusDays(1)) else monthStart.minusMonths(1)
            val at = monthStart.atTime(10, 0).atZone(z).toInstant()
            if (reviews.none { it.type == "MONTHLY" && it.periodStart == prevStart.toString() }) {
                out += Reminder("monthly-review", ReminderKind.MONTHLY, if (at > now) at else now.plusSeconds(60), ROUTE_REVIEWS)
            }
        }
        if (s.notifBackup) {
            val base = if (s.lastBackupAt > 0) Instant.ofEpochMilli(s.lastBackupAt) else now
            out += Reminder("backup-reminder", ReminderKind.BACKUP, at9(base.plus(Duration.ofDays(s.backupRemindDays.toLong())), z, now), ROUTE_BACKUP)
        }
        if (s.notifReviews) {
            val base = if (s.lastSecurityReviewAt > 0) Instant.ofEpochMilli(s.lastSecurityReviewAt) else now
            out += Reminder("security-review", ReminderKind.SECURITY, at9(base.plus(Duration.ofDays(30)), z, now), ROUTE_SECURITY)
        }
        if (s.adviceEnabled && s.notifAdvice) out += Reminder("advice-daily", ReminderKind.ADVICE, nextAt(now, z, LocalTime.of(9, 0)), ROUTE_TODAY)

        return out.map { it.copy(at = outOfQuiet(it.at, z, s.quietStart, s.quietEnd)) }
    }

    /**
     * The fire-time re-check of 7: [desiredJustBefore] is the plan computed from the current state as of one
     * second before the scheduled instant. A reminder whose condition no longer holds is not in it.
     */
    fun stillDue(name: String, desiredJustBefore: List<Reminder>): Boolean = desiredJustBefore.any { it.name == name }

    /** The next instant at [time] local, strictly after [now]. */
    fun nextAt(now: Instant, z: ZoneId, time: LocalTime): Instant {
        val today = now.atZone(z).toLocalDate().atTime(time).atZone(z).toInstant()
        return if (today > now) today else now.atZone(z).toLocalDate().plusDays(1).atTime(time).atZone(z).toInstant()
    }

    private fun at9(t: Instant, z: ZoneId, now: Instant): Instant {
        val d = t.atZone(z).toLocalDate().atTime(9, 0).atZone(z).toInstant()
        return if (d > now) d else now.plusSeconds(60)
    }

    private fun nextEvery(from: Instant, every: Duration, now: Instant): Instant {
        if (from > now) return from
        val n = Duration.between(from, now).toMillis() / every.toMillis() + 1
        return from.plusMillis(n * every.toMillis())
    }

    /** Every 4 h from entry, inside 08:00-23:00 local; outside, the next 08:00 (7). */
    fun nextOpenSlot(entry: Instant, now: Instant, z: ZoneId): Instant {
        val t = nextEvery(entry.plus(OPEN_EVERY), OPEN_EVERY, now)
        val zt = t.atZone(z)
        return when {
            zt.hour < DAY_START -> zt.toLocalDate().atTime(DAY_START, 0).atZone(z).toInstant()
            zt.hour >= DAY_END -> zt.toLocalDate().plusDays(1).atTime(DAY_START, 0).atZone(z).toInstant()
            else -> t
        }
    }

    /** Quiet hours (D-34): an instant inside `[start, end)` minutes-of-day moves to the window's end. */
    fun outOfQuiet(t: Instant, z: ZoneId, start: Int, end: Int): Instant {
        if (start == end) return t
        val zt: ZonedDateTime = t.atZone(z)
        val m = zt.hour * 60 + zt.minute
        val inside = if (start < end) m in start until end else m >= start || m < end
        if (!inside) return t
        val endDay: LocalDate = if (start < end || m < end) zt.toLocalDate() else zt.toLocalDate().plusDays(1)
        return endDay.atTime(end / 60, end % 60).atZone(z).toInstant()
    }

    const val ROUTE_TODAY = "today"
    const val ROUTE_REVIEWS = "reviews"
    const val ROUTE_BACKUP = "settings"
    const val ROUTE_SECURITY = "security"
}
