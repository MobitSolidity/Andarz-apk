package ir.andarz.market

import android.content.Context
import android.content.Intent

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
}

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
}
