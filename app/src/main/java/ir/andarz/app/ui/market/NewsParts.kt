package ir.andarz.app.ui.market

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.andarz.app.content.ContentRepository
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.components.ArchShapes
import ir.andarz.app.ui.components.ArchTile
import ir.andarz.app.ui.components.Text
import ir.andarz.app.ui.components.archRing
import ir.andarz.app.ui.components.archSurface
import ir.andarz.app.ui.components.figure
import ir.andarz.app.ui.components.withNumerals
import ir.andarz.app.ui.icons.Glyph
import ir.andarz.app.ui.icons.GlyphIcon
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.engine.Fmt
import ir.andarz.market.calendar.CalendarData
import ir.andarz.market.calendar.EconEvent
import ir.andarz.market.calendar.ReleaseTimes
import java.time.Instant
import java.time.LocalDate

private val HeroShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
private val SectionShape = RoundedCornerShape(22.dp)
private val HeaderShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
private val LastRowShape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
private val ExpiredShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
private val ChipShape = ArchShapes.arch(22.dp, 12.dp)
private val TileShape = ArchShapes.arch(26.dp, 14.dp)
private val RowBarHeights = listOf(4.dp, 7.dp, 10.dp)
private val PillBarHeights = listOf(4.dp, 8.dp, 12.dp)

/** Text above this scale does not fit the fixed size of the countdown tiles. */
private const val TILE_TEXT_CAP = 1.15f

/** Peyda Black at [size] sp, the `kv` style of the boards. */
@Composable
private fun kv(size: Int): TextStyle = MaterialTheme.typography.titleSmall.copy(fontSize = size.sp, lineHeight = (size * 1.5f).sp)

private fun impactLabel(content: ContentRepository, level: Int): String =
    content.ui(
        when {
            level >= 3 -> "app.news.impact.high"
            level == 2 -> "app.news.impact.mid"
            else -> "app.news.impact.low"
        },
    )

// ---- the next event

/** The next release: when it is, its name and impact, the countdown in two arch tiles, and its two reminders. */
@Composable
internal fun NextEventCard(
    content: ContentRepository,
    u: Ui,
    next: EconEvent,
    same: Int,
    clock: State<Long>,
    on: Boolean,
    onToggle: () -> Unit,
    onClock: () -> Boolean,
) {
    val c = LocalAndarzColors.current
    val ctx = LocalContext.current
    val now = clock.value
    val cd = countdownOf((next.at - now).coerceAtLeast(0L))
    val title = next.titleFor(u.ltr)
    val stacked = isStacked()
    val at = Instant.ofEpochMilli(next.at)
    val day = u.localDate(at)
    val dayText = if (day == u.localDate(Instant.ofEpochMilli(now))) content.ui("common.today") else dayName(u, day)
    val more = if (same > 1) SEP + content.ui("mkt.news.sameTime", "n" to u.int(same - 1)) else ""
    val caption = content.ui("app.news.next") + SEP + dayText + " " + u.time(at) + more
    val spoken = u.t(
        "app.news.left",
        "a" to u.int(cd.first.toInt()), "ua" to content.ui("mkt.news.cd.${cd.firstUnit}"),
        "b" to u.int(cd.second.toInt()), "ub" to content.ui("mkt.news.cd.${cd.secondUnit}"),
    )
    val lead = u.int(ReleaseTimes.LEAD_MINUTES)
    val stateText = content.ui(if (on) "app.news.on" else "app.news.off")
    val remindText = u.t("app.news.remind.lead", "title" to title, "n" to lead)
    val clockOk = ReleaseTimes.clockAppAllowed(next.at, now)
    val clockText = u.t("app.news.clock.for", "title" to title)

    Column(
        Modifier.fillMaxWidth()
            .archSurface(HeroShape, c.sf1, c.divider, c.edge)
            .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 10.dp),
    ) {
        if (stacked) {
            NextInfo(caption, title, next.impact, impactLabel(content, next.impact), next.country, Modifier.fillMaxWidth().heightIn(min = 72.dp))
            Spacer(Modifier.height(10.dp))
            CountdownTiles(cd, content, u, spoken)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NextInfo(caption, title, next.impact, impactLabel(content, next.impact), next.country, Modifier.weight(1f).heightIn(min = 72.dp))
                CountdownTiles(cd, content, u, spoken)
            }
        }
        Spacer(Modifier.height(8.dp))
        HeroActions(
            stacked = stacked,
            showSecond = clockOk,
            first = { m ->
                ActionChip(
                    modifier = m, active = on, glyph = if (on) Glyph.BELL_FILL else Glyph.BELL,
                    label = u.t("app.news.lead", "n" to lead),
                    action = Modifier
                        .toggleable(value = on, role = Role.Switch, onValueChange = { onToggle() })
                        .semantics { contentDescription = remindText; stateDescription = stateText },
                )
            },
            second = { m ->
                ActionChip(
                    modifier = m, active = false, glyph = Glyph.ALARM_CLOCK,
                    label = content.ui("app.news.clock"),
                    action = Modifier
                        .clickable(role = Role.Button, onClick = {
                            if (!onClock()) Toast.makeText(ctx, content.ui("mkt.news.noClock"), Toast.LENGTH_SHORT).show()
                        })
                        .semantics { contentDescription = clockText },
                )
            },
        )
    }
}

@Composable
private fun NextInfo(caption: String, title: String, impact: Int, impactText: String, country: String, modifier: Modifier) {
    val c = LocalAndarzColors.current
    Column(modifier, verticalArrangement = Arrangement.SpaceBetween) {
        Text(caption, style = MaterialTheme.typography.labelMedium, color = c.muted)
        Text(title, style = kv(16), color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            CountryChip(country, 24.dp)
            ImpactPill(impact, impactText)
        }
    }
}

/** The two reminders side by side, or one under the other on a narrow screen or with a large font. */
@Composable
private fun HeroActions(
    stacked: Boolean,
    showSecond: Boolean,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
) {
    if (stacked) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            first(Modifier.fillMaxWidth())
            if (showSecond) second(Modifier.fillMaxWidth())
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            first(Modifier.weight(1f))
            if (showSecond) second(Modifier.weight(1f))
        }
    }
}

/**
 * One of the two action chips of the card: lapis when it is on, a plain tonal chip otherwise. [action] adds the
 * click or toggle after the surface is drawn, so the press shows on top of it.
 */
@Composable
private fun ActionChip(modifier: Modifier, active: Boolean, glyph: Glyph, label: String, action: Modifier) {
    val c = LocalAndarzColors.current
    val ink = if (active) c.lapis else c.text
    Row(
        modifier
            .heightIn(min = 48.dp)
            .archSurface(ChipShape, if (active) c.lapisC else c.alt, if (active) c.lapisLine else c.divider, if (active) Color.Transparent else c.edge)
            .then(action)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(glyph, null, tint = ink, size = 18.dp, strokeWidth = 2f)
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700),
            color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** The two tiles of the countdown, drawn at a capped text size and read as one picture. */
@Composable
private fun CountdownTiles(cd: Countdown, content: ContentRepository, u: Ui, description: String) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, minOf(d.fontScale, TILE_TEXT_CAP))) {
        Row(
            Modifier.clearAndSetSemantics { role = Role.Image; contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CountTile(cd.first, content.ui("mkt.news.cd.${cd.firstUnit}"), cd.hot, u)
            CountTile(cd.second, content.ui("mkt.news.cd.${cd.secondUnit}"), cd.hot, u)
        }
    }
}

@Composable
private fun CountTile(value: Long, unit: String, hot: Boolean, u: Ui) {
    val c = LocalAndarzColors.current
    Column(
        Modifier.size(width = 52.dp, height = 72.dp).archSurface(TileShape, c.alt, c.divider, c.edge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            u.digits(value.toString().padStart(2, '0')),
            style = figure(29, FontWeight.W600).copy(lineHeight = 31.sp),
            color = if (hot) c.sf else c.text, maxLines = 1,
        )
        Text(unit, style = MaterialTheme.typography.labelMedium, color = c.muted, maxLines = 1)
    }
}

// ---- small parts

/** K6.7: the ISO country code in a small chip, instead of March's flag emoji. */
@Composable
private fun CountryChip(code: String, height: Dp) {
    if (code.isEmpty()) return
    val c = LocalAndarzColors.current
    Box(
        Modifier.heightIn(min = height).background(c.high, RoundedCornerShape(height / 2)).padding(horizontal = if (height >= 24.dp) 8.dp else 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(Fmt.isolate(code), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700), color = c.text, maxLines = 1)
    }
}

/** Three bars of rising height, the first [level] of them in [on], the rest in [off]. */
@Composable
private fun Bars(level: Int, on: Color, off: Color, barWidth: Dp, heights: List<Dp>, gap: Dp, radius: Dp, modifier: Modifier = Modifier) {
    Row(modifier.height(heights.last()), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Bottom) {
        heights.forEachIndexed { i, h ->
            Box(Modifier.size(barWidth, h).background(if (i < level) on else off, RoundedCornerShape(radius)))
        }
    }
}

/** The impact of a row: three bars, saffron for high impact, grey for the rest. The words are its description. */
@Composable
private fun ImpactBars(level: Int, label: String) {
    val c = LocalAndarzColors.current
    Bars(
        level = level, on = if (level >= 3) c.sf else c.hmuted, off = c.track, barWidth = 4.dp,
        heights = RowBarHeights, gap = 2.dp, radius = 1.5.dp,
        modifier = Modifier.semantics { contentDescription = label },
    )
}

/** The impact of the next event in words, on its soft colour: saffron for high impact. */
@Composable
private fun ImpactPill(level: Int, label: String) {
    val c = LocalAndarzColors.current
    val high = level >= 3
    val ink = if (high) c.sf else c.hmuted
    Row(
        Modifier.heightIn(min = 24.dp).background(if (high) c.sfC else c.high, ArchShapes.pill).padding(start = 8.dp, end = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Bars(
            level = level, on = ink, off = ink.copy(alpha = 0.35f), barWidth = 3.4.dp,
            heights = PillBarHeights, gap = 1.9.dp, radius = 1.dp,
        )
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700), color = ink, maxLines = 1)
    }
}

/** The filter chip of the list: an arch on `high` when chosen, a quiet one otherwise. */
@Composable
internal fun FilterOption(selected: Boolean, label: String, onClick: () -> Unit) {
    val c = LocalAndarzColors.current
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 76.dp)
            .archSurface(ChipShape, if (selected) c.high else c.sf1, if (selected) c.outline else c.divider, if (selected) c.edge else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.W700 else FontWeight.W500),
            color = if (selected) c.text else c.muted, maxLines = 1,
        )
    }
}

// ---- the days

private sealed interface Entry {
    /** The line that says how many releases share one time. */
    class Same(val slot: Slot) : Entry

    /** One release; [showTime] is false under a [Same] line, which already shows the time. */
    class Item(val event: EconEvent, val showTime: Boolean) : Entry
}

private fun entriesOf(slots: List<Slot>): List<Entry> = buildList {
    for (s in slots) {
        if (s.events.size > 1) add(Entry.Same(s))
        for (e in s.events) add(Entry.Item(e, showTime = s.events.size == 1))
    }
}

/** One day as one card: a header on the `alt` step, then the releases in time order, one hairline between rows. */
@Composable
internal fun DaySection(
    content: ContentRepository,
    u: Ui,
    day: LocalDate,
    events: List<EconEvent>,
    isToday: Boolean,
    isLast: Boolean,
    passedAt: Long,
    nextId: String?,
    reminded: Set<String>,
    onToggle: (EconEvent) -> Unit,
) {
    val c = LocalAndarzColors.current
    val entries = remember(events) { entriesOf(slotsOf(events)) }
    Column(Modifier.fillMaxWidth().archSurface(SectionShape, c.sf1, c.divider, c.edge)) {
        DayHeader(content, u, day, events.size, isToday, isLast)
        entries.forEachIndexed { i, entry ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
            when (entry) {
                is Entry.Same -> SameTimeRow(u, entry.slot, past = entry.slot.at <= passedAt)
                is Entry.Item -> EventRow(
                    content = content, u = u, e = entry.event, showTime = entry.showTime,
                    past = entry.event.at <= passedAt, isNext = entry.event.id == nextId,
                    on = entry.event.id in reminded, last = i == entries.lastIndex,
                    onToggle = { onToggle(entry.event) },
                )
            }
        }
    }
}

@Composable
private fun DayHeader(content: ContentRepository, u: Ui, day: LocalDate, count: Int, isToday: Boolean, isLast: Boolean) {
    val c = LocalAndarzColors.current
    val strong = if (isToday) content.ui("common.today") else dayName(u, day)
    val rest = buildList {
        if (isToday) add(dayName(u, day))
        if (isLast) add(content.ui("app.news.lastDay"))
    }
    val muted = if (rest.isEmpty()) "" else SEP.trimStart() + rest.joinToString(SEP)
    Row(
        Modifier.fillMaxWidth().background(c.alt, HeaderShape).heightIn(min = 30.dp).padding(horizontal = 14.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                strong,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W800),
                color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (muted.isNotEmpty()) {
                Text(
                    muted,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W500),
                    color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        Text(
            u.t(if (count == 1) "app.news.day.one" else "app.news.day", "n" to u.int(count)),
            style = MaterialTheme.typography.labelMedium, color = c.muted, maxLines = 1,
        )
    }
}

/** The time that two or more releases share, and how many they are (March's "+ n at the same time"). */
@Composable
private fun SameTimeRow(u: Ui, slot: Slot, past: Boolean) {
    val c = LocalAndarzColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 26.dp).padding(horizontal = 14.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            u.time(Instant.ofEpochMilli(slot.at)),
            style = figure(16, FontWeight.W600).copy(lineHeight = 21.sp),
            color = if (past) c.muted else c.text, maxLines = 1,
        )
        Row(
            Modifier.heightIn(min = 20.dp).background(c.high, ArchShapes.pill).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon(Glyph.LAYERS_2, null, tint = c.hmuted, size = 12.dp, strokeWidth = 2.2f)
            Text(
                u.t("app.news.same", "n" to u.int(slot.events.size)),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700),
                color = c.hmuted, maxLines = 1,
            )
        }
    }
}

/** What a row says under its name: "released", the previous and forecast values, or at least the impact in words. */
private fun subLine(content: ContentRepository, u: Ui, e: EconEvent, past: Boolean): String {
    if (past) return content.ui("mkt.news.released")
    val figures = buildList {
        if (e.previous.isNotEmpty()) add(content.ui("mkt.news.previous") + releaseFigure(e.previous, u.fa))
        if (e.forecast.isNotEmpty()) add(content.ui("mkt.news.forecast") + releaseFigure(e.forecast, u.fa))
    }
    return if (figures.isEmpty()) impactLabel(content, e.impact) else figures.joinToString(SEP)
}

@Composable
private fun EventRow(
    content: ContentRepository,
    u: Ui,
    e: EconEvent,
    showTime: Boolean,
    past: Boolean,
    isNext: Boolean,
    on: Boolean,
    last: Boolean,
    onToggle: () -> Unit,
) {
    val c = LocalAndarzColors.current
    val title = e.titleFor(u.ltr)
    val highlight = if (isNext) Modifier.background(c.alt, if (last) LastRowShape else RectangleShape) else Modifier
    Row(
        Modifier.fillMaxWidth().then(highlight).heightIn(min = 54.dp).padding(horizontal = 8.dp, vertical = 3.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.widthIn(min = 52.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (showTime) {
                Text(
                    u.time(Instant.ofEpochMilli(e.at)),
                    style = figure(17, FontWeight.W600).copy(lineHeight = 21.sp),
                    color = if (past) c.muted else c.text, maxLines = 1,
                )
            }
            ImpactBars(e.impact, impactLabel(content, e.impact))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700),
                color = if (past) c.muted else c.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                CountryChip(e.country, 20.dp)
                Text(
                    subLine(content, u, e, past),
                    style = MaterialTheme.typography.labelMedium, color = c.muted,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        if (past) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.CHECK_CIRCLE, null, tint = c.muted, size = 22.dp, strokeWidth = 1.9f)
            }
        } else {
            BellToggle(on, u.t("app.news.remind", "title" to title), content.ui(if (on) "app.news.on" else "app.news.off"), onToggle)
        }
    }
}

/** The reminder of one row: a filled lapis bell when on, a grey outline when off. A 48 dp touch target. */
@Composable
private fun BellToggle(on: Boolean, description: String, state: String, onToggle: () -> Unit) {
    val c = LocalAndarzColors.current
    Box(
        Modifier
            .size(48.dp)
            .toggleable(value = on, role = Role.Switch, onValueChange = { onToggle() })
            .semantics { contentDescription = description; stateDescription = state },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(if (on) Glyph.BELL_FILL else Glyph.BELL, null, tint = if (on) c.lapis else c.muted, size = 22.dp, strokeWidth = 1.8f)
    }
}

// ---- the expired list

/** The shipped list has run out (K6.10): one calm card in the middle, the rest of Market keeps working. */
@Composable
internal fun NewsExpired(content: ContentRepository, u: Ui, data: CalendarData) {
    val c = LocalAndarzColors.current
    val until = if (data.validUntil > 0) {
        u.date(u.localDate(Instant.ofEpochMilli(data.validUntil)), withWeekday = false)
    } else {
        content.ui("common.dash")
    }
    val text = content.ui("mkt.cal.expired.body", "date" to until)
    val at = text.indexOf(until)
    val body = AnnotatedString.Builder().apply {
        append(withNumerals(text))
        if (at >= 0 && until.isNotEmpty()) addStyle(SpanStyle(color = c.text, fontWeight = FontWeight.W700), at, at + until.length)
    }.toAnnotatedString()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .archSurface(ExpiredShape, c.sf1, c.divider, c.edge)
                .padding(start = 20.dp, top = 28.dp, end = 20.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(width = 112.dp, height = 128.dp).archRing(ArchShapes.arch(56.dp, 24.dp), c.sf.copy(alpha = 0.45f), 1.5.dp),
                contentAlignment = Alignment.Center,
            ) {
                ArchTile(c.sfC, width = 80.dp, height = 96.dp, bottom = 18.dp) {
                    GlyphIcon(Glyph.CALENDAR_MINUS, null, tint = c.sf, size = 34.dp, strokeWidth = 1.8f)
                }
            }
            Text(
                content.ui("mkt.cal.expired.title"),
                style = kv(18).copy(lineHeight = 29.sp), color = c.text, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 27.sp),
                color = c.hmuted, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 300.dp),
            )
        }
    }
}
