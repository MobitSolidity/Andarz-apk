package ir.andarz.market.alarm

/**
 * Milliseconds until the auto-stop of an alarm that started at [at]: never below 0 and never above
 * [AlarmRinger.AUTO_STOP_MS]. It drives the bar and the "auto stop in 2:40" line of the alarm screen. A plain
 * function, so a JVM test pins it without the ringer's handler and wake lock.
 */
internal fun alarmLeftMs(at: Long, now: Long): Long =
    (at + AlarmRinger.AUTO_STOP_MS - now).coerceIn(0L, AlarmRinger.AUTO_STOP_MS)

private const val DIGIT = "0-9\\u0660-\\u0669\\u06F0-\\u06F9"

/**
 * A number with its sign or currency in front, its separators inside (decimal, thousands, colon, slash, dot, comma)
 * and its unit behind (percent, multiplication sign), or a run of Latin letters. Same rule as the app's own Text.
 */
private val figureRun = Regex(
    "[+\\-\\u2212\\$]?[$DIGIT]+(?:[\\u066B\\u066C.,:/][$DIGIT]+)*[%\\u066A\\u00D7]?|[A-Za-z]+",
)

/** The character ranges of [text] that belong to the figures family: numbers with their signs and units, and Latin runs. */
internal fun figureRanges(text: String): List<IntRange> = figureRun.findAll(text).map { it.range }.toList()
