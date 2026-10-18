package ir.andarz.engine

/** One live-parameter binding of a constitution clause (3.11). */
data class Ref(val param: String, val literal: String, val template: String, val fmt: String)

/**
 * Live constitution clauses (3.11). Port of `render_clause` and `fmt_ref`.
 *
 * The Persian words for one … ten (`fmt = word`) are wording, so they are not in this file (law 3): the
 * caller passes them in [numberWords], index 0 = one. The app reads them from its content file and the
 * vector runner from a test resource; both copy the `WORDS` table of `andarz_ref.py` (D-38).
 */
object Clause {
    /** The derived value `@SL_Floor = Risk_Pct / (Margin_Max × Lev_Max)`. */
    fun slFloor(p: Params): Double = p.riskPct / (p.marginMax * p.levMax)

    fun formatRef(v: Double, fmt: String, numberWords: List<String>): String = when (fmt) {
        "pct" -> Fmt.number(XlMath.round(v * 100, 2), 2, fa = true, group = false, trim = true) + Fmt.FA_PERCENT
        "int" -> Fmt.toFaDigits(Math.round(v).toString())
        "dec" -> Fmt.number(v, 2, fa = true, group = false, trim = true)
        "word" -> {
            val i = v.toInt()
            if (v == Math.floor(v) && i in 1..numberWords.size) numberWords[i - 1]
            else Fmt.number(v, 2, fa = true, group = false, trim = true)
        }
        else -> throw IllegalArgumentException("unknown clause format $fmt")
    }

    /** Replace the first occurrence of each ref's literal by its template filled with the live value. */
    fun render(text: String, refs: List<Ref>, p: Params, numberWords: List<String>): String {
        var out = text
        for (r in refs) {
            val v = if (r.param.startsWith("@")) derived(r.param, p) else p[r.param]
            val at = out.indexOf(r.literal)
            require(at >= 0) { "literal of ${r.param} not found in the clause" }
            out = out.substring(0, at) + r.template.replace("{v}", formatRef(v, r.fmt, numberWords)) +
                out.substring(at + r.literal.length)
        }
        return out
    }

    private fun derived(key: String, p: Params): Double = when (key) {
        "@SL_Floor" -> slFloor(p)
        else -> throw IllegalArgumentException("unknown derived value $key")
    }
}
