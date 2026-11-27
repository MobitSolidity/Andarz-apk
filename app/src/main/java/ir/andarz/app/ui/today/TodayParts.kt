package ir.andarz.app.ui.today

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ir.andarz.app.R
import ir.andarz.app.ui.components.ArchShapes
import ir.andarz.app.ui.components.ArchTile
import ir.andarz.app.ui.components.FitText
import ir.andarz.app.ui.components.HeroSurface
import ir.andarz.app.ui.components.LimitGrid
import ir.andarz.app.ui.components.TagChip
import ir.andarz.app.ui.components.TonalButton
import ir.andarz.app.ui.components.Text
import ir.andarz.app.ui.components.archSurface
import ir.andarz.app.ui.icons.Glyph
import ir.andarz.app.ui.icons.GlyphIcon
import ir.andarz.app.ui.theme.AndarzColors
import ir.andarz.app.ui.theme.AndarzFonts
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.theme.Tone
import ir.andarz.app.ui.ui

/*
 * The pieces of the Today board (Today, TodayCaution, TodayLocked of the final canvas): the header, the capital card with
 * its curve and its gate lamp, the board of six dials with the verdict strip, the restriction cards, the open position
 * card, the floating button and the advice card. They draw what they are given; what to say and which tone to use is
 * decided by TodayScreen and TodayModel.
 */

/** A figure of the board: Plex 15, tabular. */
private val PlexFigure = TextStyle(
    fontFamily = AndarzFonts.plex, fontWeight = FontWeight.W700, fontSize = 15.sp, lineHeight = 21.sp, fontFeatureSettings = "tnum",
)

/** The size of the dollar sign and of the decimals next to the big figure of the equity (23 over 42). */
private const val SMALL_FIGURE_EM = 23f / 42f

private const val LRI = '⁦'
private const val PDI = '⁩'

/** The soft tile colour that goes with a tone. */
private fun AndarzColors.soft(tone: Tone): Color = when (tone) {
    Tone.ALLOWED -> fzSoft
    Tone.CAUTION -> sfSoft
    Tone.LOCKED -> anSoft
}

// ------------------------------------------------------------------------------------------------ header

/** The mark of the app: a night tile with a small lamp post, a turquoise light and an amber base. */
@Composable
private fun AppMark() {
    val teal = Color(0xFF35D6C4)
    val amber = Color(0xFFF3B63F)
    Box(
        Modifier.size(width = 30.dp, height = 36.dp).archSurface(ArchShapes.arch(15.dp, 9.dp), Color(0xFF0E1626), Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(width = 9.dp, height = 30.dp)) {
            // the mark is drawn on a 20 by 70 grid that starts at (44, 20)
            val s = minOf(size.width / 20f, size.height / 70f)
            val ox = (size.width - 20f * s) / 2f
            fun x(v: Float) = ox + (v - 44f) * s
            fun y(v: Float) = (v - 20f) * s
            val post = Path().apply {
                moveTo(x(50.5f), y(24f)); lineTo(x(57.5f), y(24f)); lineTo(x(55.5f), y(76f)); lineTo(x(52.5f), y(76f)); close()
            }
            drawPath(post, teal)
            drawPath(post, teal, style = Stroke(width = 2f * s, join = StrokeJoin.Round))
            drawRoundRect(amber, topLeft = Offset(x(46f), y(82f)), size = Size(16f * s, 4f * s), cornerRadius = CornerRadius(2f * s))
        }
    }
}

/**
 * The top bar of Today: the mark, the name of the app with the date, and the eye that hides or shows the dollar
 * amounts (D-91). At a large font the date moves under the name.
 */
@Composable
internal fun TodayHeader(date: String, amountsHidden: Boolean, onToggleAmounts: () -> Unit, modifier: Modifier = Modifier) {
    val u = ui()
    val c = LocalAndarzColors.current
    val stacked = LocalDensity.current.fontScale > 1.3f
    val name = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, lineHeight = 27.sp)
    val dateStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp)
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppMark()
        if (stacked) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                Text(u.t("app.name"), style = name)
                Text(date, style = dateStyle, color = c.muted)
            }
        } else {
            Row(
                Modifier.weight(1f).semantics(mergeDescendants = true) { heading() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(u.t("app.name"), style = name)
                Text("· $date", style = dateStyle, color = c.muted)
            }
        }
        IconButton(onClick = onToggleAmounts, modifier = Modifier.size(48.dp)) {
            GlyphIcon(
                if (amountsHidden) Glyph.EYE_OFF else Glyph.EYE,
                u.t(if (amountsHidden) "app.amounts.show" else "app.amounts.hide"),
                tint = c.muted, size = 22.dp,
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------ capital card

internal enum class Trend { UP, DOWN, FLAT }

/** One of the three figures under the curve: today, this week, the drop from the peak. */
internal class HeroStat(val label: String, val value: String, val sub: String, val trend: Trend)

/**
 * The equity as a figure of spans: the sign and the dollar sign and the decimals small and muted, the whole part big. A hidden
 * amount ("$" and four bullets) stays one big span. The text keeps its left to right isolate, so it reads right in a
 * right to left line, and keeps its digits in one piece, so it still contains "1,234.50".
 */
internal fun equityFigure(formatted: String, small: Color): AnnotatedString {
    val bare = formatted.trim(LRI, PDI)
    val parts = splitMoney(bare)
    val dollar = parts.major.indexOf('$')
    return buildAnnotatedString {
        append(LRI)
        if (dollar >= 0 && parts.minor.isNotEmpty()) {
            withStyle(SpanStyle(fontSize = SMALL_FIGURE_EM.em, color = small)) { append(parts.major.substring(0, dollar + 1)) }
            append(parts.major.substring(dollar + 1))
            withStyle(SpanStyle(fontSize = SMALL_FIGURE_EM.em, color = small)) { append(parts.minor) }
        } else {
            append(bare)
        }
        append(PDI)
    }
}

@Composable
private fun EquityFigure(formatted: String, modifier: Modifier = Modifier) {
    val c = LocalAndarzColors.current
    Crossfade(targetState = formatted, modifier = modifier, label = "equity") { shown ->
        FitText(
            equityFigure(shown, c.hmuted), MaterialTheme.typography.displayLarge, Modifier.fillMaxWidth(),
            color = c.text, minScale = 0.4f, textAlign = TextAlign.Start,
        )
    }
}

/**
 * The curve of the account under the equity, drawn left to right whatever the layout direction (time runs that way on
 * a chart): a smooth line from the first point to the last with a faint area under it, a dashed line at the level it
 * started from, a dot at the end. When the account is below its peak the peak gets a hollow dot and a dashed line, and in
 * a locked state the way down from the peak turns coral.
 */
@Composable
internal fun EquitySparkline(values: List<Double>, tone: Tone, description: String, modifier: Modifier = Modifier) {
    val c = LocalAndarzColors.current
    val peak = remember(values) { peakIndex(values) }
    val last = values.lastIndex
    val below = values.size > 1 && values[last] < values[peak]
    val locked = below && tone == Tone.LOCKED
    val line = c.fzG
    val tail = if (locked) c.anG else c.fzG
    val fill = if (locked) c.hmuted else c.fzG
    val fillAlpha = if (locked) 0.20f else 0.30f
    val hollow = c.h2
    val guide = c.outline
    Canvas(
        modifier.fillMaxWidth().height(44.dp).semantics { contentDescription = description; role = Role.Image },
    ) {
        val w = size.width - 7.dp.toPx() // room for the halo of the end dot
        val top = 6.5.dp.toPx()
        val bottom = 39.dp.toPx()
        val lo = values.min()
        val hi = values.max()
        fun px(i: Int): Float = if (last == 0) w else w * i / last
        fun py(v: Double): Float = if (hi == lo) (top + bottom) / 2f else (bottom - (v - lo) / (hi - lo) * (bottom - top)).toFloat()
        fun curve(from: Int, to: Int): Path = Path().apply {
            moveTo(px(from), py(values[from]))
            for (i in from + 1..to) {
                val x0 = px(i - 1); val y0 = py(values[i - 1]); val x1 = px(i); val y1 = py(values[i])
                val dx = (x1 - x0) * 0.45f
                cubicTo(x0 + dx, y0, x1 - dx, y1, x1, y1)
            }
        }

        val startY = py(values.first())
        drawLine(
            guide.copy(alpha = 0.6f), Offset(0f, startY), Offset(size.width, startY), strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
        )
        if (below) {
            val peakY = py(values[peak])
            drawLine(
                guide.copy(alpha = 0.7f), Offset(px(peak), peakY), Offset(w, peakY), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())),
            )
        }

        val area = curve(0, last).apply { lineTo(px(last), size.height); lineTo(px(0), size.height); close() }
        drawPath(area, Brush.verticalGradient(listOf(fill.copy(alpha = fillAlpha), fill.copy(alpha = 0f)), startY = 0f, endY = size.height))

        val stroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        if (locked) {
            drawPath(curve(0, peak), line, style = stroke)
            drawPath(curve(peak, last), tail, style = stroke)
            drawLine(tail.copy(alpha = 0.8f), Offset(w, py(values[peak])), Offset(w, py(values[last])), strokeWidth = 1.5.dp.toPx())
        } else {
            drawPath(curve(0, last), line, style = stroke)
        }
        if (below) {
            val center = Offset(px(peak), py(values[peak]))
            drawCircle(hollow, 3.dp.toPx(), center)
            drawCircle(line, 3.dp.toPx(), center, style = Stroke(1.6.dp.toPx()))
        }
        val end = Offset(px(last), py(values[last]))
        drawCircle(tail.copy(alpha = 0.22f), 7.dp.toPx(), end)
        drawCircle(tail, 3.4.dp.toPx(), end)
    }
}

/**
 * The gate lamp of the card: a 76 by 96 dp arch window onto the picture of the state (open, half open, closed), a ring
 * around it in the colour of the state, and a pill under it that says the state in one word. One TalkBack node.
 */
@Composable
internal fun GateLamp(tone: Tone, caption: String, description: String, modifier: Modifier = Modifier) {
    val c = LocalAndarzColors.current
    val art = when (tone) {
        Tone.ALLOWED -> R.drawable.art_gate_open
        Tone.CAUTION -> R.drawable.art_gate_caution
        Tone.LOCKED -> R.drawable.art_gate_closed
    }
    val glyph = when (tone) {
        Tone.ALLOWED -> Glyph.CHECK
        Tone.CAUTION -> Glyph.ALERT_CIRCLE
        Tone.LOCKED -> Glyph.LOCK
    }
    val window = ArchShapes.arch(38.dp, 12.dp)
    val ring = c.ring(tone)
    val ink = c.accent(tone)
    Box(
        modifier.padding(bottom = 12.dp).size(width = 76.dp, height = 96.dp)
            .drawBehind {
                // 1.5 dp ring, 4 dp clear of the window
                val gap = 4.75.dp.toPx()
                val outline = ArchShapes.arch(42.75.dp, 16.75.dp).createOutline(Size(size.width + 2 * gap, size.height + 2 * gap), layoutDirection, this)
                translate(-gap, -gap) { drawOutline(outline, ring, style = Stroke(1.5.dp.toPx())) }
            }
            .clearAndSetSemantics { contentDescription = description; role = Role.Image },
    ) {
        Image(
            painterResource(art), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(window).drawWithContent {
                drawContent()
                drawOutline(window.createOutline(size, layoutDirection, this), Color.White.copy(alpha = 0.12f), style = Stroke(1.dp.toPx()))
            },
        )
        Box(Modifier.align(Alignment.BottomCenter).wrapContentWidth(unbounded = true).offset(y = 13.dp)) {
            // the 2 dp ring in the colour of the card cuts the pill out of the picture
            Box(Modifier.background(c.h2, ArchShapes.pill).padding(2.dp)) {
                Row(
                    Modifier.heightIn(min = 24.dp).background(c.container(tone), ArchShapes.pill).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    GlyphIcon(glyph, null, tint = ink, size = 12.dp, strokeWidth = if (tone == Tone.ALLOWED) 3f else 2.6f)
                    Text(
                        caption, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700), color = ink,
                        maxLines = 1, softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCell(s: HeroStat, modifier: Modifier) {
    val c = LocalAndarzColors.current
    val tint = when (s.trend) {
        Trend.UP -> c.fz
        Trend.DOWN -> c.an
        Trend.FLAT -> c.text
    }
    val glyph = when (s.trend) {
        Trend.UP -> Glyph.TRI_UP
        Trend.DOWN -> Glyph.TRI_DOWN
        Trend.FLAT -> Glyph.BAR_FILL
    }
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(s.label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W500), color = c.hmuted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            GlyphIcon(glyph, null, tint = tint, size = 11.dp)
            FitText(s.value, PlexFigure, Modifier.weight(1f), color = tint, minScale = 0.5f, textAlign = TextAlign.Start)
        }
        FitText(s.sub, MaterialTheme.typography.labelMedium, Modifier.fillMaxWidth(), color = c.hmuted, minScale = 0.6f, textAlign = TextAlign.Start)
    }
}

/** Today, this week, the drop from the peak: three columns under a hairline, with hairlines between them. */
@Composable
private fun StatColumns(stats: List<HeroStat>, modifier: Modifier = Modifier) {
    val c = LocalAndarzColors.current
    Row(
        modifier.fillMaxWidth().padding(top = 6.dp)
            .drawBehind { drawLine(c.divider, Offset(0f, 0.5f), Offset(size.width, 0.5f), strokeWidth = 1.dp.toPx()) }
            .padding(top = 6.dp)
            .height(IntrinsicSize.Min),
    ) {
        stats.forEachIndexed { i, s ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(c.divider))
            StatCell(
                s,
                Modifier.weight(1f).padding(
                    start = if (i == 0) 0.dp else 10.dp,
                    end = if (i == stats.lastIndex) 0.dp else if (i == 0) 8.dp else 10.dp,
                ),
            )
        }
    }
}

/** The capital card: the equity, its curve, the lamp of the day and the three figures under them. */
@Composable
internal fun HeroCard(
    tone: Tone,
    eyebrow: String,
    equity: String,
    curve: List<Double>,
    curveDescription: String,
    lampCaption: String,
    lampDescription: String,
    stats: List<HeroStat>,
    modifier: Modifier = Modifier,
) {
    val c = LocalAndarzColors.current
    HeroSurface(tone, modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(eyebrow, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W500), color = c.hmuted)
                    EquityFigure(equity)
                    if (curve.size >= 2) EquitySparkline(curve, tone, curveDescription)
                }
                GateLamp(tone, lampCaption, lampDescription)
            }
            StatColumns(stats)
        }
    }
}

// ------------------------------------------------------------------------------------------------ limits board

/** A heading of the board: a small arch in the colour of the state, then the title (a TalkBack heading). */
@Composable
internal fun SectionHeading(title: String, tone: Tone = Tone.ALLOWED, modifier: Modifier = Modifier) {
    val c = LocalAndarzColors.current
    Row(
        modifier.semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(width = 8.dp, height = 13.dp).background(c.accentG(tone), ArchShapes.arch(4.dp, 2.dp)))
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 25.sp))
    }
}

/**
 * The verdict of the day at the foot of the limits board: a container in the colour of the state, an arch tile with its
 * glyph, one line that says it and, when there is something to add, a second line. A polite live region that also
 * exposes the state as a word (F10).
 */
@Composable
internal fun VerdictStrip(tone: Tone, glyph: Glyph, title: String, detail: String?, stateWord: String, modifier: Modifier = Modifier) {
    val c = LocalAndarzColors.current
    val ink = c.accent(tone)
    Row(
        modifier.fillMaxWidth()
            .background(c.container(tone))
            .drawBehind { drawLine(c.ring(tone), Offset(0f, 0.5f), Offset(size.width, 0.5f), strokeWidth = 1.dp.toPx()) }
            .heightIn(min = 40.dp)
            .padding(horizontal = 14.dp, vertical = if (detail != null) 8.dp else 4.dp)
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                stateDescription = stateWord
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ArchTile(c.soft(tone), width = 24.dp, height = 28.dp, bottom = 7.dp) { GlyphIcon(glyph, null, tint = ink, size = 16.dp, strokeWidth = 2.4f) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.W700), color = ink)
            if (detail != null) Text(detail, style = MaterialTheme.typography.labelMedium, color = c.hmuted)
        }
    }
}

/**
 * The board of six dials: a heading, the dials in three columns with hairlines between them, the verdict strip. The card
 * is a uniform 24 dp shape, so its square cells can be clipped to it (only unequal corners break hit-testing in tests).
 */
@Composable
internal fun LimitsCard(title: String, tone: Tone, cells: List<@Composable () -> Unit>, modifier: Modifier = Modifier, strip: @Composable () -> Unit) {
    val c = LocalAndarzColors.current
    Column(modifier.fillMaxWidth().archSurface(RoundedCornerShape(24.dp), c.sf1, c.divider, c.edge, clipContent = true)) {
        SectionHeading(title, tone, Modifier.padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 4.dp))
        LimitGrid(3, topLine = true, cells = cells)
        strip()
    }
}

/**
 * A restriction in force (a reduced risk cap, the hold after a big win, a missing pledge): an arch card with a saffron
 * ring, an arch tile with a glyph, a title and the lines that say why.
 */
@Composable
internal fun RestrictionCard(glyph: Glyph, title: String, lines: List<String>, modifier: Modifier = Modifier) {
    val c = LocalAndarzColors.current
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp)
            .archSurface(ArchShapes.arch(20.dp, 14.dp), c.sf1, c.sfLine, c.edge)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ArchTile(c.sfSoft, Modifier.padding(top = 2.dp), width = 24.dp, height = 28.dp, bottom = 7.dp) {
            GlyphIcon(glyph, null, tint = c.sf, size = 15.dp, strokeWidth = 2.4f)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.W700))
            lines.forEach { Text(it, style = MaterialTheme.typography.labelMedium, color = c.muted) }
        }
    }
}

// ------------------------------------------------------------------------------------------------ open position

/** A tile of the position card: a label (with a glyph) over a figure (with a glyph). */
internal class PositionTile(
    val label: String,
    val value: String,
    val labelGlyph: Glyph? = null,
    val labelTint: Color = Color.Unspecified,
    val valueGlyph: Glyph? = null,
    val valueTint: Color = Color.Unspecified,
)

@Composable
private fun SidePill(text: String, short: Boolean) {
    val c = LocalAndarzColors.current
    Row(
        Modifier.heightIn(min = 24.dp).background(c.lapisC, ArchShapes.pill).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        GlyphIcon(if (short) Glyph.TRI_DOWN else Glyph.TRI_UP, null, tint = c.lapis, size = 10.dp)
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700), color = c.lapis, maxLines = 1)
    }
}

@Composable
private fun PositionTitle(symbol: String, side: String, short: Boolean, demo: String?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(symbol, style = PlexFigure.copy(fontSize = 16.sp, lineHeight = 22.sp), maxLines = 1)
        SidePill(side, short)
        if (demo != null) TagChip(demo)
    }
}

/** "I checked it" with the time of the last check: a 48 dp target in lapis. */
@Composable
private fun MonitorButton(label: String, onClick: () -> Unit) {
    val c = LocalAndarzColors.current
    Row(
        Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GlyphIcon(Glyph.CHECK_CIRCLE, null, tint = c.lapis, size = 16.dp, strokeWidth = 2f)
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W700), color = c.lapis)
    }
}

@Composable
private fun PositionTileView(t: PositionTile, modifier: Modifier) {
    val c = LocalAndarzColors.current
    val labelTint = if (t.labelTint == Color.Unspecified) c.muted else t.labelTint
    val valueTint = if (t.valueTint == Color.Unspecified) c.text else t.valueTint
    Column(modifier.background(c.alt, RoundedCornerShape(14.dp)).padding(horizontal = 8.dp, vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (t.labelGlyph != null) GlyphIcon(t.labelGlyph, null, tint = labelTint, size = 9.dp)
            FitText(t.label, MaterialTheme.typography.labelMedium, Modifier.weight(1f), color = c.muted, minScale = 0.7f, textAlign = TextAlign.Start)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (t.valueGlyph != null) GlyphIcon(t.valueGlyph, null, tint = valueTint, size = 13.dp, strokeWidth = 2.2f)
            FitText(t.value, PlexFigure, Modifier.weight(1f), color = valueTint, minScale = 0.6f, textAlign = TextAlign.Start)
        }
    }
}

/**
 * An open position: the symbol, the side, a demo tag, the check button, four tiles (entry, stop, target, time stop) and a
 * button to manage it. At a large font the tiles go two to a row and the check button under the symbol.
 */
@Composable
internal fun PositionCard(
    symbol: String,
    side: String,
    short: Boolean,
    demo: String?,
    tiles: List<PositionTile>,
    monitorLabel: String,
    onMonitor: () -> Unit,
    manageLabel: String,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAndarzColors.current
    val wide = LocalDensity.current.fontScale > 1.3f
    Column(
        modifier.fillMaxWidth().archSurface(RoundedCornerShape(22.dp), c.sf1, c.divider, c.edge).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (wide) {
            PositionTitle(symbol, side, short, demo)
            MonitorButton(monitorLabel, onMonitor)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PositionTitle(symbol, side, short, demo, Modifier.weight(1f))
                MonitorButton(monitorLabel, onMonitor)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val perRow = if (wide) 2 else 4
            tiles.chunked(perRow).forEach { rowTiles ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 0 until perRow) {
                        val t = rowTiles.getOrNull(i)
                        if (t != null) PositionTileView(t, Modifier.weight(1f).fillMaxHeight()) else Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        // at least 88 dp wide, like the buttons of the board
        TonalButton(onClick = onManage, modifier = Modifier.defaultMinSize(minWidth = 88.dp)) { Text(manageLabel) }
    }
}

// ------------------------------------------------------------------------------------------------ floating button, advice, close day

/**
 * The floating "new trade" button: a 56 dp turquoise pill with a plus. When entry is closed it turns into a muted pill with
 * a lock; it still opens the wizard, which says why entry is closed. A bare clickable with a button role: the button
 * has no Material FAB shape to keep uniform.
 */
@Composable
internal fun NewTradeFab(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalAndarzColors.current
    val container = if (enabled) c.fzFill else c.high
    val content = if (enabled) c.onFz else c.muted
    Row(
        modifier
            .shadow(6.dp, ArchShapes.pill, clip = false, ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f))
            .clip(ArchShapes.pill)
            .archSurface(
                ArchShapes.pill, container, if (enabled) Color.Transparent else c.divider,
                shine = if (enabled) Color.White.copy(alpha = 0.35f) else c.edge,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlyphIcon(if (enabled) Glyph.PLUS else Glyph.LOCK, null, tint = content, size = 22.dp, strokeWidth = 2.4f)
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700), color = content)
    }
}

/** The advice of the day: a strip of the teal tile art, the label with a small crosshair, the quote in Naskh. */
@Composable
internal fun AdviceCard(label: String, quote: String, modifier: Modifier = Modifier) {
    val c = LocalAndarzColors.current
    Column(modifier.fillMaxWidth().archSurface(RoundedCornerShape(24.dp), c.sf1, c.divider, c.edge, clipContent = true)) {
        Image(
            painterResource(R.drawable.art_tile_teal), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        )
        Column(Modifier.padding(start = 18.dp, top = 12.dp, end = 18.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArchTile(c.fzC, width = 18.dp, height = 22.dp, bottom = 5.dp) { GlyphIcon(Glyph.CROSSHAIR, null, tint = c.fz, size = 11.dp, strokeWidth = 2.6f) }
                Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700), color = c.hmuted)
            }
            Text(
                quote,
                style = TextStyle(fontFamily = AndarzFonts.naskh, fontWeight = FontWeight.W500, fontSize = 21.sp, lineHeight = 42.sp),
                color = c.text,
            )
        }
    }
}

/** Close the day and review it: an outlined arch button, 52 dp tall, with a moon. */
@Composable
internal fun CloseDayButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalAndarzColors.current
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp)
            .archSurface(ArchShapes.arch(26.dp, 16.dp), Color.Transparent, c.outline)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.MOON, null, tint = c.hmuted, size = 20.dp)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700))
    }
}
