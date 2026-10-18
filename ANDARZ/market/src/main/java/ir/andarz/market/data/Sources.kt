package ir.andarz.market.data

import ir.andarz.market.MarketError
import ir.andarz.market.MarketException
import ir.andarz.market.MarketHosts
import ir.andarz.market.MarketService
import ir.andarz.market.model.Coin
import ir.andarz.market.model.LiveQuote
import ir.andarz.market.model.MarketOverview
import ir.andarz.market.model.UsdtRate
import ir.andarz.market.net.MarketHttp
import ir.andarz.market.net.WsPolicy
import ir.andarz.market.net.retrying
import ir.andarz.market.net.toMarketError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** USDT/Toman plus, when asked, coin/USDT quotes from the Iranian exchanges (March's `IranSnapshot`). */
internal data class IranSnapshot(val usdt: UsdtRate?, val quotes: Map<String, LiveQuote>)

/**
 * Everything the hub reads from the outside world. One real implementation over [MarketHttp]; the tests
 * drive the hub through a fake, so no test ever calls a real host (K12).
 */
internal interface MarketSources {
    /** CoinGecko top 200. Throws [MarketException]. */
    suspend fun top(sparkline: Boolean): List<Coin>

    /** Total cap, BTC dominance and Fear & Greed; null when both sources fail (March). */
    suspend fun overview(): MarketOverview?

    /** Nobitex, falling back to Wallex for USDT. Throws [MarketException] when nothing came back. */
    suspend fun iran(withCoins: Boolean): IranSnapshot

    /** Binance miniTicker batches for [symbols] (base symbols, e.g. "BTC"). */
    fun ticks(symbols: Set<String>): Flow<Map<String, LiveQuote>>
}

/**
 * The ports of March's `CoinRepository`, `IranMarket` and `BinanceStream` (MERGE_MAP). URLs, cadence-free
 * logic and fallbacks are March's; the localized failure texts became typed errors.
 */
internal class NetSources(
    private val http: MarketHttp,
    private val io: CoroutineDispatcher,
    private val now: () -> Long,
) : MarketSources {

    override suspend fun top(sparkline: Boolean): List<Coin> = withContext(io) {
        retrying(MarketService.COIN_LIST) {
            val body = http.get(marketsUrl(sparkline))
            try {
                Parsers.markets(body, now())
            } catch (e: org.json.JSONException) {
                throw MarketException(MarketError.BadData(MarketService.COIN_LIST), e)
            }
        }
    }

    override suspend fun overview(): MarketOverview? = withContext(io) {
        val global = runCatchingNonCancel { Parsers.global(http.get("$GECKO/global")) }
        val fng = runCatchingNonCancel { Parsers.fearGreed(http.get(FNG)) }
        if (global == null && fng == null) {
            null
        } else {
            (global ?: MarketOverview(0.0, 0.0, 0.0)).copy(fearGreed = fng?.first, fearLabel = fng?.second ?: "")
        }
    }

    override suspend fun iran(withCoins: Boolean): IranSnapshot = withContext(io) {
        val errors = ArrayList<Pair<MarketService, Exception>>()
        val usdt = attempt(errors, MarketService.IRAN_PRIMARY) {
            Parsers.nobitexUsdt(http.get("$NOBITEX?srcCurrency=usdt&dstCurrency=rls"), now())
        } ?: attempt(errors, MarketService.IRAN_FALLBACK) {
            Parsers.wallexUsdt(http.get(WALLEX), now())
        }
        val quotes: Map<String, LiveQuote> = if (withCoins) {
            attempt(errors, MarketService.IRAN_PRIMARY) {
                Parsers.nobitexQuotes(http.get("$NOBITEX?dstCurrency=usdt"), now())
            } ?: emptyMap()
        } else {
            emptyMap()
        }
        if (usdt == null && quotes.isEmpty()) {
            val first = errors.firstOrNull()
            val error = if (first == null) {
                MarketError.BadData(MarketService.IRAN_PRIMARY)
            } else {
                toMarketError(first.second, first.first)
            }
            throw MarketException(error, first?.second)
        }
        IranSnapshot(usdt, quotes)
    }

    override fun ticks(symbols: Set<String>): Flow<Map<String, LiveQuote>> = callbackFlow {
        // Only streams that pass the K4 frame policy are ever named; a bad symbol is dropped before it
        // reaches the socket, and subscribeFrame() re-checks every param.
        val streams = symbols.mapNotNull { WsPolicy.streamOf(it) }.sorted()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                streams.chunked(200).forEachIndexed { i, chunk ->
                    webSocket.send(WsPolicy.subscribeFrame(chunk, i + 1))
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val batch = runCatching { Parsers.miniTicker(text, now()) }.getOrNull()
                if (!batch.isNullOrEmpty()) this@callbackFlow.trySend(batch)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                this@callbackFlow.close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                this@callbackFlow.close(MarketException(toMarketError(t, MarketService.LIVE_TICKS), t))
            }
        }
        val ws = http.webSocket(TICKS, listener)
        awaitClose { ws.cancel() }
    }.buffer(256)

    private inline fun <T> attempt(
        errors: MutableList<Pair<MarketService, Exception>>,
        service: MarketService,
        block: () -> T?,
    ): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        errors += service to e
        null
    }

    private inline fun <T> runCatchingNonCancel(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    internal companion object {
        const val TOP_N = 200
        private const val GECKO = "https://" + MarketHosts.COIN_LIST + "/api/v3"
        private const val FNG = "https://" + MarketHosts.SENTIMENT + "/fng/?limit=1"
        private const val NOBITEX = "https://" + MarketHosts.IRAN_PRIMARY + "/market/stats"
        private const val WALLEX = "https://" + MarketHosts.IRAN_FALLBACK + "/v1/markets"
        private const val TICKS = "wss://" + MarketHosts.LIVE_TICKS + "/ws"

        /** Static query string: no value from the user or the journal ever enters a URL (K4). */
        fun marketsUrl(sparkline: Boolean): String =
            "$GECKO/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=$TOP_N&page=1" +
                "&sparkline=$sparkline&price_change_percentage=24h,7d"
    }
}
