package ir.andarz.market.calendar

import java.io.File
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** MK2: calendar loader (valid, expired, empty, broken), reminder time math, boot restore via a fake scheduler. */
class CalendarTest {

    private fun ms(iso: String) = OffsetDateTime.parse(iso).toInstant().toEpochMilli()

    private val json = """
        {"validUntil":"2026-10-29T23:59:59+03:30","events":[
          {"id":"2026-10-cpi","at":"2026-10-14T16:00:00+03:30","country":"US","title":"CPI","impact":3},
          {"id":"2026-10-ppi","at":"2026-10-15T16:00:00+03:30","country":"US","title":"PPI","impact":2,"previous":"0.1","forecast":"0.2"},
          {"id":"2026-10-nfp","at":"2026-10-02T16:00:00+03:30","country":"US","title":"NFP","impact":3},
          {"id":"2026-10-unemp","at":"2026-10-02T16:00:00+03:30","country":"US","title":"Unemployment","impact":3},
          {"id":"bad-impact","at":"2026-10-03T16:00:00+03:30","country":"US","title":"x","impact":7},
          {"id":"bad-time","at":"not a time","country":"US","title":"x","impact":1},
          {"id":"2026-10-cpi","at":"2026-10-20T16:00:00+03:30","country":"US","title":"dup","impact":1}
        ]}
    """.trimIndent()

    @Test
    fun `the loader sorts, skips bad rows and duplicate ids`() {
        val d = CalendarLoader.parse(json)
        assertEquals(listOf("2026-10-nfp", "2026-10-unemp", "2026-10-cpi", "2026-10-ppi"), d.events.map { it.id })
        assertEquals("0.2", d.events.last().forecast)
        assertEquals(ms("2026-10-29T23:59:59+03:30"), d.validUntil)
    }

    @Test
    fun `status is valid, then expired after validUntil, and empty without events`() {
        val d = CalendarLoader.parse(json)
        assertEquals(CalendarStatus.VALID, d.status(ms("2026-10-10T00:00:00+03:30")))
        assertEquals(CalendarStatus.EXPIRED, d.status(ms("2026-10-30T00:00:00+03:30")))
        assertEquals(CalendarStatus.EMPTY, CalendarLoader.parse("""{"validUntil":"2027-01-01T00:00:00+03:30","events":[]}""").status(0))
        assertEquals(CalendarData.NONE, CalendarLoader.parse("not json"))
        assertEquals(CalendarStatus.EMPTY, CalendarData.NONE.status(0))
    }

    @Test
    fun `the shipped asset parses, has unique ids and is valid until 7 Aban 1405`() {
        val f = listOf(File("src/main/assets/market_calendar.json"), File("market/src/main/assets/market_calendar.json")).first { it.isFile }
        val d = CalendarLoader.parse(f.readText())
        assertEquals(15, d.events.size)
        assertEquals(d.events.size, d.events.map { it.id }.toSet().size)
        // 2026-10-29 is 7 Aban 1405 (K6.10).
        assertEquals(ms("2026-10-29T23:59:59+03:30"), d.validUntil)
        assertTrue(d.events.all { it.impact in 1..3 && it.country == "US" })
    }

    // ---------------------------------------------------------------- reminder time math

    @Test
    fun `the reminder fires ten minutes before, never in the past`() {
        val e = EconEvent("x", ms("2026-10-14T16:00:00+03:30"), "US", "CPI", 3)
        assertEquals(ms("2026-10-14T15:50:00+03:30"), ReleaseTimes.reminderAt(e, ms("2026-10-14T12:00:00+03:30")))
        assertNull(ReleaseTimes.reminderAt(e, ms("2026-10-14T15:50:00+03:30")))
        assertNull(ReleaseTimes.reminderAt(e, ms("2026-10-14T15:55:00+03:30")))
    }

    @Test
    fun `the clock-app alarm is offered only within the next 24 hours`() {
        val at = ms("2026-10-14T16:00:00+03:30")
        assertTrue(ReleaseTimes.clockAppAllowed(at, ms("2026-10-13T16:00:00+03:30")), "fire 23 h 50 min ahead")
        assertTrue(!ReleaseTimes.clockAppAllowed(at, ms("2026-10-13T15:49:00+03:30")), "more than 24 h ahead")
        assertTrue(!ReleaseTimes.clockAppAllowed(at, ms("2026-10-14T15:49:30+03:30")), "less than a minute before the fire time")
    }

    @Test
    fun `the board picks the next high-impact release and counts simultaneous ones`() {
        val d = CalendarLoader.parse(json)
        val now = ms("2026-10-01T00:00:00+03:30")
        val next = ReleaseTimes.nextRelease(d.events, now)!!
        assertEquals("2026-10-nfp", next.id)
        assertEquals(2, ReleaseTimes.sameTime(d.events, next))
        // After the last high-impact one, the next of any impact.
        assertEquals("2026-10-ppi", ReleaseTimes.nextRelease(d.events, ms("2026-10-14T17:00:00+03:30"))!!.id)
        assertNull(ReleaseTimes.nextRelease(d.events, ms("2026-11-01T00:00:00+03:30")))
    }

    @Test
    fun `split gives days hours minutes seconds`() {
        assertEquals(listOf(1L, 2L, 3L, 4L), ReleaseTimes.split(((26 * 60 + 3) * 60 + 4) * 1000L).toList())
        assertEquals(listOf(0L, 0L, 0L, 0L), ReleaseTimes.split(-5).toList())
    }

    // ---------------------------------------------------------------- boot restore through a fake scheduler

    private class FakeScheduler : ReminderScheduler {
        val armed = LinkedHashMap<String, Long>()
        override fun schedule(id: String, title: String, at: Long) { armed[id] = at }
        override fun cancel(id: String) { armed.remove(id) }
    }

    @Test
    fun `boot restore re-arms saved reminders that are still ahead`() {
        val d = CalendarLoader.parse(json)
        val s = FakeScheduler()
        val now = ms("2026-10-10T00:00:00+03:30")
        val armed = Reminders.restore(s, d, setOf("2026-10-nfp", "2026-10-cpi", "2026-10-ppi", "2025-01-gone"), now)
        // NFP is past; an id no longer in the list is ignored, so an old reminder never comes back.
        assertEquals(listOf("2026-10-cpi", "2026-10-ppi"), armed)
        assertEquals(ms("2026-10-14T15:50:00+03:30"), s.armed["2026-10-cpi"])
    }

    @Test
    fun `arming a past release does nothing`() {
        val s = FakeScheduler()
        val e = EconEvent("x", 1_000L, "US", "t", 3)
        assertTrue(!Reminders.arm(s, e, 2_000_000L))
        assertTrue(s.armed.isEmpty())
    }
}
