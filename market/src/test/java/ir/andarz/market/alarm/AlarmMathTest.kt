package ir.andarz.market.alarm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The pure parts of the alarm screen: the time left for the bar, and which parts of a text are figures. */
class AlarmMathTest {

    private val total = AlarmRinger.AUTO_STOP_MS

    @Test
    fun `the time left starts at the whole span and runs down to zero`() {
        val at = 1_000_000L
        assertEquals(total, alarmLeftMs(at, at))
        assertEquals(total - 20_000L, alarmLeftMs(at, at + 20_000L))
        assertEquals(0L, alarmLeftMs(at, at + total))
    }

    @Test
    fun `the time left never leaves the span`() {
        val at = 1_000_000L
        assertEquals(0L, alarmLeftMs(at, at + total + 5_000L), "after the auto-stop it stays at zero")
        assertEquals(total, alarmLeftMs(at, at - 5_000L), "a clock that went back does not grow the bar past full")
    }

    private fun parts(text: String) = figureRanges(text).map { text.substring(it.first, it.last + 1) }

    @Test
    fun `numbers keep their sign, separators and unit as one run`() {
        val inWord = "\u062F\u0631"
        assertEquals(listOf("+3.3%", "24"), parts(inWord + " +3.3% " + inWord + " 24"))
        assertEquals(listOf("\$112,040"), parts(inWord + " \$112,040"))
        assertEquals(listOf("2:40"), parts(inWord + " 2:40"))
        assertEquals(listOf("\u2212" + "0.3%"), parts(inWord + " \u2212" + "0.3%"))
    }

    @Test
    fun `persian digits and their separators are one run`() {
        val n = "\u06F3\u066B\u06F3\u066A"
        val big = "\u06F1\u06F1\u06F2\u066C\u06F0\u06F4\u06F0"
        assertEquals(listOf(n), parts(n))
        assertEquals(listOf(big), parts("\u2066" + big + "\u2069"))
    }

    @Test
    fun `latin letters are a run of their own and plain words have none`() {
        assertEquals(listOf("BTC", "5"), parts("BTC 5"))
        assertTrue(figureRanges("").isEmpty())
        assertTrue(figureRanges("\u062A\u0648\u0642\u0641").isEmpty())
    }
}
