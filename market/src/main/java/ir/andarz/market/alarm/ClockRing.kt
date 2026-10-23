package ir.andarz.market.alarm

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import java.time.Instant
import java.time.ZoneId

/**
 * Rings a price alert through the phone's own Clock app, ported from March's `ClockRing` unchanged: a
 * 1-second timer with no UI, so the Clock app plays its alarm until the user stops it. Android only lets
 * an app open another app's screen while visible or with "display over other apps"; otherwise [ringNow]
 * returns false and the caller falls back to [AlarmRinger], so the phone always rings.
 */
object ClockRing {
    /** True while MainActivity is started; set by :app's lifecycle observer through MarketModule. */
    @Volatile
    var appVisible: Boolean = false

    fun canOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun clockAvailable(context: Context): Boolean =
        Intent(AlarmClock.ACTION_SET_TIMER).resolveActivity(context.packageManager) != null

    fun openOverlaySettings(context: Context) {
        val pkg = Uri.parse("package:" + context.packageName)
        try {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    fun ringNow(context: Context, label: String): Boolean {
        val ctx = context.applicationContext
        if (!appVisible && !canOverlay(ctx)) return false
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, 1)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label.take(60))
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(i)
            true
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Hands a release reminder to the phone's Clock app (March's `ClockApp`). It keeps
 * `ZoneId.systemDefault()` on purpose: the Clock app reads hour and minute in device time, not in the
 * app's zone setting (MERGE_MAP).
 */
object ClockApp {
    fun setAlarm(context: Context, fireAt: Long, message: String): Boolean {
        val t = Instant.ofEpochMilli(fireAt).atZone(ZoneId.systemDefault())
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, t.hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, t.minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, message.take(60))
            .putExtra(AlarmClock.EXTRA_VIBRATE, true)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(i)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
