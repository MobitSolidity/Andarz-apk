package ir.andarz.app.ui.market

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.andarz.app.R
import ir.andarz.app.content.ContentRepository
import ir.andarz.app.ui.theme.LocalAndarzColors
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
        content.ui("mkt.usdt.name") + SEP + when (c.iranSource) {
            IranSource.WALLEX -> content.ui("mkt.data.wallex")
            else -> content.ui("mkt.data.nobitex")
        }
    } else {
        c.name
    }

/** A locale-neutral separator, as March uses between the two parts of a label (law 3). */
internal const val SEP = " \u00B7 "

/**
 * A small line chart for a price series (§6.5: own Canvas code, no chart library). The x axis runs left
 * to right like every Andarz chart, in RTL too, because time reads that way on a price chart.
 */
@Composable
internal fun Sparkline(points: List<Double>, color: Color, modifier: Modifier = Modifier, fill: Boolean = false) {
    if (points.size < 2) {
        Box(modifier)
        return
    }
    Canvas(modifier) {
        val min = points.min()
        val max = points.max()
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        val stepX = size.width / (points.size - 1)
        val path = Path()
        points.forEachIndexed { i, p ->
            val x = i * stepX
            val y = (size.height * (1 - (p - min) / span)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (fill) {
            val area = Path().apply {
                addPath(path)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), Color.Transparent)))
        }
        drawPath(path, color, style = Stroke(width = (if (fill) 2.dp else 1.5.dp).toPx(), cap = StrokeCap.Round))
        // The last point, so the current price has an anchor on the chart.
        val lastY = (size.height * (1 - (points.last() - min) / span)).toFloat()
        drawCircle(color, radius = 2.dp.toPx(), center = Offset(size.width, lastY))
    }
}

@Composable
internal fun StarButton(starred: Boolean, label: String, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        if (starred) {
            Icon(Icons.Filled.Star, contentDescription = label, tint = LocalAndarzColors.current.caution)
        } else {
            Icon(painterResource(R.drawable.ic_star_outline), contentDescription = label, tint = LocalAndarzColors.current.textMuted)
        }
    }
}

/** A rounded symbol badge (March's `CoinBadge`, rebuilt from Andarz surfaces). */
@Composable
internal fun SymbolBadge(symbol: String, toman: Boolean) {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(36.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = Fmt.isolate(symbol.take(if (toman) 4 else 3)),
                style = MaterialTheme.typography.labelMedium,
                color = if (toman) LocalAndarzColors.current.caution else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One coin in the Prices list: badge, symbol and rank, name, 24 h sparkline, price, change, star. */
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
) {
    val tone = toneOf(c.change24h)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 2.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            SymbolBadge(c.symbol, c.isToman)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.isolate(c.symbol), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (c.isToman) Fmt.isolate("IR") else Fmt.isolate("#" + fmt.int(c.rank)),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (c.isToman) LocalAndarzColors.current.caution else LocalAndarzColors.current.textMuted,
                    )
                }
                Text(coinName(content, c), style = MaterialTheme.typography.bodySmall, color = LocalAndarzColors.current.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Sparkline(c.spark.takeLast(if (c.isToman) 120 else 24), tone, Modifier.size(width = 52.dp, height = 26.dp))
            Column(Modifier.width(116.dp), horizontalAlignment = Alignment.End) {
                Text(fmt.coinPrice(c, toman, rate), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(fmt.pct(c.change24h), style = MaterialTheme.typography.labelMedium, color = tone)
            }
            StarButton(starred, content.ui("mkt.sheet.star"), onToggle)
        }
    }
}

@Composable
internal fun Centered(modifier: Modifier = Modifier, body: @Composable () -> Unit) {
    Column(modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        body()
    }
}
