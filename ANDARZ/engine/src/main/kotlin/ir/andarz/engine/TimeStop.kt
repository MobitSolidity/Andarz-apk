package ir.andarz.engine

import java.time.Duration
import java.time.Instant

/** Entry timeframe ids (`enums.timeframe`) and their length in minutes. */
object Timeframes {
    val MINUTES: Map<String, Int> = linkedMapOf(
        "1m" to 1, "3m" to 3, "5m" to 5, "15m" to 15, "30m" to 30, "1h" to 60, "4h" to 240, "1d" to 1440,
    )

    fun minutes(id: String): Int = MINUTES[id] ?: throw IllegalArgumentException("unknown timeframe $id")
}

/** Management helpers of 5.4: time stop (D-29), stop moves, break-even shortcut (D-30), floating R. */
object Management {
    const val BREAKEVEN_FEE_BUFFER = 0.001

    data class TimeStop(val elapsed: Long, val left: Long, val dueAt: Instant, val due: Boolean)

    /**
     * `elapsed = floor((now − entryTime) / timeframe)`, `left = max(0, Time_Stop_Candles − elapsed)`. Due when
     * the candles have passed, no partial exit exists and the stop is not yet at or beyond entry (D-29).
     */
    fun timeStop(
        side: Side, entry: Double, slCurrent: Double?, entryTime: Instant, timeframe: String, hasPartialExit: Boolean,
        now: Instant, p: Params,
    ): TimeStop {
        val tf = Timeframes.minutes(timeframe).toLong()
        val elapsed = Math.floorDiv(Duration.between(entryTime, now).toMinutes(), tf)
        val candles = p.timeStopCandles.toLong()
        val stopProtects = slCurrent != null && if (side == Side.LONG) slCurrent >= entry else slCurrent <= entry
        val dueAt = entryTime.plus(Duration.ofMinutes(candles * tf))
        return TimeStop(elapsed, maxOf(0, candles - elapsed), dueAt, elapsed >= candles && !hasPartialExit && !stopProtects)
    }

    /** A stop move toward profit is accepted at once; away (or removal, `newSl == null`) needs `V04`. */
    fun isAwayMove(side: Side, current: Double?, newSl: Double?): Boolean {
        if (newSl == null) return true
        if (current == null) return false
        return if (side == Side.LONG) newSl < current else newSl > current
    }

    /** Break-even shortcut: LONG `entry × 1.001`, SHORT `entry × 0.999`. */
    fun breakEven(side: Side, entry: Double): Double =
        if (side == Side.LONG) entry * (1 + BREAKEVEN_FEE_BUFFER) else entry * (1 - BREAKEVEN_FEE_BUFFER)

    /**
     * Floating P&L at a typed price (display only, never stored): the open remainder at that price plus the
     * realized legs; floating R against the initial risk.
     */
    fun floating(side: Side, entry: Double, sl: Double, notional: Double, legs: List<Leg>, price: Double): Pair<Double, Double?> {
        val sign = if (side == Side.LONG) 1 else -1
        val closedFraction = legs.sumOf { it.fraction }
        var pnl = sign * (price - entry) / entry * notional * (1 - closedFraction)
        for (l in legs) pnl += sign * (l.price - entry) / entry * notional * l.fraction
        val rrisk = notional * kotlin.math.abs(entry - sl) / entry
        return pnl to if (rrisk != 0.0) pnl / rrisk else null
    }

    /**
     * Price at which floating R of the whole position equals [r] when nothing has been exited (MK3 uses it for
     * the partial-exit level, `Partial_R_Lo`). `entry ± r × |entry − sl|`.
     */
    fun priceAtR(side: Side, entry: Double, sl: Double, r: Double): Double {
        val d = kotlin.math.abs(entry - sl)
        return if (side == Side.LONG) entry + r * d else entry - r * d
    }
}
