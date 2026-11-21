package ir.andarz.app.ui.trade

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.components.ArchTile
import ir.andarz.app.ui.components.FitText
import ir.andarz.app.ui.components.OutlinedButton
import ir.andarz.app.ui.components.RChip
import ir.andarz.app.ui.components.Text
import ir.andarz.app.ui.components.archRing
import ir.andarz.app.ui.components.archSurface
import ir.andarz.app.ui.components.figure
import ir.andarz.app.ui.icons.Glyph
import ir.andarz.app.ui.icons.GlyphIcon
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.data.ReviewInput
import ir.andarz.data.db.TradeEntity
import ir.andarz.engine.Management
import ir.andarz.engine.Params
import java.time.Instant

/*
 * The post-trade review as a full page (board "Review"): a timeline of sections, each with an arch marker on a
 * vertical connector. Order of article 8-5: which rule did I break, the report of the breach, the one main
 * mistake, the feeling after the exit, the one-line lesson. Saving needs a main mistake and, when a rule was
 * broken, a report of the minimum length (law 8-5).
 */

private enum class Step { RULES, REPORT, MISTAKE, EMOTION, LESSON }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReviewPage(u: Ui, t: TradeEntity, p: Params, setupName: String?, onClose: () -> Unit, onSave: (ReviewInput) -> Unit) {
    val k = LocalAndarzColors.current
    val auto = remember(t.violations) { t.violations.toSet() }
    val suggested = remember(t.id) {
        Management.suggestedViolations(
            t.emotion, enteredInCooldown = false, losing = (t.net ?: 0.0) < 0,
            heldMinutes = ((t.exitTime ?: 0) - (t.entryTime ?: 0)) / 60_000, timeframe = t.timeframe, p = p,
        ).filter { it !in auto }
    }
    // A suggestion counts only after the confirming tap (5.5); the ticked ones of a saved review are in `auto`.
    var chosen by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    var more by rememberSaveable { mutableStateOf(false) }
    var report by rememberSaveable { mutableStateOf(t.violationReport ?: "") }
    var mistake by rememberSaveable { mutableStateOf(t.mistake) }
    var emotionAfter by rememberSaveable { mutableStateOf(t.emotionAfter) }
    var lesson by rememberSaveable { mutableStateOf(t.lesson ?: "") }
    val min = p.reportMinChars.toInt()
    val anyViolation = auto.isNotEmpty() || chosen.isNotEmpty()
    val reportOk = report.trim().length >= min
    val canSave = mistake != null && (!anyViolation || reportOk)

    val steps = if (anyViolation) Step.values().toList() else Step.values().filter { it != Step.REPORT }
    fun done(s: Step) = when (s) {
        Step.RULES -> true
        Step.REPORT -> reportOk
        Step.MISTAKE -> mistake != null
        Step.EMOTION -> emotionAfter != null
        Step.LESSON -> lesson.isNotBlank()
    }
    // the first step that is not done yet; all done puts the marker past the last one
    val current = steps.indexOfFirst { !done(it) }.let { if (it < 0) steps.size else it }

    Column(Modifier.fillMaxSize()) {
        ReviewHeader(u, steps.size, current, onClose)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TradeSummary(u, t, setupName)
            steps.forEachIndexed { i, step ->
                val state = if (done(step)) 2 else if (i == current) 1 else 0
                val last = i == steps.lastIndex
                when (step) {
                    Step.RULES -> StepSection(
                        u, i + 1, state, last,
                        u.t("app.trade.review.s1"), u.t("common.article", "n" to u.digits("8-5")),
                    ) {
                        val others = u.enumIds("violation").filter { it !in auto && it !in suggested }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            auto.forEach { id -> ViolationRow(u, id, Row0.AUTO, true, null) }
                            suggested.forEach { id ->
                                ViolationRow(u, id, Row0.SUGGESTED, id in chosen) { chosen = if (id in chosen) chosen - id else chosen + id }
                            }
                            if (others.isNotEmpty()) {
                                MoreToggle(u, others.size, more) { more = !more }
                                if (more) others.forEach { id ->
                                    ViolationRow(u, id, Row0.PLAIN, id in chosen) { chosen = if (id in chosen) chosen - id else chosen + id }
                                }
                            }
                        }
                    }
                    Step.REPORT -> StepSection(
                        u, i + 1, state, last, u.t("review.report"), u.t("common.required"),
                    ) {
                        BareField(report, { report = it }, u.t("review.report.hint"), singleLine = false, minLines = 2)
                        Spacer(Modifier.height(8.dp))
                        ReportCounter(u, report.trim().length, min)
                    }
                    Step.MISTAKE -> StepSection(u, i + 1, state, last, u.t("review.mistake"), u.t("app.trade.review.s3.cap")) {
                        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            u.enumIds("mistake").forEach { id -> ChoiceChip(u.enumLabel("mistake", id), mistake == id, { mistake = id }) }
                        }
                    }
                    Step.EMOTION -> StepSection(u, i + 1, state, last, u.t("review.emotionAfter"), null) {
                        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            u.enumIds("emotion").chunked(3).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { id -> ChoiceChip(u.enumLabel("emotion", id), emotionAfter == id, { emotionAfter = id }, Modifier.weight(1f), center = true) }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                    Step.LESSON -> StepSection(u, i + 1, state, last, u.t("app.trade.review.s5"), u.t("app.trade.review.s5.cap")) {
                        BareField(lesson, { lesson = it }, u.t("review.lesson.hint"), singleLine = true, minLines = 1)
                    }
                }
            }
        }
        // the foot: what is still missing, then Later and Save
        Column(
            Modifier.fillMaxWidth().background(k.sf1)
                .drawBehind { drawLine(k.divider, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!canSave) {
                Text(
                    when {
                        mistake == null && anyViolation && !reportOk -> u.t("app.trade.review.hint.both", "min" to u.int(min))
                        mistake == null -> u.t("app.trade.review.hint.mistake")
                        else -> u.t("app.trade.review.hint.report", "min" to u.int(min))
                    },
                    style = MaterialTheme.typography.labelMedium, color = k.muted,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(u.t("review.skip")) }
                FilledAction(
                    onClick = { onSave(ReviewInput(chosen.toList(), report.takeIf { anyViolation }, mistake!!, emotionAfter, lesson)) },
                    enabled = canSave, modifier = Modifier.weight(1.4f),
                ) { Text(u.t("app.trade.review.save")) }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------

@Composable
private fun ReviewHeader(u: Ui, total: Int, current: Int, onClose: () -> Unit) {
    val k = LocalAndarzColors.current
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            GlyphIcon(Glyph.X, u.t("app.trade.review.close"), tint = k.text, size = 22.dp, strokeWidth = 2f)
        }
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(u.t("app.trade.review.title"), style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp), modifier = Modifier.semantics { heading() })
            Text(
                u.t("app.trade.review.step", "n" to u.int((current + 1).coerceAtMost(total)), "total" to u.int(total)),
                style = MaterialTheme.typography.labelMedium, color = k.muted,
            )
        }
        val dot = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomEnd = 2.dp, bottomStart = 2.dp)
        Row(Modifier.padding(end = 12.dp).clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until total) {
                val base = Modifier.size(width = 8.dp, height = 11.dp)
                when {
                    i < current -> Box(base.background(k.fzG, dot))
                    i == current -> Box(base.archRing(dot, k.lapis, 2.dp))
                    else -> Box(base.archRing(dot, k.outline, 1.5.dp))
                }
            }
        }
    }
}

/** The trade being reviewed: a tile in the colour of the result, the symbol and side, and the net with its R. */
@Composable
private fun TradeSummary(u: Ui, t: TradeEntity, setupName: String?) {
    val k = LocalAndarzColors.current
    val net = t.net ?: 0.0
    val (tile, ink) = when {
        net > 0 -> k.fzC to k.fz
        net < 0 -> k.anC to k.an
        else -> k.high to k.muted
    }
    val line = listOfNotNull(setupName, t.exitTime?.let { u.dateTime(Instant.ofEpochMilli(it)) }).joinToString(DOT)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).panel().padding(horizontal = 12.dp, vertical = 8.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ArchTile(tile, width = 28.dp, height = 32.dp, bottom = 7.dp) {
            GlyphIcon(if (net >= 0) Glyph.TRI_UP else Glyph.TRI_DOWN, null, tint = ink, size = 12.dp)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(u.symbol(t.symbol), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.W700), maxLines = 1)
                SidePill(u, t.side)
            }
            if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.labelMedium, color = k.muted, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            FitText(u.signedMoney(t.net), figure(15), color = ink, minScale = 0.7f, textAlign = TextAlign.End)
            RChip(t.r)
        }
    }
}

/**
 * A section of the timeline: an arch marker (check when done, ringed number now, plain number later) joined to the
 * next one by a vertical line, then the title with its caption and the content.
 */
@Composable
private fun StepSection(
    u: Ui,
    index: Int,
    state: Int,
    last: Boolean,
    title: String,
    caption: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val k = LocalAndarzColors.current
    val shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 7.dp, bottomStart = 7.dp)
    Box(
        Modifier.fillMaxWidth().drawBehind {
            if (!last) {
                val x = if (layoutDirection == LayoutDirection.Rtl) size.width - 12.dp.toPx() else 12.dp.toPx()
                drawLine(k.divider, Offset(x, 32.dp.toPx()), Offset(x, size.height + 12.dp.toPx()), strokeWidth = 2.dp.toPx())
            }
        },
    ) {
        val marker = Modifier.size(width = 24.dp, height = 28.dp).align(Alignment.TopStart)
        when (state) {
            2 -> Box(marker.background(k.fzC, shape).archRing(shape, k.fzG, 1.5.dp), contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.CHECK, null, tint = k.fz, size = 14.dp, strokeWidth = 2.8f)
            }
            1 -> Box(marker.archRing(shape, k.lapis, 2.dp), contentAlignment = Alignment.Center) {
                Text(u.int(index), style = figure(13, FontWeight.W800), color = k.lapis)
            }
            else -> Box(marker.archRing(shape, k.outline, 1.5.dp), contentAlignment = Alignment.Center) {
                Text(u.int(index), style = figure(13, FontWeight.W800), color = k.muted)
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 36.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.W800),
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (caption != null) Text(caption, style = MaterialTheme.typography.labelMedium, color = k.muted)
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

private enum class Row0 { AUTO, SUGGESTED, PLAIN }

/**
 * A rule that can be marked as broken. AUTO rows were found by the app and cannot be removed; SUGGESTED rows
 * wait for a confirming tap; PLAIN rows are the other rules. The whole row is the touch target.
 */
@Composable
private fun ViolationRow(u: Ui, id: String, kind: Row0, on: Boolean, onToggle: (() -> Unit)?) {
    val k = LocalAndarzColors.current
    val label = u.enumLabel("violation", id)
    val shape = RoundedCornerShape(16.dp)
    val (bg, line) = when {
        kind == Row0.AUTO -> k.anC to k.anLine
        on -> k.anC to k.anLine
        else -> k.sf1 to k.divider
    }
    val sub = when (kind) {
        Row0.AUTO -> u.t("app.trade.review.auto", "id" to u.symbol(id))
        Row0.SUGGESTED -> u.t("app.trade.review.suggested", "id" to u.symbol(id))
        Row0.PLAIN -> null
    }
    val stateText = u.t(if (kind == Row0.AUTO || on) "a11y.toggleOn" else "a11y.toggleOff")
    val tap = if (onToggle != null) {
        Modifier.toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle() })
    } else {
        Modifier.semantics(mergeDescendants = true) { stateDescription = stateText }
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).archSurface(shape, bg, line).then(tap).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val checked = kind == Row0.AUTO || on
        if (kind == Row0.PLAIN) {
            val box = RoundedCornerShape(8.dp)
            Box(
                Modifier.size(24.dp).then(if (checked) Modifier.background(k.an, box) else Modifier.archRing(box, k.outline, 1.5.dp)),
                contentAlignment = Alignment.Center,
            ) { if (checked) GlyphIcon(Glyph.CHECK, null, tint = k.bg, size = 14.dp, strokeWidth = 3f) }
        } else {
            val tile = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 8.dp, bottomStart = 8.dp)
            if (checked) {
                ArchTile(k.anSoft, width = 32.dp, height = 38.dp) { GlyphIcon(Glyph.CHECK, null, tint = k.an, size = 16.dp, strokeWidth = 2.8f) }
            } else {
                Box(
                    Modifier.size(width = 32.dp, height = 38.dp).drawBehind {
                        val w = 1.5.dp.toPx()
                        val o = tile.createOutline(Size(size.width - w, size.height - w), layoutDirection, this)
                        translate(w / 2f, w / 2f) {
                            drawOutline(o, k.sfLine, style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) { GlyphIcon(Glyph.BULB, null, tint = k.sf, size = 16.dp, strokeWidth = 2.2f) }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700))
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelMedium, color = if (kind == Row0.AUTO) k.an else k.sf)
        }
        when (kind) {
            Row0.AUTO -> GlyphIcon(Glyph.LOCK, null, tint = k.hmuted, size = 16.dp)
            Row0.SUGGESTED -> Text(
                u.t(if (on) "common.remove" else "app.trade.review.confirm"),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700), color = if (on) k.muted else k.lapis,
            )
            Row0.PLAIN -> {}
        }
    }
}

@Composable
private fun MoreToggle(u: Ui, count: Int, open: Boolean, onClick: () -> Unit) {
    val k = LocalAndarzColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = open, role = Role.Button, onValueChange = { onClick() })
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            u.t(if (open) "common.less" else "app.trade.review.more", "n" to u.int(count)),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700), color = k.lapis,
        )
        GlyphIcon(Glyph.CHEVRON_DOWN, null, modifier = Modifier.rotate(if (open) 180f else 0f), tint = k.lapis, size = 16.dp, strokeWidth = 2.4f)
    }
}

/** "11 of 20 characters, 9 more needed", with a short progress bar that turns turquoise when the report is long enough. */
@Composable
private fun ReportCounter(u: Ui, n: Int, min: Int) {
    val k = LocalAndarzColors.current
    val ok = n >= min
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (ok) u.t("app.trade.review.counter.ok", "n" to u.int(n), "min" to u.int(min))
            else u.t("app.trade.review.counter", "n" to u.int(n), "min" to u.int(min), "left" to u.int(min - n)),
            style = MaterialTheme.typography.labelMedium, color = if (ok) k.fz else k.sf, modifier = Modifier.weight(1f),
        )
        Box(Modifier.width(96.dp).height(4.dp).background(k.track, RoundedCornerShape(2.dp)).clearAndSetSemantics {}) {
            Box(
                Modifier.fillMaxWidth(if (min > 0) (n.toFloat() / min).coerceIn(0f, 1f) else 1f).height(4.dp)
                    .background(if (ok) k.fzG else k.sfG, RoundedCornerShape(2.dp)),
            )
        }
    }
}

/** A choice of a single-select group: an arch pill, checked with a glyph and a colour (never colour alone). */
@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, center: Boolean = false) {
    val k = LocalAndarzColors.current
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 12.dp, bottomStart = 12.dp)
    Row(
        modifier.heightIn(min = 48.dp)
            .archSurface(shape, if (selected) k.fzC else k.sf1, if (selected) k.fzG else k.outline)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (center) Arrangement.Center else Arrangement.spacedBy(6.dp),
    ) {
        if (selected) {
            GlyphIcon(Glyph.CHECK, null, tint = k.fz, size = 14.dp, strokeWidth = 2.6f)
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W600), color = if (selected) k.fz else k.text)
    }
}

/** A text input without a label of its own (the section title is the label): 18 dp corners, a lapis ring once it is in use. */
@Composable
private fun BareField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean,
    minLines: Int,
    modifier: Modifier = Modifier,
) {
    val k = LocalAndarzColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    val active = focused || value.isNotEmpty()
    Box(
        modifier.fillMaxWidth().heightIn(min = if (singleLine) 48.dp else 56.dp)
            .archSurface(shape, k.sf1, Color.Transparent)
            .archRing(shape, if (active) k.lapis else k.outline, if (focused) 2.dp else 1.5.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = k.hmuted)
        BasicTextField(
            value = value, onValueChange = onValue, singleLine = singleLine, minLines = minLines,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = k.text),
            cursorBrush = SolidColor(k.lapis),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        )
    }
}
