package ir.andarz.app.market

import android.content.Context
import android.content.Intent
import ir.andarz.app.MainActivity
import ir.andarz.app.content.ContentRepository
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.market.MarketFmt
import ir.andarz.app.ui.market.SEP
import ir.andarz.data.settings.Settings
import ir.andarz.data.settings.SettingsRepository
import ir.andarz.engine.Fmt
import ir.andarz.market.NotifText
import ir.andarz.market.alerts.AlertAction
import ir.andarz.market.model.AlertSettings
import ir.andarz.market.model.Coin
import ir.andarz.market.model.MarketState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.abs
import ir.andarz.market.ChannelText
import ir.andarz.market.MarketChannel
import ir.andarz.market.MarketError
import ir.andarz.market.MarketHost
import ir.andarz.market.MarketHosts
import ir.andarz.market.MarketService
import ir.andarz.market.MarketTarget
import ir.andarz.market.MarketTexts
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The :app side of the `:market` bridge (K4): every Persian word and every deep link that the market code
 * needs. :market itself holds no wording and does no formatting (law 3).
 */
@Singleton
class MarketHostImpl @Inject constructor(
    private val content: ContentRepository,
    private val settings: SettingsRepository,
) : MarketHost {

    /**
     * The digit, calendar and zone settings the texts format with. Kept current by [watchSettings] (called
     * from `AndarzApp`), so building a notification never blocks the caller; receivers run on the main
     * thread. Until the first read lands, the defaults apply (Persian digits, Jalali, Tehran).
     */
    @Volatile
    private var current: Settings = Settings()

    override val texts: MarketTexts = AndarzMarketTexts(content) { current }

    /** Follows the settings for the life of the process. */
    fun watchSettings(scope: CoroutineScope) {
        scope.launch { settings.settings.collect { current = it } }
    }

    /**
     * An explicit intent into `MainActivity` carrying the route. The caller wraps it in a `PendingIntent`
     * with `FLAG_IMMUTABLE`; the app lock is shown first, then the route (K6.11).
     */
    override fun openIntent(context: Context, target: MarketTarget): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_ROUTE, routeOf(target))

    private fun routeOf(target: MarketTarget): String = when (target) {
        is MarketTarget.Coin -> "market/coin/" + target.id
        MarketTarget.News -> "market/news"
        MarketTarget.Home -> "market"
    }
}

/**
 * Typed market events in, finished Persian strings out.
 *
 * The channel wording lands in MK2 together with the channels themselves; until then the keys resolve
 * through `ContentRepository`, which renders a missing key as `[[key]]` in debug builds and records it for
 * `MISSING_COPY.md` rather than crashing a receiver (§0.3).
 */
internal class AndarzMarketTexts(
    private val content: ContentRepository,
    private val settings: () -> Settings = { Settings() },
) : MarketTexts {

    private fun ui(): Ui = Ui(content.bundle, settings())

    private fun fmt(u: Ui): MarketFmt = MarketFmt(u.fa, content.ui("mkt.unit.tomanSuffix"), u.zone)

    private fun greeting(s: AlertSettings): String =
        s.name.trim().let { if (it.isEmpty()) "" else it + content.ui("mkt.sep.comma") }

    private fun pctNumber(u: Ui, v: Double): String = Fmt.isolate(u.num(v, 1, group = false, trim = true))

    private fun priceLine(f: MarketFmt, c: Coin, rate: Double?): String = when {
        c.isToman -> f.toman(c.price)
        rate != null && rate > 0 -> f.usd(c.price) + " (" + APPROX + f.toman(c.price * rate) + ")"
        else -> f.usd(c.price)
    }

    private fun unit(f: MarketFmt, c: Coin, v: Double): String = if (c.isToman) f.toman(v) else f.usd(v)

    override fun move(m: AlertAction.Move, s: AlertSettings, usdtToman: Double?): NotifText {
        val u = ui()
        val f = fmt(u)
        val up = m.pct > 0
        val key = if (up) "mkt.alert.move.up" else "mkt.alert.move.down"
        // K6.7: the rocket/red-arrow emoji become the arrows; the sentence itself is March's.
        val title = (if (up) MarketFmt.UP else MarketFmt.DOWN) + " " + greeting(s) + content.ui(
            key,
            mapOf("symbol" to Fmt.isolate(m.coin.symbol), "window" to content.ui("mkt.win." + s.window.name), "pct" to pctNumber(u, abs(m.pct))),
        )
        val body = content.ui("mkt.alert.now") + priceLine(f, m.coin, usdtToman) +
            "\n" + content.ui("mkt.alert.change24") + f.pct(m.coin.change24h) +
            SEP + content.ui("mkt.alert.yourThreshold") + pctNumber(u, m.threshold) + percent(u)
        return NotifText(title, body)
    }

    override fun target(t: AlertAction.Target, s: AlertSettings, usdtToman: Double?): NotifText {
        val f = fmt(ui())
        val title = greeting(s) + content.ui("mkt.alert.target.title", "symbol" to Fmt.isolate(t.coin.symbol))
        val body = content.ui(if (t.above) "mkt.word.above" else "mkt.word.below") + unit(f, t.coin, t.target) +
            SEP + content.ui("mkt.alert.now") + priceLine(f, t.coin, usdtToman)
        return NotifText(title, body)
    }

    override fun summary(rest: List<AlertAction.Move>, s: AlertSettings): NotifText {
        val u = ui()
        val f = fmt(u)
        val title = greeting(s) + content.ui("mkt.alert.summary", "n" to u.int(rest.size))
        val body = rest.take(8).joinToString(content.ui("mkt.sep.comma")) { Fmt.isolate(it.coin.symbol) + " " + f.pct(it.pct) }
        return NotifText(title, body)
    }

    override fun testAlert(s: AlertSettings): NotifText {
        val u = ui()
        return NotifText(
            greeting(s) + content.ui("mkt.alert.test.title"),
            content.ui(
                "mkt.alert.test.body",
                mapOf("window" to content.ui("mkt.win." + s.window.name), "pct" to pctNumber(u, s.thresholdPct.toDouble())),
            ),
        )
    }

    override fun testAlarm(s: AlertSettings): NotifText =
        NotifText(greeting(s) + content.ui("mkt.alerts.testAlarm"), content.ui("mkt.alert.testAlarm.body"))

    override fun alarmStop(): String = content.ui("mkt.alarm.stop")
    override fun alarmSnooze(): String = content.ui("mkt.alarm.snooze")
    override fun alarmOpenCoin(): String = content.ui("mkt.alarm.openCoin")

    override fun clock(ms: Long): String = ui().time(Instant.ofEpochMilli(ms))

    override fun day(ms: Long): String {
        val u = ui()
        return u.date(u.localDate(Instant.ofEpochMilli(ms)))
    }

    override fun monitorTitle(): String = content.ui("mkt.monitor.title")
    override fun monitorConnecting(): String = content.ui("mkt.monitor.connecting")
    override fun monitorStop(): String = content.ui("mkt.monitor.stop")

    /** March: "BTC $x · USDT x T". */
    override fun monitorLine(state: MarketState): String? {
        val f = fmt(ui())
        val parts = ArrayList<String>()
        state.coins.firstOrNull { it.id == "bitcoin" }?.let { parts += Fmt.isolate("BTC") + " " + f.usd(it.price) }
        state.usdt?.let { parts += content.ui("mkt.monitor.usdt") + f.toman(it.toman) }
        return if (parts.isEmpty()) null else parts.joinToString(SEP)
    }

    override fun reminderBody(leadMinutes: Int): String = content.ui("mkt.rem.body", "n" to ui().int(leadMinutes))

    private fun percent(u: Ui): String = if (u.fa) Fmt.FA_PERCENT.toString() else "%"

    override fun channelGroupName(): String = content.ui("mkt.nav.market")

    override fun channel(channel: MarketChannel): ChannelText {
        val base = "mkt.channel." + channel.id
        return ChannelText(
            name = content.ui(base + ".name"),
            // March has no description for two of the four channels; absent means absent, not invented.
            description = content.uiOrNull(base + ".desc"),
        )
    }

    /**
     * One sentence per typed failure. The service is named through its own key, so the error text and the
     * host list cannot drift apart (K4).
     */
    override fun error(error: MarketError): String = when (error) {
        MarketError.NoNetwork -> content.ui("mkt.err.noNetwork")
        is MarketError.Timeout -> content.ui("mkt.err.timeout", "service" to serviceName(error.service))
        is MarketError.RateLimited -> content.ui("mkt.err.rateLimited", "service" to serviceName(error.service))
        is MarketError.Blocked -> content.ui("mkt.err.blocked", "service" to serviceName(error.service))
        is MarketError.Server -> content.ui(
            "mkt.err.server",
            mapOf("service" to serviceName(error.service), "code" to error.code.toString()),
        )
        is MarketError.BadData -> content.ui("mkt.err.badData", "service" to serviceName(error.service))
    }

    /**
     * A service is named the way March named it (`Net.friendly(service)`): the Persian names of Nobitex and Wallex for the
     * two Iranian sources, and the brand itself for CoinGecko, Binance and alternative.me. The words live
     * in the content JSON like every other string, so this returns a key lookup, not a constant.
     */
    private fun serviceName(service: MarketService): String = when (service) {
        MarketService.IRAN_PRIMARY -> content.ui("mkt.data.nobitex")
        MarketService.IRAN_FALLBACK -> content.ui("mkt.data.wallex")
        // Latin brand names inside Persian text are LTR runs (Appendix C trap 9). The isolate is applied
        // here rather than stored in the JSON, so the wording file stays plain text.
        MarketService.COIN_LIST -> Fmt.isolate("CoinGecko")
        MarketService.LIVE_TICKS -> Fmt.isolate("Binance")
        MarketService.SENTIMENT -> Fmt.isolate("alternative.me")
    }

}

private const val APPROX = "\u2248 "
