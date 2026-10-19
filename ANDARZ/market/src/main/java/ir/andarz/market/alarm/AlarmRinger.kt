package ir.andarz.market.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ir.andarz.market.MarketChannel
import ir.andarz.market.MarketModule
import ir.andarz.market.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * Makes the phone ring like an alarm clock when a price alert fires. Port of March's `AlarmRinger`
 * (K5.2, behaviour unchanged):
 * - looping alarm tone on the ALARM stream (plays with the ringer on silent), raised to a 70 % floor and
 *   restored afterwards;
 * - repeating vibration and a partial wake lock;
 * - a full-screen [AlarmActivity] over the lock screen (full-screen intent, plus `setAlarmClock` when
 *   exact alarms are allowed);
 * - Stop and Snooze 5 min; auto-stop after 3 min.
 *
 * Changed for the merge: labels come from [ir.andarz.market.MarketTexts]; the notification carries the tag
 * "market" (K6.6); the colour argument is gone (Andarz tokens live in :app, the alarm screen is dark).
 */
object AlarmRinger {
    const val ACTION_STOP = "ir.andarz.market.action.ALARM_STOP"
    const val ACTION_SNOOZE = "ir.andarz.market.action.ALARM_SNOOZE"
    const val ACTION_FIRE = "ir.andarz.market.action.ALARM_FIRE"
    const val SNOOZE_MIN = 5
    const val AUTO_STOP_MS = 3 * 60_000L
    private const val NOTIF_ID = 7301

    /** If the alarm stream is quieter than this share of max, raise it while ringing (restored afterwards). */
    const val MIN_VOLUME = 0.7f

    data class Info(val title: String, val body: String, val coinId: String?, val at: Long)

    private val _state = MutableStateFlow<Info?>(null)

    /** The alarm currently ringing, or null. Observed by [AlarmActivity]. */
    val state: StateFlow<Info?> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wake: PowerManager.WakeLock? = null
    private var restoreVolume: Int? = null
    private val autoStop = Runnable { app?.let { stop(it) } }

    /** Start ringing (or update the text if already ringing). Safe to call from any thread. */
    fun fire(context: Context, title: String, body: String, coinId: String?) {
        val ctx = context.applicationContext
        main.post { start(ctx, Info(title, body, coinId, System.currentTimeMillis())) }
    }

    fun stop(context: Context) {
        val ctx = context.applicationContext
        main.post {
            main.removeCallbacks(autoStop)
            runCatching { player?.stop() }
            runCatching { player?.release() }
            player = null
            runCatching { vibrator?.cancel() }
            vibrator = null
            restoreVolume?.let { v ->
                runCatching { ctx.getSystemService(AudioManager::class.java)?.setStreamVolume(AudioManager.STREAM_ALARM, v, 0) }
            }
            restoreVolume = null
            runCatching { if (wake?.isHeld == true) wake?.release() }
            wake = null
            ctx.getSystemService(AlarmManager::class.java)?.cancel(showPending(ctx))
            NotificationManagerCompat.from(ctx).cancel(Channels.TAG, NOTIF_ID)
            _state.value = null
        }
    }

    /** Silence now and ring again in [SNOOZE_MIN] minutes. */
    fun snooze(context: Context) {
        val info = _state.value
        stop(context)
        if (info == null) return
        val ctx = context.applicationContext
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_FIRE)
            .putExtra("title", info.title)
            .putExtra("body", info.body)
            .putExtra("coin", info.coinId)
        val pi = PendingIntent.getBroadcast(ctx, 7306, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val at = System.currentTimeMillis() + SNOOZE_MIN * 60_000L
        if (canExact(ctx)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    // ---- Permission helpers (shown on the Alerts section) ----

    fun canFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 ||
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() != false

    fun canExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() != false

    fun openFullScreenSettings(context: Context) {
        if (Build.VERSION.SDK_INT < 34) return
        openSettings(context, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + context.packageName)))
    }

    fun openExactSettings(context: Context) {
        if (Build.VERSION.SDK_INT < 31) return
        openSettings(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)))
    }

    private fun openSettings(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    // ---- Internals (main thread) ----

    private fun start(ctx: Context, info: Info) {
        app = ctx
        _state.value = info
        main.removeCallbacks(autoStop)
        main.postDelayed(autoStop, AUTO_STOP_MS)
        holdWake(ctx)
        playTone(ctx)
        vibrate(ctx)
        postNotification(ctx, info)
        // Visible app: open the alarm screen directly. In background Android blocks this silently and the
        // full-screen intent / alarm-clock path below takes over.
        runCatching { ctx.startActivity(alarmIntent(ctx)) }
        scheduleAlarmClock(ctx)
    }

    private fun alarmIntent(ctx: Context): Intent =
        Intent(ctx, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)

    private fun showPending(ctx: Context): PendingIntent =
        PendingIntent.getActivity(ctx, 7305, alarmIntent(ctx), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun scheduleAlarmClock(ctx: Context) {
        if (!canExact(ctx)) return
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = showPending(ctx)
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(System.currentTimeMillis() + 500, pi), pi)
        } catch (_: SecurityException) {
        }
    }

    private fun holdWake(ctx: Context) {
        if (wake?.isHeld == true) return
        wake = ctx.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "andarz:alarm")
            ?.apply {
                setReferenceCounted(false)
                acquire(AUTO_STOP_MS + 10_000L)
            }
    }

    private fun alarmAttrs(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** The volume floor of K5.2 as a pure function, so a JVM test pins it. */
    internal fun volumeFloor(max: Int): Int = (max * MIN_VOLUME).roundToInt().coerceAtLeast(1)

    private fun playTone(ctx: Context) {
        if (player != null) return
        val am = ctx.getSystemService(AudioManager::class.java)
        if (am != null && restoreVolume == null) {
            runCatching {
                val cur = am.getStreamVolume(AudioManager.STREAM_ALARM)
                val floor = volumeFloor(am.getStreamMaxVolume(AudioManager.STREAM_ALARM))
                if (cur < floor) {
                    am.setStreamVolume(AudioManager.STREAM_ALARM, floor, 0)
                    restoreVolume = cur
                }
            }
        }
        val candidates = listOfNotNull(
            RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        )
        for (uri in candidates) {
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(alarmAttrs())
                mp.setDataSource(ctx, uri)
                mp.isLooping = true
                mp.prepare()
                mp.start()
                player = mp
                return
            } catch (_: Exception) {
                runCatching { mp.release() }
            }
        }
    }

    private fun vibrate(ctx: Context) {
        if (vibrator != null) return
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Vibrator::class.java)
        }
        if (v == null || !v.hasVibrator()) return
        vibrator = v
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 900, 500, 900, 500), 0)
        @Suppress("DEPRECATION")
        v.vibrate(effect, alarmAttrs())
    }

    private fun postNotification(ctx: Context, info: Info) {
        val texts = MarketModule.textsOrNull() ?: return
        Channels.ensure(ctx, texts)
        val full = PendingIntent.getActivity(ctx, 7302, alarmIntent(ctx), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getBroadcast(
            ctx, 7303, Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snooze = PendingIntent.getBroadcast(
            ctx, 7304, Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, MarketChannel.PRICE_ALARM.id)
            .setSmallIcon(R.drawable.ic_stat_market)
            .setContentTitle(info.title)
            .setContentText(info.body.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(info.body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(full)
            .setFullScreenIntent(full, true)
            .addAction(0, texts.alarmStop(), stop)
            .addAction(0, texts.alarmSnooze(), snooze)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(Channels.TAG, NOTIF_ID, n)
        } catch (_: SecurityException) {
            // Notification permission denied: the tone, vibration and alarm screen still work.
        }
    }
}

/** Stop / Snooze buttons on the alarm notification, and snoozed alarms coming back. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmRinger.ACTION_STOP -> AlarmRinger.stop(context)
            AlarmRinger.ACTION_SNOOZE -> AlarmRinger.snooze(context)
            AlarmRinger.ACTION_FIRE -> AlarmRinger.fire(
                context,
                intent.getStringExtra("title") ?: "",
                intent.getStringExtra("body") ?: "",
                intent.getStringExtra("coin"),
            )
        }
    }
}
