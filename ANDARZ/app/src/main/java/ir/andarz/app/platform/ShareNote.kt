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
}
