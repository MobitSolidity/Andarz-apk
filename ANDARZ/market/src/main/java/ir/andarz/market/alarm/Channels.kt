package ir.andarz.market.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import ir.andarz.market.MarketChannel
import ir.andarz.market.MarketTexts

/**
 * The `market` channel group and its four channels (K6.6), created at app start. Names and descriptions
 * come from content through [MarketTexts]. Settings per channel are March's.
 */
internal object Channels {
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
