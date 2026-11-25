package ir.andarz.app.ui.market

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.andarz.app.content.ContentRepository
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.components.ArchShapes
import ir.andarz.app.ui.components.ArchTile
import ir.andarz.app.ui.components.FilledAction
import ir.andarz.app.ui.components.Pill
import ir.andarz.app.ui.components.Text
import ir.andarz.app.ui.components.archSurface
import ir.andarz.app.ui.components.figure
import ir.andarz.app.ui.icons.Glyph
import ir.andarz.app.ui.icons.GlyphIcon
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui
import ir.andarz.engine.Fmt
import ir.andarz.market.MarketError
import ir.andarz.market.model.Coin
import ir.andarz.market.model.CoinRule
import ir.andarz.market.model.Source
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The coin sheet of the canvas: who the coin is with the star and the close button, the price with its change, the
 * chart with the range of the day, three key numbers, and the coin's own alert (a threshold stepper and two one-shot
 * targets) with a Save button that stays below the scrolling part. When the chart is on its way or failed, only the
 * chart and the numbers change (the two other boards of the canvas); the alert editor stays usable because it is
 * local. Only the 7 day range exists, because that is all the coin list carries.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CoinSheet(
    content: ContentRepository,
    fmt: MarketFmt,
    c: Coin,
    toman: Boolean,
    rate: Double?,
    starred: Boolean,
    session: List<Double>,
    error: MarketError?,
    loading: Boolean,
    updatedAt: Long,
    onToggleStar: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    rule: CoinRule? = null,
    defaultPct: Float = 5f,
    onSaveRule: ((CoinRule) -> Unit)? = null,
) {
    val u = ui()
    val k = LocalAndarzColors.current
    val ctx = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pct by remember(c.id, rule) { mutableFloatStateOf(rule?.pct ?: defaultPct) }
    var above by remember(c.id, rule) { mutableStateOf(rule?.above?.let { plain(it) } ?: "") }
    var below by remember(c.id, rule) { mutableStateOf(rule?.below?.let { plain(it) } ?: "") }
    val weekly = !c.isToman && c.spark.size >= 2
    val series = if (weekly) c.spark else session
    val stale = error != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = k.sf1,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(width = 36.dp, height = 4.dp).background(k.outline, RoundedCornerShape(2.dp))) },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                CoinHead(content, fmt, c, starred, onToggleStar, onDismiss)
                PriceBlock(content, fmt, c, toman, rate, stale, if (c.tickAt > 0) c.tickAt else updatedAt)
                ChartCard(content, fmt, c, series, weekly, error, loading, onRetry, Modifier.padding(top = 8.dp))
                StatsBlock(content, fmt, c, pending = series.size < 2 && error == null && !c.isToman, Modifier.padding(top = 8.dp))
                if (onSaveRule != null) {
                    RuleCard(
                        content, fmt, c, stale, defaultPct,
                        pct = pct, onPct = { pct = it },
                        above = above, onAbove = { above = it },
                        below = below, onBelow = { below = it },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            if (onSaveRule != null) {
                FilledAction(
                    onClick = {
                        onSaveRule(ruleOf(c.id, pct != defaultPct, pct, above, below))
                        Toast.makeText(ctx, content.ui("mkt.rule.saved", "symbol" to Fmt.isolate(c.symbol)), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    height = 52.dp,
                    radius = 26.dp,
                ) { Text(u.t("mkt.rule.save")) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** The rule a save produces; a field that is not a positive number is no target (March). */
internal fun ruleOf(id: String, custom: Boolean, pct: Float, above: String, below: String): CoinRule =
    CoinRule(
        id = id,
        pct = if (custom) pct else null,
        above = Fmt.normalizeInput(above)?.takeIf { it > 0 },
        below = Fmt.normalizeInput(below)?.takeIf { it > 0 },
    )

/** One press of the threshold stepper: half a point, between 0.5 and 20. */
internal fun stepPct(v: Float, up: Boolean): Float =
    (((if (up) v + 0.5f else v - 0.5f) * 10f).roundToInt() / 10f).coerceIn(0.5f, 20f)

private fun plain(v: Double): String = java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString()

/** True when the sheet's side-by-side rows would not fit: a narrow screen or a large font. */
@Composable
internal fun isStacked(): Boolean = LocalDensity.current.fontScale > 1.15f || LocalConfiguration.current.screenWidthDp < 340

private fun sourceLabel(content: ContentRepository, c: Coin): String = when (c.source) {
    Source.BINANCE -> content.ui("mkt.live.BINANCE")
    Source.NOBITEX -> content.ui("mkt.sheet.liveIran")
    Source.COINGECKO -> Fmt.isolate("CoinGecko")
}

/** The price of a coin in the unit of its own chart, stats and targets: dollars, or Toman for USDT/Toman. */
private fun unitText(fmt: MarketFmt, c: Coin, v: Double): String = if (c.isToman) fmt.toman(v) else fmt.usd(v)

/** The coin's tile and names, the star and the close button. */
@Composable
private fun CoinHead(content: ContentRepository, fmt: MarketFmt, c: Coin, starred: Boolean, onToggleStar: () -> Unit, onDismiss: () -> Unit) {
    val u = ui()
    val k = LocalAndarzColors.current
    val name = if (c.isToman) content.ui("mkt.usdt.name") else Fmt.isolate(c.name)
    val sub = if (c.isToman) sourceName(content, c) else Fmt.isolate(c.symbol) + content.ui("mkt.sheet.rank") + fmt.int(c.rank)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArchTile(if (c.isToman) k.lapisC else k.high, width = 44.dp, height = 52.dp, bottom = 12.dp) {
            if (c.isToman) {
                GlyphIcon(Glyph.SWAP, null, tint = k.lapis, size = 22.dp, strokeWidth = 2f)
            } else {
                Text(
                    Fmt.isolate(c.symbol.take(4)),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            Text(sub, style = MaterialTheme.typography.labelMedium, color = k.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        StarButton(starred, u.t(if (starred) "app.mkt.star.remove" else "app.mkt.star.add", "name" to name), onToggleStar)
        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
            Box(Modifier.size(40.dp).background(k.alt, ArchShapes.arch(20.dp, 12.dp)), contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.X_SMALL, u.t("common.close"), tint = k.hmuted, size = 20.dp, strokeWidth = 2f)
            }
        }
    }
}

/**
 * The big price with its second line (the other unit and when it came), and at the end edge the 24 hour change, or
 * the saffron "old" pill when the last refresh failed. A long Toman amount gets a smaller figure and the pill goes
 * under it on a narrow screen or at a large font.
 */
@Composable
private fun PriceBlock(content: ContentRepository, fmt: MarketFmt, c: Coin, toman: Boolean, rate: Double?, stale: Boolean, ts: Long) {
    val u = ui()
    val k = LocalAndarzColors.current
    val d = LocalDensity.current
    val big = fmt.coinPrice(c, toman, rate)
    val size = when {
        big.length <= 11 -> 38
        big.length <= 14 -> 32
        big.length <= 17 -> 26
        else -> 22
    }
    val alt = when {
        c.isToman -> null
        toman -> fmt.usd(c.price)
        rate != null && rate > 0 -> u.t("app.mkt.sheet.about", "v" to fmt.toman(c.price * rate))
        else -> null
    }
    val meta = listOfNotNull(
        if (ts > 0) u.t(if (stale) "app.mkt.stale.at" else "app.mkt.status.updated", "t" to fmt.clock(ts)) else null,
        sourceLabel(content, c),
    ).joinToString(SEP)
    val up = c.change24h >= 0
    val change: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (stale) {
                Pill(u.t("app.mkt.stale"), container = k.sfC, ink = k.sf, glyph = Glyph.ALERT_CIRCLE, height = 26.dp)
            } else if (!c.isToman) {
                Pill(
                    fmt.change(c.change24h),
                    container = if (up) k.fzC else k.anC,
                    ink = if (up) k.fz else k.an,
                    glyph = if (up) Glyph.TRI_UP else Glyph.TRI_DOWN,
                    height = 26.dp,
                )
                Text(u.t("app.mkt.sheet.h24"), style = MaterialTheme.typography.labelMedium, color = k.muted)
            }
        }
    }
    val figureBlock: @Composable () -> Unit = {
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            CompositionLocalProvider(LocalDensity provides Density(d.density, minOf(d.fontScale, 1.2f))) {
                Text(big, style = figure(size, FontWeight.W600), color = if (stale) k.hmuted else k.text, maxLines = 1, softWrap = false)
            }
            if (alt != null) Text(alt, style = MaterialTheme.typography.labelMedium, color = k.muted)
            Text(meta, style = MaterialTheme.typography.labelMedium, color = k.muted)
        }
    }
    if (isStacked()) {
        Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            figureBlock()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) { change() }
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { figureBlock() }
            change()
        }
    }
}

/**
 * The chart card: its title, then the chart with the range bar of the day, or a quiet loading frame, or the failure
 * with its typed reason and a retry button (the three boards). With no chart and no error the frame is shown.
 */
@Composable
private fun ChartCard(
    content: ContentRepository,
    fmt: MarketFmt,
    c: Coin,
    series: List<Double>,
    weekly: Boolean,
    error: MarketError?,
    loading: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val u = ui()
    val k = LocalAndarzColors.current
    val title = content.ui(if (weekly) "mkt.sheet.chart7d" else "mkt.sheet.chartSession")
    val hasChart = series.size >= 2
    Column(modifier.fillMaxWidth().archSurface(ArchShapes.card, k.alt, Color.Transparent, k.edge).padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700),
            color = k.muted,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )
        when {
            hasChart -> {
                val up = series.last() >= series.first()
                val name = if (c.isToman) content.ui("mkt.usdt.name") else Fmt.isolate(c.name)
                val desc = u.t(
                    "app.mkt.sheet.chart.desc",
                    "title" to title, "name" to name,
                    "from" to unitText(fmt, c, series.first()), "to" to unitText(fmt, c, series.last()),
                    "low" to unitText(fmt, c, series.min()), "high" to unitText(fmt, c, series.max()),
                )
                val now = remember(c.id) { java.time.Instant.now() }
                PriceChart(
                    points = series,
                    color = if (up) k.fzG else k.anG,
                    fmt = fmt,
                    description = desc,
                    startLabel = if (weekly) dayLabel(u, now.minusSeconds(7L * 86_400L)) else null,
                    midLabel = if (weekly) dayLabel(u, now.minusSeconds(3L * 86_400L + 43_200L)) else null,
                    endLabel = if (weekly) u.t("app.mkt.sheet.today") else null,
                )
                if (c.low24h > 0 && c.high24h > c.low24h) {
                    val low = unitText(fmt, c, c.low24h)
                    val high = unitText(fmt, c, c.high24h)
                    RangeBar(
                        position = ((c.price - c.low24h) / (c.high24h - c.low24h)).toFloat(),
                        lowText = low,
                        highText = high,
                        description = u.t(
                            if (error != null) "app.mkt.sheet.range.saved" else "app.mkt.sheet.range.desc",
                            "low" to low, "high" to high, "price" to unitText(fmt, c, c.price),
                        ),
                    )
                }
            }
            error != null -> ChartFailed(error, onRetry)
            else -> ChartWaiting(
                text = when {
                    c.isToman -> content.ui("mkt.sheet.collecting")
                    loading -> u.t("app.mkt.sheet.chart.loading")
                    else -> u.t("app.mkt.sheet.chart.pending")
                },
            )
        }
    }
}

/** "25 Mehr": the date without its year, for the chart's day axis. */
private fun dayLabel(u: Ui, at: java.time.Instant): String = u.date(u.localDate(at), false).substringBeforeLast(' ')

/** The frame of a chart that is on its way, with a quiet status chip in the middle (no spinner, nothing moves). */
@Composable
private fun ChartWaiting(text: String) {
    val k = LocalAndarzColors.current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column {
            ChartSkeleton()
            RangeSkeleton()
        }
        Row(
            Modifier.padding(top = 46.dp).heightIn(min = 32.dp)
                .archSurface(ArchShapes.arch(16.dp, 9.dp), k.sf1, k.divider)
                .padding(horizontal = 14.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Canvas(Modifier.size(8.dp)) {
                val ring = 1.5.dp.toPx()
                drawCircle(k.hmuted, size.minDimension / 2f - ring / 2f, center, style = Stroke(ring))
            }
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700), color = k.hmuted, maxLines = 2)
        }
    }
}

/** The failed chart: the saffron mark, what failed (the service and the typed reason), and the retry button. */
@Composable
private fun ChartFailed(error: MarketError, onRetry: () -> Unit) {
    val u = ui()
    val k = LocalAndarzColors.current
    val row = serviceRowOf(error)
    val reason = if (row == null) {
        u.t("mkt.err.noNetwork")
    } else {
        val status = if (row.code != null) u.t(row.statusKey, "code" to u.int(row.code)) else u.t(row.statusKey)
        Fmt.isolate(row.name) + " " + status
    }
    Column(
        Modifier.fillMaxWidth().heightIn(min = 170.dp).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ArchTile(k.sfC, width = 28.dp, height = 32.dp, bottom = 8.dp) {
            GlyphIcon(Glyph.ALERT_TRIANGLE, null, tint = k.sf, size = 16.dp, strokeWidth = 2f)
        }
        Text(
            u.t("app.mkt.sheet.chart.failed"),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(reason, style = MaterialTheme.typography.labelMedium, color = k.muted, textAlign = TextAlign.Center)
        Row(
            Modifier.padding(top = 8.dp).heightIn(min = 48.dp)
                .archSurface(ArchShapes.arch(24.dp, 12.dp), Color.Transparent, k.outline)
                .clickable(role = Role.Button, onClick = onRetry)
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlyphIcon(Glyph.REFRESH, null, tint = k.text, size = 18.dp, strokeWidth = 2f)
            Text(u.t("app.mkt.err.retry"), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700))
        }
    }
}

/** One key number: its label and a value drawn by [value]. */
private class StatItem(val label: String, val value: @Composable () -> Unit)

/**
 * Market cap, 24 hour volume and 7 day change in one row of tiles (one tile per row on a narrow screen or at a large
 * font), then the all-time high and the distance to it when the coin has one. A missing number is a skeleton bar
 * while the data is on its way and a dash with "no value" for TalkBack otherwise.
 */
@Composable
private fun StatsBlock(content: ContentRepository, fmt: MarketFmt, c: Coin, pending: Boolean, modifier: Modifier = Modifier) {
    val u = ui()
    val stacked = isStacked()
    val d7 = if (!c.isToman && c.change7d != 0.0) c.change7d else null
    val main = listOf(
        StatItem(content.ui("mkt.sheet.cap")) { StatText(if (c.marketCap > 0) fmt.cap(c.marketCap) else null, pending) },
        StatItem(content.ui("mkt.sheet.vol24")) { StatText(if (c.volume24h > 0) fmt.cap(c.volume24h) else null, pending) },
        StatItem(u.t("app.mkt.sheet.d7")) { StatChange(d7, fmt, pending) },
    )
    val ath = if (c.ath > 0) {
        listOf(
            StatItem(content.ui("mkt.sheet.ath")) { StatText(unitText(fmt, c, c.ath), false) },
            StatItem(content.ui("mkt.sheet.athDist")) { StatText(fmt.change(c.athChange), false) },
        )
    } else {
        emptyList()
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StatGrid(main, if (stacked) 1 else 3)
        if (ath.isNotEmpty()) StatGrid(ath, if (stacked) 1 else 2)
    }
}

@Composable
private fun StatGrid(items: List<StatItem>, columns: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { item -> StatCell(item.label, columns == 1, Modifier.weight(1f)) { item.value() } }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A tile on the `alt` step with its label above (or beside, when stacked) its value. One TalkBack node. */
@Composable
private fun StatCell(label: String, stacked: Boolean, modifier: Modifier, value: @Composable () -> Unit) {
    val k = LocalAndarzColors.current
    val base = modifier.archSurface(ArchShapes.tile, k.alt, Color.Transparent, k.edge).semantics(mergeDescendants = true) {}
    if (stacked) {
        Row(
            base.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = k.muted, modifier = Modifier.weight(1f))
            value()
        }
    } else {
        Column(base.heightIn(min = 54.dp).padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = k.muted, maxLines = 1, softWrap = false)
            value()
        }
    }
}

@Composable
private fun StatText(text: String?, pending: Boolean) {
    val k = LocalAndarzColors.current
    if (text != null) {
        Text(text, style = figure(15, FontWeight.W700), color = k.text, maxLines = 1, softWrap = false)
    } else if (pending) {
        Box(Modifier.padding(top = 6.dp).size(width = 56.dp, height = 14.dp).background(k.track, RoundedCornerShape(5.dp)).clearAndSetSemantics { })
    } else {
        NoValue()
    }
}

@Composable
private fun StatChange(v: Double?, fmt: MarketFmt, pending: Boolean) {
    if (v == null) {
        StatText(null, pending)
        return
    }
    val k = LocalAndarzColors.current
    val up = v >= 0
    val tint = if (up) k.fz else k.an
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        GlyphIcon(if (up) Glyph.TRI_UP else Glyph.TRI_DOWN, null, tint = tint, size = 12.dp)
        Text(fmt.change(v), style = figure(15, FontWeight.W700), color = tint, maxLines = 1, softWrap = false)
    }
}

/** The dash of a number that does not exist; TalkBack says "no value". */
@Composable
private fun NoValue() {
    val u = ui()
    val k = LocalAndarzColors.current
    val label = u.t("app.mkt.sheet.noValue")
    Box(Modifier.heightIn(min = 24.dp).semantics { contentDescription = label }, contentAlignment = Alignment.CenterStart) {
        Box(Modifier.size(width = 18.dp, height = 3.dp).background(k.muted, RoundedCornerShape(2.dp)))
    }
}

/**
 * The alert of this coin: the threshold stepper with the general default beside it, the strip that shows the targets
 * around the price, and the two one-shot targets. The save button is outside this card, under the scrolling part.
 */
@Composable
private fun RuleCard(
    content: ContentRepository,
    fmt: MarketFmt,
    c: Coin,
    stale: Boolean,
    defaultPct: Float,
    pct: Float,
    onPct: (Float) -> Unit,
    above: String,
    onAbove: (String) -> Unit,
    below: String,
    onBelow: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val u = ui()
    val k = LocalAndarzColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val low = Fmt.normalizeInput(below)?.takeIf { it > 0 }
    val high = Fmt.normalizeInput(above)?.takeIf { it > 0 }
    val prefix = if (c.isToman) null else "$"
    val unitLabel = content.ui(if (c.isToman) "mkt.unit.toman" else "mkt.unit.usd")

    Column(modifier.fillMaxWidth().archSurface(ArchShapes.card, k.alt, Color.Transparent, k.edge).padding(12.dp)) {
        Row(Modifier.heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ArchTile(k.sfC, width = 24.dp, height = 28.dp, bottom = 7.dp) {
                GlyphIcon(Glyph.BELL, null, tint = k.sf, size = 14.dp, strokeWidth = 2f)
            }
            Text(
                u.t("app.mkt.rule.title"),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W800),
                modifier = Modifier.semantics { heading() },
            )
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(u.t("app.mkt.rule.threshold"), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700))
                Text(
                    u.t("app.mkt.rule.default", "pct" to pctText(u, defaultPct.toDouble())),
                    style = MaterialTheme.typography.labelMedium,
                    color = k.muted,
                )
            }
            StepButton(Glyph.MINUS, u.t("app.mkt.rule.dec"), enabled = pct > 0.5f) { onPct(stepPct(pct, false)) }
            Text(
                pctText(u, pct.toDouble()),
                style = figure(20, FontWeight.W600),
                color = k.text,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.widthIn(min = 62.dp),
            )
            StepButton(Glyph.PLUS, u.t("app.mkt.rule.inc"), enabled = pct < 20f) { onPct(stepPct(pct, true)) }
        }
        if (low != null || high != null) {
            val parts = buildList {
                if (low != null) add(low to u.t("app.mkt.rule.track.low", "v" to unitText(fmt, c, low)))
                if (high != null) add(high to u.t("app.mkt.rule.track.high", "v" to unitText(fmt, c, high)))
                add(c.price to u.t(if (stale) "app.mkt.rule.track.saved" else "app.mkt.rule.track.now", "v" to unitText(fmt, c, c.price)))
            }.sortedBy { it.first }.joinToString(SEP) { it.second }
            RuleTrack(c.price, low, high, parts, Modifier.padding(top = 4.dp, bottom = 6.dp))
        }
        val lowerField: @Composable (Modifier) -> Unit = { m ->
            TargetField(
                label = u.t("app.mkt.rule.lower"), glyph = Glyph.TRI_DOWN, tint = k.an,
                value = below, onValue = onBelow, prefix = prefix, distance = low?.let { pctFrom(c.price, it) },
                a11y = content.ui("mkt.rule.belowField", "unit" to unitLabel), fmt = fmt, modifier = m,
            )
        }
        val upperField: @Composable (Modifier) -> Unit = { m ->
            TargetField(
                label = u.t("app.mkt.rule.upper"), glyph = Glyph.TRI_UP, tint = k.fz,
                value = above, onValue = onAbove, prefix = prefix, distance = high?.let { pctFrom(c.price, it) },
                a11y = content.ui("mkt.rule.aboveField", "unit" to unitLabel), fmt = fmt, modifier = m,
            )
        }
        val stacked = isStacked()
        if (stacked) {
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lowerField(Modifier.fillMaxWidth())
                upperField(Modifier.fillMaxWidth())
            }
        } else {
            // The lower target sits at the left, where the strip above puts it, in both languages.
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rtl) {
                    upperField(Modifier.weight(1f))
                    lowerField(Modifier.weight(1f))
                } else {
                    lowerField(Modifier.weight(1f))
                    upperField(Modifier.weight(1f))
                }
            }
        }
        Text(
            u.t("mkt.rule.once"),
            style = MaterialTheme.typography.labelMedium,
            color = k.muted,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** How far [target] is from [price], in percent of the price. */
private fun pctFrom(price: Double, target: Double): Double? = if (price > 0) (target - price) / price * 100.0 else null

/** A 48 dp round-cornered step button of the threshold stepper. */
@Composable
private fun StepButton(glyph: Glyph, label: String, enabled: Boolean, onClick: () -> Unit) {
    val k = LocalAndarzColors.current
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Box(Modifier.size(44.dp).background(k.high, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            GlyphIcon(glyph, label, tint = if (enabled) k.text else k.hmuted, size = 20.dp, strokeWidth = 2.2f)
        }
    }
}

/**
 * The strip under the stepper: the price as a dot, the lower target as a red tick and the upper one as a green tick,
 * with the band between them. It always reads left to right, like the chart. One image for TalkBack.
 */
@Composable
private fun RuleTrack(price: Double, low: Double?, high: Double?, description: String, modifier: Modifier = Modifier) {
    val k = LocalAndarzColors.current
    val points = listOfNotNull(low, high, price)
    val lo = points.min()
    val hi = points.max()
    val margin = if (hi - lo > 0) (hi - lo) * 0.5 else abs(price) * 0.05 + 1e-9
    val a = lo - margin
    val b = hi + margin
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Canvas(
            modifier.fillMaxWidth().height(22.dp).clearAndSetSemantics {
                role = Role.Image
                contentDescription = description
            },
        ) {
            val r = 7.dp.toPx()
            val cy = size.height / 2f
            val bar = 4.dp.toPx()
            fun x(v: Double): Float = r + (size.width - 2f * r) * ((v - a) / (b - a)).toFloat().coerceIn(0f, 1f)
            drawRoundRect(k.track, Offset(0f, cy - bar / 2f), Size(size.width, bar), CornerRadius(bar / 2f))
            val from = x(low ?: price)
            val to = x(high ?: price)
            if (to > from) {
                drawRoundRect(k.lapis.copy(alpha = 0.55f), Offset(from, cy - bar / 2f), Size(to - from, bar), CornerRadius(bar / 2f))
            }
            val tick = 3.dp.toPx()
            if (low != null) drawRoundRect(k.anG, Offset(x(low) - tick / 2f, cy - 7.dp.toPx()), Size(tick, 14.dp.toPx()), CornerRadius(tick / 2f))
            if (high != null) drawRoundRect(k.fzG, Offset(x(high) - tick / 2f, cy - 7.dp.toPx()), Size(tick, 14.dp.toPx()), CornerRadius(tick / 2f))
            drawCircle(k.alt, r, Offset(x(price), cy))
            drawCircle(k.text, r - 3.dp.toPx(), Offset(x(price), cy))
        }
    }
}

/**
 * A target field: its label with the direction mark, the number (always left to right, with the dollar sign before
 * it), and how far it is from the price. The whole tile is the text field, so the whole tile is the touch target.
 */
@Composable
private fun TargetField(
    label: String,
    glyph: Glyph,
    tint: Color,
    value: String,
    onValue: (String) -> Unit,
    prefix: String?,
    distance: Double?,
    a11y: String,
    fmt: MarketFmt,
    modifier: Modifier = Modifier,
) {
    val u = ui()
    val k = LocalAndarzColors.current
    BasicTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        textStyle = figure(16, FontWeight.W700).copy(color = k.text, textDirection = TextDirection.Ltr),
        cursorBrush = SolidColor(k.fz),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        modifier = modifier.heightIn(min = 56.dp)
            .archSurface(ArchShapes.tile, k.sf1, k.divider)
            .semantics { contentDescription = a11y },
        decorationBox = { inner ->
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlyphIcon(glyph, null, tint = tint, size = 12.dp)
                    Text(label, style = MaterialTheme.typography.labelMedium, color = k.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(Modifier.heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (prefix != null) Text(prefix, style = figure(16, FontWeight.W700), color = k.muted, maxLines = 1)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty()) {
                                Text(u.t("app.mkt.rule.none"), style = MaterialTheme.typography.labelMedium, color = k.muted, maxLines = 1)
                            }
                            inner()
                        }
                        if (distance != null) {
                            Text(
                                fmt.change(distance),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700),
                                color = if (distance < 0) k.an else k.fz,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        },
    )
}
