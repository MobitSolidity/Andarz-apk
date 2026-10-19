package ir.andarz.market

import android.content.Context
import ir.andarz.market.alarm.AlarmRinger
import ir.andarz.market.alarm.ClockApp
import ir.andarz.market.alarm.ClockRing
import ir.andarz.market.alerts.AlertDispatcher
import ir.andarz.market.calendar.AlarmScheduler
import ir.andarz.market.calendar.CalendarData
import ir.andarz.market.calendar.CalendarLoader
import ir.andarz.market.calendar.EconEvent
import ir.andarz.market.calendar.ReleaseTimes
import ir.andarz.market.calendar.ReminderScheduler
import ir.andarz.market.calendar.Reminders
import ir.andarz.market.model.AlertSettings
import ir.andarz.market.model.CoinRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Price-alert settings, per-coin rules, the test buttons and the permission rows of the Alerts section
 * (K4 sketch `MarketModule.alerts`). Alerts are information only (K6.5): nothing here can reach the journal.
 */
class AlertsController internal constructor(
    private val context: Context,
    private val prefs: MarketPrefs,
    private val dispatcher: AlertDispatcher,
) {
    val settings: Flow<AlertSettings> get() = prefs.alertSettings
    val rules: Flow<Map<String, CoinRule>> get() = prefs.rules

    suspend fun update(change: (AlertSettings) -> AlertSettings) = prefs.updateSettings(change)

    /** Saves a rule; an empty rule removes it (March). */
    suspend fun setRule(rule: CoinRule) = prefs.setRule(rule)

    suspend fun removeRule(id: String) = prefs.setRule(CoinRule(id))

    suspend fun test() = dispatcher.test(prefs.alertSettings.first())

    suspend fun testAlarm() = dispatcher.testAlarm(prefs.alertSettings.first())

    // ---- permission rows (each asked only when its switch is on, through the system screen, K6.3) ----
    fun canOverlay(): Boolean = ClockRing.canOverlay(context)
    fun clockAvailable(): Boolean = ClockRing.clockAvailable(context)
    fun canFullScreen(): Boolean = AlarmRinger.canFullScreen(context)
    fun canExact(): Boolean = AlarmRinger.canExact(context)
    fun openOverlaySettings() = ClockRing.openOverlaySettings(context)
    fun openFullScreenSettings() = AlarmRinger.openFullScreenSettings(context)
    fun openExactSettings() = AlarmRinger.openExactSettings(context)
}

/**
 * The release calendar and its reminders (K4 sketch `MarketModule.calendar`). Local only: it works with
 * Market off (K4), and has no network source (K6.10).
 */
class CalendarController internal constructor(
    private val context: Context,
    private val prefs: MarketPrefs,
    private val scheduler: ReminderScheduler = AlarmScheduler(context),
) {
    @Volatile
    private var loaded: CalendarData? = null

    /** The parsed asset; [CalendarData.NONE] when it is missing or broken. */
    suspend fun data(): CalendarData = loaded ?: withContext(Dispatchers.IO) {
        val parsed = runCatching {
            context.assets.open(CalendarLoader.ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.map { CalendarLoader.parse(it) }.getOrDefault(CalendarData.NONE)
        loaded = parsed
        parsed
    }

    /** Ids of the releases the user set a reminder for (March's `alarms`). */
    val reminded: Flow<Set<String>> get() = prefs.alarms

    /** The bell: arms or cancels a reminder ten minutes before the release. Returns the new state. */
    suspend fun toggle(e: EconEvent, now: Long): Boolean {
        val on = prefs.toggleAlarm(e.id)
        if (on) Reminders.arm(scheduler, e, now) else scheduler.cancel(e.id)
        return on
    }

    /** The Clock-app alarm for a release within 24 h (March). False when no Clock app handles it. */
    fun setClockAlarm(e: EconEvent, now: Long): Boolean =
        ReleaseTimes.clockAppAllowed(e.at, now) &&
            ClockApp.setAlarm(context, e.at - ReleaseTimes.LEAD_MINUTES * 60_000L, e.title)

    /** Re-arms every saved reminder still ahead (app start, reboot, app update). */
    suspend fun restore(now: Long): List<String> = Reminders.restore(scheduler, data(), prefs.alarms.first(), now)
}
