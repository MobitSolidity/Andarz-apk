package ir.andarz.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Excel-style rounding (3.1): round half away from zero on the *shortest decimal representation* of the
 * double, exactly as `andarz_ref.xl_round` does with `Decimal(repr(x)).quantize(..., ROUND_HALF_UP)`.
 */
object XlMath {

    /**
     * The shortest decimal string that parses back to [x], and among strings of that length the one closest
     * to the exact binary value. That is Python's `repr`.
     *
     * The search rounds the *exact* value of the double (`BigDecimal(x)`) to 1, 2, … 17 significant digits
     * and keeps the first one that round-trips. The closest p-digit decimal is the only p-digit candidate
     * that can lie inside the double's rounding interval, so the first hit is the right one. Unlike a
     * `String.format("%.pe")` loop this does not depend on how a JDK or an ART version formats doubles
     * (Appendix C trap 2), and the round-trip check uses `Double.parseDouble`, which is correctly rounded on
     * both (D-38).
     */
    fun shortest(x: Double): String {
        require(x.isFinite()) { "shortest() needs a finite double" }
        if (x == 0.0) return "0"
        val exact = BigDecimal(x)
        // Round-tripping is monotone in p: the closest (p+1)-digit decimal is never farther from x than the
        // closest p-digit one, so once p digits round-trip, every larger p does too. 17 always does.
        // That allows a binary search (about 4 steps instead of up to 17).
        var lo = 1
        var hi = 17
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (roundTrips(exact, mid, x)) hi = mid else lo = mid + 1
        }
        return exact.round(MathContext(lo, RoundingMode.HALF_EVEN)).toString()
    }

    private fun roundTrips(exact: BigDecimal, p: Int, x: Double): Boolean =
        exact.round(MathContext(p, RoundingMode.HALF_EVEN)).toString().toDouble() == x

    /** `ROUND(x, d)` of Excel. `RoundingMode.HALF_UP` rounds ties away from zero (Appendix C trap 3). */
    fun round(x: Double, d: Int): Double =
        BigDecimal(shortest(x)).setScale(d, RoundingMode.HALF_UP).toString().toDouble()

    /** Null-propagating variant: the reference returns `None` for `None`. */
    fun roundOrNull(x: Double?, d: Int): Double? = x?.let { round(it, d) }
}
