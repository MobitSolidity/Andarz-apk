package ir.andarz.app.market

import ir.andarz.data.db.TradeAlertEntity
import ir.andarz.data.db.TradeEntity
import ir.andarz.engine.Management
import ir.andarz.engine.Params
import ir.andarz.engine.Side
import ir.andarz.market.model.Coin

/** The three trade levels of K9.1 (b). */
enum class LinkLevel { TP, PARTIAL, STOP }

/**
 * Pure helpers of the MK3 integration (K9.1 b-d). Journal data never enters `:market`: `:app` reads the
 * market state and the trades side by side (D-M21). Market prices never enter the engine (D-M06).
 */
object TradeLink {
    private val SUFFIXES = listOf("USDT", "USDC", "USD", "PERP", ".P")

    /** `BTCUSDT`, `BTC-PERP`, `BTCUSDT.P` -> `BTC`: suffixes removed repeatedly, separators trimmed. */
    fun baseOf(symbol: String): String {
        var s = symbol.trim().uppercase(java.util.Locale.ROOT)
        var changed = true
        while (changed) {
            changed = false
            for (suf in SUFFIXES) if (s.length > suf.length && s.endsWith(suf)) { s = s.removeSuffix(suf).trimEnd('-', '_', '/'); changed = true }
        }
        return s
    }

    /** The market coin of a trade symbol: an exact, unique symbol match in the top 200, else null (mkt.link.nocoin). */
    fun resolve(symbol: String, coins: List<Coin>): Coin? {
        val base = baseOf(symbol)
        val hits = coins.filter { !it.isToman && it.symbol.equals(base, ignoreCase = true) }
        return hits.singleOrNull()
    }

    /** The price of each level: take-profit, the partial-exit level (floating R = Partial_R_Lo, 5.4) and the stop. */
    fun levelPrice(t: TradeEntity, level: LinkLevel, p: Params): Double {
        val entry = t.entry ?: t.plannedEntry
        return when (level) {
            LinkLevel.TP -> t.tp
            LinkLevel.STOP -> t.slCurrent ?: t.slInitial
            LinkLevel.PARTIAL -> Management.priceAtR(Side.valueOf(t.side), entry, t.slInitial, p.partialRLo)
        }
    }

    /** True once the price has reached the level in the direction that matters for the trade's side. */
    fun reached(side: Side, level: LinkLevel, levelPrice: Double, price: Double): Boolean {
        val up = when (level) { LinkLevel.TP, LinkLevel.PARTIAL -> side == Side.LONG; LinkLevel.STOP -> side == Side.SHORT }
        return if (up) price >= levelPrice else price <= levelPrice
    }

    data class Fire(val trade: TradeEntity, val level: LinkLevel, val price: Double)

    /**
     * One evaluation round: armed, unfired alerts of OPEN trades whose resolved coin price reached the level.
     * Each fires once; the caller marks it fired before posting.
     */
    fun evaluate(trades: List<TradeEntity>, alerts: List<TradeAlertEntity>, coins: List<Coin>, nowMs: Long, maxAgeMs: Long = 60_000): List<Fire> {
        val open = trades.filter { it.status == "OPEN" }.associateBy { it.id }
        val out = ArrayList<Fire>()
        for (a in alerts) {
            if (a.firedAt != null) continue
            val t = open[a.tradeId] ?: continue
            val coin = resolve(t.symbol, coins) ?: continue
            if (!fresh(coin, nowMs, maxAgeMs)) continue
            val level = runCatching { LinkLevel.valueOf(a.level) }.getOrNull() ?: continue
            if (reached(Side.valueOf(t.side), level, a.price, coin.price)) out += Fire(t, level, coin.price)
        }
        return out
    }

    /** The live chip and the watcher use only fresh quotes: a tick younger than [maxAgeMs] (K9.1 c). */
    fun fresh(coin: Coin, nowMs: Long, maxAgeMs: Long = 60_000): Boolean = coin.tickAt > 0 && nowMs - coin.tickAt <= maxAgeMs

    /** Base symbols the watcher asks the hub to stream (K6.4 d). */
    fun symbols(trades: List<TradeEntity>, alerts: List<TradeAlertEntity>): Set<String> {
        val armed = alerts.filter { it.firedAt == null }.map { it.tradeId }.toSet()
        return trades.filter { it.status == "OPEN" && it.id in armed }.map { baseOf(it.symbol) }.toSet()
    }
}
