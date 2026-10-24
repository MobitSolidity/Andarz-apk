package ir.andarz.app.platform

/**
 * The quick-note template of 9.5, filled by line index so no Persian label appears in code. A token that
 * contains a delimiter (`:` or the Arabic question mark) is a field: its text up to and including the
 * last delimiter is kept. Tokens without a delimiter are option text and are dropped.
 */
object ShareNote {
    private const val QMARK = '\u061F'
    private val SPLIT = Regex(" {2,}")

    /** The field labels of each template line (empty for the separator line). */
    fun fields(template: String): List<List<String>> = template.split('\n').map { line ->
        line.trim().split(SPLIT).mapNotNull { tok ->
            val last = maxOf(tok.lastIndexOf(':'), tok.lastIndexOf(QMARK))
            if (last < 0) null else tok.substring(0, last + 1)
        }
    }

    /**
     * Fills [values] (one list per line, in field order) into the template. A line with no fields is copied
     * verbatim; a field with more than one value (line 1) joins them with " / ".
     */
    fun fill(template: String, values: List<List<List<String>>>): String {
        val lines = template.split('\n')
        val f = fields(template)
        return lines.indices.joinToString("\n") { i ->
            if (f[i].isEmpty()) lines[i].trim()
            else f[i].withIndex().joinToString("    ") { (j, label) ->
                val v = values.getOrNull(i)?.getOrNull(j).orEmpty().joinToString(" / ")
                if (v.isEmpty()) label else "$label $v"
            }
        }
    }

    /**
     * The 9.5 values of one trade, line by line. [u] formats numbers per the user's digit setting;
     * every label comes from content through [u].
     */
    fun values(ui: ir.andarz.app.ui.Ui, t: ir.andarz.data.db.TradeEntity, setupName: String?): List<List<List<String>>> {
        // An explicit export the user asked for: never masked (D-91).
        val u = ui.unmasked()
        val entryAt = t.entryTime?.let { u.dateTime(java.time.Instant.ofEpochMilli(it)) } ?: ""
        val exitAvg = if (t.legs.isEmpty()) null else t.legs.sumOf { it.price * it.fraction } / t.legs.sumOf { it.fraction }
        val closed = t.status == "CLOSED"
        return listOf(
            listOf(listOf(entryAt)),
            listOf(listOf(u.symbol(t.symbol), u.enumLabel("side", t.side))),
            listOf(listOf(u.enumLabel("kind", t.kind))),
            listOf(listOf(setupName ?: "")),
            listOf(listOf(u.enumLabel("htfTrend", t.htfTrend))),
            listOf(listOf(u.int(t.chk))),
            listOf(listOf(u.money(t.equity)), listOf(u.pct(t.riskPctChosen, 2))),
            listOf(listOf(u.price(t.entry ?: t.plannedEntry)), listOf(u.price(t.slInitial)), listOf(u.price(t.tp)), listOf(u.num(t.plan.rr))),
            listOf(listOf(u.mult(t.lev)), listOf(u.money(t.plan.margin)), listOf(u.money(t.notional ?: t.plan.notionalMax)), listOf(u.price(t.plan.liq))),
            listOf(listOf(u.enumLabel("emotion", t.emotion))),
            emptyList(),
            if (closed) listOf(listOf(u.price(exitAvg)), listOf(u.money(t.fee + t.legs.sumOf { it.fee })), listOf(u.signedMoney(t.net)), listOf(u.rChip(t.r))) else emptyList(),
            if (closed) listOf(listOf(u.t(if (t.ok == true) "common.yes" else "common.no"))) else emptyList(),
            listOf(listOf(t.mistake?.let { u.enumLabel("mistake", it) } ?: "")),
            listOf(listOf(t.lesson ?: "")),
        )
    }
}
