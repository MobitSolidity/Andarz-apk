package ir.andarz.app.ui.market

import ir.andarz.app.content.ContentBundle
import ir.andarz.app.content.ContentRepository
import ir.andarz.market.model.Coin
import ir.andarz.market.model.LiveMode
import ir.andarz.market.model.USDT_IRT_ID
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** MK1: search, sort and the dynamic keys of the Market screens. */
class PricesLogicTest {

    private val usdt = Coin(USDT_IRT_ID, 0, "USDT", "", 60450.0, 0.75, 0.0, isToman = true)
    private val btc = Coin("bitcoin", 1, "BTC", "Bitcoin", 64000.0, 1.0, 1e12, volume24h = 3e10)
    private val eth = Coin("ethereum", 2, "ETH", "Ethereum", 3000.0, -2.0, 4e11, volume24h = 2e10)
    private val sol = Coin("solana", 3, "SOL", "Solana", 150.0, 5.0, 7e10, volume24h = 4e10)
    private val all = listOf(usdt, btc, eth, sol)

    @Test
    fun `usdt toman stays pinned on top under every sort`() {
        for (s in PriceSort.entries) assertEquals(USDT_IRT_ID, shownCoins(all, "", s).first().id, s.name)
        assertEquals(listOf("bitcoin", "ethereum", "solana"), shownCoins(all, "", PriceSort.RANK).drop(1).map { it.id })
        assertEquals(listOf("solana", "bitcoin", "ethereum"), shownCoins(all, "", PriceSort.GAIN).drop(1).map { it.id })
        assertEquals(listOf("ethereum", "bitcoin", "solana"), shownCoins(all, "", PriceSort.LOSS).drop(1).map { it.id })
        assertEquals(listOf("solana", "bitcoin", "ethereum"), shownCoins(all, "", PriceSort.VOLUME).drop(1).map { it.id })
    }

    @Test
    fun `search matches symbol or name, case-insensitive, trimmed`() {
        assertEquals(listOf("bitcoin"), shownCoins(all, " btc ", PriceSort.RANK).map { it.id })
        assertEquals(listOf("ethereum"), shownCoins(all, "ether", PriceSort.RANK).map { it.id })
        assertEquals(listOf(USDT_IRT_ID), shownCoins(all, "usdt", PriceSort.RANK).map { it.id })
        assertTrue(shownCoins(all, "zzz", PriceSort.RANK).isEmpty())
    }

    @Test
    fun `fear and greed classes map to keys`() {
        assertEquals("mkt.fg.ExtremeFear", fearKey("Extreme Fear"))
        assertEquals("mkt.fg.Greed", fearKey("Greed"))
        assertNull(fearKey("Something new"), "an unknown class shows raw rather than a missing key")
    }

    // ---------------------------------------------------------------- dynamic keys exist

    private val bundle: ContentBundle by lazy {
        fun asset(n: String) = listOf(File("src/main/assets/$n"), File("app/src/main/assets/$n")).first { it.isFile }.readText()
        ContentBundle.parse(asset(ContentRepository.BASE_ASSET), asset(ContentRepository.MARKET_ASSET))
    }

    @Test
    fun `every dynamic market key resolves`() {
        val keys = PriceSort.entries.map { "mkt.sort." + it.name } +
            LiveMode.entries.map { "mkt.live." + it.name } +
            listOf("Extreme Fear", "Fear", "Neutral", "Greed", "Extreme Greed").map { fearKey(it)!! } +
            ir.andarz.app.ui.nav.MarketSection.entries.map { it.labelKey }
        val missing = keys.filter { bundle.uiOrNull(it) == null }
        assertTrue(missing.isEmpty(), "missing: $missing")
    }

    @Test
    fun `every typed error has its sentence with the right placeholders`() {
        // AndarzMarketTexts.error() builds exactly these keys; each must exist and carry its arguments.
        val expect = mapOf(
            "mkt.err.noNetwork" to emptySet(),
            "mkt.err.timeout" to setOf("service"),
            "mkt.err.rateLimited" to setOf("service"),
            "mkt.err.blocked" to setOf("service"),
            "mkt.err.server" to setOf("service", "code"),
            "mkt.err.badData" to emptySet(),
            "mkt.data.nobitex" to emptySet(),
            "mkt.data.wallex" to emptySet<String>(),
        )
        val ph = Regex("""\{([A-Za-z0-9_]+)\}""")
        for ((k, args) in expect) {
            val text = bundle.uiOrNull(k)
            assertTrue(text != null, "missing $k")
            assertEquals(args, ph.findAll(text).map { it.groupValues[1] }.toSet(), k)
        }
        val rendered = bundle.ui("mkt.err.server", mapOf("service" to "X", "code" to "502"))
        assertTrue("X" in rendered && "502" in rendered && "{" !in rendered)
    }
}
