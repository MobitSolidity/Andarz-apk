package ir.andarz.market.data

import ir.andarz.market.model.IranSource
import ir.andarz.market.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** MK1: every parser against a recorded-shape fixture. No test touches a real host (K12). */
class ParsersTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResourceAsStream("fixtures/$name")) { "missing fixture $name" }
            .bufferedReader(Charsets.UTF_8).readText()

    private val now = 1_718_150_400_000L

    @Test
    fun `coingecko markets map every field and survive nulls and numeric strings`() {
        val coins = Parsers.markets(fixture("coingecko_markets.json"), now)
        assertEquals(3, coins.size)
        val btc = coins[0]
        assertEquals("bitcoin", btc.id)
        assertEquals("BTC", btc.symbol)
        assertEquals("Bitcoin", btc.name)
        assertEquals(1, btc.rank)
        assertEquals(64250.5, btc.price)
        assertEquals(1.234, btc.change24h)
        assertEquals(1.265e12, btc.marketCap)
        assertEquals(3.1e10, btc.volume24h)
        assertEquals(65000.0, btc.high24h)
        assertEquals(63100.25, btc.low24h)
        assertEquals(3.5, btc.change7d)
        assertEquals(73738.0, btc.ath)
        assertEquals(-12.9, btc.athChange)
        assertEquals(listOf(63000.1, 63500.2, 64250.5), btc.spark, "a null sample is dropped")
        assertEquals(Source.COINGECKO, btc.source)
        assertEquals(now, btc.tickAt)

        val usdt = coins[1]
        assertEquals(0.0, usdt.change7d, "a null 7-day change is zero")
        assertTrue(usdt.spark.isEmpty(), "no sparkline object means an empty series")

        val weird = coins[2]
        assertEquals(3, weird.rank, "a null rank falls back to the position")
        assertEquals(0.000012345, weird.price, "a numeric string is read")
        assertEquals(12.0, weird.volume24h)
        assertEquals(0.0, weird.marketCap)
        assertEquals(0.0, weird.change24h)
    }

    @Test
    fun `coingecko global gives cap, change and dominance`() {
        val ov = Parsers.global(fixture("coingecko_global.json"))
        assertEquals(2450000000000.5, ov.totalCap)
        assertEquals(-1.75, ov.capChange24h)
        assertEquals(51.62, ov.btcDominance)
        assertNull(ov.fearGreed)
    }

    @Test
    fun `alternative me gives value and raw classification`() {
        assertEquals(27 to "Fear", Parsers.fearGreed(fixture("alternative_fng.json")))
        assertNull(Parsers.fearGreed("""{"data":[{"value":"x","value_classification":"Fear"}]}"""))
    }

    @Test
    fun `nobitex usdt is converted from rial to toman`() {
        val r = Parsers.nobitexUsdt(fixture("nobitex_usdt_rls.json"), now)!!
        assertEquals(60450.0, r.toman)
        assertEquals(0.75, r.change24h)
        assertEquals(61000.0, r.high)
        assertEquals(59800.0, r.low)
        assertEquals(60400.0, r.bestBuy)
        assertEquals(60500.0, r.bestSell)
        assertEquals(IranSource.NOBITEX, r.source)
        assertEquals(now, r.at)
        assertNull(Parsers.nobitexUsdt("""{"stats":{"usdt-rls":{"latest":"0"}}}""", now), "a zero rate is no rate")
        assertNull(Parsers.nobitexUsdt("""{"status":"failed"}""", now))
    }

    @Test
    fun `nobitex quotes keep open usdt markets with a price only`() {
        val q = Parsers.nobitexQuotes(fixture("nobitex_usdt_quotes.json"), now)
        assertEquals(setOf("BTC", "XRP"), q.keys, "closed, zero-priced and non-USDT markets are skipped")
        assertEquals(64300.12, q["BTC"]!!.price)
        assertEquals(1.5, q["BTC"]!!.change24h)
        assertEquals(-3.1, q["XRP"]!!.change24h)
    }

    @Test
    fun `wallex usdt is already in toman`() {
        val r = Parsers.wallexUsdt(fixture("wallex_markets.json"), now)!!
        assertEquals(60400.0, r.toman)
        assertEquals(-0.4, r.change24h)
        assertEquals(61000.0, r.high)
        assertEquals(60000.0, r.low)
        assertEquals(60390.0, r.bestBuy)
        assertEquals(60420.0, r.bestSell)
        assertEquals(IranSource.WALLEX, r.source)
        assertNull(Parsers.wallexUsdt("""{"result":{"symbols":{}}}""", now))
    }

    @Test
    fun `binance single frame gives price and change from open`() {
        val m = Parsers.miniTicker(fixture("binance_miniticker_single.json"), now)
        assertEquals(setOf("BTC"), m.keys)
        assertEquals(64400.0, m["BTC"]!!.price)
        assertEquals((64400.0 - 63000.0) / 63000.0 * 100, m["BTC"]!!.change24h, 1e-12)
        assertEquals(now, m["BTC"]!!.at)
    }

    @Test
    fun `binance array frame keeps usdt miniTickers only`() {
        val m = Parsers.miniTicker(fixture("binance_miniticker_array.json"), now)
        assertEquals(setOf("ETH", "SOL"), m.keys, "non-USDT pairs and other event types are skipped")
        assertEquals(0.0, m["SOL"]!!.change24h, "a zero open gives no change rather than infinity")
    }

    @Test
    fun `the subscribe acknowledgement carries no prices`() {
        assertTrue(Parsers.miniTicker(fixture("binance_subscribe_ack.json"), now).isEmpty())
    }

    @Test
    fun `the markets url is static and asks for the top 200`() {
        val url = NetSources.marketsUrl(sparkline = true)
        assertTrue(url.startsWith("https://api.coingecko.com/api/v3/coins/markets?"))
        assertTrue("per_page=200" in url)
        assertTrue("sparkline=true" in url)
        assertTrue("sparkline=false" in NetSources.marketsUrl(sparkline = false))
    }
}
