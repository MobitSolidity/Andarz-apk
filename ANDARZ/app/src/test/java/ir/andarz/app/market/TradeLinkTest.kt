package ir.andarz.app.market

import ir.andarz.data.db.PlanSnapshot
import ir.andarz.data.db.TradeAlertEntity
import ir.andarz.data.db.TradeEntity
import ir.andarz.engine.Params
import ir.andarz.engine.Side
import ir.andarz.market.model.Coin
import ir.andarz.market.model.Source
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** K9.1 (b-d): symbol resolution, levels, one-shot firing on a fake clock, freshness. */
class TradeLinkTest {
    private fun coin(id: String, symbol: String, price: Double, tickAt: Long) =
        Coin(id, 1, symbol, id, price, 1.0, 1e9, source = Source.BINANCE, tickAt = tickAt)

    private fun trade(id: String = "t1", side: String = "LONG", status: String = "OPEN", symbol: String = "BTCUSDT") = TradeEntity(
        id = id, account = "REAL", status = status, symbol = symbol, side = side, kind = "INTRADAY", setupId = null,
        timeframe = "15m", htfTrend = "UP", counterTrendReason = null, emotion = "CALM", checklistMask = 0xFF, chk = 8,
        equity = 2000.0, plannedEntry = 100.0, slInitial = 96.0, tp = 108.0, lev = 5.0, riskPctChosen = 0.01, riskPctCap = 0.01,
        gradeA = true, entryOrder = "LIMIT", isolated = true, slTpOnExchange = true, canMonitor = true,
        plan = PlanSnapshot(0.04, 2.0, 20.0, 500.0, 100.0, 0.05, 80.5, 0.195, 4.875), gateReasons = "", gatePlanOk = true,
        entryAllowed = true, gateAllowed = true, entry = 100.0, notional = 500.0, entryTime = 0, slCurrent = 96.0,
        createdAt = 0, updatedAt = 0,
    )

    @Test
    fun `symbols resolve by suffix removal and exact unique match`() {
        assertEquals("BTC", TradeLink.baseOf("btcusdt"))
        assertEquals("ETH", TradeLink.baseOf("ETH-PERP"))
        assertEquals("SOL", TradeLink.baseOf("SOLUSDT.P"))
        assertEquals("XRP", TradeLink.baseOf("XRPUSDC"))
        val coins = listOf(coin("bitcoin", "BTC", 100.0, 1), coin("a", "DUP", 1.0, 1), coin("b", "DUP", 1.0, 1))
        assertEquals("bitcoin", TradeLink.resolve("BTCUSDT", coins)!!.id)
        assertNull(TradeLink.resolve("DUPUSDT", coins), "an ambiguous symbol is not linked")
        assertNull(TradeLink.resolve("ZZZUSDT", coins))
    }

    @Test
    fun `levels and directions`() {
        val p = Params.DEFAULTS
        val t = trade()
        assertEquals(108.0, TradeLink.levelPrice(t, LinkLevel.TP, p))
        assertEquals(96.0, TradeLink.levelPrice(t, LinkLevel.STOP, p))
        assertEquals(104.0, TradeLink.levelPrice(t, LinkLevel.PARTIAL, p), "floating R = Partial_R_Lo (1R) at 104")
        assertTrue(TradeLink.reached(Side.LONG, LinkLevel.TP, 108.0, 108.1))
        assertFalse(TradeLink.reached(Side.LONG, LinkLevel.STOP, 96.0, 97.0))
        assertTrue(TradeLink.reached(Side.SHORT, LinkLevel.STOP, 104.0, 104.5))
        assertTrue(TradeLink.reached(Side.SHORT, LinkLevel.TP, 92.0, 91.0))
    }

    @Test
    fun `an alert fires once per level and is gone after close`() {
        val t = trade()
        var alerts = listOf(TradeAlertEntity("t1", "TP", 108.0, 0), TradeAlertEntity("t1", "STOP", 96.0, 0))
        var clock = 1_000_000L
        // below the take-profit: nothing
        assertTrue(TradeLink.evaluate(listOf(t), alerts, listOf(coin("bitcoin", "BTC", 105.0, clock)), clock).isEmpty())
        clock += 10_000
        val fire = TradeLink.evaluate(listOf(t), alerts, listOf(coin("bitcoin", "BTC", 108.5, clock)), clock)
        assertEquals(listOf(LinkLevel.TP), fire.map { it.level })
        // the caller marks it fired: the next rounds do not repeat it
        alerts = alerts.map { if (it.level == "TP") it.copy(firedAt = clock) else it }
        clock += 10_000
        assertTrue(TradeLink.evaluate(listOf(t), alerts, listOf(coin("bitcoin", "BTC", 110.0, clock)), clock).isEmpty())
        // a stale quote never fires
        assertTrue(TradeLink.evaluate(listOf(t), alerts, listOf(coin("bitcoin", "BTC", 90.0, clock - 120_000)), clock).isEmpty())
        // closed trades are ignored and request no symbols
        val closed = t.copy(status = "CLOSED")
        assertTrue(TradeLink.evaluate(listOf(closed), alerts, listOf(coin("bitcoin", "BTC", 90.0, clock)), clock).isEmpty())
        assertEquals(setOf("BTC"), TradeLink.symbols(listOf(t), alerts))
        assertTrue(TradeLink.symbols(listOf(closed), alerts).isEmpty())
    }
}
