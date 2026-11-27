package ir.andarz.app.ui.market

import ir.andarz.engine.Fmt
import ir.andarz.market.calendar.EconEvent
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The pure parts of the News screen of the canvas: the days, the same-time groups, the countdown tiles, the figures. */
class NewsLogicTest {

    private val zone = ZoneId.of("+03:30")

    private fun at(iso: String): Long = OffsetDateTime.parse(iso).toInstant().toEpochMilli()

    private fun event(id: String, iso: String, impact: Int) = EconEvent(id, at(iso), "US", id, impact)

    private val list = listOf(
        event("late", "2026-10-07T21:30:00+03:30", 3),
        event("a", "2026-10-06T16:00:00+03:30", 1),
        event("b", "2026-10-06T16:00:00+03:30", 3),
        event("early", "2026-10-05T17:30:00+03:30", 2),
        event("c", "2026-10-06T16:45:00+03:30", 2),
    )

    @Test
    fun `days start at the given day, run in time order and group by local day`() {
        val days = groupByDay(list, onlyHigh = false, from = LocalDate.of(2026, 10, 6), zone = zone)
        assertEquals(listOf(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 7)), days.map { it.first })
        assertEquals(listOf("a", "b", "c"), days[0].second.map { it.id })
        assertEquals(listOf("late"), days[1].second.map { it.id })
    }

    @Test
    fun `a release is placed on its local day, not its UTC day`() {
        val e = event("night", "2026-10-05T21:00:00Z", 2)
        val days = groupByDay(listOf(e), onlyHigh = false, from = LocalDate.of(2026, 10, 5), zone = zone)
        assertEquals(LocalDate.of(2026, 10, 6), days.single().first)
    }

    @Test
    fun `the high impact filter keeps impact three only and drops days that become empty`() {
        val days = groupByDay(list, onlyHigh = true, from = LocalDate.of(2026, 10, 5), zone = zone)
        assertEquals(listOf(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 7)), days.map { it.first })
        assertEquals(listOf("b"), days[0].second.map { it.id })
        assertTrue(groupByDay(list, true, LocalDate.of(2026, 10, 8), zone).isEmpty())
    }

    @Test
    fun `releases at one moment form one slot`() {
        val slots = slotsOf(list.filter { it.id in setOf("a", "b", "c") })
        assertEquals(2, slots.size)
        assertEquals(listOf("a", "b"), slots[0].events.map { it.id })
        assertEquals(listOf("c"), slots[1].events.map { it.id })
        assertTrue(slots[0].at < slots[1].at)
    }

    @Test
    fun `the countdown shows days and hours, hours and minutes, or minutes and seconds`() {
        val h = 3_600_000L
        val m = 60_000L
        val s = 1_000L
        val days = countdownOf(3 * 24 * h + 4 * h + 59 * m)
        assertEquals(listOf(3L, 4L), listOf(days.first, days.second))
        assertEquals(listOf('d', 'h'), listOf(days.firstUnit, days.secondUnit))
        assertFalse(days.hot)

        val hours = countdownOf(2 * h + 25 * m + 40 * s)
        assertEquals(listOf(2L, 25L), listOf(hours.first, hours.second))
        assertEquals(listOf('h', 'm'), listOf(hours.firstUnit, hours.secondUnit))
        assertFalse(hours.hot)

        val hour = countdownOf(h)
        assertEquals(listOf(1L, 0L), listOf(hour.first, hour.second))
        assertFalse(hour.hot, "exactly one hour is still hours and minutes")

        val last = countdownOf(59 * m + 59 * s)
        assertEquals(listOf(59L, 59L), listOf(last.first, last.second))
        assertEquals(listOf('m', 's'), listOf(last.firstUnit, last.secondUnit))
        assertTrue(last.hot)

        val none = countdownOf(0L)
        assertEquals(listOf(0L, 0L), listOf(none.first, none.second))
        assertTrue(none.hot)
    }

    @Test
    fun `a release figure follows the digit setting and keeps its unit`() {
        val fa = Fmt.toFaDigits("3") + Fmt.FA_DECIMAL + Fmt.toFaDigits("75") + Fmt.FA_PERCENT
        assertEquals(Fmt.isolate(fa), releaseFigure("3.75%", fa = true))
        assertEquals(Fmt.isolate("3.75%"), releaseFigure("3.75%", fa = false))
        assertEquals(Fmt.isolate(Fmt.toFaDigits("228") + "K"), releaseFigure("228K", fa = true))
        assertEquals(Fmt.isolate(Fmt.MINUS + "0.3%"), releaseFigure("-0.3%", fa = false))
    }
}
