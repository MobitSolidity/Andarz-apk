package ir.andarz.app.ui.market

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.andarz.app.content.ContentRepository
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.components.EmptyState
import ir.andarz.app.ui.components.Text
import ir.andarz.app.ui.rememberNowMillis
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui
import ir.andarz.engine.Fmt
import ir.andarz.market.calendar.CalendarData
import ir.andarz.market.calendar.CalendarStatus
import ir.andarz.market.calendar.EconEvent
import ir.andarz.market.calendar.ReleaseTimes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Releases by local day from [from] on, in time order. [onlyHigh] keeps impact 3 only. Days without a release do
 * not appear, and days before [from] are left out: the list starts with today, as on the canvas.
 */
internal fun groupByDay(events: List<EconEvent>, onlyHigh: Boolean, from: LocalDate, zone: ZoneId): List<Pair<LocalDate, List<EconEvent>>> =
    events.filter { !onlyHigh || it.impact == 3 }
        .sortedBy { it.at }
        .groupBy { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }
        .filterKeys { !it.isBefore(from) }
        .toList()

/** The releases of one moment, so a day can say "2 events at the same time" once instead of on every row. */
internal class Slot(val at: Long, val events: List<EconEvent>)

internal fun slotsOf(events: List<EconEvent>): List<Slot> =
    events.sortedBy { it.at }.groupBy { it.at }.map { (at, list) -> Slot(at, list) }

/**
 * The two tiles of the countdown with the unit letter of each (`d`, `h`, `m`, `s`): days and hours from a day on,
 * hours and minutes inside the day, minutes and seconds in the last hour, which is also when it counts as [hot].
 */
internal class Countdown(val first: Long, val firstUnit: Char, val second: Long, val secondUnit: Char) {
    val hot: Boolean get() = firstUnit == 'm'
}

internal fun countdownOf(ms: Long): Countdown {
    val p = ReleaseTimes.split(ms)
    return when {
        p[0] > 0 -> Countdown(p[0], 'd', p[1], 'h')
        p[1] > 0 -> Countdown(p[1], 'h', p[2], 'm')
        else -> Countdown(p[2], 'm', p[3], 's')
    }
}

/** A previous or forecast value of the calendar asset ("3.75%", "228K") with digits and separators per setting, isolated. */
internal fun releaseFigure(raw: String, fa: Boolean): String {
    val digits = if (fa) Fmt.toFaDigits(raw).replace('.', Fmt.FA_DECIMAL).replace('%', Fmt.FA_PERCENT) else Fmt.toLatinDigits(raw)
    return Fmt.isolate(if (digits.startsWith('-')) Fmt.MINUS + digits.drop(1) else digits)
}

/** "Wednesday 6 Aban": the weekday, day and month of [d] without the year. */
internal fun dayName(u: Ui, d: LocalDate): String = u.date(d).substringBeforeLast(' ')

/** "6 Aban": day and month only. */
internal fun shortDate(u: Ui, d: LocalDate): String = u.date(d, withWeekday = false).substringBeforeLast(' ')

/**
 * News (March's `ui/NewsScreen.kt`, REIMPLEMENT) on the canvas: the next event with its countdown and its two
 * reminders, the impact filter, the releases by day, and the expired card when the shipped list has run out
 * (K6.10). Local only: it works with Market off.
 */
@Composable
internal fun NewsScreen(
    content: ContentRepository,
    data: CalendarData,
    reminded: Set<String>,
    onToggle: (EconEvent) -> Unit,
    onClock: (EconEvent) -> Boolean,
) {
    val u = ui()
    val clock = rememberNowMillis(1_000, aligned = true)
    // Only the answer is read here, so the screen is not redrawn every second.
    val valid by remember(data) { derivedStateOf { data.status(clock.value) == CalendarStatus.VALID } }
    if (valid) {
        NewsList(content, u, data, clock, reminded, onToggle, onClock)
    } else {
        NewsExpired(content, u, data)
    }
}

@Composable
private fun NewsList(
    content: ContentRepository,
    u: Ui,
    data: CalendarData,
    clock: State<Long>,
    reminded: Set<String>,
    onToggle: (EconEvent) -> Unit,
    onClock: (EconEvent) -> Boolean,
) {
    var onlyHigh by rememberSaveable { mutableStateOf(false) }
    val today by remember(u.zone) { derivedStateOf { u.localDate(Instant.ofEpochMilli(clock.value)) } }
    val days = remember(data, onlyHigh, today, u.zone) { groupByDay(data.events, onlyHigh, today, u.zone) }
    val lastDay = remember(data, u.zone) { u.localDate(Instant.ofEpochMilli(data.validUntil)) }
    // These two change only when a release passes, so the rows are not redrawn every second.
    val passedAt by remember(data) { derivedStateOf { data.events.lastOrNull { it.at <= clock.value }?.at ?: Long.MIN_VALUE } }
    val nextId by remember(data) { derivedStateOf { ReleaseTimes.nextRelease(data.events, clock.value)?.id } }
    val next = remember(data, nextId) { data.events.firstOrNull { it.id == nextId } }
    val same = remember(data, next) { if (next == null) 0 else ReleaseTimes.sameTime(data.events, next) }
    val total = days.sumOf { it.second.size }
    val c = LocalAndarzColors.current

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (next != null) {
            item(key = "next") {
                NextEventCard(
                    content = content, u = u, next = next, same = same, clock = clock, on = next.id in reminded,
                    onToggle = { onToggle(next) }, onClock = { onClock(next) },
                )
            }
        }
        item(key = "filter") {
            FilterRow(content, u, onlyHigh, { onlyHigh = it }, total, lastDay)
        }
        if (days.isEmpty()) {
            item(key = "none") { EmptyState(content.ui(if (onlyHigh) "app.news.emptyHigh" else "app.news.emptyAll")) }
        }
        days.forEach { (day, list) ->
            item(key = "d-$day") {
                DaySection(
                    content = content, u = u, day = day, events = list, isToday = day == today, isLast = day == lastDay,
                    passedAt = passedAt, nextId = nextId, reminded = reminded, onToggle = onToggle,
                )
            }
        }
        item(key = "foot") {
            Text(
                u.t("app.news.footer", "n" to u.int(ReleaseTimes.LEAD_MINUTES)),
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}

/** The impact filter at the start edge and what the list holds at the end edge. */
@Composable
private fun FilterRow(content: ContentRepository, u: Ui, onlyHigh: Boolean, onChange: (Boolean) -> Unit, total: Int, lastDay: LocalDate) {
    val c = LocalAndarzColors.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterOption(!onlyHigh, content.ui("common.all")) { onChange(false) }
            FilterOption(onlyHigh, content.ui("app.news.impact.high")) { onChange(true) }
        }
        if (total > 0) {
            Text(
                u.t(if (total == 1) "app.news.count.one" else "app.news.count", "n" to u.int(total), "date" to shortDate(u, lastDay)),
                style = MaterialTheme.typography.labelMedium,
                color = c.muted,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** March's `shortLeft`: "3d 04h", "04:05:06" or "05:06", digits per setting, LTR isolate. */
internal fun shortLeft(u: Ui, ms: Long): String {
    val (d, h, m, s) = ReleaseTimes.split(ms).toList()
    fun p(n: Long) = n.toString().padStart(2, '0')
    val raw = when {
        d > 0 -> d.toString() + "d " + p(h) + "h"
        h > 0 -> p(h) + ":" + p(m) + ":" + p(s)
        else -> p(m) + ":" + p(s)
    }
    return Fmt.isolate(u.digits(raw))
}
