package ir.andarz.market.alerts

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ir.andarz.market.MarketChannel
import ir.andarz.market.MarketHost
import ir.andarz.market.MarketTarget
import ir.andarz.market.NotifText
import ir.andarz.market.R
import ir.andarz.market.alarm.AlarmRinger
import ir.andarz.market.alarm.Channels
import ir.andarz.market.alarm.ClockRing
import ir.andarz.market.model.AlertSettings

/**
 * The Android half of March's `PriceAlerts`: posts what [AlertEvaluator] decided. Wording comes from
 * [MarketHost.texts], the open intent from [MarketHost.openIntent] (app lock first, then the coin, K6.11),
 * and every notification carries the tag "market" (K6.6).
 *
 * [quiet] is the P1 switch `mkt.set.quiet` (K6.6): while it returns true, alerts are posted silently and
 * nothing rings. Quiet hours never defer an alert.
 */
internal class AlertDispatcher(
    private val context: Context,
    private val host: MarketHost,
    private val quiet: () -> Boolean = { false },
) {
    fun dispatch(round: AlertRound, s: AlertSettings, usdtToman: Double?) {
        if (round.actions.isEmpty()) return
        Channels.ensure(context, host.texts)
        val silent = quiet()
        for (a in round.actions) {
            when (a) {
                is AlertAction.Target -> {
                    val t = host.texts.target(a, s, usdtToman)
                    if (a.ring && !silent) alarm(s, a.coin.id + "|target", t, a.coin.id) else post(a.coin.id + "|target", t, a.coin.id, silent)
                }
                is AlertAction.Move -> {
                    val t = host.texts.move(a, s, usdtToman)
                    if (a.ring && !silent) alarm(s, a.coin.id + "|move", t, a.coin.id) else post(a.coin.id + "|move", t, a.coin.id, silent)
                }
                is AlertAction.Summary -> post("summary", host.texts.summary(a.rest, s), null, silent)
            }
        }
    }

    fun test(s: AlertSettings) = post("test", host.texts.testAlert(s), null, silent = false)

    /** Rings the phone exactly like a real alert would (Clock app or the own alarm). */
    fun testAlarm(s: AlertSettings) = alarm(s, "test-alarm", host.texts.testAlarm(s), null)

    /**
     * Clock app first (if wanted and Android allows it), the alert details then stay in a regular
     * notification; otherwise the own full-screen alarm, so the phone always rings (March).
     */
    private fun alarm(s: AlertSettings, key: String, t: NotifText, coinId: String?) {
        if (s.clockApp && ClockRing.ringNow(context, t.title)) {
            post(key, t, coinId, silent = false)
        } else {
            AlarmRinger.fire(context, t.title, t.body, coinId)
        }
    }

    private fun post(key: String, t: NotifText, coinId: String?, silent: Boolean) {
        val target = if (coinId != null) MarketTarget.Coin(coinId) else MarketTarget.Home
        val open = PendingIntent.getActivity(
            context, key.hashCode(), host.openIntent(context, target),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, MarketChannel.PRICE_ALERTS.id)
            .setSmallIcon(R.drawable.ic_stat_market)
            .setContentTitle(t.title)
            .setContentText(t.body.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(t.body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSilent(silent)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        Channels.notify(context, key.hashCode(), n)
    }
}
