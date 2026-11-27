package ir.andarz.app.ui.market

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.andarz.app.ui.components.ArchShapes
import ir.andarz.app.ui.components.ArchTile
import ir.andarz.app.ui.components.FilledAction
import ir.andarz.app.ui.components.Pill
import ir.andarz.app.ui.components.Text
import ir.andarz.app.ui.components.archSurface
import ir.andarz.app.ui.icons.Glyph
import ir.andarz.app.ui.icons.GlyphIcon
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui
import ir.andarz.market.MarketError
import ir.andarz.market.MarketService

/*
 * The states around the Prices list: the quiet card while the first answer is on its way (the canvas draws no spinner
 * and no moving bar), the saffron card when a service fails, the one failing service, the placeholder rows, and the
 * note rows. Saffron means "be careful", never "you broke a rule".
 */

/** The card of the first load: an hourglass tile, what is happening, and why it takes a moment. */
@Composable
internal fun LoadingCard(modifier: Modifier = Modifier) {
    val u = ui()
    val k = LocalAndarzColors.current
    Row(
        modifier.fillMaxWidth()
            .archSurface(ArchShapes.banner, k.sf1, k.divider, k.edge)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArchTile(k.lapisC, width = 32.dp, height = 36.dp, bottom = 9.dp) {
            GlyphIcon(Glyph.HOURGLASS, null, tint = k.lapis, size = 18.dp, strokeWidth = 2f)
        }
        Column(Modifier.weight(1f)) {
            Text(u.t("app.mkt.loading.title"), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700))
            Text(u.t("app.mkt.loading.body"), style = MaterialTheme.typography.labelMedium, color = k.muted)
        }
    }
}

/**
 * The warning card: a saffron tile, the title, one sentence of advice and the retry button, with a 4 dp bar of
 * saffron down the start edge. The bar is drawn inside the card shape; nothing is clipped, so the button stays hittable.
 */
@Composable
internal fun ErrorCard(title: String, body: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val u = ui()
    val k = LocalAndarzColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
    Row(
        modifier.fillMaxWidth()
            .archSurface(shape, k.sfC, k.sfLine)
            .drawBehind {
                val outline = shape.createOutline(size, layoutDirection, this)
                val path = Path().apply {
                    when (outline) {
                        is Outline.Rectangle -> addRect(outline.rect)
                        is Outline.Rounded -> addRoundRect(outline.roundRect)
                        is Outline.Generic -> addPath(outline.path)
                    }
                }
                clipPath(path) {
                    val w = 4.dp.toPx()
                    drawRect(k.sf, topLeft = Offset(if (rtl) size.width - w else 0f, 0f), size = Size(w, size.height))
                }
            }
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArchTile(k.sf, width = 32.dp, height = 36.dp, bottom = 9.dp) {
            GlyphIcon(Glyph.ALERT_TRIANGLE, null, tint = k.sfC, size = 18.dp, strokeWidth = 2f)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700))
            Text(body, style = MaterialTheme.typography.labelMedium.copy(lineHeight = 20.sp), color = k.text, modifier = Modifier.padding(top = 2.dp))
            FilledAction(onClick = onRetry, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), height = 48.dp, radius = 16.dp) {
                GlyphIcon(Glyph.REFRESH, null, size = 18.dp, strokeWidth = 2f)
                Spacer(Modifier.width(8.dp))
                Text(u.t("app.mkt.err.retry"))
            }
        }
    }
}

/** The one service that failed, as the typed error names it: its brand, what it is for and a saffron status word. */
internal class ServiceRow(val name: String, val purposeKey: String, val statusKey: String, val code: Int?, val glyph: Glyph)

private fun brandOf(s: MarketService): String = when (s) {
    MarketService.COIN_LIST -> "CoinGecko"
    MarketService.SENTIMENT -> "alternative.me"
    MarketService.LIVE_TICKS -> "Binance"
    MarketService.IRAN_PRIMARY -> "Nobitex"
    MarketService.IRAN_FALLBACK -> "Wallex"
}

internal fun purposeKeyOf(s: MarketService): String = when (s) {
    MarketService.COIN_LIST -> "app.mkt.svc.coinList"
    MarketService.SENTIMENT -> "app.mkt.svc.sentiment"
    MarketService.LIVE_TICKS -> "app.mkt.svc.ticks"
    MarketService.IRAN_PRIMARY -> "app.mkt.svc.iran"
    MarketService.IRAN_FALLBACK -> "app.mkt.svc.iranBackup"
}

/** The row for a typed failure; null when no single service is to blame (the device is offline, or there is no error). */
internal fun serviceRowOf(e: MarketError?): ServiceRow? {
    fun row(s: MarketService, status: String, glyph: Glyph, code: Int? = null) = ServiceRow(brandOf(s), purposeKeyOf(s), status, code, glyph)
    return when (e) {
        null, MarketError.NoNetwork -> null
        is MarketError.Timeout -> row(e.service, "app.mkt.svc.timeout", Glyph.HOURGLASS)
        is MarketError.RateLimited -> row(e.service, "app.mkt.svc.rate", Glyph.HOURGLASS_2)
        is MarketError.Blocked -> row(e.service, "app.mkt.svc.blocked", Glyph.BAN)
        is MarketError.Server -> row(e.service, "app.mkt.svc.server", Glyph.SERVER, e.code)
        is MarketError.BadData -> row(e.service, "app.mkt.svc.bad", Glyph.ALERT_TRIANGLE)
    }
}

/** The status card: an `alt` header and the failing service. */
@Composable
internal fun ServicesCard(row: ServiceRow, modifier: Modifier = Modifier) {
    val u = ui()
    val k = LocalAndarzColors.current
    Column(modifier.fillMaxWidth().archSurface(RoundedCornerShape(24.dp), k.sf1, k.divider, k.edge, clipContent = true)) {
        Row(Modifier.fillMaxWidth().background(k.alt).heightIn(min = 34.dp).padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(u.t("app.mkt.svc.title"), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800, fontSize = 13.sp))
        }
        Row(
            Modifier.fillMaxWidth()
                .drawBehind { drawLine(k.divider, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
                .heightIn(min = 46.dp).padding(horizontal = 14.dp, vertical = 5.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ArchTile(k.sfC, width = 28.dp, height = 32.dp, bottom = 8.dp) {
                GlyphIcon(row.glyph, null, tint = k.sf, size = 16.dp, strokeWidth = 2f)
            }
            Column(Modifier.weight(1f)) {
                Text(row.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700), maxLines = 1)
                Text(u.t(row.purposeKey), style = MaterialTheme.typography.labelMedium, color = k.muted)
            }
            Pill(
                if (row.code != null) u.t(row.statusKey, "code" to u.int(row.code)) else u.t(row.statusKey),
                container = k.sfC, ink = k.sf,
            )
        }
    }
}

private val SK_A = intArrayOf(36, 34, 38, 32, 40)
private val SK_B = intArrayOf(70, 56, 64, 76, 60)
private val SK_C = intArrayOf(66, 58, 62, 60, 64)

/** A placeholder row with the shape of a coin row; hidden from TalkBack, the list around it says it is busy. */
@Composable
internal fun SkeletonRow(slot: RowSlot, i: Int) {
    val k = LocalAndarzColors.current
    Row(
        Modifier.fillMaxWidth().cardRow(slot, k.sf1, k.divider).heightIn(min = 58.dp)
            .padding(start = 12.dp, end = 14.dp).clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(width = 32.dp, height = 36.dp).background(k.track, ArchShapes.arch(16.dp, 9.dp)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.size(width = SK_A[i % 5].dp, height = 10.dp).background(k.track, RoundedCornerShape(5.dp)))
            Box(Modifier.size(width = SK_B[i % 5].dp, height = 8.dp).background(k.alt, RoundedCornerShape(4.dp)))
        }
        Canvas(Modifier.size(width = 56.dp, height = 2.dp)) {
            drawLine(
                k.outline.copy(alpha = 0.6f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, 5.dp.toPx()), 0f),
            )
        }
        Column(Modifier.width(84.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(width = SK_C[i % 5].dp, height = 12.dp).background(k.track, RoundedCornerShape(6.dp)))
            Box(Modifier.size(width = 46.dp, height = 18.dp).background(k.alt, RoundedCornerShape(9.dp)))
        }
    }
}

/** A note with an info tile at the start edge: the price disclaimer, the offline reassurance. */
@Composable
internal fun NoteRow(text: String, modifier: Modifier = Modifier) {
    val k = LocalAndarzColors.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        ArchTile(k.lapisC, width = 24.dp, height = 28.dp, bottom = 7.dp) {
            GlyphIcon(Glyph.INFO_I, null, tint = k.lapis, size = 14.dp, strokeWidth = 2.6f)
        }
        Text(text, style = MaterialTheme.typography.labelMedium.copy(lineHeight = 20.sp), color = k.muted, modifier = Modifier.weight(1f))
    }
}
