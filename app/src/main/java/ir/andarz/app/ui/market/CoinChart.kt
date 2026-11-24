package ir.andarz.app.ui.market

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.andarz.app.ui.components.Text
import ir.andarz.app.ui.theme.LocalAndarzColors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/*
 * The chart of the coin sheet on the canvas: three dotted price levels with their labels at the start edge, the
 * line with its soft area and an end dot, and a day axis. Charts always read left to right, in both languages.
 */

/** The height of the chart, in dp; the numbers below are positions inside it. */
private const val CHART_HEIGHT = 136f

/** Width reserved for the price labels. */
private const val GUTTER = 44f

/** Room at the end edge so the end dot is not cut. */
private const val END_PAD = 8f

/** The highest and the lowest y of the line, and the y of the day axis. */
private const val TOP = 12f
private const val BOTTOM = 104f
private const val AXIS = 118f

/** The largest font scale the text inside a diagram follows; the diagram would not fit beyond it. */
private const val TEXT_CAP = 1.15f

/** A round step at least as large as [raw]: 1, 2 or 5 times a power of ten. */
internal fun niceStep(raw: Double): Double {
    if (raw <= 0.0 || raw.isNaN() || raw.isInfinite()) return 1.0
    val mag = 10.0.pow(floor(log10(raw)))
    val n = raw / mag
    val f = when {
        n <= 1.0 -> 1.0
        n <= 2.0 -> 2.0
        n <= 5.0 -> 5.0
        else -> 10.0
    }
    return f * mag
}

/** The vertical scale of a chart: the data range and up to three round levels inside it, each with its label. */
internal class ChartScale(val lo: Double, val hi: Double, val levels: List<Double>, val labels: List<String>) {
    /** 0 at the top of the line area and 1 at its bottom. */
    fun frac(v: Double): Float = if (hi <= lo) 0.5f else ((hi - v) / (hi - lo)).toFloat().coerceIn(0f, 1f)
}

internal fun chartScale(points: List<Double>, fmt: MarketFmt): ChartScale {
    if (points.isEmpty()) return ChartScale(0.0, 1.0, emptyList(), emptyList())
    var lo = points.min()
    var hi = points.max()
    if (hi - lo < 1e-12) {
        val m = if (hi == 0.0) 1.0 else abs(hi) * 0.01
        lo -= m
        hi += m
    }
    val step = niceStep((hi - lo) / 3.0)
    val levels = ArrayList<Double>(3)
    var n = ceil(lo / step - 1e-9)
    while (levels.size < 3) {
        val v = n * step
        if (v >= hi) break
        if (v > lo) levels.add(v)
        n += 1.0
    }
    return ChartScale(lo, hi, levels, fmt.axisLabels(levels, step))
}

/** The dotted levels, the axis and its seven ticks. */
private fun DrawScope.drawFrame(left: Float, right: Float, axis: Float, rows: List<Float>, grid: Color, line: Color) {
    val hair = 1.dp.toPx()
    val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx()))
    rows.forEach { y -> drawLine(grid, Offset(left, y), Offset(size.width, y), strokeWidth = hair, pathEffect = dash) }
    drawLine(line, Offset(left, axis), Offset(right, axis), strokeWidth = hair)
    for (i in 0..7) {
        val x = left + (right - left) * i / 7f
        drawLine(line, Offset(x, axis), Offset(x, axis + 4.dp.toPx()), strokeWidth = hair)
    }
}

/**
 * The price chart. [points] are drawn from the start to the end edge in the order given; [color] is the trend's own
 * colour. The three optional labels sit under the axis at its start, middle and end. The whole chart is one image for
 * TalkBack, read as [description].
 */
@Composable
internal fun PriceChart(
    points: List<Double>,
    color: Color,
    fmt: MarketFmt,
    description: String,
    modifier: Modifier = Modifier,
    startLabel: String? = null,
    midLabel: String? = null,
    endLabel: String? = null,
) {
    val k = LocalAndarzColors.current
    val d = LocalDensity.current
    val scale = remember(points, fmt) { chartScale(points, fmt) }
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Ltr,
        LocalDensity provides Density(d.density, minOf(d.fontScale, TEXT_CAP)),
    ) {
        Box(
            modifier.fillMaxWidth().height(CHART_HEIGHT.dp).clearAndSetSemantics {
                role = Role.Image
                contentDescription = description
            },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val left = GUTTER.dp.toPx()
                val right = size.width - END_PAD.dp.toPx()
                val top = TOP.dp.toPx()
                val bottom = BOTTOM.dp.toPx()
                val axis = AXIS.dp.toPx()
                val n = points.size
                if (n < 2) return@Canvas
                fun px(i: Int): Float = left + (right - left) * i / (n - 1)
                fun py(v: Double): Float = top + scale.frac(v) * (bottom - top)
                drawFrame(left, right, axis, scale.levels.map { py(it) }, k.divider, k.outline)
                val line = Path()
                val area = Path()
                area.moveTo(px(0), axis)
                points.forEachIndexed { i, v ->
                    if (i == 0) line.moveTo(px(i), py(v)) else line.lineTo(px(i), py(v))
                    area.lineTo(px(i), py(v))
                }
                area.lineTo(px(n - 1), axis)
                area.close()
                drawPath(area, color.copy(alpha = 0.10f))
                drawPath(line, color, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                val end = Offset(px(n - 1), py(points.last()))
                drawCircle(k.alt, 5.5.dp.toPx(), end)
                drawCircle(color, 4.5.dp.toPx(), end)
            }
            scale.levels.forEachIndexed { i, v ->
                val y = TOP + scale.frac(v) * (BOTTOM - TOP)
                Text(
                    scale.labels[i],
                    style = MaterialTheme.typography.labelMedium,
                    color = k.muted,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.offset(y = (y - 8f).dp).width((GUTTER - 4f).dp),
                )
            }
            if (startLabel != null && midLabel != null && endLabel != null) {
                Row(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = GUTTER.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(startLabel, style = MaterialTheme.typography.labelMedium, color = k.muted, maxLines = 1, softWrap = false)
                    Text(midLabel, style = MaterialTheme.typography.labelMedium, color = k.muted, maxLines = 1, softWrap = false)
                    Text(endLabel, style = MaterialTheme.typography.labelMedium, color = k.muted, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/** The empty frame of a chart whose data is on its way: the dotted levels, the axis, bars where the labels will be. */
@Composable
internal fun ChartSkeleton(modifier: Modifier = Modifier) {
    val k = LocalAndarzColors.current
    Canvas(modifier.fillMaxWidth().height(CHART_HEIGHT.dp).clearAndSetSemantics { }) {
        val left = GUTTER.dp.toPx()
        val right = size.width - END_PAD.dp.toPx()
        val top = TOP.dp.toPx()
        val bottom = BOTTOM.dp.toPx()
        val rows = listOf(0.15f, 0.5f, 0.85f).map { top + it * (bottom - top) }
        drawFrame(left, right, AXIS.dp.toPx(), rows, k.divider, k.outline)
        rows.forEach { y ->
            drawRoundRect(k.track, Offset(0f, y - 4.dp.toPx()), Size(30.dp.toPx(), 8.dp.toPx()), CornerRadius(4.dp.toPx()))
        }
        val mid = rows[1]
        drawLine(
            k.track, Offset(left + 6.dp.toPx(), mid), Offset(right - 6.dp.toPx(), mid), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())),
        )
    }
}

/**
 * The range of the last day as one bar: the lowest price at the start, the highest at the end, and a dot where the
 * price stands ([position] 0 to 1). One image for TalkBack, read as [description].
 */
@Composable
internal fun RangeBar(position: Float, lowText: String, highText: String, description: String, modifier: Modifier = Modifier) {
    val k = LocalAndarzColors.current
    val d = LocalDensity.current
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Ltr,
        LocalDensity provides Density(d.density, minOf(d.fontScale, TEXT_CAP)),
    ) {
        Row(
            modifier.fillMaxWidth().heightIn(min = 32.dp).clearAndSetSemantics {
                role = Role.Image
                contentDescription = description
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(lowText, style = MaterialTheme.typography.labelMedium, color = k.muted, maxLines = 1, softWrap = false)
            Canvas(Modifier.weight(1f).height(14.dp)) {
                val h = 6.dp.toPx()
                val r = 7.dp.toPx()
                drawRoundRect(k.track, Offset(0f, (size.height - h) / 2f), Size(size.width, h), CornerRadius(h / 2f))
                val x = r + (size.width - 2f * r) * position.coerceIn(0f, 1f)
                drawCircle(k.alt, r + 3.dp.toPx(), Offset(x, size.height / 2f))
                drawCircle(k.text, r, Offset(x, size.height / 2f))
            }
            Text(highText, style = MaterialTheme.typography.labelMedium, color = k.muted, maxLines = 1, softWrap = false)
        }
    }
}

/** The empty range bar of a coin whose day range is on its way: two label bars and the track. */
@Composable
internal fun RangeSkeleton(modifier: Modifier = Modifier) {
    val k = LocalAndarzColors.current
    Canvas(modifier.fillMaxWidth().height(32.dp).clearAndSetSemantics { }) {
        val bar = Size(52.dp.toPx(), 10.dp.toPx())
        val cy = size.height / 2f
        drawRoundRect(k.track, Offset(0f, cy - bar.height / 2f), bar, CornerRadius(5.dp.toPx()))
        drawRoundRect(k.track, Offset(size.width - bar.width, cy - bar.height / 2f), bar, CornerRadius(5.dp.toPx()))
        val h = 6.dp.toPx()
        drawRoundRect(
            k.track, Offset(bar.width + 8.dp.toPx(), cy - h / 2f), Size(size.width - 2f * (bar.width + 8.dp.toPx()), h), CornerRadius(h / 2f),
        )
    }
}
