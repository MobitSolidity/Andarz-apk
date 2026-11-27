package ir.andarz.app.ui.journal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.components.ArchShapes
import ir.andarz.app.ui.components.HeaderTile
import ir.andarz.app.ui.components.EmptyState
import ir.andarz.app.ui.components.FilterChip
import ir.andarz.app.ui.components.RChip
import ir.andarz.app.ui.components.TagChip
import ir.andarz.app.ui.components.Text
import ir.andarz.app.ui.components.TextButton
import ir.andarz.app.ui.components.archSurface
import ir.andarz.app.ui.icons.Glyph
import ir.andarz.app.ui.icons.GlyphIcon
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.today.NewTradeFab
import ir.andarz.app.ui.ui
import ir.andarz.data.db.AndarzDatabase
import ir.andarz.data.db.SetupEntity
import ir.andarz.data.db.TradeEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class JournalViewModel @Inject constructor(db: AndarzDatabase) : ViewModel() {
    val trades: StateFlow<List<TradeEntity>> = db.trades().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val setups: StateFlow<List<SetupEntity>> = db.setups().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}

enum class JournalSort { NEWEST, OLDEST, BEST_R, WORST_R }

/** Filter state of the journal (5.6). Pure, so it is tested on the JVM. */
data class JournalFilter(
    val status: String? = null,
    val result: String? = null,
    val account: String? = null,
    val compliance: Boolean? = null,
    val hasViolation: Boolean = false,
    val query: String = "",
    val sort: JournalSort = JournalSort.NEWEST,
) {
    fun apply(rows: List<TradeEntity>, setupName: (String?) -> String): List<TradeEntity> {
        val q = query.trim().lowercase(java.util.Locale.ROOT)
        val out = rows.filter { t ->
            (status == null || t.status == status) &&
                (result == null || t.result == result) &&
                (account == null || t.account == account) &&
                (compliance == null || t.ok == compliance) &&
                (!hasViolation || t.violations.isNotEmpty()) &&
                (q.isEmpty() || listOf(t.symbol, setupName(t.setupId), t.lesson ?: "", t.counterTrendReason ?: "").any { it.lowercase(java.util.Locale.ROOT).contains(q) })
        }
        val time = { t: TradeEntity -> t.entryTime ?: t.createdAt }
        return when (sort) {
            JournalSort.NEWEST -> out.sortedByDescending(time)
            JournalSort.OLDEST -> out.sortedBy(time)
            JournalSort.BEST_R -> out.sortedByDescending { it.r ?: Double.NEGATIVE_INFINITY }
            JournalSort.WORST_R -> out.sortedBy { it.r ?: Double.POSITIVE_INFINITY }
        }
    }

    val active: Boolean get() = status != null || result != null || account != null || compliance != null || hasViolation || query.isNotBlank()
}

/** The days of the rhythm chart: the last two weeks, today at the end. */
private const val RHYTHM_DAYS = 14

/**
 * Journal list (5.6): a header with the count and the tools, the rhythm of the last two weeks, then one card per
 * display-calendar day with the day net; searchable and filterable. [onNewTrade] adds the "new trade" action.
 */
@Composable
fun JournalScreen(onOpen: (String) -> Unit, onInsights: () -> Unit, vm: JournalViewModel = hiltViewModel(), onNewTrade: (() -> Unit)? = null) {
    val u = ui()
    val trades by vm.trades.collectAsState()
    val setups by vm.setups.collectAsState()
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(JournalSort.NEWEST) }
    var violation by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    val names = setups.associate { it.id to it.name }
    val filter = JournalFilter(status = status, hasViolation = violation, query = query, sort = sort)
    val rows = filter.apply(trades) { names[it] ?: "" }
    val dayOf = { t: TradeEntity -> u.localDate(Instant.ofEpochMilli(t.entryTime ?: t.createdAt)) }
    val today = LocalDate.now(u.zone)
    val pending = trades.count { it.status == "CLOSED" && it.reviewedAt == null }
    val realNet = remember(trades, u) {
        trades.filter { it.account == "REAL" && it.net != null }.groupBy(dayOf).mapValues { (_, l) -> l.sumOf { it.net ?: 0.0 } }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            JournalHeader(
                title = u.t("journal.title"),
                subtitle = if (pending > 0) u.t("app.journal.sub", "n" to u.int(trades.size), "m" to u.int(pending)) else u.t("journal.count", "n" to u.int(trades.size)),
                searchLabel = u.t("common.search"), filterLabel = u.t("journal.filter"), insightsLabel = u.t("nav.insights"),
                hasTrades = trades.isNotEmpty(),
                searchOpen = searchOpen || query.isNotBlank(), filterOpen = filterOpen || status != null || violation || sort != JournalSort.NEWEST,
                onSearch = { searchOpen = !searchOpen }, onFilter = { filterOpen = !filterOpen }, onInsights = onInsights,
            )
            if (trades.isEmpty()) {
                JournalEmpty(u.t("app.journal.empty"), u.t("today.newTrade"), onNewTrade, Modifier.weight(1f).fillMaxWidth())
            } else {
                if (searchOpen || query.isNotBlank()) {
                    OutlinedTextField(
                        query, { query = it }, placeholder = { Text(u.t("journal.search.hint")) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
                if (filterOpen || status != null || violation || sort != JournalSort.NEWEST) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = status == null, onClick = { status = null }, label = { Text(u.t("common.all")) })
                        listOf("OPEN", "CLOSED", "PLANNED", "REJECTED").forEach { s -> FilterChip(selected = status == s, onClick = { status = s }, label = { Text(u.t("trade.status.$s")) }) }
                        FilterChip(selected = violation, onClick = { violation = !violation }, label = { Text(u.t("journal.filter.violation")) })
                        JournalSort.entries.forEach { s ->
                            val key = when (s) { JournalSort.NEWEST -> "newest"; JournalSort.OLDEST -> "oldest"; JournalSort.BEST_R -> "bestR"; JournalSort.WORST_R -> "worstR" }
                            FilterChip(selected = sort == s, onClick = { sort = s }, label = { Text(u.t("journal.sort.$key")) })
                        }
                        if (filter.active) TextButton(onClick = { status = null; query = ""; violation = false }) { Text(u.t("journal.filter.clear")) }
                    }
                }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val values = (RHYTHM_DAYS - 1 downTo 0).map { realNet[today.minusDays(it.toLong())] }
                    if (values.any { it != null }) {
                        item(key = "rhythm", contentType = "rhythm") {
                            RhythmCard(
                                title = u.t("app.journal.rhythm.title", "n" to u.int(RHYTHM_DAYS)),
                                caption = u.t("app.journal.rhythm.caption"),
                                values = values,
                                description = u.t(
                                    "app.journal.rhythm.desc", "n" to u.int(RHYTHM_DAYS),
                                    "today" to u.signedMoney(values.last()), "yesterday" to u.signedMoney(values[RHYTHM_DAYS - 2]),
                                ),
                            )
                        }
                    }
                    if (rows.isEmpty()) item(key = "none") { EmptyState(u.t("journal.emptyFilter")) }
                    rows.groupBy(dayOf).forEach { (day, list) ->
                        item(key = "d$day", contentType = "day") { DayCard(u, day, day == today, list, names, onOpen) }
                    }
                }
            }
        }
        if (trades.isNotEmpty() && onNewTrade != null) {
            NewTradeFab(
                label = u.t("today.newTrade"), enabled = true, onClick = onNewTrade,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

/** Title with the count under it, then the insights shortcut and the search and filter toggles (48 dp arch tiles). */
@Composable
private fun JournalHeader(
    title: String, subtitle: String, searchLabel: String, filterLabel: String, insightsLabel: String,
    hasTrades: Boolean, searchOpen: Boolean, filterOpen: Boolean,
    onSearch: () -> Unit, onFilter: () -> Unit, onInsights: () -> Unit,
) {
    val c = LocalAndarzColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = c.muted)
        }
        HeaderTile(Glyph.LINE_CHART, insightsLabel, tint = c.lapis, container = c.lapisC, onClick = onInsights)
        HeaderTile(
            Glyph.SEARCH, searchLabel, tint = if (hasTrades) c.hmuted else c.muted, container = if (searchOpen) c.fzC else c.alt,
            enabled = hasTrades, selected = searchOpen, onClick = onSearch,
        )
        HeaderTile(
            Glyph.FILTER, filterLabel, tint = if (hasTrades) c.hmuted else c.muted, container = if (filterOpen) c.fzC else c.alt,
            enabled = hasTrades, selected = filterOpen, onClick = onFilter,
        )
    }
}

/** The empty journal: a dashed arch ("not yet") around a small arch card with the book, a sentence and the way in. */
@Composable
private fun JournalEmpty(text: String, action: String, onNewTrade: (() -> Unit)?, modifier: Modifier = Modifier) {
    val c = LocalAndarzColors.current
    Box(modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(bottom = 36.dp).widthIn(max = 300.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(Modifier.size(width = 132.dp, height = 152.dp).dashedArch(c.outline, 66.dp, 28.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(width = 96.dp, height = 116.dp).archSurface(ArchShapes.arch(48.dp, 20.dp), c.sf1, c.divider, c.edge),
                    contentAlignment = Alignment.Center,
                ) { GlyphIcon(Glyph.BOOK, null, tint = c.lapis, size = 34.dp) }
            }
            Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (onNewTrade != null) NewTradeFab(label = action, enabled = true, onClick = onNewTrade)
        }
    }
}

/** A dashed arch outline (1.5 dp), the way the journal draws everything that has not happened yet. */
private fun Modifier.dashedArch(color: Color, top: Dp, bottom: Dp): Modifier = drawBehind {
    val w = 1.5.dp.toPx()
    val outline = ArchShapes.arch(top, bottom).createOutline(Size(size.width - w, size.height - w), layoutDirection, this)
    translate(w / 2f, w / 2f) {
        drawOutline(outline, color, style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
    }
}

/** Daily net of the last [RHYTHM_DAYS] days as bars on a baseline: up in turquoise, down in coral, a dot for no result. */
@Composable
private fun RhythmCard(title: String, caption: String, values: List<Double?>, description: String) {
    val c = LocalAndarzColors.current
    Column(Modifier.fillMaxWidth().archSurface(RoundedCornerShape(24.dp), c.sf1, c.divider, c.edge).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700), modifier = Modifier.weight(1f))
            Text(caption, style = MaterialTheme.typography.labelMedium, color = c.muted)
        }
        RhythmBars(values, Modifier.fillMaxWidth().padding(top = 4.dp).height(48.dp).clearAndSetSemantics { contentDescription = description; role = Role.Image })
    }
}

@Composable
private fun RhythmBars(values: List<Double?>, modifier: Modifier = Modifier) {
    val c = LocalAndarzColors.current
    val up = c.fzG
    val down = c.anG
    val todayTint = c.alt
    val line = c.divider
    val dot = c.outline
    Canvas(modifier) {
        // the board's 330 by 48 grid: baseline at 28, room for 22 up and 20 down; the last slot is today
        val slot = size.width / values.size
        val k = size.height / 48f
        val base = 28f * k
        val maxUp = values.filterNotNull().filter { it > 0.0 }.maxOrNull() ?: 0.0
        val maxDown = values.filterNotNull().filter { it < 0.0 }.minOfOrNull { -it } ?: 0.0
        val unit = when {
            maxUp > 0.0 && maxDown > 0.0 -> minOf(22.0 / maxUp, 20.0 / maxDown)
            maxUp > 0.0 -> 22.0 / maxUp
            maxDown > 0.0 -> 20.0 / maxDown
            else -> 0.0
        } * k
        drawRoundRect(todayTint, topLeft = Offset((values.size - 1) * slot, 0f), size = Size(slot, size.height), cornerRadius = CornerRadius(8.dp.toPx()))
        drawLine(line, Offset(0f, base), Offset(size.width, base), strokeWidth = 1.dp.toPx())
        val bar = slot * 0.51f
        val corner = CornerRadius(3.5.dp.toPx())
        values.forEachIndexed { i, raw ->
            val v = raw ?: 0.0
            val x = i * slot + (slot - bar) / 2f
            when {
                v == 0.0 -> drawCircle(dot, 2.dp.toPx(), Offset(i * slot + slot / 2f, base))
                v > 0.0 -> {
                    val h = maxOf((v * unit).toFloat(), 2.dp.toPx())
                    drawRoundRect(up, Offset(x, base - h), Size(bar, h), corner)
                }
                else -> {
                    val h = maxOf((-v * unit).toFloat(), 2.dp.toPx())
                    drawRoundRect(down, Offset(x, base), Size(bar, h), corner)
                }
            }
        }
    }
}

/** One day: a band with the date, a "today" tag or the count, and the net; below it the trades of the day. */
@Composable
private fun DayCard(u: Ui, day: LocalDate, isToday: Boolean, list: List<TradeEntity>, names: Map<String, String>, onOpen: (String) -> Unit) {
    val c = LocalAndarzColors.current
    val net = list.filter { it.account == "REAL" }.mapNotNull { it.net }
    Column(Modifier.fillMaxWidth().archSurface(RoundedCornerShape(24.dp), c.sf1, c.divider, c.edge, clipContent = true)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 36.dp).background(c.alt).padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(u.date(day), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            if (isToday) {
                Text(
                    u.t("common.today"),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700), color = c.hmuted,
                    modifier = Modifier.heightIn(min = 22.dp).background(c.high, RoundedCornerShape(11.dp)).padding(horizontal = 9.dp),
                )
            } else {
                Text(u.t("journal.count", "n" to u.int(list.size)), style = MaterialTheme.typography.labelMedium, color = c.muted)
            }
            Spacer(Modifier.weight(1f))
            if (net.isNotEmpty()) NetLabel(u.signedMoney(net.sum()), net.sum())
        }
        list.forEach { t ->
            HorizontalDivider(color = c.divider)
            JournalRow(t, names[t.setupId], onOpen)
        }
    }
}

/** The net of a day: a triangle that points the way, the signed amount in the colour of the sign. */
@Composable
private fun NetLabel(text: String, value: Double) {
    val c = LocalAndarzColors.current
    val ink = if (value > 0) c.fz else if (value < 0) c.an else c.hmuted
    val glyph = if (value > 0) Glyph.TRI_UP else if (value < 0) Glyph.TRI_DOWN else Glyph.BAR_FILL
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        GlyphIcon(glyph, null, tint = ink, size = 10.dp)
        Text(text, style = MaterialTheme.typography.titleSmall, color = ink)
    }
}

/**
 * One journal row (5.7): a status tile, the symbol with its side, the setup and time, the state chips, and at the end
 * the R chip over the net. The chip lines wrap (FlowRow) so nothing clips at 200% font (6.8, F9). The tile is the
 * compliance verdict as a shape and a word for TalkBack: a check (kept the rules), a cross (broke them), dashed ("not yet").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun JournalRow(t: TradeEntity, setup: String?, onOpen: (String) -> Unit) {
    val u = ui()
    val c = LocalAndarzColors.current
    val time = u.time(Instant.ofEpochMilli(t.entryTime ?: t.createdAt))
    val closed = t.status == "CLOSED"
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = { onOpen(t.id) })
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusTile(u, t)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(u.symbol(t.symbol), style = MaterialTheme.typography.titleSmall)
                SideChip(u.enumLabel("side", t.side), t.side == "SHORT")
            }
            Text(
                listOfNotNull(setup?.takeIf { it.isNotBlank() }, time).joinToString(" \u00B7 "),
                style = MaterialTheme.typography.labelMedium, color = c.muted,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (t.status == "OPEN") StateChip(u.t("journal.row.open"), c.lapisC, c.lapis, Glyph.DOT_FILL)
                if (closed && t.reviewedAt == null) StateChip(u.t("journal.row.pending"), c.sfC, c.sf, Glyph.CLIPBOARD_CHECK)
                if (t.violations.isNotEmpty()) StateChip(u.t("journal.filter.violation"), c.anC, c.an, Glyph.X)
                if (t.account == "DEMO") TagChip(u.t("common.demo"))
                if (t.imported) TagChip(u.t("trade.imported"))
                if (t.status == "PLANNED" || t.status == "REJECTED" || t.status == "CANCELLED") TagChip(u.t("trade.status.${t.status}"))
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (closed || t.status == "OPEN") RChip(if (closed) t.r else null)
            if (t.net != null) Text(u.signedMoney(t.net), style = MaterialTheme.typography.bodySmall, color = c.hmuted)
        }
    }
}

@Composable
private fun StatusTile(u: Ui, t: TradeEntity) {
    val c = LocalAndarzColors.current
    val shape = ArchShapes.arch(14.dp, 8.dp)
    val tile = Modifier.padding(top = 2.dp).size(width = 28.dp, height = 32.dp)
    when {
        t.status == "CLOSED" && t.ok == true ->
            Box(tile.background(c.fzC, shape), Alignment.Center) { GlyphIcon(Glyph.CHECK, u.t("result.compliant.yes"), tint = c.fz, size = 15.dp, strokeWidth = 2.6f) }
        t.status == "CLOSED" && t.ok == false ->
            Box(tile.background(c.anC, shape), Alignment.Center) { GlyphIcon(Glyph.X, u.t("result.compliant.no"), tint = c.an, size = 14.dp, strokeWidth = 2.6f) }
        t.status == "CLOSED" ->
            Box(tile.background(c.high, shape), Alignment.Center) { GlyphIcon(Glyph.MINUS, null, tint = c.hmuted, size = 14.dp, strokeWidth = 2.6f) }
        else ->
            Box(tile.dashedArch(c.outline, 14.dp, 8.dp), Alignment.Center) {
                GlyphIcon(if (t.status == "OPEN") Glyph.CLOCK_ROUND else Glyph.DOTTED_CIRCLE, null, tint = c.muted, size = 14.dp, strokeWidth = 2.2f)
            }
    }
}

/** LONG or SHORT: a lapis chip with a triangle that points up or down, plus the word. */
@Composable
private fun SideChip(label: String, short: Boolean) {
    val c = LocalAndarzColors.current
    Row(
        Modifier.heightIn(min = 22.dp).background(c.lapisC, RoundedCornerShape(11.dp)).padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GlyphIcon(if (short) Glyph.TRI_DOWN else Glyph.TRI_UP, null, tint = c.lapis, size = 9.dp)
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700), color = c.lapis)
    }
}

/** A state of the row (open, review pending, broke a rule): tinted container, glyph and word, never colour alone. */
@Composable
private fun StateChip(text: String, container: Color, ink: Color, glyph: Glyph) {
    Row(
        Modifier.heightIn(min = 22.dp).background(container, RoundedCornerShape(11.dp)).padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        GlyphIcon(glyph, null, tint = ink, size = 10.dp, strokeWidth = 2.4f)
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700), color = ink)
    }
}
