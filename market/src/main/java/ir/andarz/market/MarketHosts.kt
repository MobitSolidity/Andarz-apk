package ir.andarz.market

/**
 * The exact set of hosts Market may reach (K4). These constants are the single source of truth: the
 * consent sheet (`mkt.consent.hosts`) and the About network page (`mkt.net.*`) are built from this list,
 * so the text can never drift from the code, and [ir.andarz.market.net.EgressInterceptor] refuses
 * anything else.
 *
 * Adding a host needs a D-M decision and a change of the consent text (K4).
 */
object MarketHosts {
    /** Top-200 markets, 7-day sparkline, global overview. */
    const val COIN_LIST = "api.coingecko.com"

    /** Fear & Greed index. */
    const val SENTIMENT = "api.alternative.me"

    /** Live tick prices, WebSocket only. */
    const val LIVE_TICKS = "data-stream.binance.vision"

    /** USDT/Toman, primary. */
    const val IRAN_PRIMARY = "api.nobitex.ir"

    /** USDT/Toman, fallback. */
    const val IRAN_FALLBACK = "api.wallex.ir"

    /** Every allowed host, in the order the consent sheet and the About page list them. */
    val all: List<String> = listOf(COIN_LIST, SENTIMENT, LIVE_TICKS, IRAN_PRIMARY, IRAN_FALLBACK)

    /** The host each service uses. */
    fun hostOf(service: MarketService): String = when (service) {
        MarketService.COIN_LIST -> COIN_LIST
        MarketService.SENTIMENT -> SENTIMENT
        MarketService.LIVE_TICKS -> LIVE_TICKS
        MarketService.IRAN_PRIMARY -> IRAN_PRIMARY
        MarketService.IRAN_FALLBACK -> IRAN_FALLBACK
    }
}
