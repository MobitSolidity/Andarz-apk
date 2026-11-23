package ir.andarz.app.ui.market

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.andarz.app.R
import ir.andarz.app.content.ContentRepository
import ir.andarz.app.ui.components.ArchShapes
import ir.andarz.app.ui.components.Leader
import ir.andarz.app.ui.components.Text
import ir.andarz.app.ui.components.archSurface
import ir.andarz.app.ui.components.figure
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui
import ir.andarz.market.model.MarketOverview
import kotlin.math.cos
import kotlin.math.sin

/*
 * The overview card of the Prices section: a strip of the terrain picture, the market cap and the Bitcoin share on
 * one side, and the Fear and Greed plaque on the other, an arch with a half-circle gauge in five classes.
 */

/** The five classes of the index, as alternative.me draws them: 0 to 24, 25 to 46, 47 to 54, 55 to 75, 76 to 100. */
internal fun fearClass(v: Int): Int = when {
    v < 25 -> 0
    v < 47 -> 1
    v < 55 -> 2
    v < 76 -> 3
    else -> 4
}

private val GAUGE_BOUNDS = intArrayOf(0, 25, 47, 55, 76, 100)

/** The card. [ov] is null while the first answer is on its way: the same shapes then show as quiet blocks. */
@Composable
internal fun OverviewCard(content: ContentRepository, fmt: MarketFmt, ov: MarketOverview?, compact: Boolean, modifier: Modifier = Modifier) {
    val u = ui()
    val k = LocalAndarzColors.current
    val fg = ov?.fearGreed
    val label = ov?.let { o -> fearKey(o.fearLabel)?.let { content.ui(it) } ?: o.fearLabel }
    Box(
        modifier.fillMaxWidth()
            .semantics { contentDescription = u.t("app.mkt.ov.title") }
            .archSurface(ArchShapes.hero, k.sf1, k.divider, k.edge, clipContent = true),
    ) {
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            Image(
                painterResource(R.drawable.art_terrain), contentDescription = null, contentScale = ContentScale.Crop,
                alignment = BiasAlignment(0f, -0.4f), modifier = Modifier.fillMaxWidth().height(48.dp),
            )
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(26.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, k.sf1))),
            )
        }
        if (compact) {
            Column(Modifier.fillMaxWidth().padding(start = 14.dp, top = 60.dp, end = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Figures(fmt, ov, Modifier.fillMaxWidth())
                FearPlaque(fg, label, ov == null, Modifier.align(Alignment.CenterHorizontally))
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 48.dp, end = 14.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Figures(fmt, ov, Modifier.weight(1f).padding(top = 12.dp))
                FearPlaque(fg, label, ov == null, Modifier.liftedBy(28.dp))
            }
        }
    }
}

/** A modifier that moves a box up by [lift] without giving the row that room: the plaque overlaps the picture strip. */
private fun Modifier.liftedBy(lift: Dp): Modifier = this.layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    val l = lift.roundToPx()
    layout(p.width, (p.height - l).coerceAtLeast(0)) { p.place(0, -l) }
}

/** The market cap and the Bitcoin share: a label, a dotted leader and a figure on one line each. */
@Composable
private fun Figures(fmt: MarketFmt, ov: MarketOverview?, modifier: Modifier) {
    val u = ui()
    val k = LocalAndarzColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (ov == null || ov.totalCap > 0) {
            FigureLine(u.t("app.mkt.ov.cap")) {
                if (ov == null) Block(64.dp) else Text(fmt.cap(ov.totalCap), style = figure(19, FontWeight.W600), maxLines = 1, softWrap = false)
            }
        }
        if (ov == null || ov.btcDominance > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FigureLine(u.t("app.mkt.ov.dom")) {
                    if (ov == null) Block(36.dp) else Text(fmt.share(ov.btcDominance), style = figure(19, FontWeight.W600), maxLines = 1, softWrap = false)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).background(k.track, RoundedCornerShape(3.dp)).clearAndSetSemantics { }) {
                    if (ov != null) {
                        val frac = (ov.btcDominance / 100.0).coerceIn(0.0, 1.0).toFloat()
                        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(k.lapis, RoundedCornerShape(3.dp)))
                    }
                }
            }
        }
    }
}

@Composable
private fun FigureLine(label: String, value: @Composable () -> Unit) {
    val k = LocalAndarzColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 28.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = k.muted, maxLines = 1, softWrap = false)
        Leader(Modifier.weight(1f))
        value()
    }
}

/** A quiet block that stands for a number that has not arrived. */
@Composable
private fun Block(width: Dp) {
    Box(Modifier.size(width, 16.dp).background(LocalAndarzColors.current.track, RoundedCornerShape(6.dp)))
}

/**
 * The Fear and Greed plaque: a 124 by 112 dp arch on `alt`, ringed by a 4 dp halo of the card colour. The gauge
 * is five arcs that start at the fear end (the start edge, mirrored for English), the arc of the current class
 * is lit and a needle points at the value. One TalkBack node with the value and its class in words.
 */
@Composable
private fun FearPlaque(value: Int?, activeLabel: String?, pending: Boolean, modifier: Modifier = Modifier) {
    val u = ui()
    val k = LocalAndarzColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val d = LocalDensity.current
    val capped = Density(d.density, minOf(d.fontScale, 1.15f))
    val desc = when {
        value != null -> u.t("app.mkt.fg.desc", "v" to u.int(value), "label" to (activeLabel ?: ""))
        pending -> u.t("app.mkt.fg.pending")
        else -> u.t("app.mkt.fg.title") + SEP + u.t("app.mkt.fg.none")
    }
    val cls = value?.let { fearClass(it) }
    val lit = when (cls) {
        0, 1 -> k.sfG
        2 -> k.lapis
        else -> k.fzG
    }
    Box(
        modifier.size(width = 124.dp, height = 112.dp)
            .drawBehind {
                val gap = 4.dp.toPx()
                val outline = ArchShapes.arch(66.dp, 22.dp).createOutline(Size(size.width + 2 * gap, size.height + 2 * gap), layoutDirection, this)
                translate(-gap, -gap) { drawOutline(outline, k.sf1) }
            }
            .archSurface(ArchShapes.arch(62.dp, 18.dp), k.alt, k.divider, k.edge)
            .clearAndSetSemantics {
                contentDescription = desc
                role = Role.Image
            },
    ) {
        CompositionLocalProvider(LocalDensity provides capped) {
            Canvas(Modifier.offset(10.dp, 12.dp).size(width = 104.dp, height = 52.dp)) {
                val cx = 52.dp.toPx()
                val cy = 49.92.dp.toPx()
                val r = 41.6.dp.toPx()
                val topLeft = Offset(cx - r, cy - r)
                val box = Size(2 * r, 2 * r)
                if (value == null) {
                    drawArc(
                        k.outline.copy(alpha = 0.8f), startAngle = if (rtl) 0f else 180f, sweepAngle = if (rtl) -180f else 180f,
                        useCenter = false, topLeft = topLeft, size = box,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, 7.dp.toPx()), 0f)),
                    )
                } else {
                    for (i in 0 until 5) {
                        val a = GAUGE_BOUNDS[i] * 1.8f + (if (i > 0) 1.5f else 0f)
                        val b = GAUGE_BOUNDS[i + 1] * 1.8f - (if (i < 4) 1.5f else 0f)
                        drawArc(
                            if (i == cls) lit else k.outline.copy(alpha = 0.5f),
                            startAngle = if (rtl) -a else 180f + a, sweepAngle = if (rtl) -(b - a) else b - a,
                            useCenter = false, topLeft = topLeft, size = box, style = Stroke(width = 7.28.dp.toPx()),
                        )
                    }
                    val th = Math.toRadians(value * 1.8)
                    val sx = if (rtl) 1f else -1f
                    fun at(rr: Float) = Offset(cx + sx * cos(th).toFloat() * rr, cy - sin(th).toFloat() * rr)
                    drawLine(k.text, at(32.24.dp.toPx()), at(47.32.dp.toPx()), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                }
            }
            if (value != null) {
                Text(
                    u.int(value), Modifier.offset(y = 36.dp).fillMaxWidth(), color = k.text,
                    style = figure(27, FontWeight.W600).copy(lineHeight = 26.4.sp), textAlign = TextAlign.Center,
                )
            } else if (!pending) {
                Text(u.t("app.mkt.fg.none"), Modifier.offset(y = 42.dp).fillMaxWidth(), color = k.muted, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
            } else {
                Box(Modifier.offset(x = 40.dp, y = 40.dp).size(width = 44.dp, height = 24.dp).background(k.track, RoundedCornerShape(8.dp)))
            }
            Row(Modifier.offset(y = 66.dp).fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(u.t("mkt.fg.Fear"), style = MaterialTheme.typography.labelMedium, color = k.muted, maxLines = 1)
                if (value != null && !activeLabel.isNullOrEmpty()) {
                    Text(activeLabel, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700), color = k.lapis, maxLines = 1)
                }
                Text(u.t("mkt.fg.Greed"), style = MaterialTheme.typography.labelMedium, color = k.muted, maxLines = 1)
            }
            Text(
                u.t("app.mkt.fg.title"), Modifier.offset(y = 87.dp).fillMaxWidth(), color = k.hmuted,
                style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1,
            )
        }
    }
}
