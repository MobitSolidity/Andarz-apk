package ir.andarz.market.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ir.andarz.market.MarketChannel
import ir.andarz.market.MarketTexts

/**
 * The `market` channel group and its four channels (K6.6), created at app start. Names and descriptions
 * come from content through [MarketTexts]. Settings per channel are March's.
 */
internal object Channels {

    /**
     * Posts a market notification with the tag "market". POST_NOTIFICATIONS is declared by :app (K8.1), so
     * the runtime grant is checked here explicitly; without it nothing is posted (the user said no, §7).
     */
    fun notify(context: Context, id: Int, n: Notification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        try {
            NotificationManagerCompat.from(context).notify(TAG, id, n)
        } catch (_: SecurityException) {
            // Revoked between the check and the call.
        }
    }

    const val GROUP = "market"

    /** Every market notification is posted with this tag, so its ids cannot collide with the discipline ones. */
    const val TAG = "market"

    fun ensure(context: Context, texts: MarketTexts) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannelGroup(NotificationChannelGroup(GROUP, texts.channelGroupName()))
        for (c in MarketChannel.entries) nm.createNotificationChannel(build(c, texts))
    }

    private fun build(c: MarketChannel, texts: MarketTexts): NotificationChannel {
        val t = texts.channel(c)
        val importance = if (c == MarketChannel.LIVE_MONITOR) NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_HIGH
        return NotificationChannel(c.id, t.name, importance).apply {
            group = GROUP
            t.description?.let { description = it }
            when (c) {
                MarketChannel.PRICE_ALERTS -> {
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 150, 250)
                    enableLights(true)
                }
                MarketChannel.PRICE_ALARM -> {
                    // The tone is played on the alarm stream by AlarmRinger, so the channel stays silent.
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setBypassDnd(true)
                }
                MarketChannel.LIVE_MONITOR, MarketChannel.RELEASES -> Unit
            }
        }
    }
}
