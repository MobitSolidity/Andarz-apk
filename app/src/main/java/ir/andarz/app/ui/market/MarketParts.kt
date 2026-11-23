package ir.andarz.app.ui.market

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import ir.andarz.app.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.andarz.app.content.ContentRepository
import ir.andarz.app.ui.components.ArchShapes
import ir.andarz.app.ui.components.Pill
import ir.andarz.app.ui.components.figure
import ir.andarz.app.ui.icons.Glyph
import ir.andarz.app.ui.icons.GlyphIcon
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui
import ir.andarz.engine.Fmt
import ir.andarz.market.model.Coin
import ir.andarz.market.model.IranSource

/** Up and down use the semantic tokens (§6.2) and are never shown by color alone: the arrow and sign go with it. */
@Composable
internal fun toneOf(change: Double): Color =
    if (change >= 0) LocalAndarzColors.current.allowed else LocalAndarzColors.current.redline

/** The display name of a coin: the catalog name, or for USDT/Toman the content name plus its source. */
internal fun coinName(content: ContentRepository, c: Coin): String =
    if (c.isToman) {
        content.ui("mkt.usdt.name") + SEP + sourceName(content, c)
    } else {
        c.name
    }

/** The Iranian exchange a USDT/Toman rate came from, by its content name. */
internal fun sourceName(content: ContentRepository, c: Coin): String = when (c.iranSource) {
    IranSource.WALLEX -> content.ui("mkt.data.wallex")
    else -> content.ui("mkt.data.nobitex")
}

/** A locale-neutral separator, as March uses between the two parts of a label (law 3). */
internal const val SEP = " \u00B7 "

/**
 * A small line chart for a price series (§6.5: own Canvas code, no chart library). The x axis runs left
 * to right like every Andarz chart, in RTL too, because time reads that way on a price chart.
 * [pad] keeps the line and its end dot inside the box (the rows of the price list use 2.5 dp).
 */
@Composable
internal fun Sparkline(
    points: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
    strokeWidth: Dp = if (fill) 2.dp else 1.5.dp,
    dot: Dp = 2.dp,
    pad: Dp = 0.dp,
) {
    if (points.size < 2) {
        Box(modifier)
        return
    }
    Canvas(modifier) {
        val inset = pad.toPx()
        val w = size.width - 2 * inset
        val h = size.height - 2 * inset
        val min = points.min()
        val max = points.max()
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        val stepX = w / (points.size - 1)
        fun y(p: Double): Float = inset + (h * (1 - (p - min) / span)).toFloat()
        val path = Path()
        points.forEachIndexed { i, p ->
            val x = inset + i * stepX
            if (i == 0) path.moveTo(x, y(p)) else path.lineTo(x, y(p))
        }
        if (fill) {
            val area = Path().apply {
                addPath(path)
                lineTo(inset + w, inset + h)
                lineTo(inset, inset + h)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), Color.Transparent)))
        }
        drawPath(path, color, style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        // The last point, so the current price has an anchor on the chart.
        drawCircle(color, radius = dot.toPx(), center = Offset(inset + w, y(points.last())))
    }
}

/**
 * The star of a coin: a 48 dp toggle, a turquoise-free saffron star when it is on the watch list and an
 * outline when it is not. The label names the coin, the toggle state is announced by the platform.
 */
@Composable
internal fun StarButton(starred: Boolean, label: String, onToggle: () -> Unit) {
    val k = LocalAndarzColors.current
    Box(
        Modifier.size(48.dp)
            .toggleable(value = starred, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(if (starred) Glyph.STAR_FILL else Glyph.STAR, null, tint = if (starred) k.sf else k.muted, size = 22.dp, strokeWidth = 1.8f)
    }
}

/** A coin tile: a 32 by 36 dp arch with the symbol, or for USDT/Toman the swap glyph on the lapis tone. */
@Composable
internal fun SymbolBadge(symbol: String, toman: Boolean) {
    val k = LocalAndarzColors.current
    Box(
        Modifier.heightIn(min = 36.dp).widthIn(min = 32.dp)
            .background(if (toman) k.lapisC else k.high, ArchShapes.arch(16.dp, 9.dp))
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (toman) {
            GlyphIcon(Glyph.SWAP, null, tint = k.lapis, size = 18.dp, strokeWidth = 2f)
        } else {
            Text(
                text = Fmt.isolate(symbol.take(3)),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700),
                color = k.text,
                maxLines = 1, softWrap = false,
            )
        }
    }
}

/** Where a row sits in its card, so the card's rounded ends and single divider lines come out right. */
internal enum class RowSlot {
    FIRST, MIDDLE, LAST, ONLY;

    val top: Boolean get() = this == FIRST || this == ONLY
    val bottom: Boolean get() = this == LAST || this == ONLY
}

/** The slot of item [i] among [n]; [headed] is true when a header row of the same card sits above item 0. */
internal fun slotOf(i: Int, n: Int, headed: Boolean = false): RowSlot = when {
    n <= 1 && !headed -> RowSlot.ONLY
    i == 0 && !headed -> RowSlot.FIRST
    i == n - 1 -> RowSlot.LAST
    else -> RowSlot.MIDDLE
}

/**
 * One row of a list that reads as one card although it is a lazy list: [fill] in the rounded shape of its
 * [slot] (24 dp at the ends of the card), and a one pixel ring whose bottom line is left out unless the row
 * is the last, so two rows meet in one divider line. Nothing is clipped (see archSurface).
 */
internal fun Modifier.cardRow(slot: RowSlot, fill: Color, ring: Color): Modifier = this.drawWithCache {
    val end = 24.dp
    val top = if (slot.top) end else 0.dp
    val bottom = if (slot.bottom) end else 0.dp
    val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
    val w = 1.dp.toPx()
    val fillOutline = shape.createOutline(size, layoutDirection, this)
    val ringOutline = shape.createOutline(Size(size.width - w, size.height - w), layoutDirection, this)
    onDrawWithContent {
        drawOutline(fillOutline, fill)
        clipRect(bottom = if (slot.bottom) size.height else size.height - w) {
            translate(w / 2f, w / 2f) { drawOutline(ringOutline, ring, style = Stroke(w)) }
        }
        drawContent()
    }
}

/** How the rows of the price list are laid out; chosen once per screen from its width and the font scale. */
internal enum class RowLayout { FULL, NO_SPARK, STACKED }

/** The direction of a 7 day series, for its colour and its description: null when there is too little to say. */
internal fun trendUp(c: Coin): Boolean? =
    if (c.spark.size < 2) null else c.spark.last() >= c.spark.first()

/**
 * One coin in the Prices list: tile, symbol and name, 7 day sparkline, price over a change pill, star. The whole
 * row opens the coin sheet; the star is its own toggle. The USDT/Toman row sits on the `alt` step.
 */
@Composable
internal fun CoinRow(
    content: ContentRepository,
    fmt: MarketFmt,
    c: Coin,
    starred: Boolean,
    toman: Boolean,
    rate: Double?,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    slot: RowSlot = RowSlot.ONLY,
    layout: RowLayout = RowLayout.FULL,
) {
    val u = ui()
    val k = LocalAndarzColors.current
    val up = c.change24h >= 0
    val trend = trendUp(c)
    val trendColor = if (trend == false) k.anG else k.fzG
    val sub = if (c.isToman) {
        sourceName(content, c) + (if (c.tickAt > 0) SEP + fmt.clock(c.tickAt) else "")
    } else {
        c.name
    }
    val title = if (c.isToman) content.ui("mkt.usdt.name") else Fmt.isolate(c.symbol)
    val price: @Composable () -> Unit = {
        if (c.isToman) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
                Text(Fmt.isolate(fmt.priceNumber(c.price)), style = figure(19, FontWeight.W600), color = k.text, maxLines = 1, softWrap = false)
                Text(content.ui("mkt.unit.toman"), style = MaterialTheme.typography.labelMedium, color = k.muted, maxLines = 1, softWrap = false)
            }
        } else {
            Text(fmt.coinPrice(c, toman, rate), style = figure(15, FontWeight.W700), color = k.text, maxLines = 1, softWrap = false)
        }
    }
    val pill: @Composable () -> Unit = {
        if (!c.isToman) {
            Pill(
                text = fmt.change(c.change24h),
                container = if (up) k.fzC else k.anC,
                ink = if (up) k.fz else k.an,
                glyph = if (up) Glyph.TRI_UP else Glyph.TRI_DOWN,
                height = 22.dp,
            )
        }
    }
    val star = u.t(if (starred) "app.mkt.star.remove" else "app.mkt.star.add", "name" to (if (c.isToman) content.ui("mkt.usdt.name") else c.name))
    Row(
        Modifier.fillMaxWidth()
            .cardRow(slot, if (c.isToman) k.alt else k.sf1, k.divider)
            .clickable(role = Role.Button, onClick = onOpen)
            .heightIn(min = if (c.isToman) 56.dp else 58.dp)
            .padding(start = 12.dp, end = 2.dp, top = if (layout == RowLayout.STACKED) 8.dp else 0.dp, bottom = if (layout == RowLayout.STACKED) 8.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SymbolBadge(c.symbol, c.isToman)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.labelMedium, color = k.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (layout == RowLayout.STACKED) {
                price()
                pill()
            }
        }
        if (layout == RowLayout.FULL && trend != null) {
            val spark = if (c.isToman) c.spark.takeLast(120) else c.spark
            Sparkline(
                spark, trendColor,
                Modifier.size(width = 56.dp, height = 24.dp).semantics {
                    role = Role.Image
                    contentDescription = u.t(
                        when {
                            c.isToman && trend -> "app.mkt.trend.sUp"
                            c.isToman -> "app.mkt.trend.sDown"
                            trend -> "app.mkt.trend.up"
                            else -> "app.mkt.trend.down"
                        },
                    )
                },
                strokeWidth = 1.8.dp, dot = 2.4.dp, pad = 2.5.dp,
            )
        }
        if (layout != RowLayout.STACKED) {
            Column(Modifier.widthIn(min = 72.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                price()
                pill()
            }
        }
        StarButton(starred, star, onToggle)
    }
}

@Composable
internal fun Centered(modifier: Modifier = Modifier, body: @Composable () -> Unit) {
    Column(modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        body()
    }
}

/** A card header row on the `alt` step: a title at the start edge and an optional slot at the end. */
@Composable
internal fun CardHeaderRow(title: String, slot: RowSlot, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val k = LocalAndarzColors.current
    Row(
        modifier.fillMaxWidth().cardRow(slot, k.alt, k.divider).heightIn(min = 34.dp).padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W800), modifier = Modifier.weight(1f))
        if (trailing != null) trailing()
    }
}
