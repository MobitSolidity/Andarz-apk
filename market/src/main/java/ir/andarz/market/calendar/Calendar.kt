package ir.andarz.market.calendar

import org.json.JSONObject
import java.time.OffsetDateTime

/**
 * One economic release (March's `EconEvent`). The title is Persian and comes from the calendar asset,
 * which K7 allows to hold Persian; :market never builds a sentence from it.
 */
data class EconEvent(
    /** Unique per occurrence (e.g. `2026-10-cpi`), so a refreshed list never re-arms an old reminder. */
    val id: String,
    val at: Long,
    /** ISO 3166 alpha-2; the UI shows it as a small chip instead of March's flag emoji (K6.7). */
    val country: String,
    val title: String,
    /** 1 (low) .. 3 (high). */
    val impact: Int,
    val previous: String = "",
    val forecast: String = "",
    /** The English title from the asset's `titleEn` (D-90); empty when absent. */
    val titleEn: String = "",
) {
    /** The title in the display language: English when asked for and present, Persian otherwise. */
    fun titleFor(english: Boolean): String = if (english && titleEn.isNotEmpty()) titleEn else title
}

/** The calendar as loaded from `market_calendar.json` (K6.10). */
data class CalendarData(
    val validUntil: Long,
    /** Sorted by time. */
    val events: List<EconEvent>,
) {
    /** EXPIRED after [validUntil] or when there is nothing to show; News then shows `mkt.cal.expired.*`. */
    fun status(now: Long): CalendarStatus = when {
        events.isEmpty() -> CalendarStatus.EMPTY
        now > validUntil -> CalendarStatus.EXPIRED
        else -> CalendarStatus.VALID
    }

    companion object {
        val NONE = CalendarData(0L, emptyList())
    }
}

enum class CalendarStatus { VALID, EXPIRED, EMPTY }

/**
 * Parses the calendar asset. A malformed row is skipped, a malformed file gives [CalendarData.NONE]: the
 * calendar is informational and must never break the app (K6.10).
 */
object CalendarLoader {
    const val ASSET = "market_calendar.json"

    fun parse(json: String): CalendarData = try {
        val o = JSONObject(json)
        val until = o.optString("validUntil").takeIf { it.isNotEmpty() }?.let { epoch(it) } ?: 0L
        val arr = o.optJSONArray("events")
        val out = ArrayList<EconEvent>()
        val seen = HashSet<String>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val id = e.optString("id").takeIf { it.isNotEmpty() } ?: continue
                if (!seen.add(id)) continue
                val at = runCatching { epoch(e.getString("at")) }.getOrNull() ?: continue
                val impact = e.optInt("impact", 0).takeIf { it in 1..3 } ?: continue
                val title = e.optString("title").takeIf { it.isNotEmpty() } ?: continue
                out += EconEvent(
                    id = id,
                    at = at,
                    country = e.optString("country"),
                    title = title,
                    impact = impact,
                    previous = e.optString("previous"),
                    forecast = e.optString("forecast"),
                    titleEn = e.optString("titleEn"),
                )
            }
        }
        CalendarData(until, out.sortedBy { it.at })
    } catch (_: Exception) {
        CalendarData.NONE
    }

    private fun epoch(iso: String): Long = OffsetDateTime.parse(iso).toInstant().toEpochMilli()
}

/** The pure time rules of March's reminders and Clock-app alarms (K5.2), tested on the JVM. */
object ReleaseTimes {
    /** March's lead: the reminder fires ten minutes before the release. */
    const val LEAD_MINUTES: Int = 10
    private const val LEAD_MS = LEAD_MINUTES * 60_000L
    private const val DAY_MS = 86_400_000L

    /** When the reminder of [e] fires, or null when that moment has passed. */
    fun reminderAt(e: EconEvent, now: Long): Long? = (e.at - LEAD_MS).takeIf { it > now }

    /** The Clock app only takes hour:minute, so it is offered for releases in the next 24 hours (March). */
    fun clockAppAllowed(at: Long, now: Long): Boolean {
        val fire = at - LEAD_MS
        return fire > now + 60_000L && fire - now < DAY_MS
    }

    /** The countdown board's release: the next high-impact one, else the next of any impact (March). */
    fun nextRelease(events: List<EconEvent>, now: Long): EconEvent? =
        events.firstOrNull { it.impact == 3 && it.at > now } ?: events.firstOrNull { it.at > now }

    /** How many releases share the board's time (March's "+ n at the same time"). */
    fun sameTime(events: List<EconEvent>, e: EconEvent): Int = events.count { it.at == e.at }

    /** Days, hours, minutes, seconds of a non-negative duration (March's `split`). */
    fun split(ms: Long): LongArray {
        val t = (ms / 1000).coerceAtLeast(0)
        return longArrayOf(t / 86400, (t % 86400) / 3600, (t % 3600) / 60, t % 60)
    }
}
