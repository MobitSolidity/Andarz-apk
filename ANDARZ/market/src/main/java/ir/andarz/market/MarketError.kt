package ir.andarz.market

/**
 * The external services Market may talk to. The enum exists so that an error can name its service
 * without :market ever holding a localized string (K4): :app maps the typed value to `mkt.err.*`.
 */
enum class MarketService {
    /** Top-200 list, 7-day sparkline and the global overview. */
    COIN_LIST,

    /** Fear & Greed index. */
    SENTIMENT,

    /** Live tick prices over a WebSocket. */
    LIVE_TICKS,

    /** USDT/Toman rate, primary source. */
    IRAN_PRIMARY,

    /** USDT/Toman rate, fallback source. */
    IRAN_FALLBACK,
}

/**
 * Typed failures that cross the module boundary. March's `Net.friendly()` produced a localized sentence;
 * here the sentence is built in :app from `mkt.err.*`, because :market holds no Persian (K5, law 3).
 */
sealed interface MarketError {
    /** No route to the network at all (the device is offline). */
    data object NoNetwork : MarketError

    /** The service did not answer in time. */
    data class Timeout(val service: MarketService) : MarketError

    /** HTTP 429: too many requests; the hub backs off (K5.2). */
    data class RateLimited(val service: MarketService) : MarketError

    /** HTTP 403/451: the service refuses this IP. Common for some hosts from Iranian networks (K12). */
    data class Blocked(val service: MarketService) : MarketError

    /** Any other server-side failure. */
    data class Server(val service: MarketService, val code: Int) : MarketError

    /** The answer arrived but could not be read as the expected shape. */
    data class BadData(val service: MarketService) : MarketError
}

/** Carries a [MarketError] through code paths that must throw. */
class MarketException(val error: MarketError, cause: Throwable? = null) :
    java.io.IOException(error.toString(), cause)
