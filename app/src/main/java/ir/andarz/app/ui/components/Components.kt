package ir.andarz.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.andarz.app.ui.icons.Glyph
import ir.andarz.app.ui.icons.GlyphIcon
import ir.andarz.app.ui.theme.AndarzFonts
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.theme.Tone
import ir.andarz.app.ui.ui
import ir.andarz.engine.Fmt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * The shared building blocks of the design canvas (System board): verdict banners, the arc limit dials, the lock
 * banner, tags, cards, checklist rows, inputs and the empty/loading/error states. Names and parameters of the v2
 * components are kept (screens and tests call them); only the drawing changed. Colour is never the only signal:
 * every tone also carries a glyph and a word, and every state is announced through semantics (F10).
 */

enum class Verdict { ALLOWED, DENIED, LOCKED, CAUTION }

@Composable
fun verdictColor(v: Verdict): Color {
    val c = LocalAndarzColors.current
    return when (v) {
        Verdict.ALLOWED -> c.allowed
        Verdict.CAUTION -> c.caution
        Verdict.DENIED, Verdict.LOCKED -> c.redline
    }
}

private fun verdictGlyph(v: Verdict): Glyph = when (v) {
    Verdict.ALLOWED -> Glyph.CHECK
    Verdict.CAUTION -> Glyph.ALERT_CIRCLE
    Verdict.DENIED -> Glyph.X
    Verdict.LOCKED -> Glyph.LOCK
}

/** The four colours of a banner: container, text and glyph, the soft tile behind the glyph, the 1 dp ring. */
private class BannerPalette(val container: Color, val text: Color, val tile: Color, val ring: Color)

@Composable
private fun bannerPalette(v: Verdict): BannerPalette {
    val c = LocalAndarzColors.current
    return when (v) {
        Verdict.ALLOWED -> BannerPalette(c.fzC, c.fz, c.fzSoft, c.fz.copy(alpha = 0.4f))
        Verdict.CAUTION -> BannerPalette(c.sfC, c.sf, c.sfSoft, c.sfLine)
        Verdict.DENIED, Verdict.LOCKED -> BannerPalette(c.anC, c.an, c.anSoft, c.an.copy(alpha = 0.4f))
    }
}

/** The 17 sp title of a banner and the 14 sp lines under it (Peyda Regular; 700 resolves to Regular). */
@Composable
private fun bannerTitle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 25.sp, fontWeight = FontWeight.W700)

@Composable
private fun bannerBody(): TextStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 24.sp)

/**
 * 6.4.1 VerdictBanner: a tone container with an arch tile holding the glyph, the title in the tone, the numbers
 * line, and a chevron that opens the "why" lines. A polite live region exposing a state word, not only a colour.
 */
@Composable
fun VerdictBanner(state: Verdict, title: String, numbers: String?, why: List<String> = emptyList(), modifier: Modifier = Modifier) {
    val u = ui()
    val p = bannerPalette(state)
    var open by remember { mutableStateOf(state == Verdict.DENIED) }
    // 6.6: 250 ms standard easing; the in-app reduce-motion switch removes it (the system scale is honored by Compose).
    val reduce = u.settings.reduceMotion
    val verdictState = u.t(
        when (state) {
            Verdict.ALLOWED -> "a11y.allowedState"; Verdict.CAUTION -> "a11y.cautionState"
            Verdict.DENIED -> "a11y.redState"; Verdict.LOCKED -> "a11y.lockedState"
        },
    )
    val sized = if (reduce) Modifier else Modifier.animateContentSize(androidx.compose.animation.core.tween(250))
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
            // 6.8 / F10: the verdict exposes a state word, not only a colour.
            stateDescription = verdictState
        }.then(sized)
            .archSurface(ArchShapes.banner, p.container, p.ring)
            .padding(start = 14.dp, top = 12.dp, end = if (why.isNotEmpty()) 4.dp else 14.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArchTile(p.tile) { GlyphIcon(verdictGlyph(state), null, tint = p.text, size = 20.dp, strokeWidth = 2.4f) }
        Column(Modifier.weight(1f)) {
            Text(title, style = bannerTitle(), color = p.text)
            if (numbers != null) Text(numbers, style = bannerBody())
            if (open) why.forEach { Text(it, style = bannerBody(), modifier = Modifier.padding(top = 6.dp)) }
        }
        if (why.isNotEmpty()) {
            IconButton(onClick = { open = !open }, modifier = Modifier.size(48.dp)) {
                GlyphIcon(Glyph.CHEVRON_DOWN, u.t(if (open) "common.less" else "common.details"), modifier = Modifier.rotate(if (open) 180f else 0f), tint = p.text, strokeWidth = 2f)
            }
        }
    }
}

/** The value and the cap of "2 / of 3": the part before " / " is the big figure, the rest sits under it. */
private fun splitValue(valueText: String): Pair<String, String?> {
    val i = valueText.indexOf(" / ")
    return if (i < 0) valueText to null else valueText.substring(0, i) to valueText.substring(i + 3)
}

/**
 * 6.4.2 LimitGauge: an arc dial (84 by 49 dp) with the value and the cap inside it, a label above and an
 * optional status word with a glyph below. One TalkBack node with a state description. The arc fills from the
 * start edge, so it runs right to left in Persian. A [segments] gauge (trades, positions) is drawn as that many
 * arcs. The tick marks two thirds of the cap, where the tone turns from turquoise to saffron.
 */
@Composable
fun LimitGauge(
    label: String,
    valueText: String,
    ratio: Double,
    red: Boolean,
    segments: Int? = null,
    modifier: Modifier = Modifier,
    statusText: String? = null,
) {
    val c = LocalAndarzColors.current
    val u = ui()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val r = ratio.coerceIn(0.0, 1.0)
    val tone = when {
        red -> Tone.LOCKED
        r >= 2.0 / 3.0 -> Tone.CAUTION
        else -> Tone.ALLOWED
    }
    val arc = c.accentG(tone)
    val ink = c.accent(tone)
    val state = u.t(if (red) "a11y.redState" else if (r >= 2.0 / 3.0) "a11y.cautionState" else "a11y.allowedState")
    val (value, cap) = splitValue(valueText)
    // At a large font the figure no longer fits inside the arc: it moves under it.
    val inside = LocalDensity.current.fontScale <= 1.3f
    val track = c.track
    val tick = if (red) c.text else c.muted
    Column(
        modifier.heightIn(min = 48.dp).padding(horizontal = 6.dp, vertical = 6.dp).semantics(mergeDescendants = true) {
            contentDescription = "$label, $valueText"
            stateDescription = state
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W500), color = c.muted, textAlign = TextAlign.Center)
        Box(Modifier.size(width = 84.dp, height = 49.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val s = size.width / 92f
                val stroke = Stroke(width = 7f * s, cap = StrokeCap.Round)
                val topLeft = Offset(8f * s, 10f * s)
                val box = Size(76f * s, 76f * s)
                val dir = if (rtl) -1f else 1f
                val start = if (rtl) 0f else 180f
                fun arcAt(color: Color, from: Float, sweep: Float) {
                    drawArc(color, startAngle = start + dir * from, sweepAngle = dir * sweep, useCenter = false, topLeft = topLeft, size = box, style = stroke)
                }
                if (segments != null && segments > 0) {
                    val gap = 16f
                    val w = (180f - gap * (segments - 1)) / segments
                    val filled = kotlin.math.round(r * segments).toInt()
                    for (i in 0 until segments) arcAt(if (i < filled) arc else track, i * (w + gap), w)
                } else {
                    arcAt(track, 0f, 180f)
                    if (r > 0.0) arcAt(arc, 0f, (180.0 * r).toFloat())
                }
                // the tick at two thirds of the cap, drawn just outside the arc
                val phi = Math.toRadians((start + dir * 120f).toDouble())
                fun at(radius: Float) = Offset((46f + radius * cos(phi).toFloat()) * s, (48f + radius * sin(phi).toFloat()) * s)
                drawLine(tick, at(44f), at(49f), strokeWidth = 1.5f * s, cap = StrokeCap.Round)
            }
            if (inside) {
                Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    DialFigure(value, cap)
                }
            }
        }
        if (!inside) DialFigure(value, cap)
        if (statusText != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
                GlyphIcon(
                    when (tone) { Tone.LOCKED -> Glyph.X; Tone.CAUTION -> Glyph.ALERT_CIRCLE; Tone.ALLOWED -> Glyph.CHECK },
                    null, tint = ink, size = 14.dp, strokeWidth = 2.4f,
                )
                Text(statusText, style = MaterialTheme.typography.labelMedium, color = ink)
            }
        }
    }
}

@Composable
private fun DialFigure(value: String, cap: String?) {
    val c = LocalAndarzColors.current
    Text(
        value,
        style = TextStyle(fontFamily = AndarzFonts.plex, fontWeight = FontWeight.W700, fontSize = 16.sp, lineHeight = 18.sp, fontFeatureSettings = "tnum"),
        maxLines = 1,
    )
    if (cap != null) Text(cap, style = MaterialTheme.typography.labelMedium.copy(lineHeight = 14.sp), color = c.muted, maxLines = 1)
}

/**
 * The board of dials: cells separated by 1 dp hairlines inside one card, like the "limits board" of the canvas.
 * [cells] are laid out [columns] to a row; an incomplete last row is padded with empty cells.
 */
@Composable
fun LimitBoard(columns: Int, modifier: Modifier = Modifier, cells: List<@Composable () -> Unit>) {
    val c = LocalAndarzColors.current
    Column(
        // The hairlines between the cells are the card's own divider colour showing through 1 dp gaps. The shape is
        // uniform, so clipping the square cells to it is safe (only unequal corners break hit-testing in tests).
        modifier.fillMaxWidth().archSurface(RoundedCornerShape(24.dp), c.divider, c.divider, c.edge, clipContent = true),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        cells.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                for (i in 0 until columns) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(c.sf1), contentAlignment = Alignment.TopCenter) {
                        row.getOrNull(i)?.invoke()
                    }
                }
            }
        }
    }
}

/** The countdown of a lock: how much of the pause is left (0 to 1) and the text in the middle of the ring. */
class LockCountdown(val remaining: Float, val label: String)

/**
 * 6.4.6 LockBanner: red-line container, the countdown ring (or a lock tile when no countdown is known), the
 * title, the explanation, one button per need. A polite live region.
 */
@Composable
fun LockBanner(
    title: String,
    body: String,
    until: String?,
    actions: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
    countdown: LockCountdown? = null,
) {
    val c = LocalAndarzColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(
        modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
            .archSurface(ArchShapes.banner, c.anC, c.anLine)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (countdown != null) {
            Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                val track = c.anG.copy(alpha = 0.2f)
                val arc = c.anG
                Canvas(Modifier.fillMaxSize()) {
                    val w = 5.dp.toPx()
                    val inset = w / 2f
                    val box = Size(size.width - w, size.height - w)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), box, style = Stroke(w))
                    val sweep = 360f * countdown.remaining.coerceIn(0f, 1f)
                    // starts at 12 o'clock and shrinks toward the start edge of the layout
                    drawArc(arc, -90f, if (rtl) -sweep else sweep, false, Offset(inset, inset), box, style = Stroke(w, cap = StrokeCap.Round))
                }
                Text(
                    countdown.label,
                    style = TextStyle(fontFamily = AndarzFonts.plex, fontWeight = FontWeight.W600, fontSize = 14.sp, fontFeatureSettings = "tnum"),
                    maxLines = 1,
                )
            }
        } else {
            ArchTile(c.anSoft) { GlyphIcon(Glyph.LOCK, ui().t("a11y.lockedState"), tint = c.an, size = 20.dp, strokeWidth = 2.4f) }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W700), color = c.an)
            Text(body, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 22.sp), modifier = Modifier.padding(top = 2.dp))
            if (until != null && countdown == null) Text(until, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
            actions.forEach { (label, go) -> OutlinedButton(onClick = go, modifier = Modifier.padding(top = 8.dp)) { Text(label) } }
        }
    }
}

/** 6.4.5 RChip: signed R in a pill, container by sign, and a filled triangle so sign is never only colour. */
@Composable
fun RChip(r: Double?) {
    val c = LocalAndarzColors.current
    val u = ui()
    val rounded = r?.let { ir.andarz.engine.XlMath.round(it, 2) }
    val neutral = rounded == null || rounded == 0.0
    val container = when { neutral -> c.high; rounded!! > 0 -> c.fzC; else -> c.anC }
    val color = when { neutral -> c.hmuted; rounded!! > 0 -> c.fz; else -> c.an }
    val glyph = when { neutral -> Glyph.BAR_FILL; rounded!! > 0 -> Glyph.TRI_UP; else -> Glyph.TRI_DOWN }
    Row(
        Modifier.height(22.dp).background(container, RoundedCornerShape(11.dp)).padding(horizontal = 8.dp)
            .semantics { contentDescription = (if ((rounded ?: 0.0) >= 0) u.t("a11y.positive") else u.t("a11y.negative")) + " " + u.rChip(r) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GlyphIcon(glyph, null, tint = color, size = 9.dp)
        Text(u.rChip(r), color = color, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700), maxLines = 1)
    }
}

/** The solid container that goes with a tone colour: turquoise, saffron, coral and lapis have one; anything else is `high`. */
@Composable
private fun containerFor(color: Color): Color {
    val c = LocalAndarzColors.current
    return when (color) {
        c.allowed -> c.fzC
        c.caution -> c.sfC
        c.redline -> c.anC
        c.info -> c.lapisC
        else -> c.high
    }
}

/** A small pill with text, 24 dp tall. The default is the neutral `high` container. */
@Composable
fun TagChip(text: String, color: Color = LocalAndarzColors.current.textMuted) {
    val c = LocalAndarzColors.current
    val neutral = color == c.textMuted
    Box(
        Modifier.heightIn(min = 24.dp).background(containerFor(color), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (neutral) c.hmuted else color, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700))
    }
}

/**
 * 6.4.8 SectionCard: an arch card (24 dp over 12 dp corners) on the `sf1` step with a divider ring and a top
 * highlight; an optional heading as a TalkBack heading, with an action at its end edge.
 */
@Composable
fun SectionCard(title: String?, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null, body: @Composable () -> Unit) {
    ArchCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        if (title != null || action != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
                action?.invoke()
            }
            Spacer(Modifier.height(10.dp))
        }
        body()
    }
}

/** A label/value row; the value sits at the end edge. */
@Composable
fun KeyValue(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = LocalAndarzColors.current.muted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

/** 6.4.8 StatTile: a muted label, the figure in Plex 22, a status pill, the target. A 20 dp tile with a ring. */
@Composable
fun StatTile(label: String, value: String, status: String?, statusOk: Boolean?, target: String?, modifier: Modifier = Modifier) {
    val c = LocalAndarzColors.current
    Column(
        modifier.archSurface(RoundedCornerShape(20.dp), c.sf1, c.divider, c.edge).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.muted)
        Text(value, style = TextStyle(fontFamily = AndarzFonts.plex, fontWeight = FontWeight.W600, fontSize = 22.sp, lineHeight = 26.sp, fontFeatureSettings = "tnum"))
        if (status != null) TagChip(status, if (statusOk == true) c.allowed else if (statusOk == false) c.caution else c.textMuted)
        if (target != null) Text(target, style = MaterialTheme.typography.labelMedium, color = c.muted)
    }
}

/**
 * A checklist row (5.3 step 3): fully tappable, at least 56 dp, one TalkBack node with the on/off state word
 * (6.8, F10). The mark is a 28 dp circle: turquoise with a check when done, a 2 dp ring when open, a dashed ring
 * when the item is derived and cannot be ticked by hand (the "auto" pill). Disabled rows still read their state.
 */
@Composable
fun ChecklistRow(title: String, detail: String?, checked: Boolean, enabled: Boolean, warning: String? = null, tag: String? = null, onToggle: () -> Unit) {
    val u = ui()
    val c = LocalAndarzColors.current
    val state = u.t(if (checked) "a11y.toggleOn" else "a11y.toggleOff")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled, role = Role.Checkbox, onClick = onToggle)
            .semantics(mergeDescendants = true) { stateDescription = state }
            .drawBehind { drawLine(c.divider, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), strokeWidth = 1.dp.toPx()) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CheckMark(checked, dashed = !enabled && !checked)
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = if (checked) c.text else c.muted)
            if (detail != null) Text(detail, style = MaterialTheme.typography.labelMedium, color = c.muted)
            if (warning != null) Text(warning, style = MaterialTheme.typography.labelMedium, color = c.caution)
        }
        if (tag != null) {
            Text(
                tag,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W500),
                color = c.muted,
                modifier = Modifier.background(c.high, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

/** The 28 dp mark of a checklist row. */
@Composable
fun CheckMark(checked: Boolean, dashed: Boolean = false, modifier: Modifier = Modifier) {
    val c = LocalAndarzColors.current
    Box(
        modifier.size(28.dp).then(if (checked) Modifier.background(c.fzFill, CircleShape) else Modifier.drawBehind {
            val w = 2.dp.toPx()
            drawCircle(
                c.outline, radius = (size.minDimension - w) / 2f, style = Stroke(
                    width = w,
                    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.5.dp.toPx())) else null,
                ),
            )
        }),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) GlyphIcon(Glyph.CHECK, null, tint = c.onFz, size = 16.dp, strokeWidth = 3f)
    }
}

/** A neutral loading state (M7 empty/error pass): one muted line, announced politely; no spinner animation. */
@Composable
fun LoadingState() {
    val u = ui()
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            u.t("common.loading"), style = MaterialTheme.typography.bodyMedium, color = LocalAndarzColors.current.muted,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** An error line with a retry action (`err.generic` + `common.retry`). */
@Composable
fun ErrorState(text: String, onRetry: (() -> Unit)? = null) {
    val u = ui()
    val c = LocalAndarzColors.current
    Column(Modifier.fillMaxWidth().padding(24.dp).semantics { liveRegion = LiveRegionMode.Assertive }, horizontalAlignment = Alignment.CenterHorizontally) {
        ArchTile(c.anSoft, width = 44.dp, height = 52.dp, bottom = 12.dp) { GlyphIcon(Glyph.ALERT_TRIANGLE, null, tint = c.an, size = 22.dp) }
        Spacer(Modifier.height(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.an, textAlign = TextAlign.Center)
        if (onRetry != null) TextButton(onClick = onRetry) { Text(u.t("common.retry")) }
    }
}

/** 6.4.8 EmptyState: an arch tile with a glyph, one sentence, an optional action. */
@Composable
fun EmptyState(text: String, action: Pair<String, () -> Unit>? = null) {
    val c = LocalAndarzColors.current
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ArchTile(c.high, width = 44.dp, height = 52.dp, bottom = 12.dp) { GlyphIcon(Glyph.INFO, null, tint = c.muted, size = 22.dp) }
        Spacer(Modifier.height(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.muted, textAlign = TextAlign.Center)
        if (action != null) TextButton(onClick = action.second) { Text(action.first) }
    }
}

/** The figure of an input: Plex 22, tabular. */
private val inputFigure = TextStyle(fontFamily = AndarzFonts.plex, fontWeight = FontWeight.W600, fontSize = 22.sp, lineHeight = 30.sp, fontFeatureSettings = "tnum")

/**
 * The field of the canvas: an arch on the `alt` step with an `outline` ring, a muted label above and the figure in
 * Plex 22 below. Focus draws a 2 dp lapis ring 2 dp outside; an error turns the ring red and adds the message.
 */
@Composable
private fun FieldBox(
    label: String,
    errorText: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val c = LocalAndarzColors.current
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.hasFocus }
            .drawBehind {
                if (focused) {
                    val g = 3.dp.toPx()
                    drawRoundRect(
                        c.lapis, Offset(-g, -g), Size(size.width + 2 * g, size.height + 2 * g), CornerRadius(22.dp.toPx()), style = Stroke(2.dp.toPx()),
                    )
                }
            }
            .archSurface(ArchShapes.field, c.alt, if (errorText != null) c.an else c.outline)
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 8.dp)
            .semantics(mergeDescendants = true) { if (errorText != null) error(errorText) },
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.muted)
        content()
    }
}

/**
 * 6.3 NumberField: accepts Persian/Arabic digits and separators while typing; the raw text is kept, the
 * parsed value is shown below when it differs. Parsing is `Fmt.normalizeInput`.
 */
@Composable
fun NumberField(label: String, text: String, onText: (String) -> Unit, modifier: Modifier = Modifier, error: String? = null, suffix: String? = null, hint: String? = null) {
    val u = ui()
    val c = LocalAndarzColors.current
    val parsed = Fmt.normalizeInput(text)
    Column(modifier) {
        FieldBox(label, errorText = error) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = text, onValueChange = onText, singleLine = true,
                    textStyle = inputFigure.copy(color = c.text),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    cursorBrush = SolidColor(c.lapis),
                    modifier = Modifier.weight(1f),
                )
                if (suffix != null) Text(suffix, style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.padding(start = 8.dp))
            }
        }
        val support = when {
            error != null -> error
            parsed != null && Fmt.toLatinDigits(text.trim()) != parsed.toString().removeSuffix(".0") -> u.num(parsed, 8, trim = true)
            else -> hint
        }
        if (support != null) {
            Text(support, style = MaterialTheme.typography.labelMedium, color = if (error != null) c.an else c.muted, modifier = Modifier.padding(start = 14.dp, top = 4.dp))
        }
    }
}

/** A labelled switch row (48 dp target), exposing on/off to TalkBack. */
@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true, hint: String? = null) {
    val u = ui()
    val c = LocalAndarzColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .semantics(mergeDescendants = true) { stateDescription = u.t(if (checked) "a11y.toggleOn" else "a11y.toggleOff") },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) Text(hint, style = MaterialTheme.typography.labelMedium, color = c.muted)
        }
        AndarzSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** 6.4.8 ReasonField: multiline with the live counter, in the same arch field as the numbers. */
@Composable
fun ReasonField(label: String, text: String, onText: (String) -> Unit, min: Int, counter: (Int) -> String, hint: String? = null) {
    val c = LocalAndarzColors.current
    val n = text.trim().length
    val short = n in 1 until min
    Column(Modifier.fillMaxWidth()) {
        FieldBox(label, errorText = null) {
            BasicTextField(
                value = text, onValueChange = onText, minLines = 3,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.text),
                cursorBrush = SolidColor(c.lapis),
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty() && hint != null) Text(hint, style = MaterialTheme.typography.bodyLarge, color = c.muted)
                        inner()
                    }
                },
            )
        }
        Text(counter(n), style = MaterialTheme.typography.labelMedium, color = if (short) c.an else c.muted, modifier = Modifier.padding(start = 14.dp, top = 4.dp))
    }
}

/**
 * 6.4.9 KhatamPattern: a lattice of eight-pointed stars (two squares, one turned 45 degrees) on a 48 dp tile,
 * 1 dp strokes at 6% of the text colour, static. Clipping to the card is the parent's job.
 */
@Composable
fun KhatamPattern(modifier: Modifier = Modifier) {
    val color = LocalAndarzColors.current.text.copy(alpha = 0.06f)
    Canvas(modifier.fillMaxSize()) {
        val tile = 48.dp.toPx()
        val half = 10.dp.toPx()
        val stroke = Stroke(width = 1.dp.toPx())
        var y = tile / 2
        while (y < size.height + tile) {
            var x = tile / 2
            while (x < size.width + tile) {
                val sq = Path().apply {
                    moveTo(x - half, y - half); lineTo(x + half, y - half); lineTo(x + half, y + half); lineTo(x - half, y + half); close()
                }
                drawPath(sq, color, style = stroke)
                rotate(45f, Offset(x, y)) { drawPath(sq, color, style = stroke) }
                x += tile
            }
            y += tile
        }
    }
}
