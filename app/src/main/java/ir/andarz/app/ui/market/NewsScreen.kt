package ir.andarz.app.ui.market

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import ir.andarz.app.ui.components.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.andarz.app.R
import ir.andarz.app.content.ContentRepository
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.components.EmptyState
import ir.andarz.app.ui.components.SectionCard
import ir.andarz.app.ui.components.TagChip
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui
import ir.andarz.engine.Fmt
import ir.andarz.market.calendar.CalendarData
import ir.andarz.market.calendar.CalendarStatus
import ir.andarz.market.calendar.EconEvent
import ir.andarz.market.calendar.ReleaseTimes
import kotlinx.coroutines.delay
import java.time.Instant

private const val HOUR = 3_600_000L
private const val DAY = 86_400_000L

/** Groups the calendar by local day, keeping time order; [onlyHigh] keeps impact 3 only (March). */
internal fun groupByDay(events: List<EconEvent>, onlyHigh: Boolean, u: Ui): List<Pair<java.time.LocalDate, List<EconEvent>>> =
    events.filter { !onlyHigh || it.impact == 3 }
        .groupBy { u.localDate(Instant.ofEpochMilli(it.at)) }
        .toList()

/**
 * News (March's `ui/NewsScreen.kt`, REIMPLEMENT): the countdown to the next release, the impact filter,
 * releases by day with a reminder bell and the Clock-app alarm for releases within 24 h, the "released"
 * state, and the expired state when the shipped list has run out (K6.10). Local only: it works with
 * Market off.
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
    val now by ir.andarz.app.ui.rememberNowMillis(1_000, aligned = true)
    val status = data.status(now)
    if (status != CalendarStatus.VALID) {
        val until = if (data.validUntil > 0) u.date(u.localDate(Instant.ofEpochMilli(data.validUntil)), withWeekday = false) else content.ui("common.dash")
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            SectionCard(content.ui("mkt.cal.expired.title")) {
                Text(content.ui("mkt.cal.expired.body", "date" to until), style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }
    var onlyHigh by rememberSaveable { mutableStateOf(false) }
    val groups = remember(data, onlyHigh, u.zone, u.fa) { groupByDay(data.events, onlyHigh, u) }
    val today = u.localDate(Instant.ofEpochMilli(now))
    LazyColumn(Modifier.fillMaxWidth()) {
        item { Board(content, u, data.events, now) }
        item {
            Row(Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !onlyHigh, onClick = { onlyHigh = false }, label = { Text(content.ui("common.all")) })
                FilterChip(selected = onlyHigh, onClick = { onlyHigh = true }, label = { Text(content.ui("mkt.news.high")) })
            }
        }
        groups.forEach { (day, list) ->
            item(key = "h-$day") {
                val isToday = day == today
                Text(
                    (if (isToday) content.ui("common.today") + SEP else "") + u.date(day),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isToday) LocalAndarzColors.current.caution else LocalAndarzColors.current.textMuted,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
                )
            }
            item(key = "g-$day") {
                Surface(Modifier.padding(horizontal = 12.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column {
                        list.forEachIndexed { i, e ->
                            if (i > 0) HorizontalDivider(color = LocalAndarzColors.current.divider)
                            EventRow(content, u, e, now, e.id in reminded, onToggle = { onToggle(e) }, onClock = { onClock(e) })
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(20.dp)) {
                Text(content.ui("mkt.news.footer"), style = MaterialTheme.typography.bodySmall, color = LocalAndarzColors.current.textMuted)
                Spacer(Modifier.height(8.dp))
                Text(
                    content.ui("mkt.cal.validUntil", "date" to u.date(u.localDate(Instant.ofEpochMilli(data.validUntil)), withWeekday = false)),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAndarzColors.current.textMuted,
                )
            }
        }
    }
}

@Composable
private fun Board(content: ContentRepository, u: Ui, events: List<EconEvent>, now: Long) {
    val next = ReleaseTimes.nextRelease(events, now) ?: return
    val left = next.at - now
    val hot = left < HOUR
    val (d, h, m, s) = ReleaseTimes.split(left).toList()
    val cells = if (d > 0) {
        listOf(d to "mkt.news.cd.d", h to "mkt.news.cd.h", m to "mkt.news.cd.m", s to "mkt.news.cd.s")
    } else {
        listOf(h to "mkt.news.cd.h", m to "mkt.news.cd.m", s to "mkt.news.cd.s")
    }
    val same = ReleaseTimes.sameTime(events, next)
    val c = LocalAndarzColors.current
    Surface(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = c.surfaceHigh) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(content.ui("mkt.news.next"), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                Text(u.date(u.localDate(Instant.ofEpochMilli(next.at))) + SEP + u.time(Instant.ofEpochMilli(next.at)), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            }
            // The countdown reads left to right like a clock, even in RTL (March).
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Top) {
                    cells.forEachIndexed { i, (v, key) ->
                        if (i > 0) Text(":", style = MaterialTheme.typography.displaySmall, color = c.textMuted, modifier = Modifier.padding(horizontal = 4.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(u.digits(v.toString().padStart(2, '0')), style = MaterialTheme.typography.displayMedium, color = if (hot) c.caution else MaterialTheme.colorScheme.onSurface)
                            Text(content.ui(key), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                        }
                    }
                }
            }
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                CountryChip(next.country)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(next.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (same > 1) Text(content.ui("mkt.news.sameTime", "n" to u.int(same - 1)), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                }
            }
        }
    }
}

/** K6.7: the ISO country code in a small chip, instead of March's flag emoji. */
@Composable
private fun CountryChip(code: String) {
    if (code.isEmpty()) return
    Text(
        Fmt.isolate(code),
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** March's three impact bars, filled up to [impact]. The count is also the content description. */
@Composable
private fun ImpactBars(impact: Int) {
    val c = LocalAndarzColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..3) {
            Spacer(
                Modifier.size(width = 4.dp, height = (6 + i * 3).dp).clip(RoundedCornerShape(2.dp))
                    .background(if (i <= impact) (if (impact == 3) c.redline else c.caution) else c.divider),
            )
        }
    }
}

@Composable
private fun EventRow(
    content: ContentRepository,
    u: Ui,
    e: EconEvent,
    now: Long,
    on: Boolean,
    onToggle: () -> Unit,
    onClock: () -> Boolean,
) {
    val ctx = LocalContext.current
    val c = LocalAndarzColors.current
    val left = e.at - now
    val past = left <= 0
    val hot = !past && left < HOUR
    val soon = !past && left < DAY
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(u.time(Instant.ofEpochMilli(e.at)), style = MaterialTheme.typography.titleSmall)
            CountryChip(e.country)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ImpactBars(e.impact)
                Spacer(Modifier.width(8.dp))
                Text(e.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (past) c.textMuted else MaterialTheme.colorScheme.onSurface)
            }
            if (e.forecast.isNotEmpty() || e.previous.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 2.dp)) {
                    if (e.forecast.isNotEmpty()) Text(content.ui("mkt.news.forecast") + Fmt.isolate(e.forecast), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    if (e.previous.isNotEmpty()) Text(content.ui("mkt.news.previous") + Fmt.isolate(e.previous), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                }
            }
        }
        if (past) {
            Text(content.ui("mkt.news.released"), style = MaterialTheme.typography.bodySmall, color = c.textMuted, modifier = Modifier.padding(end = 10.dp))
        } else {
            Column(horizontalAlignment = Alignment.End) {
                TagChip(shortLeft(u, left), if (hot) c.caution else if (soon) c.info else c.textMuted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ReleaseTimes.clockAppAllowed(e.at, now)) {
                        IconButton(onClick = {
                            if (!onClock()) Toast.makeText(ctx, content.ui("mkt.news.noClock"), Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.size(48.dp)) {
                            Icon(painterResource(R.drawable.ic_alarm_add), contentDescription = content.ui("mkt.news.clock"), tint = c.info)
                        }
                    }
                    IconButton(onClick = onToggle, modifier = Modifier.size(48.dp)) {
                        if (on) {
                            Icon(painterResource(R.drawable.ic_bell_active), contentDescription = content.ui("mkt.news.reminder"), tint = c.caution)
                        } else {
                            Icon(Icons.Filled.Notifications, contentDescription = content.ui("mkt.news.reminder"), tint = c.textMuted)
                        }
                    }
                }
            }
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
