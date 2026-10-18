package ir.andarz.market.data

import ir.andarz.market.MarketError
import ir.andarz.market.MarketException
import ir.andarz.market.MarketService
import ir.andarz.market.model.AlertScope
import ir.andarz.market.model.AlertSettings
import ir.andarz.market.model.Coin
import ir.andarz.market.model.CoinRule
import ir.andarz.market.model.IranSource
import ir.andarz.market.model.LiveMode
import ir.andarz.market.model.LiveQuote
import ir.andarz.market.model.MarketOverview
import ir.andarz.market.model.Source
import ir.andarz.market.model.USDT_IRT_ID
import ir.andarz.market.model.UsdtRate
import ir.andarz.market.net.HttpStatus
import ir.andarz.market.net.toMarketError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MK1: the hub driven with virtual time and a fake [MarketSources]. Covers the merge of base, live and
 * fallback prices including the synthetic USDT/Toman coin, the cadence of K5.2, the demand rule of K6.4,
 * the zero-traffic rule of K4 at the hub level, and the typed errors.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PriceHubTest {

    private fun coin(id: String, symbol: String, price: Double, rank: Int = 1) =
        Coin(id = id, rank = rank, symbol = symbol, name = id, price = price, change24h = 0.5, marketCap = 1e9)

    private val rate = UsdtRate(60450.0, 0.75, 61000.0, 59800.0, 60400.0, 60500.0, IranSource.NOBITEX, 0L)

    private class FakeSources(
        var coins: List<Coin> = emptyList(),
        var topError: Exception? = null,
        var tick: Map<String, LiveQuote> = emptyMap(),
    ) : MarketSources {
        var topCalls = 0
        var sparkCalls = 0
        var overviewCalls = 0
        var iranCalls = 0
        val tickRequests = ArrayList<Set<String>>()
        var openSockets = 0

        override suspend fun top(sparkline: Boolean): List<Coin> {
            topCalls++
            if (sparkline) sparkCalls++
            topError?.let { throw it }
            return coins
        }

        override suspend fun overview(): MarketOverview? {
            overviewCalls++
            return MarketOverview(2.4e12, -1.0, 51.0, 27, "Fear")
        }

        override suspend fun iran(withCoins: Boolean): IranSnapshot {
            iranCalls++
            return IranSnapshot(UsdtRate(60450.0, 0.75, 0.0, 0.0, 0.0, 0.0, IranSource.NOBITEX, 0L), emptyMap())
        }

        override fun ticks(symbols: Set<String>): Flow<Map<String, LiveQuote>> = flow {
            tickRequests += symbols
            openSockets++
            try {
                if (tick.isEmpty()) awaitCancellation()
                while (true) {
                    emit(tick)
                    delay(1_000)
                }
            } finally {
                openSockets--
            }
        }
    }

    private class Rig(
        val hub: PriceHub,
        val sources: FakeSources,
        val gate: MutableStateFlow<Boolean>,
        val watch: MutableStateFlow<Set<String>>,
        val rules: MutableStateFlow<Map<String, CoinRule>>,
        val settings: MutableStateFlow<AlertSettings>,
    )

    private fun TestScope.rig(sources: FakeSources = FakeSources(), gateOpen: Boolean = true): Rig {
        val gate = MutableStateFlow(gateOpen)
        val watch = MutableStateFlow<Set<String>>(emptySet())
        val rules = MutableStateFlow<Map<String, CoinRule>>(emptyMap())
        val settings = MutableStateFlow(AlertSettings())
        val scope: CoroutineScope = backgroundScope
        val hub = PriceHub(sources, scope, { testScheduler.currentTime }, gate, watch, rules, settings)
        return Rig(hub, sources, gate, watch, rules, settings)
    }

    // ---------------------------------------------------------------- the merge (compose)

    @Test
    fun `a fresh binance tick wins and the usdt toman coin comes first`() = runTest {
        val r = rig()
        advanceTimeBy(100_000)
        val t = testScheduler.currentTime
        r.hub.seedForTest(
            base = listOf(coin("bitcoin", "BTC", 64000.0)),
            binance = mapOf("BTC" to LiveQuote(64400.0, 2.0, t - 1_000)),
            lastWsAt = t - 1_000,
            iran = mapOf("BTC" to LiveQuote(64300.0, 1.5, t)),
            iranAt = t,
            usdt = rate,
            geckoAt = t - 30_000,
        )
        val s = r.hub.compose()
        assertEquals(LiveMode.BINANCE, s.live)
        assertEquals(t, s.updatedAt, "a live mode stamps now")
        assertEquals(USDT_IRT_ID, s.coins[0].id)
        assertTrue(s.coins[0].isToman)
        assertEquals(60450.0, s.coins[0].price)
        assertEquals(IranSource.NOBITEX, s.coins[0].iranSource)
        assertEquals("USDT", s.coins[0].symbol)
        val btc = s.coins[1]
        assertEquals(64400.0, btc.price)
        assertEquals(2.0, btc.change24h)
        assertEquals(Source.BINANCE, btc.source)
    }

    @Test
    fun `nobitex takes over when the socket has been quiet for 15 s`() = runTest {
        val r = rig()
        advanceTimeBy(100_000)
        val t = testScheduler.currentTime
        r.hub.seedForTest(
            base = listOf(coin("bitcoin", "BTC", 64000.0)),
            binance = mapOf("BTC" to LiveQuote(64400.0, 2.0, t - 16_000)),
            lastWsAt = t - PriceHub.WS_LIVE_MS,
            iran = mapOf("BTC" to LiveQuote(64300.0, 1.5, t)),
            iranAt = t - 5_000,
        )
        val s = r.hub.compose()
        assertEquals(LiveMode.NOBITEX, s.live)
        assertEquals(64300.0, s.coins[0].price)
        assertEquals(Source.NOBITEX, s.coins[0].source)
    }

    @Test
    fun `a quote older than ten minutes is unused`() = runTest {
        val r = rig()
        advanceTimeBy(20 * 60_000L)
        val t = testScheduler.currentTime
        r.hub.seedForTest(
            base = listOf(coin("bitcoin", "BTC", 64000.0), coin("ethereum", "ETH", 3000.0, 2)),
            binance = mapOf(
                "BTC" to LiveQuote(64400.0, 2.0, t - PriceHub.WS_QUOTE_MAX_AGE),
                "ETH" to LiveQuote(3010.0, 1.0, t - 1_000),
            ),
            lastWsAt = t - 1_000,
            geckoAt = t - 30_000,
        )
        val s = r.hub.compose()
        assertEquals(64000.0, s.coins[0].price, "the stale BTC quote is not used")
        assertEquals(Source.COINGECKO, s.coins[0].source)
        assertEquals(3010.0, s.coins[1].price)
    }

    @Test
    fun `a quote 30 percent away from coingecko cannot hijack the price`() = runTest {
        val r = rig()
        advanceTimeBy(100_000)
        val t = testScheduler.currentTime
        r.hub.seedForTest(
            base = listOf(coin("x", "XYZ", 10.0)),
            binance = mapOf("XYZ" to LiveQuote(13.0, 1.0, t)),
            lastWsAt = t,
        )
        assertEquals(10.0, r.hub.compose().coins[0].price)
        r.hub.seedForTest(
            base = listOf(coin("x", "XYZ", 10.0)),
            binance = mapOf("XYZ" to LiveQuote(12.9, 1.0, t)),
            lastWsAt = t,
        )
        assertEquals(12.9, r.hub.compose().coins[0].price)
    }

    @Test
    fun `only the highest ranked coin of a shared ticker gets live prices`() = runTest {
        val r = rig()
        advanceTimeBy(100_000)
        val t = testScheduler.currentTime
        r.hub.seedForTest(
            base = listOf(coin("a", "DUP", 1.0, 1), coin("b", "DUP", 1.0, 2)),
            binance = mapOf("DUP" to LiveQuote(1.1, 1.0, t)),
            lastWsAt = t,
        )
        val s = r.hub.compose()
        assertEquals(1.1, s.coins[0].price)
        assertEquals(1.0, s.coins[1].price)
    }

    @Test
    fun `without live quotes the mode is polling, without anything it is connecting`() = runTest {
        val r = rig()
        advanceTimeBy(100_000)
        r.hub.seedForTest(base = listOf(coin("bitcoin", "BTC", 64000.0)), geckoAt = 42L)
        val s = r.hub.compose()
        assertEquals(LiveMode.POLLING, s.live)
        assertEquals(42L, s.updatedAt, "polling shows the age of the last list")
        r.hub.seedForTest()
        assertEquals(LiveMode.CONNECTING, r.hub.compose().live)
    }

    // ---------------------------------------------------------------- demand and zero traffic (K4, K6.4)

    @Test
    fun `gate closed - an acquire opens nothing`() = runTest {
        val r = rig(gateOpen = false)
        r.hub.acquire(ui = true)
        advanceTimeBy(10 * 60_000L)
        assertFalse(r.hub.running)
        assertEquals(0, r.sources.topCalls + r.sources.overviewCalls + r.sources.iranCalls)
        assertTrue(r.sources.tickRequests.isEmpty())
    }

    @Test
    fun `gate open but nobody holds the hub - nothing is fetched`() = runTest {
        val r = rig(gateOpen = true)
        advanceTimeBy(10 * 60_000L)
        assertFalse(r.hub.running)
        assertEquals(0, r.sources.topCalls + r.sources.overviewCalls + r.sources.iranCalls)
    }

    @Test
    fun `release and switching off both stop every loop and close the socket`() = runTest {
        val src = FakeSources(coins = listOf(coin("bitcoin", "BTC", 64000.0)), tick = mapOf("BTC" to LiveQuote(64001.0, 0.0, 0L)))
        val r = rig(src)
        r.hub.acquire(ui = true)
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(r.hub.running)
        assertEquals(1, src.openSockets, "the list is on screen, so the socket is open")

        r.hub.release(ui = true)
        runCurrent()
        assertFalse(r.hub.running)
        assertEquals(0, src.openSockets)
        val calls = src.topCalls + src.iranCalls + src.overviewCalls
        advanceTimeBy(30 * 60_000L)
        assertEquals(calls, src.topCalls + src.iranCalls + src.overviewCalls, "no request after the last release")

        r.hub.acquire(ui = true)
        advanceTimeBy(5_000)
        assertTrue(r.hub.running)
        r.gate.value = false
        runCurrent()
        assertFalse(r.hub.running, "the master switch off stops the hub (K4)")
        assertEquals(0, src.openSockets)
    }

    // ---------------------------------------------------------------- cadence (K5.2)

    @Test
    fun `coingecko every 60 s on screen and iran every 10 s`() = runTest {
        val src = FakeSources(coins = listOf(coin("bitcoin", "BTC", 64000.0)))
        val r = rig(src)
        r.hub.acquire(ui = true)
        runCurrent()
        assertEquals(1, src.topCalls)
        assertEquals(1, src.sparkCalls, "the on-screen list asks for the sparkline")
        advanceTimeBy(59_000)
        assertEquals(1, src.topCalls)
        advanceTimeBy(1_001)
        assertEquals(2, src.topCalls)
        assertEquals(7, src.iranCalls, "0, 10, ... 60 s")
        assertEquals(1, src.overviewCalls, "overview every 5 min")
        advanceTimeBy(4 * 60_000L)
        assertEquals(2, src.overviewCalls)
    }

    @Test
    fun `background-only runs poll every 5 min without the sparkline`() = runTest {
        val src = FakeSources(coins = listOf(coin("bitcoin", "BTC", 64000.0)))
        val r = rig(src)
        r.hub.acquire(ui = false)
        runCurrent()
        assertEquals(1, src.topCalls)
        assertEquals(0, src.sparkCalls)
        advanceTimeBy(5 * 60_000L - 1)
        assertEquals(1, src.topCalls)
        advanceTimeBy(2)
        assertEquals(2, src.topCalls)
    }

    @Test
    fun `a 429 backs off for 2 min and the error is typed`() = runTest {
        val src = FakeSources(topError = MarketException(MarketError.RateLimited(MarketService.COIN_LIST)))
        val r = rig(src)
        r.hub.acquire(ui = true)
        runCurrent()
        assertEquals(1, src.topCalls)
        advanceTimeBy(2_000)
        assertEquals(MarketError.RateLimited(MarketService.COIN_LIST), r.hub.state.value.error)
        advanceTimeBy(60_000)
        assertEquals(1, src.topCalls, "not after 60 s")
        advanceTimeBy(60_000)
        assertEquals(2, src.topCalls, "after 2 min")
    }

    @Test
    fun `the refresh button fetches the list at once`() = runTest {
        val src = FakeSources(coins = listOf(coin("bitcoin", "BTC", 64000.0)))
        val r = rig(src)
        r.hub.acquire(ui = true)
        advanceTimeBy(5_000)
        assertEquals(1, src.topCalls)
        r.hub.refreshNow()
        runCurrent()
        assertEquals(2, src.topCalls)
    }

    @Test
    fun `history samples every 5 s and the state is published`() = runTest {
        // Samples are taken when a snapshot is published, which needs fresh data: with ticks arriving every
        // second that is every 5 s (March; without ticks it is every Iran poll, 10 s).
        val src = FakeSources(coins = listOf(coin("bitcoin", "BTC", 64000.0)), tick = mapOf("BTC" to LiveQuote(64001.0, 0.0, 0L)))
        val r = rig(src)
        r.hub.acquire(ui = true)
        advanceTimeBy(30_500)
        val s = r.hub.state.value
        assertEquals(listOf(USDT_IRT_ID, "bitcoin"), s.coins.map { it.id })
        assertEquals(27, s.overview?.fearGreed)
        val n = r.hub.history.series("bitcoin").size
        assertTrue(n in 5..7, "about one sample per 5 s, got $n")
    }

    // ---------------------------------------------------------------- which symbols are streamed

    @Test
    fun `background runs stream watch-list, rule and extra coins only`() = runTest {
        val src = FakeSources(
            coins = listOf(coin("bitcoin", "BTC", 1.0), coin("ethereum", "ETH", 1.0, 2), coin("solana", "SOL", 1.0, 3), coin("tether", "USDT", 1.0, 4)),
        )
        val r = rig(src)
        r.watch.value = setOf("bitcoin", "tether")
        r.rules.value = mapOf("ethereum" to CoinRule("ethereum", above = 4000.0))
        r.hub.acquire(ui = false)
        advanceTimeBy(5_000)
        assertEquals(setOf("BTC", "ETH"), r.hub.wantedSymbols(), "USDT is never streamed")
        r.hub.extraSymbols = setOf("ada")
        assertEquals(setOf("BTC", "ETH", "ADA"), r.hub.wantedSymbols())
        r.settings.value = AlertSettings(scope = AlertScope.ALL)
        advanceTimeBy(1_000)
        assertEquals(setOf("BTC", "ETH", "SOL", "ADA"), r.hub.wantedSymbols())
    }

    @Test
    fun `the screen streams the whole list`() = runTest {
        val src = FakeSources(coins = listOf(coin("bitcoin", "BTC", 1.0), coin("solana", "SOL", 1.0, 2), coin("bad", "B-D", 1.0, 3)))
        val r = rig(src)
        r.hub.acquire(ui = true)
        advanceTimeBy(5_000)
        assertEquals(setOf("BTC", "SOL"), r.hub.wantedSymbols(), "symbols that cannot be a stream are dropped")
        assertEquals(setOf("BTC", "SOL"), src.tickRequests.last())
    }

    // ---------------------------------------------------------------- typed errors (K4)

    @Test
    fun `transport failures map onto typed errors`() {
        val s = MarketService.COIN_LIST
        assertEquals(MarketError.NoNetwork, toMarketError(java.net.UnknownHostException(), s))
        assertEquals(MarketError.NoNetwork, toMarketError(java.net.ConnectException(), s))
        assertEquals(MarketError.Timeout(s), toMarketError(java.net.SocketTimeoutException(), s))
        assertEquals(MarketError.RateLimited(s), toMarketError(HttpStatus(429), s))
        assertEquals(MarketError.Blocked(s), toMarketError(HttpStatus(403), s))
        assertEquals(MarketError.Blocked(s), toMarketError(HttpStatus(451), s))
        assertEquals(MarketError.Server(s, 502), toMarketError(HttpStatus(502), s))
        assertEquals(MarketError.Blocked(s), toMarketError(javax.net.ssl.SSLHandshakeException("x"), s))
        val wrapped = MarketException(MarketError.BadData(MarketService.IRAN_PRIMARY))
        assertEquals(MarketError.BadData(MarketService.IRAN_PRIMARY), toMarketError(wrapped, s))
    }

    @Test
    fun `a fresh state carries no error`() = runTest {
        val r = rig()
        assertNull(r.hub.state.value.error)
        assertEquals(LiveMode.CONNECTING, r.hub.state.value.live)
    }
}
