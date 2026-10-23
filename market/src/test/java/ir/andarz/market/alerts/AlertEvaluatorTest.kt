package ir.andarz.market.alerts

import ir.andarz.market.data.PriceHistory
import ir.andarz.market.model.AlertDirection
import ir.andarz.market.model.AlertScope
import ir.andarz.market.model.AlertSettings
import ir.andarz.market.model.AlertWindow
import ir.andarz.market.model.Coin
import ir.andarz.market.model.CoinRule
import ir.andarz.market.model.MarketState
import ir.andarz.market.model.RingMode
import ir.andarz.market.model.USDT_IRT_ID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** MK2: table tests of the pure alert evaluator, one per behaviour of K5.2. */
class AlertEvaluatorTest {

    private val t0 = 10_000_000L
    private val on = AlertSettings(enabled = true)

    private fun coin(id: String, price: Double, change24h: Double = 0.0, symbol: String = id.uppercase()) =
        Coin(id = id, rank = 1, symbol = symbol, name = id, price = price, change24h = change24h, marketCap = 1e9)

    private fun state(vararg c: Coin) = MarketState(coins = c.toList())

    /** History where [id] was at [from] within the last [agoMin] minutes. */
    private fun hist(id: String, from: Double, agoMin: Int = 4): PriceHistory = PriceHistory().apply {
        add(id, t0 - agoMin * 60_000L, from)
        add(id, t0 - 60_000L, from)
    }

    private fun eval(
        e: AlertEvaluator = AlertEvaluator(),
        st: MarketState,
        h: PriceHistory = PriceHistory(),
        s: AlertSettings = on,
        rules: Map<String, CoinRule> = emptyMap(),
        watch: Set<String> = setOf("btc"),
        now: Long = t0,
    ) = e.evaluate(st, h, s, rules, watch, now)

    private fun moves(r: AlertRound) = r.actions.filterIsInstance<AlertAction.Move>()

    // ---------------------------------------------------------------- threshold and window

    @Test
    fun `a rise over the threshold inside the window fires`() {
        val r = eval(st = state(coin("btc", 105.5)), h = hist("btc", 100.0))
        val m = moves(r).single()
        assertEquals(5.5, m.pct, 1e-9)
        assertEquals(5.0, m.threshold)
    }

    @Test
    fun `a move under the threshold does not fire`() {
        assertTrue(eval(st = state(coin("btc", 104.9)), h = hist("btc", 100.0)).actions.isEmpty())
    }

    @Test
    fun `samples older than the window are ignored`() {
        // 20 minutes ago at 100, window 15 min: only the recent sample at 104.9 counts.
        val h = PriceHistory().apply {
            add("btc", t0 - 20 * 60_000L, 100.0)
            add("btc", t0 - 2 * 60_000L, 104.9)
            add("btc", t0 - 60_000L, 104.9)
        }
        assertTrue(eval(st = state(coin("btc", 106.0)), h = h).actions.isEmpty())
        // With a 1 h window the old sample counts.
        assertEquals(1, moves(eval(st = state(coin("btc", 106.0)), h = h, s = on.copy(window = AlertWindow.H1))).size)
    }

    @Test
    fun `the 24 h window uses the feed's change`() {
        val r = eval(st = state(coin("btc", 100.0, change24h = -6.0)), s = on.copy(window = AlertWindow.H24))
        assertEquals(-6.0, moves(r).single().pct)
    }

    @Test
    fun `fewer than two samples means no move`() {
        val h = PriceHistory().apply { add("btc", t0 - 60_000L, 100.0) }
        assertTrue(eval(st = state(coin("btc", 120.0)), h = h).actions.isEmpty())
    }

    // ---------------------------------------------------------------- direction

    @Test
    fun `direction up ignores drops and down ignores rises`() {
        val drop = state(coin("btc", 94.0))
        val h = hist("btc", 100.0)
        assertTrue(eval(st = drop, h = h, s = on.copy(direction = AlertDirection.UP)).actions.isEmpty())
        assertEquals(-6.0, moves(eval(st = drop, h = hist("btc", 100.0), s = on.copy(direction = AlertDirection.DOWN))).single().pct, 1e-9)
        val rise = state(coin("btc", 106.0))
        assertTrue(eval(st = rise, h = hist("btc", 100.0), s = on.copy(direction = AlertDirection.DOWN)).actions.isEmpty())
    }

    @Test
    fun `both picks the larger of rise and drop`() {
        // Window low 90, high 110, now 100: rise 11.1 %, drop 9.09 %: the rise wins.
        val h = PriceHistory().apply {
            add("btc", t0 - 3 * 60_000L, 90.0)
            add("btc", t0 - 2 * 60_000L, 110.0)
        }
        val m = moves(eval(st = state(coin("btc", 100.0)), h = h)).single()
        assertEquals((100.0 - 90.0) / 90.0 * 100, m.pct, 1e-9)
    }

    // ---------------------------------------------------------------- cooldown

    @Test
    fun `the cooldown blocks a repeat per coin and direction`() {
        val e = AlertEvaluator()
        val st = state(coin("btc", 106.0))
        assertEquals(1, moves(eval(e, st, hist("btc", 100.0))).size)
        assertTrue(eval(e, st, hist("btc", 100.0), now = t0 + 29 * 60_000L).actions.isEmpty(), "inside 30 min")
        // The other direction is a separate key.
        assertEquals(1, moves(eval(e, state(coin("btc", 94.0)), hist("btc", 100.0), now = t0 + 60_000L)).size)
        // After 30 min it fires again; history is re-stamped around the new now.
        val later = t0 + 30 * 60_000L
        val h2 = PriceHistory().apply { add("btc", later - 3 * 60_000L, 100.0); add("btc", later - 60_000L, 100.0) }
        assertEquals(1, moves(eval(e, st, h2, now = later)).size)
    }

    // ---------------------------------------------------------------- scope

    @Test
    fun `watch-list scope fires for starred or customised coins only`() {
        val st = state(coin("btc", 106.0), coin("eth", 106.0), coin("sol", 106.0))
        val h = PriceHistory().apply {
            for (id in listOf("btc", "eth", "sol")) { add(id, t0 - 3 * 60_000L, 100.0); add(id, t0 - 60_000L, 100.0) }
        }
        val r = eval(st = st, h = h, rules = mapOf("sol" to CoinRule("sol", pct = 3f)), watch = setOf("btc"))
        assertEquals(setOf("btc", "sol"), moves(r).map { it.coin.id }.toSet())
    }

    @Test
    fun `all scope fires for every coin`() {
        val st = state(coin("btc", 106.0), coin("eth", 106.0))
        val h = PriceHistory().apply { for (id in listOf("btc", "eth")) { add(id, t0 - 3 * 60_000L, 100.0); add(id, t0 - 60_000L, 100.0) } }
        assertEquals(2, moves(eval(st = st, h = h, s = on.copy(scope = AlertScope.ALL), watch = emptySet())).size)
    }

    @Test
    fun `usdt toman follows includeUsdt even when not starred`() {
        val st = state(coin(USDT_IRT_ID, 63000.0, symbol = "USDT"))
        val h = hist(USDT_IRT_ID, 60000.0)
        assertEquals(1, moves(eval(st = st, h = h, watch = emptySet())).size)
        assertTrue(eval(st = st, h = hist(USDT_IRT_ID, 60000.0), s = on.copy(includeUsdt = false), watch = emptySet()).actions.isEmpty())
    }

    @Test
    fun `a per-coin threshold overrides the global one`() {
        val st = state(coin("btc", 103.5))
        val r = eval(st = st, h = hist("btc", 100.0), rules = mapOf("btc" to CoinRule("btc", pct = 3f)))
        assertEquals(3.0, moves(r).single().threshold)
    }

    @Test
    fun `sharp moves stay silent while alerts are off`() {
        assertTrue(eval(st = state(coin("btc", 120.0)), h = hist("btc", 100.0), s = AlertSettings()).actions.isEmpty())
    }

    // ---------------------------------------------------------------- one-shot targets

    @Test
    fun `targets are one-shot, independent of the switch, and ask to be cleared`() {
        val e = AlertEvaluator()
        val rules = mapOf("btc" to CoinRule("btc", above = 70000.0, below = 60000.0))
        val off = AlertSettings()
        val r1 = eval(e, state(coin("btc", 70500.0)), s = off, rules = rules)
        val t = r1.actions.filterIsInstance<AlertAction.Target>().single()
        assertTrue(t.above)
        assertEquals(listOf("btc" to true), r1.clearTargets)
        // Same rule still present (the clear has not landed yet): it does not fire twice.
        assertTrue(eval(e, state(coin("btc", 71000.0)), s = off, rules = rules).actions.isEmpty())
        val r2 = eval(e, state(coin("btc", 59000.0)), s = off, rules = rules)
        assertEquals(listOf("btc" to false), r2.clearTargets)
    }

    // ---------------------------------------------------------------- ring modes and the round cap

    @Test
    fun `ring modes decide what rings`() {
        val rules = mapOf("btc" to CoinRule("btc", above = 100.0))
        val target = { ring: RingMode -> eval(st = state(coin("btc", 101.0)), s = AlertSettings(ring = ring), rules = rules).actions.filterIsInstance<AlertAction.Target>().single().ring }
        assertEquals(false, target(RingMode.OFF))
        assertEquals(true, target(RingMode.TARGETS))
        assertEquals(true, target(RingMode.ALL))
        val move = { ring: RingMode -> moves(eval(st = state(coin("btc", 106.0)), h = hist("btc", 100.0), s = on.copy(ring = ring))).single().ring }
        assertEquals(false, move(RingMode.OFF))
        assertEquals(false, move(RingMode.TARGETS))
        assertEquals(true, move(RingMode.ALL))
    }

    @Test
    fun `at most three moves per round plus one summary, strongest first, only the first rings`() {
        val ids = listOf("a", "b", "c", "d", "e")
        val prices = listOf(106.0, 110.0, 107.0, 120.0, 108.0)
        val st = MarketState(coins = ids.zip(prices).map { (id, p) -> coin(id, p) })
        val h = PriceHistory().apply { for (id in ids) { add(id, t0 - 3 * 60_000L, 100.0); add(id, t0 - 60_000L, 100.0) } }
        val r = eval(st = st, h = h, s = on.copy(scope = AlertScope.ALL, ring = RingMode.ALL), watch = emptySet())
        val m = moves(r)
        assertEquals(listOf("d", "b", "e"), m.map { it.coin.id })
        assertEquals(listOf(true, false, false), m.map { it.ring })
        val sum = r.actions.filterIsInstance<AlertAction.Summary>().single()
        assertEquals(listOf("c", "a"), sum.rest.map { it.coin.id })
    }
}
