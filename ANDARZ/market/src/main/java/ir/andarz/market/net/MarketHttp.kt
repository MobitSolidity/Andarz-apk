package ir.andarz.market.net

import ir.andarz.market.MarketException
import ir.andarz.market.MarketService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * The only HTTP/WebSocket client in the project (K4). Ported from March's `data/Net.kt` with its timeouts
 * and retry behaviour; the localized `friendly()` became [toMarketError], and the egress policy is now
 * enforced by [EgressInterceptor] instead of trusted to the call sites.
 *
 * The client is built lazily on the first call, so an installed-but-never-demanded Market opens no socket
 * (the zero-traffic rule of K4; `ZeroTrafficTest` pins it).
 */
internal class MarketHttp(
    private val userAgent: String = USER_AGENT,
    callFactory: (() -> Call.Factory)? = null,
) {
    private val factory: Lazy<Call.Factory> = lazy {
        callFactory?.invoke() ?: OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(EgressInterceptor())
            .build()
    }

    /** True once a client exists, i.e. once something actually asked for data. Used by the zero-traffic test. */
    val started: Boolean get() = factory.isInitialized()

    /** Blocking GET. Call from an IO dispatcher. Throws [HttpStatus] for a non-2xx answer. */
    fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", userAgent)
            .get()
            .build()
        factory.value.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpStatus(response.code)
            return response.body?.string() ?: throw HttpStatus(response.code)
        }
    }

    /**
     * Opens a WebSocket through the same client, so the egress interceptor sees the upgrade request (K4).
     * Client frames must be built with [WsPolicy.subscribeFrame]; nothing else is ever sent.
     */
    fun webSocket(url: String, listener: WebSocketListener): WebSocket {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .build()
        val f = factory.value as? WebSocket.Factory
            ?: throw java.io.IOException("this client cannot open a WebSocket")
        return f.newWebSocket(request, listener)
    }

    internal companion object {
        /**
         * Deliberately generic: a coarse product marker and nothing else. No device model, no Android
         * version, no identifier (K4, law 10).
         */
        const val USER_AGENT: String = "Andarz-Market/1"
    }
}

/**
 * Two attempts with a short pause, then the typed error (March's `retrying`, K5). The original throwable
 * is kept as the cause so a log line stays useful, but no localized text is built here (law 3).
 */
internal suspend fun <T> retrying(service: MarketService, block: () -> T): T {
    var last: Exception? = null
    for (attempt in 0 until ATTEMPTS) {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            last = e
            if (e is EgressRefused) throw e // a policy breach is a bug, never retried
            if (attempt == 0) delay(RETRY_PAUSE_MS)
        }
    }
    throw MarketException(toMarketError(last, service), last)
}

private const val ATTEMPTS = 2
private const val RETRY_PAUSE_MS = 1_500L
