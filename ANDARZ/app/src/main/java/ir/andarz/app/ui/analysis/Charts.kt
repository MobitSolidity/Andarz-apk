package ir.andarz.app.ui.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.andarz.app.ui.components.SectionCard
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui

/*
 * Charts of 6.5, drawn with Canvas (no chart library). Time runs left to right even in RTL, as in every
 * financial chart; labels stay in the RTL text flow. Each chart is one TalkBack node whose description is
 * `ins.chart.alt` with a short summary, so a screen-reader user gets the same facts (accessibility pass 1).
 */

/** A chart card: title, optional note, the chart, or `ins.chart.empty`. */
@Composable
fun ChartCard(title: String, summary: String, empty: Boolean, note: String? = null, chart: @Composable () -> Unit) {
    val u = ui()
    SectionCard(title) {
        if (note != null) Text(note, style = MaterialTheme.typography.labelMedium, color = LocalAndarzColors.current.textMuted)
        if (empty) {
            Text(u.t("ins.chart.empty"), style = MaterialTheme.typography.bodyMedium, color = LocalAndarzColors.current.textMuted)
        } else {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).clearAndSetSemantics { contentDescription = u.t("ins.chart.alt", "title" to title, "summary" to summary) }) {
                chart()
            }
        }
    }
}

/** A line over [points]; [baseline] draws a dashed reference (e.g. the start equity), [fillDown] shades below. */
@Composable
fun LineChart(points: List<Double>, color: Color, modifier: Modifier = Modifier, baseline: Double? = null, invert: Boolean = false, fillDown: Boolean = false) {
    val grid = LocalAndarzColors.current.divider
    Canvas(modifier.fillMaxWidth().height(160.dp)) {
        if (points.size < 2) return@Canvas
        val all = if (baseline != null) points + baseline else points
        val min = all.min()
        val max = all.max()
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        fun y(v: Double): Float {
            val f = ((v - min) / span).toFloat()
            return if (invert) size.height * f else size.height * (1 - f)
        }
        val stepX = size.width / (points.size - 1)
        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        if (baseline != null) {
            drawLine(grid, Offset(0f, y(baseline)), Offset(size.width, y(baseline)), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        }
        val path = Path()
        points.forEachIndexed { i, v -> if (i == 0) path.moveTo(0f, y(v)) else path.lineTo(i * stepX, y(v)) }
        if (fillDown) {
            val area = Path().apply { addPath(path); lineTo(size.width, y(if (invert) min else min)); lineTo(0f, y(min)); close() }
            drawPath(area, color.copy(alpha = 0.18f))
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, 3.dp.toPx(), Offset(size.width, y(points.last())))
    }
}

/**
 * Vertical bars with labels under them; negative values hang below a zero line. Colour carries sign, and
 * the value is printed on every bar, so colour is never the only signal (6.1).
 */
@Composable
fun BarChart(bars: List<Bar>, label: (Bar) -> String, value: (Bar) -> String, modifier: Modifier = Modifier, signed: Boolean = true, color: Color? = null) {
    val c = LocalAndarzColors.current
    val maxAbs = bars.maxOfOrNull { kotlin.math.abs(it.value) }?.takeIf { it > 0 } ?: 1.0
    val hasNeg = signed && bars.any { it.value < 0 }
    // Bars read left to right in both directions, like the line charts.
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            for (b in bars) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(value(b), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    val col = color ?: if (b.value < 0) c.redline else c.allowed
                    Canvas(Modifier.fillMaxWidth().height(if (hasNeg) 120.dp else 90.dp)) {
                        val zeroY = if (hasNeg) size.height / 2 else size.height
                        val h = (kotlin.math.abs(b.value) / maxAbs).toFloat() * (if (hasNeg) size.height / 2 else size.height)
                        val w = size.width * 0.7f
                        val x = (size.width - w) / 2
                        if (b.value >= 0) drawRect(col, Offset(x, zeroY - h), Size(w, h)) else drawRect(col, Offset(x, zeroY), Size(w, h))
                        drawLine(c.divider, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.dp.toPx())
                    }
                    Text(label(b), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

/** Horizontal ranked bars (symbols, setups, mistakes): label, track, value; one row per item. */
@Composable
fun RankBars(bars: List<Bar>, label: (Bar) -> String, value: (Bar) -> String, signed: Boolean = true) {
    val c = LocalAndarzColors.current
    val maxAbs = bars.maxOfOrNull { kotlin.math.abs(it.value) }?.takeIf { it > 0 } ?: 1.0
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (b in bars) {
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                Text(label(b), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(110.dp), maxLines = 1)
                val col = if (!signed) c.info else if (b.value < 0) c.redline else c.allowed
                Canvas(Modifier.weight(1f).height(10.dp)) {
                    drawRect(c.divider, Offset.Zero, size)
                    val w = (kotlin.math.abs(b.value) / maxAbs).toFloat() * size.width
                    drawRect(col, Offset.Zero, Size(w, size.height))
                }
                Text(value(b), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp).width(64.dp), maxLines = 1)
            }
        }
    }
}
