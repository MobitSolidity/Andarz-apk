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
import kotlin.math.abs

/** What one evaluation round decided. The dispatcher turns it into notifications and rings. */
sealed interface AlertAction {
    /** A one-shot target price was crossed. */
    data class Target(val coin: Coin, val target: Double, val above: Boolean, val ring: Boolean) : AlertAction

    /** A sharp move over the user's window. [pct] is signed. */
    data class Move(val coin: Coin, val pct: Double, val threshold: Double, val ring: Boolean) : AlertAction

    /** Moves beyond the first three of a round, folded into one notification (March). */
    data class Summary(val rest: List<Move>) : AlertAction
}

/** The actions of a round plus the targets to clear from the rules (they are one-shot). */
data class AlertRound(val actions: List<AlertAction>, val clearTargets: List<Pair<String, Boolean>>)

/**
 * The pure half of March's `PriceAlerts` (MERGE_MAP: "split into a pure evaluator and a dispatcher").
 * Every rule of K5.2 is March's, line for line:
 *
 * - targets are one-shot and independent of the sharp-move switch;
 * - sharp moves: window 5/15/60 min from the session samples, 24 h from the feed; rise from the window
 *   low, drop from the window high; direction up/down/both; per-coin threshold override; scope watch-list
 *   or all; USDT/Toman when included, starred or customised; a cooldown per coin and direction;
 * - ring modes off / targets / all; at most three individual moves per round plus one summary, and only
 *   the strongest move of a round rings.
 *
 * It keeps March's memory (last fire per coin+direction, fired targets) and nothing else. K6.5: it never
 * touches the journal, the gate, a lock or a parameter.
 */
class AlertEvaluator {
    private val lastFired = HashMap<String, Long>()
    private val firedTargets = HashSet<String>()

    @Synchronized
    fun evaluate(
        state: MarketState,
        history: PriceHistory,
        s: AlertSettings,
        rules: Map<String, CoinRule>,
        watch: Set<String>,
        now: Long,
    ): AlertRound {
        val actions = ArrayList<AlertAction>()
        val clears = ArrayList<Pair<String, Boolean>>()
        val hits = ArrayList<AlertAction.Move>()
        for (c in state.coins) {
            if (c.price <= 0) continue
            val rule = rules[c.id]

            // 1) Target prices: one-shot, independent of the sharp-move switch.
            rule?.above?.let { target ->
                if (c.price >= target && firedTargets.add(c.id + "|a|" + target)) {
                    actions += AlertAction.Target(c, target, above = true, ring = rings(s, target = true))
                    clears += c.id to true
                }
            }
            rule?.below?.let { target ->
                if (c.price <= target && firedTargets.add(c.id + "|b|" + target)) {
                    actions += AlertAction.Target(c, target, above = false, ring = rings(s, target = true))
                    clears += c.id to false
                }
            }

            // 2) Sharp moves.
            if (!s.enabled) continue
            val custom = rule?.pct != null
            val inScope = when {
                c.id == USDT_IRT_ID -> s.includeUsdt || c.id in watch || custom
                s.scope == AlertScope.ALL -> true
                else -> c.id in watch || custom
            }
            if (!inScope) continue
            val (rise, drop) = move(c, history, s.window, now) ?: continue
            val pick = when (s.direction) {
                AlertDirection.UP -> rise
                AlertDirection.DOWN -> drop
                AlertDirection.BOTH -> if (rise >= -drop) rise else drop
            }
            val threshold = (rule?.pct ?: s.thresholdPct).toDouble()
            if (pick == 0.0 || abs(pick) < threshold) continue
            val key = c.id + if (pick > 0) "|up" else "|down"
            if (now - (lastFired[key] ?: Long.MIN_VALUE / 2) < s.cooldownMin * 60_000L) continue
            lastFired[key] = now
            hits += AlertAction.Move(c, pick, threshold, ring = false)
        }
        if (hits.isNotEmpty()) {
            hits.sortByDescending { abs(it.pct) }
            val ringTop = rings(s, target = false)
            hits.take(MAX_PER_ROUND).forEachIndexed { i, h -> actions += h.copy(ring = ringTop && i == 0) }
            if (hits.size > MAX_PER_ROUND) actions += AlertAction.Summary(hits.drop(MAX_PER_ROUND))
        }
        return AlertRound(actions, clears)
    }

    /** (rise %, drop %) inside the window: rise from the window low, drop from the window high. */
    private fun move(c: Coin, history: PriceHistory, w: AlertWindow, now: Long): Pair<Double, Double>? {
        if (w == AlertWindow.H24) return maxOf(c.change24h, 0.0) to minOf(c.change24h, 0.0)
        val (lo, hi) = history.range(c.id, now - w.minutes * 60_000L) ?: return null
        val min = minOf(lo, c.price)
        val max = maxOf(hi, c.price)
        if (min <= 0 || max <= 0) return null
        return (c.price - min) / min * 100 to (c.price - max) / max * 100
    }

    companion object {
        const val MAX_PER_ROUND = 3

        fun rings(s: AlertSettings, target: Boolean): Boolean = when (s.ring) {
            RingMode.OFF -> false
            RingMode.TARGETS -> target
            RingMode.ALL -> true
        }
    }
}
