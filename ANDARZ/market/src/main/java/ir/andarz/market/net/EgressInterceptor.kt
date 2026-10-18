package ir.andarz.market.net

import ir.andarz.market.MarketError
import ir.andarz.market.MarketException
import ir.andarz.market.MarketHosts
import ir.andarz.market.MarketService
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Refuses, in code, every request the egress policy of K4 does not allow. The policy is not a convention
 * the call sites are trusted to follow: it is enforced here, and a violation throws instead of reaching
 * the network.
 *
 * Allowed: HTTPS only (a `wss://` URL is normalized to `https` by OkHttp before it gets here), one of the
 * hosts of [MarketHosts], method GET, no request body, and no header beyond `Accept` and the generic
 * `User-Agent`. Cookies and any form of authorization are refused.
 *
 * [allowedHosts] is injectable so the tests can drive a loopback MockWebServer through the real
 * interceptor; production always uses the five constants.
 */
internal class EgressInterceptor(
    private val allowedHosts: Set<String> = MarketHosts.all.toSet(),
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        if (url.scheme != "https") {
            refuse("scheme '" + url.scheme + "' is not https")
        }
        if (url.host !in allowedHosts) {
            refuse("host '" + url.host + "' is not one of the allowed hosts")
        }
        if (request.method != "GET") {
            refuse("method '" + request.method + "' is not GET")
        }
        if (request.body != null) {
            refuse("a request body is never sent")
        }
        val upgrade = request.header("Upgrade") != null
        if (upgrade && url.host != MarketHosts.LIVE_TICKS) {
            refuse("a WebSocket upgrade is allowed only to " + MarketHosts.LIVE_TICKS)
        }
        for (name in request.headers.names()) {
            val n = name.lowercase()
            val ok = n in ALLOWED_HEADERS || (upgrade && n in WS_HANDSHAKE_HEADERS)
            if (!ok) {
                refuse("header '" + name + "' is not allowed")
            }
        }
        return chain.proceed(request)
    }

    private fun refuse(why: String): Nothing =
        throw EgressRefused("market egress policy (K4) refused this request: " + why)

    internal companion object {
        /** Only these two headers leave the device (K4). */
        val ALLOWED_HEADERS: Set<String> = setOf("accept", "user-agent")

        /**
         * The RFC 6455 handshake headers OkHttp itself adds to a WebSocket upgrade (D-M42). They carry no
         * user data (the key is 16 random bytes per connection) and are accepted only on an upgrade request
         * to the live-ticks host; on any other request they are refused like every other header.
         */
        val WS_HANDSHAKE_HEADERS: Set<String> = setOf(
            "upgrade", "connection", "sec-websocket-key", "sec-websocket-version", "sec-websocket-extensions",
        )
    }
}

/** Thrown when a call site tries to leave the egress policy. Never caught as an ordinary network error. */
internal class EgressRefused(message: String) : java.io.IOException(message)

/**
 * Client-frame policy for the live-tick WebSocket (K4): the only frames Andarz sends are Binance
 * SUBSCRIBE messages whose params are miniTicker streams of a USDT pair.
 */
internal object WsPolicy {
    private val PARAM = Regex("^[a-z0-9]{2,15}usdt@miniTicker$")

    /** True when [param] is a stream name this app may subscribe to. */
    fun isAllowedParam(param: String): Boolean = PARAM.matches(param)

    /**
     * Builds the one kind of client frame that may be sent. Throws when any param breaks the policy, so a
     * bad symbol can never reach the socket.
     */
    fun subscribeFrame(params: List<String>, id: Int): String {
        require(params.isNotEmpty()) { "a SUBSCRIBE frame needs at least one stream" }
        for (p in params) {
            if (!isAllowedParam(p)) {
                throw EgressRefused("market egress policy (K4) refused the stream '" + p + "'")
            }
        }
        val list = params.joinToString(",") { "\"" + it + "\"" }
        return "{\"method\":\"SUBSCRIBE\",\"params\":[" + list + "],\"id\":" + id + "}"
    }

    /** The miniTicker stream name of a symbol, or null when the symbol cannot be streamed. */
    fun streamOf(symbol: String): String? {
        val s = symbol.lowercase() + "usdt@miniTicker"
        return if (isAllowedParam(s)) s else null
    }
}

/** Maps a transport failure onto the typed error that crosses the module boundary (K4, K5 of Net.kt). */
internal fun toMarketError(e: Throwable?, service: MarketService): MarketError = when (e) {
    is MarketException -> e.error
    is java.net.UnknownHostException -> MarketError.NoNetwork
    is java.net.SocketTimeoutException -> MarketError.Timeout(service)
    is java.net.ConnectException -> MarketError.NoNetwork
    is javax.net.ssl.SSLException -> MarketError.Blocked(service)
    is HttpStatus -> when (e.code) {
        429 -> MarketError.RateLimited(service)
        403, 451 -> MarketError.Blocked(service)
        else -> MarketError.Server(service, e.code)
    }
    else -> MarketError.Server(service, 0)
}

/** A non-2xx answer. Carries the code so the hub can back off on 429 exactly as March does (K5.2). */
internal class HttpStatus(val code: Int) : java.io.IOException("HTTP " + code)
