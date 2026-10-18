package ir.andarz.engine

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Number display and input parsing (3.10). Port of `format_number`, `format_signed`, `format_percent`,
 * `normalize_input` and `to_fa_digits` of the reference.
 *
 * Law 3: no character of U+0600–U+06FF may appear in a `.kt` file, so every Persian/Arabic digit and
 * separator is written as a `\u` escape here. Nothing uses the default `Locale` (Appendix C trap 1).
 */
object Fmt {
    private const val FA_ZERO = '\u06F0'      // Persian digit zero; the ten digits are contiguous
    private const val AR_ZERO = '\u0660'      // Arabic-Indic digit zero
    const val FA_DECIMAL = '\u066B'           // Arabic decimal separator
    const val FA_GROUP = '\u066C'             // Arabic thousands separator
    const val MINUS = '\u2212'                // minus sign, used in both digit modes
    const val FA_PERCENT = '\u066A'           // Arabic percent sign
    private const val ZWNJ = '\u200C'
    private const val LRI = '\u2066'          // left-to-right isolate
    private const val PDI = '\u2069'          // pop directional isolate

    private val NUMBER = Regex("^[+-]?([0-9]+\\.?[0-9]*|\\.[0-9]+)$")

    /** ASCII digits → Persian digits; everything else unchanged. */
    fun toFaDigits(s: String): String = buildString(s.length) {
        for (c in s) append(if (c in '0'..'9') FA_ZERO + (c - '0') else c)
    }

    /** Persian and Arabic-Indic digits → ASCII digits; everything else unchanged. */
    fun toLatinDigits(s: String): String = buildString(s.length) {
        for (c in s) append(
            when (c) {
                in FA_ZERO..(FA_ZERO + 9) -> '0' + (c - FA_ZERO)
                in AR_ZERO..(AR_ZERO + 9) -> '0' + (c - AR_ZERO)
                else -> c
            },
        )
    }

    /** `formatNumber(x, decimals, fa, group, trim)`; rounds with [XlMath.round], negative with U+2212. */
    fun number(x: Double, decimals: Int = 2, fa: Boolean = true, group: Boolean = true, trim: Boolean = false): String {
        val r = XlMath.round(x, decimals)
        val neg = r < 0
        val s = BigDecimal(kotlin.math.abs(r)).setScale(decimals, RoundingMode.HALF_EVEN).toPlainString()
        var ip = s.substringBefore('.')
        var fp = if ('.' in s) s.substringAfter('.') else ""
        if (group) ip = groupThousands(ip)
        if (trim) fp = fp.trimEnd('0')
        var out = ip + (if (fp.isNotEmpty()) ".$fp" else "")
        if (fa) out = toFaDigits(out).replace(',', FA_GROUP).replace('.', FA_DECIMAL)
        val isZero = s.toDouble() == 0.0
        return (if (neg && !isZero) MINUS.toString() else "") + out
    }

    /** `formatSigned`: `+` for positive, U+2212 for negative, nothing for zero. */
    fun signed(x: Double, decimals: Int = 2, fa: Boolean = true): String {
        val r = XlMath.round(x, decimals)
        val s = number(kotlin.math.abs(r), decimals, fa, group = true)
        return (if (r > 0) "+" else if (r < 0) MINUS.toString() else "") + s
    }

    /** `formatPercent`: multiplies by 100 (rounded with xlRound) and appends the percent sign. */
    fun percent(x: Double, decimals: Int = 1, fa: Boolean = true, signed: Boolean = false): String {
        val r = XlMath.round(x * 100, decimals)
        val core = if (signed) signed(r, decimals, fa) else number(r, decimals, fa, group = false)
        return core + if (fa) FA_PERCENT else '%'
    }

    /** User text → number, or null (3.10, 17 vectors). */
    fun normalizeInput(text: String?): Double? {
        if (text == null) return null
        var s = text.trim()
        if (s.isEmpty()) return null
        s = toLatinDigits(s)
        s = s.replace(MINUS, '-').replace(ZWNJ.toString(), "").replace(" ", "")
        s = s.replace(FA_GROUP.toString(), "").replace(FA_DECIMAL, '.')
        if (',' in s && '.' in s) {
            s = s.replace(",", "")
        } else if (',' in s) {
            val idx = s.lastIndexOf(',')
            val head = s.substring(0, idx)
            val tail = s.substring(idx + 1)
            s = head.replace(",", "") + if (tail.length != 3 || head.isEmpty()) ".$tail" else tail
        }
        if (!NUMBER.matches(s)) return null
        return s.toDouble()
    }

    /** Wrap [text] in a left-to-right isolate so it reads correctly inside RTL text (6.3, trap 9). */
    fun isolate(text: String): String = "$LRI$text$PDI"

    private fun groupThousands(digits: String): String {
        if (digits.length <= 3) return digits
        val sb = StringBuilder()
        val first = digits.length % 3
        if (first > 0) sb.append(digits, 0, first)
        var i = first
        while (i < digits.length) {
            if (sb.isNotEmpty()) sb.append(',')
            sb.append(digits, i, i + 3)
            i += 3
        }
        return sb.toString()
    }
}
