package ir.andarz.market.monitor

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ir.andarz.market.MarketChannel
import ir.andarz.market.MarketModule
import ir.andarz.market.MarketTarget
import ir.andarz.market.R
import ir.andarz.market.alarm.Channels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Opt-in foreground service (D-M08) that keeps the price hub, and therefore alert evaluation and ringing,
 * running while the app is closed (March's `LiveService`; K6.4 reason c). In background only watch-list
 * and rule coins are streamed. The notification is refreshed at most every 20 s and has a Stop action that
 * turns the monitor off (K5.2). It holds the hub only while it runs; the master switch off still stops
 * every request, because the hub itself is gated (K4).
 */
class LiveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var acquired = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val texts = MarketModule.textsOrNull()
        if (texts == null) {
            stopSelf()
            return
        }
        Channels.ensure(this, texts)
        goForeground(build(texts.monitorConnecting()))
        MarketModule.repository.acquire(ui = false)
        acquired = true
        scope.launch {
            var last = 0L
            MarketModule.repository.state.collect { st ->
                val now = System.currentTimeMillis()
                if (now - last < REFRESH_MS) return@collect
                val line = texts.monitorLine(st) ?: return@collect
                last = now
                try {
                    NotificationManagerCompat.from(this@LiveService).notify(Channels.TAG, ID, build(line))
                } catch (_: SecurityException) {
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch {
                MarketModule.setMonitor(false)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        if (acquired) {
            MarketModule.repository.release(ui = false)
            acquired = false
        }
        super.onDestroy()
    }

    private fun goForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID, n)
        }
    }

    private fun build(text: String): Notification {
        val texts = MarketModule.texts
        val open = PendingIntent.getActivity(
            this, 1, MarketModule.openIntent(this, MarketTarget.Home),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, LiveService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, MarketChannel.LIVE_MONITOR.id)
            .setSmallIcon(R.drawable.ic_stat_market)
            .setContentTitle(texts.monitorTitle())
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(open)
            .addAction(0, texts.monitorStop(), stop)
            .build()
    }

    companion object {
        private const val ID = 4201
        const val REFRESH_MS = 20_000L
        const val ACTION_STOP = "ir.andarz.market.action.STOP_LIVE"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, LiveService::class.java))
            } catch (_: Exception) {
                // Background-start restrictions: it starts next time the app is opened.
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LiveService::class.java))
        }
    }
}
