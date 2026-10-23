package ir.andarz.market

import android.content.Context
import android.content.Intent
import ir.andarz.market.alerts.AlertAction
import ir.andarz.market.model.AlertSettings
import ir.andarz.market.model.MarketState

/**
 * A place inside the app that a Market notification can open (K4). Route arguments are ids only, so the
 * target survives process death (K6.1).
 */
sealed interface MarketTarget {
    /** The coin sheet, `market/coin/{id}`. */
    data class Coin(val id: String) : MarketTarget

    /** The releases calendar, `market/news`. */
    data object News : MarketTarget

    /** The Market destination on its last used section, `market`. */
    data object Home : MarketTarget
}

/** The four notification channels of the `market` group (K6.6). */
enum class MarketChannel(val id: String) {
    /** Sharp-move and target alerts (high importance). */
    PRICE_ALERTS("price_alerts"),

    /** Alarm-style ringing. Silent channel: the tone is played on the alarm stream (K5.2). */
    PRICE_ALARM("price_alarm"),

    /** The opt-in background monitor (low importance). */
    LIVE_MONITOR("live_monitor"),

    /** Economic-release reminders (high importance). */
    RELEASES("releases"),
}

/**
 * A channel's user-visible name and description, both from the content JSON.
 *
 * [description] is nullable because March never gave `live_monitor` and `releases` one, and K7 moves
 * wording rather than writing it.
 */
data class ChannelText(val name: String, val description: String?)

/**
 * Every user-visible word :market shows comes from here. :market holds no Persian and does no number
 * formatting (law 3, K4): it hands over typed values and receives finished strings.
 *
 * Implemented by :app (`MarketHostImpl`) on top of `ContentRepository` and `MarketFmt`.
 */
interface MarketTexts {
    /** The name of the `market` channel group in the system settings (K6.6). */
    fun channelGroupName(): String

    /** Name and description of one market channel. */
    fun channel(channel: MarketChannel): ChannelText

    /** One sentence for a typed failure, from `mkt.err.*`. */
    fun error(error: MarketError): String

    // ---------------------------------------------------------------- MK2: notifications and the alarm screen

    /** A sharp move (`mkt.alert.move.*`). K6.7: arrow and plain words, no emoji. */
    fun move(m: AlertAction.Move, s: AlertSettings, usdtToman: Double?): NotifText

    /** A one-shot target price (`mkt.alert.target.title`). */
    fun target(t: AlertAction.Target, s: AlertSettings, usdtToman: Double?): NotifText

    /** Moves beyond the first three of a round (`mkt.alert.summary`). */
    fun summary(rest: List<AlertAction.Move>, s: AlertSettings): NotifText

    /** The "send a test notification" button. */
    fun testAlert(s: AlertSettings): NotifText

    /** The "test the alarm" button. */
    fun testAlarm(s: AlertSettings): NotifText

    /** Labels of the ringing notification and the alarm screen. */
    fun alarmStop(): String
    fun alarmSnooze(): String
    fun alarmOpenCoin(): String

    /** HH:mm and the weekday/date of the alarm screen, in the app's digit, calendar and zone settings. */
    fun clock(ms: Long): String
    fun day(ms: Long): String

    /** The live-monitor notification (K5.2). [monitorLine] is null while no price is known yet. */
    fun monitorTitle(): String
    fun monitorConnecting(): String
    fun monitorStop(): String
    fun monitorLine(state: MarketState): String?

    /** The body of a release reminder (`mkt.rem.body`). The title is the release's own title. */
    fun reminderBody(leadMinutes: Int): String
}

/** A finished notification text. */
data class NotifText(val title: String, val body: String)

/**
 * The bridge :market uses to reach the app: wording and deep links. Installed once from
 * `Application.onCreate` through [MarketModule.install].
 */
interface MarketHost {
    /** Every user-visible word :market shows. */
    val texts: MarketTexts

    /**
     * An explicit deep link into `MainActivity`. The caller wraps it in a `PendingIntent` with
     * `FLAG_IMMUTABLE`; the app lock is shown first, then the route (K6.11).
     */
    fun openIntent(context: Context, target: MarketTarget): Intent

    /** The journal's quiet hours (D-34), used only by the P1 mute switch `mkt.set.quiet` (K6.6). */
    fun inQuietHours(nowMs: Long): Boolean = false
}
