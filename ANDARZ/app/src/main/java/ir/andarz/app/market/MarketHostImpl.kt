package ir.andarz.app.market

import android.content.Context
import android.content.Intent
import ir.andarz.app.MainActivity
import ir.andarz.app.content.ContentRepository
import ir.andarz.engine.Fmt
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
) : MarketHost {

    override val texts: MarketTexts = AndarzMarketTexts(content)

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
) : MarketTexts {

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
