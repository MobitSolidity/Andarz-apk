package ir.andarz.app.platform

import ir.andarz.data.db.ReviewEntity
import ir.andarz.data.settings.Settings
import ir.andarz.engine.Account
import ir.andarz.engine.Dashboard
import ir.andarz.engine.DashboardStatus
import ir.andarz.engine.DashStatus
import ir.andarz.engine.Equity
import ir.andarz.engine.GovernorResult
import ir.andarz.engine.Lock
import ir.andarz.engine.LockCode
import ir.andarz.engine.LockNeed
import ir.andarz.engine.Params
import ir.andarz.engine.Snapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Share note (9.5), reminder planning and quiet hours (7). */
class PlatformLogicTest {
    private val template: String by lazy {
        val f = listOf("src/main/assets/content_fa.json", "app/src/main/assets/content_fa.json").map(::File).first { it.isFile }
        Json.parseToJsonElement(f.readText(Charsets.UTF_8)).jsonObject["learn"]!!.jsonObject["journal"]!!.jsonObject["quickTemplate"]!!.jsonPrimitive.content
    }

    @Test
    fun `share note field counts on the real template`() {
        assertEquals(listOf(1, 1, 1, 1, 1, 1, 2, 4, 4, 1, 0, 4, 1, 1, 1), ShareNote.fields(template).map { it.size })
    }

    @Test
    fun `share note fill keeps the separator and joins two-value fields`() {
        val values = List(15) { i -> ShareNote.fields(template)[i].map { listOf("v$i") } }.toMutableList()
        values[1] = listOf(listOf("BTCUSDT", "L"))
        val out = ShareNote.fill(template, values).split('\n')
        assertEquals(15, out.size)
        assertEquals(template.split('\n')[10].trim(), out[10])
        assertTrue(out[1].endsWith("BTCUSDT / L"))
        assertEquals(4, out[7].split("    ").size)
        // option text (the kind list on line 2) is dropped
        assertFalse(out[2].contains("|"))
    }

    private val zone = ZoneOffset.ofHoursMinutes(3, 30)
    private fun t(s: String) = OffsetDateTime.parse(s).toInstant()

    @Test
    fun `quiet hours defer to the window end, across midnight`() {
        val start = 23 * 60 + 30; val end = 7 * 60
        assertEquals(t("2026-10-15T07:00:00+03:30"), ReminderPlanner.outOfQuiet(t("2026-10-14T23:45:00+03:30"), zone, start, end))
        assertEquals(t("2026-10-15T07:00:00+03:30"), ReminderPlanner.outOfQuiet(t("2026-10-15T03:00:00+03:30"), zone, start, end))
        assertEquals(t("2026-10-14T21:00:00+03:30"), ReminderPlanner.outOfQuiet(t("2026-10-14T21:00:00+03:30"), zone, start, end))
        assertEquals(t("2026-10-14T14:00:00+03:30"), ReminderPlanner.outOfQuiet(t("2026-10-14T13:00:00+03:30"), zone, 12 * 60, 14 * 60))
    }

    @Test
    fun `open reminders stay inside the day window`() {
        val entry = t("2026-10-14T20:00:00+03:30")
        assertEquals(t("2026-10-15T08:00:00+03:30"), ReminderPlanner.nextOpenSlot(entry, t("2026-10-14T21:00:00+03:30"), zone))
        assertEquals(t("2026-10-14T14:00:00+03:30"), ReminderPlanner.nextOpenSlot(t("2026-10-14T10:00:00+03:30"), t("2026-10-14T11:00:00+03:30"), zone))
        assertEquals(t("2026-10-14T21:00:00+03:30"), ReminderPlanner.nextAt(t("2026-10-14T09:00:00+03:30"), zone, LocalTime.of(21, 0)))
    }

    private fun snap(locks: List<Lock>, now: Instant) = Snapshot(
        Equity(2000.0, 2000.0, 2000.0),
        Dashboard(0, 0.0, 0.0, 0.0, 0, 0.0, DashboardStatus(DashStatus.OK, DashStatus.OK, DashStatus.OK, DashStatus.OK, DashStatus.OK, DashStatus.OK)),
        GovernorResult(locks.isEmpty(), locks, 0.01, emptyList(), true, null, 0, 0, 0.0, 0.0),
        emptyList(), Params.DEFAULTS, now,
    )

    @Test
    fun `desired set follows state and toggles`() {
        val now = t("2026-10-14T09:00:00+03:30")
        val s = Settings(zoneId = "Asia/Tehran", notifAdvice = true)
        val cool = Lock(LockCode.COOLDOWN, now.plusSeconds(1800), emptyList())
        val dd = Lock(LockCode.DRAWDOWN, now.plusSeconds(86_400), listOf(LockNeed.REVIEW))
        val day = Lock(LockCode.DAILY_LOSS, now.plusSeconds(50_000), emptyList())
        val plan = ReminderPlanner.plan(snap(listOf(cool, dd, day), now), emptyList(), emptyList(), s, now)
        val names = plan.map { it.name }
        assertTrue("cooldown-end" in names)
        assertTrue(names.any { it.startsWith("lock-end-DAILY_LOSS-") })
        assertFalse(names.any { it.startsWith("lock-end-DRAWDOWN") }, "a lock with unmet needs has no end reminder")
        assertTrue("weekly-review" in names && "monthly-review" in names && "backup-reminder" in names && "advice-daily" in names)
        assertFalse("daily-close" in names, "no trades today and nothing open")
        // every instant is outside quiet hours
        plan.forEach { r -> assertEquals(r.at, ReminderPlanner.outOfQuiet(r.at, s.zone, s.quietStart, s.quietEnd)) }

        val off = s.copy(notifCooldown = false, notifReviews = false, notifBackup = false, notifAdvice = false)
        assertTrue(ReminderPlanner.plan(snap(listOf(cool), now), emptyList(), emptyList(), off, now).isEmpty())

        val weeklyDone = listOf(ReviewEntity("r", "WEEKLY", now.toEpochMilli(), "2026-10-10", "2026-10-16", "{}", "{}"))
        assertFalse("weekly-review" in ReminderPlanner.plan(snap(emptyList(), now), emptyList(), weeklyDone, s, now).map { it.name })
        assertTrue(ReminderPlanner.stillDue("weekly-review", plan))
        assertEquals(Account.REAL, Account.valueOf("REAL"))
    }
}
