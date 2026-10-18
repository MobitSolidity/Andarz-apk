package ir.andarz.app.ui.market

import ir.andarz.engine.Fmt
import ir.andarz.market.model.Coin
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10

/**
 * March's `ui/Format.kt` rebuilt on top of [Fmt] (D-M14, K7). No `String.format` with a locale, no ICU
 * calendar classes: digits follow the digit setting, times follow the app's zone setting.
 *
 * Kept from March exactly:
 * - price tiers: >= 1000 -> 0 decimals, >= 1 -> 2, >= 0.01 -> 4, below that enough decimals for three
 *   significant digits (at most 10); zero or less shows as 0; thousands grouped from 1 upwards;
 * - market cap with T (2 decimals), B and M (1 decimal), otherwise grouped whole dollars;
 * - percent with sign **and** arrow (K6.7: never color alone).
 *
 * Every number is an LTR isolate (Appendix C trap 9). The Toman suffix is a content string
 * (`mkt.unit.tomanSuffix`), passed in so this class holds no Persian (law 3).
 */
class MarketFmt(
    private val fa: Boolean,
    private val tomanSuffix: String,
    private val zone: ZoneId,
) {
    private val percent: Char get() = if (fa) Fmt.FA_PERCENT else '%'

    /** The bare price number of March's `fmtPrice`, not isolated. */
    fun priceNumber(p: Double): String {
        if (p <= 0) return digits("0")
        val d = priceDecimals(p)
        return Fmt.number(p, d, fa, group = p >= 1, trim = false)
    }

    /** `$64,250` style, isolated. */
    fun usd(p: Double): String = Fmt.isolate("$" + priceNumber(p))

    /** Toman amount with the content suffix (March's `fmtToman`). */
    fun toman(v: Double): String = Fmt.isolate(priceNumber(v)) + tomanSuffix

    /** Price as the user wants to see it. USDT/IRT is always shown in Toman (March's `coinPrice`). */
    fun coinPrice(c: Coin, toman: Boolean, rate: Double?): String = when {
        c.isToman -> toman(c.price)
        toman && rate != null && rate > 0 -> toman(c.price * rate)
        else -> usd(c.price)
    }

    /** The second line under the big price in the coin sheet (March: `$x` or `~ x T`). */
    fun altPrice(c: Coin, toman: Boolean, rate: Double?): String? = when {
        c.isToman -> null
        toman -> usd(c.price)
        rate != null && rate > 0 -> APPROX + toman(c.price * rate)
        else -> null
    }

    /** Market cap with T/B/M (March's `fmtCap`). */
    fun cap(v: Double): String {
        val body = when {
            v >= 1e12 -> Fmt.number(v / 1e12, 2, fa, group = false) + "T"
            v >= 1e9 -> Fmt.number(v / 1e9, 1, fa, group = false) + "B"
            v >= 1e6 -> Fmt.number(v / 1e6, 1, fa, group = false) + "M"
            else -> Fmt.number(v, 0, fa, group = true)
        }
        return Fmt.isolate("$" + body)
    }

    /** `▲ 1.23%` / `▼ 0.40%` (March's `fmtPct`). The arrow carries the direction, not the color. */
    fun pct(v: Double): String =
        Fmt.isolate((if (v >= 0) UP else DOWN) + " " + Fmt.number(abs(v), 2, fa, group = false) + percent)

    /** BTC dominance, one decimal (March: `%.1f%%`). */
    fun share(v: Double): String = Fmt.isolate(Fmt.number(v, 1, fa, group = false) + percent)

    /** A plain integer with the digit setting (rank, Fear & Greed value). */
    fun int(n: Int): String = digits(n.toString())

    /** March's header clock (`faClock`): HH:mm:ss of the last data, in the app's zone. */
    fun clock(ms: Long): String {
        val z = Instant.ofEpochMilli(ms).atZone(zone)
        return Fmt.isolate(digits(pad(z.hour) + ":" + pad(z.minute) + ":" + pad(z.second)))
    }

    private fun digits(s: String): String = if (fa) Fmt.toFaDigits(s) else s

    private fun pad(n: Int): String = if (n < 10) "0$n" else n.toString()

    companion object {
        const val UP: String = "\u25B2"
        const val DOWN: String = "\u25BC"
        private const val APPROX = "\u2248 "

        /** March's tiers. */
        fun priceDecimals(p: Double): Int = when {
            p >= 1000 -> 0
            p >= 1 -> 2
            p >= 0.01 -> 4
            else -> (3 - floor(log10(p)).toInt()).coerceAtMost(10)
        }
    }
}
