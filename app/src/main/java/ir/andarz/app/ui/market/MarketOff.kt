package ir.andarz.app.ui.market

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.andarz.app.R
import ir.andarz.app.ui.components.ArchShapes
import ir.andarz.app.ui.components.ArchTile
import ir.andarz.app.ui.components.FilledAction
import ir.andarz.app.ui.components.Text
import ir.andarz.app.ui.components.TextButton
import ir.andarz.app.ui.components.archSurface
import ir.andarz.app.ui.icons.Glyph
import ir.andarz.app.ui.icons.GlyphIcon
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui
import ir.andarz.engine.Fmt
import ir.andarz.market.MarketHosts

/*
 * Market switched off: the grey arch with the power mark, the plain statement that nothing is sent, the one filled
 * action, the link to what is sent, the four things that wake up, and the reassurance that the rest of Andarz does not
 * need the internet. And the one-time consent sheet that the action and the link open.
 */

/** The off state of Prices and My coins (`mkt.off.*`): it is the literal truth, no socket is opened before consent (K4). */
@Composable
internal fun MarketOff(onTurnOn: () -> Unit, onWhat: () -> Unit) {
    val u = ui()
    val k = LocalAndarzColors.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OffArch(Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(44.dp))
        Text(u.t("mkt.off.title"), style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 34.sp), textAlign = TextAlign.Center)
        Text(
            u.t("mkt.off.body"), style = MaterialTheme.typography.bodyMedium, color = k.muted, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp).widthIn(max = 300.dp),
        )
        FilledAction(onClick = onTurnOn, modifier = Modifier.fillMaxWidth().padding(top = 14.dp), height = 52.dp, radius = 16.dp) {
            Text(u.t("mkt.off.action"))
        }
        TextButton(onClick = onWhat) {
            Text(u.t("app.mkt.off.what"), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700))
            Spacer(Modifier.width(6.dp))
            GlyphIcon(Glyph.CHEVRON_RIGHT, null, size = 16.dp, strokeWidth = 2.2f)
        }
        Column(
            Modifier.fillMaxWidth().padding(top = 6.dp).semantics { contentDescription = u.t("app.mkt.off.features") },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeatureTile(Glyph.BARS, u.t("mkt.tab.prices"), u.t("app.mkt.off.f.prices"), Modifier.weight(1f).fillMaxHeight())
                FeatureTile(Glyph.STAR, u.t("mkt.tab.coins"), u.t("app.mkt.off.f.coins"), Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeatureTile(Glyph.CALENDAR, u.t("mkt.tab.news"), u.t("app.mkt.off.f.news"), Modifier.weight(1f).fillMaxHeight())
                FeatureTile(Glyph.BELL, u.t("mkt.tab.alerts"), u.t("app.mkt.off.f.alerts"), Modifier.weight(1f).fillMaxHeight())
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp).archSurface(RoundedCornerShape(18.dp), k.sf1, k.divider).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ArchTile(k.lapisC, width = 24.dp, height = 28.dp, bottom = 7.dp) {
                GlyphIcon(Glyph.SHIELD_CHECK, null, tint = k.lapis, size = 15.dp, strokeWidth = 2f)
            }
            Text(u.t("app.mkt.off.note"), style = MaterialTheme.typography.labelMedium.copy(lineHeight = 19.sp), color = k.muted, modifier = Modifier.weight(1f))
        }
    }
}

/** The grey arch: the terrain picture drained of colour inside a dashed outer arch, with the power mark across its foot. */
@Composable
private fun OffArch(modifier: Modifier = Modifier) {
    val k = LocalAndarzColors.current
    val window = ArchShapes.arch(116.dp, 28.dp)
    Box(
        modifier.size(width = 232.dp, height = 224.dp)
            .drawBehind {
                val gap = 8.dp.toPx()
                val w = 1.5.dp.toPx()
                val outline = ArchShapes.arch(124.dp, 36.dp).createOutline(Size(size.width + 2 * gap - w, size.height + 2 * gap - w), layoutDirection, this)
                translate(-gap + w / 2f, -gap + w / 2f) {
                    drawOutline(
                        outline, k.outline.copy(alpha = 0.7f),
                        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                    )
                }
            },
    ) {
        Box(Modifier.matchParentSize().archSurface(window, k.alt, k.divider)) {
            Image(
                painterResource(R.drawable.art_terrain), contentDescription = null, contentScale = ContentScale.Crop,
                alignment = BiasAlignment(0.24f, 0f),
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                alpha = 0.6f,
                modifier = Modifier.matchParentSize().clip(window),
            )
        }
        Box(
            Modifier.align(Alignment.BottomCenter).offset(y = 28.dp).size(width = 56.dp, height = 64.dp)
                .drawBehind {
                    val gap = 4.dp.toPx()
                    val outline = ArchShapes.arch(32.dp, 20.dp).createOutline(Size(size.width + 2 * gap, size.height + 2 * gap), layoutDirection, this)
                    translate(-gap, -gap) { drawOutline(outline, k.bg) }
                }
                .archSurface(ArchShapes.arch(28.dp, 16.dp), k.sf1, k.outline),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.POWER, null, tint = k.hmuted, size = 26.dp, strokeWidth = 2f)
        }
    }
}

/** One of the four things that wake up with Market: an `alt` arch tile with a glyph, a name and a few words. */
@Composable
private fun FeatureTile(glyph: Glyph, title: String, sub: String, modifier: Modifier = Modifier) {
    val k = LocalAndarzColors.current
    Row(
        modifier.heightIn(min = 60.dp).archSurface(ArchShapes.arch(20.dp, 14.dp), k.sf1, k.divider).padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ArchTile(k.alt, width = 24.dp, height = 28.dp, bottom = 7.dp) {
            GlyphIcon(glyph, null, tint = k.hmuted, size = 15.dp, strokeWidth = 2f)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700, fontSize = 14.sp))
            Text(sub, style = MaterialTheme.typography.labelMedium, color = k.muted)
        }
    }
}

/**
 * The one-time consent sheet (K4): what leaves the phone (the symbols of the coins) and what never does (journal, trades,
 * balance) as a two-lane picture, every host with what it is for, and the filled action. Nothing starts until the user
 * accepts; "Not now" leaves Market off and silent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConsentSheet(hosts: List<String>, onAccept: () -> Unit, onDecline: () -> Unit) {
    val u = ui()
    val k = LocalAndarzColors.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDecline,
        sheetState = state,
        containerColor = k.alt,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(width = 40.dp, height = 4.dp).background(k.outline, RoundedCornerShape(2.dp))) },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ArchTile(k.lapisC, width = 44.dp, height = 50.dp, bottom = 12.dp) {
                    GlyphIcon(Glyph.GLOBE, null, tint = k.lapis, size = 24.dp, strokeWidth = 1.8f)
                }
                Text(u.t("mkt.consent.title"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            Text(u.t("mkt.consent.body"), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 25.sp))
            Column(
                Modifier.fillMaxWidth().archSurface(RoundedCornerShape(20.dp), k.sf1, k.divider).padding(horizontal = 12.dp, vertical = 10.dp)
                    .clearAndSetSemantics {
                        role = Role.Image
                        contentDescription = u.t("app.mkt.consent.diagram")
                    },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DataLane(
                    Glyph.PHONE, k.high, k.text, Glyph.GLOBE, k.lapisC, k.lapis,
                    u.t("app.mkt.consent.sends"), solid = true,
                )
                DataLane(
                    Glyph.BOOK, k.high, k.text, Glyph.LOCK, k.fzC, k.fz,
                    u.t("app.mkt.consent.stays"), solid = false,
                )
            }
            Column(Modifier.fillMaxWidth().archSurface(RoundedCornerShape(20.dp), k.sf1, k.divider).padding(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 6.dp)) {
                Text(u.t("app.mkt.consent.services"), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700), color = k.muted, modifier = Modifier.padding(bottom = 2.dp))
                hosts.forEachIndexed { i, host ->
                    HostRow(host, purposeOfHost(host)?.let { u.t(it) } ?: "", first = i == 0)
                }
            }
            FilledAction(onClick = onAccept, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), height = 56.dp, radius = 28.dp) {
                GlyphIcon(Glyph.POWER, null, size = 22.dp, strokeWidth = 2.2f)
                Spacer(Modifier.width(10.dp))
                Text(u.t("mkt.consent.accept"), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W700))
            }
            TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
                Text(u.t("mkt.consent.decline"), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** What each allowed host is for, as the consent sheet states it. Both Iranian exchanges serve the same purpose. */
private fun purposeOfHost(host: String): String? = when (host) {
    MarketHosts.COIN_LIST -> "app.mkt.svc.coinList"
    MarketHosts.SENTIMENT -> "app.mkt.svc.sentiment"
    MarketHosts.LIVE_TICKS -> "app.mkt.svc.ticks"
    MarketHosts.IRAN_PRIMARY, MarketHosts.IRAN_FALLBACK -> "app.mkt.svc.iran"
    else -> null
}

/** One host: a lapis dot, the host in a monospaced LTR run, and its purpose at the end edge. */
@Composable
private fun HostRow(host: String, purpose: String, first: Boolean) {
    val k = LocalAndarzColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 28.dp)
            .drawBehind {
                if (!first) {
                    drawLine(
                        k.divider, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, 3.dp.toPx())),
                    )
                }
            }
            .padding(vertical = 3.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(6.dp).background(k.lapis, RoundedCornerShape(3.dp)))
        Text(
            Fmt.isolate(host), style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            maxLines = 1, softWrap = false,
        )
        Spacer(Modifier.weight(1f))
        Text(purpose, style = MaterialTheme.typography.labelMedium, color = k.muted)
    }
}

/**
 * One lane of the data-path picture: a tile at the start edge, a line with its label, a tile at the end edge. A solid
 * lapis line with an arrowhead is what is sent; a dashed grey line is what stays.
 */
@Composable
private fun DataLane(
    startGlyph: Glyph, startBg: Color, startInk: Color,
    endGlyph: Glyph, endBg: Color, endInk: Color,
    label: String, solid: Boolean,
) {
    val k = LocalAndarzColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ArchTile(startBg, width = 30.dp, height = 36.dp, bottom = 9.dp) { GlyphIcon(startGlyph, null, tint = startInk, size = 16.dp, strokeWidth = 2f) }
        Box(Modifier.weight(1f).heightIn(min = 40.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val y = size.height / 2f
                val w = 2.dp.toPx()
                if (solid) {
                    drawLine(k.lapis.copy(alpha = 0.75f), Offset(0f, y), Offset(size.width, y), strokeWidth = w)
                    val tip = if (rtl) 1.dp.toPx() else size.width - 1.dp.toPx()
                    val dir = if (rtl) 1f else -1f
                    drawLine(k.lapis, Offset(tip, y), Offset(tip + dir * 5.5.dp.toPx(), y - 5.dp.toPx()), strokeWidth = w, cap = StrokeCap.Round)
                    drawLine(k.lapis, Offset(tip, y), Offset(tip + dir * 5.5.dp.toPx(), y + 5.dp.toPx()), strokeWidth = w, cap = StrokeCap.Round)
                } else {
                    drawLine(
                        k.outline, Offset(0f, y), Offset(size.width, y), strokeWidth = w,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                    )
                }
            }
            val pillShape = ArchShapes.arch(13.dp, 8.dp)
            Text(
                label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700), textAlign = TextAlign.Center,
                color = if (solid) k.lapis else k.hmuted,
                modifier = (if (solid) Modifier.background(k.lapisC, pillShape) else Modifier.archSurface(pillShape, k.sf1, k.outline))
                    .heightIn(min = 26.dp).padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        ArchTile(endBg, width = 30.dp, height = 36.dp, bottom = 9.dp) { GlyphIcon(endGlyph, null, tint = endInk, size = 16.dp, strokeWidth = 2f) }
    }
}
