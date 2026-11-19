package ir.andarz.app.ui.today

import ir.andarz.app.ui.theme.Tone
import ir.andarz.engine.DashStatus
import ir.andarz.engine.ResolvedTrade
import java.time.Instant

/*
 * What the Today screen decides before it draws: the tone of the day, the word under each dial, the equity curve and
 * the cut of a money figure into its big and small parts. Pure functions, unit-tested; the composables only draw.
 */

/** Where a dial turns from turquoise to saffron: two thirds of its cap. */
internal const val NEAR_RATIO = 2.0 / 3.0

/**
 * The tone of the day, which the lamp, the glow and the verdict strip all share: a lock or a closed limit means
 * [Tone.LOCKED], an allowed day with a limit close to its cap (or a risk reduction in force) [Tone.CAUTION],
 * anything else [Tone.ALLOWED].
 */
internal fun todayTone(mayEnter: Boolean, locked: Boolean, near: Boolean): Tone = when {
    locked || !mayEnter -> Tone.LOCKED
    near -> Tone.CAUTION
    else -> Tone.ALLOWED
}

/** A limit is red when its status says the line was crossed; a full position count only stops new entries. */
internal fun isRed(status: DashStatus): Boolean = status != DashStatus.OK && status != DashStatus.POSITIONS_FULL

/** What a dial says in one word. */
internal enum class GaugeWord { FREE, NEAR, FULL }

internal fun gaugeWord(status: DashStatus, ratio: Double): GaugeWord = when {
    status != DashStatus.OK -> GaugeWord.FULL
    ratio >= NEAR_RATIO -> GaugeWord.NEAR
    else -> GaugeWord.FREE
}

/** True when the dial is not red but already close to its cap (or its count is full): the day is cautious. */
internal fun isNear(status: DashStatus, ratio: Double): Boolean = !isRed(status) && (status != DashStatus.OK || ratio >= NEAR_RATIO)

/** At most [max] points of a series, always keeping the first and the last. */
internal fun downsample(values: List<Double>, max: Int): List<Double> {
    if (max < 2 || values.size <= max) return values
    return List(max) { i -> values[(i.toLong() * (values.size - 1) / (max - 1)).toInt()] }
}

/**
 * The equity after each closed real trade, oldest first, starting from what the account held before the first of
 * them and ending exactly at [equityNow] (deposits and withdrawals are not trades, so the start is derived from the
 * end, not the other way round). Empty when no real trade has closed yet: the screen then draws no curve.
 */
internal fun equityCurve(trades: List<ResolvedTrade>, now: Instant, equityNow: Double, max: Int = 32): List<Double> {
    val nets = trades.asSequence()
        .filter { it.isReal && it.net != null }
        .mapNotNull { t -> t.exitTime?.takeIf { !it.isAfter(now) }?.let { it to t.net!! } }
        .sortedBy { it.first }
        .map { it.second }
        .toList()
    if (nets.isEmpty()) return emptyList()
    var acc = equityNow - nets.sum()
    val out = ArrayList<Double>(nets.size + 1)
    out += acc
    for (n in nets) { acc += n; out += acc }
    return downsample(out, max)
}

/** The index of the highest point of a series (the last one when several tie), or -1 when it is empty. */
internal fun peakIndex(values: List<Double>): Int {
    var best = -1
    for (i in values.indices) if (best < 0 || values[i] >= values[best]) best = i
    return best
}

/** A formatted money figure cut at its decimal separator: "$2,039" and ".40". */
internal class MoneyParts(val major: String, val minor: String)

internal fun splitMoney(formatted: String): MoneyParts {
    // '.' for Latin digits, U+066B for Arabic-Indic and Persian ones
    val i = formatted.lastIndexOfAny(charArrayOf('.', '\u066B'))
    return if (i < 0) MoneyParts(formatted, "") else MoneyParts(formatted.substring(0, i), formatted.substring(i))
}
