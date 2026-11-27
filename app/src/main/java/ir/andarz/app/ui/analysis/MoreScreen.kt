package ir.andarz.app.ui.analysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.andarz.app.R
import ir.andarz.app.ui.components.ArchShapes
import ir.andarz.app.ui.components.ArchTile
import ir.andarz.app.ui.components.ScreenHeader
import ir.andarz.app.ui.components.Text
import ir.andarz.app.ui.components.archSurface
import ir.andarz.app.ui.icons.Glyph
import ir.andarz.app.ui.icons.GlyphIcon
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.theme.Tone
import ir.andarz.app.ui.ui

/** Which tint an arch tile of the tools grid wears: amber for work waiting, lapis for numbers, neutral otherwise. */
private enum class ToolInk { AMBER, LAPIS, NEUTRAL }

private class Tool(val key: String, val glyph: Glyph, val ink: ToolInk)

private val TOOLS = listOf(
    Tool("rev.title", Glyph.CLIPBOARD_CHECK, ToolInk.AMBER),
    Tool("ins.title", Glyph.LINE_CHART, ToolInk.LAPIS),
    Tool("params.title", Glyph.SLIDERS, ToolInk.NEUTRAL),
    Tool("setups.title", Glyph.LAYERS, ToolInk.NEUTRAL),
    Tool("wl.title", Glyph.EYE, ToolInk.NEUTRAL),
    Tool("ledger.title", Glyph.WALLET, ToolInk.NEUTRAL),
)

private val PROGRAM = listOf(
    Tool("learn.title", Glyph.BULB, ToolInk.NEUTRAL),
    Tool("sec.title", Glyph.SHIELD_CHECK, ToolInk.NEUTRAL),
    Tool("set.title", Glyph.GEAR, ToolInk.NEUTRAL),
    Tool("about.title", Glyph.INFO, ToolInk.NEUTRAL),
    Tool("app.legal.title", Glyph.NOTES, ToolInk.NEUTRAL),
)

/**
 * The More destination (D-M07): the constitution card, a grid of the working tools and the list of the program's own
 * pages. [entries] pairs the content key of a page's title with the action that opens it; a page that is not in the
 * list is not shown. Titles are the pages' own titles.
 */
@Composable
fun MoreScreen(entries: List<Pair<String, () -> Unit>>) {
    val u = ui()
    val c = LocalAndarzColors.current
    val open = remember(entries) { entries.toMap() }
    val tools = TOOLS.filter { open.containsKey(it.key) }
    val program = PROGRAM.filter { open.containsKey(it.key) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenHeader(u.t("nav.more"), modifier = Modifier.padding(bottom = 2.dp))
        open["con.title"]?.let { go ->
            ConstitutionCard(
                title = u.t("con.title"),
                subtitle = u.t("app.more.constitution.sub", "n" to u.int(u.content.arr("constitution", "articles")?.size ?: 0)),
                onClick = go,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tools.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { t ->
                        ToolTile(u.t(t.key), t.glyph, t.ink, open.getValue(t.key), Modifier.weight(1f).fillMaxHeight())
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (program.isNotEmpty()) {
            Column {
                Text(
                    u.t("app.more.program"),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
                    color = c.muted,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp),
                )
                Column(Modifier.fillMaxWidth().archSurface(RoundedCornerShape(20.dp), c.sf1, c.divider, c.edge, clipContent = true)) {
                    program.forEachIndexed { i, t ->
                        if (i > 0) HorizontalDivider(color = c.divider)
                        ProgramRow(u.t(t.key), t.glyph, open.getValue(t.key))
                    }
                }
            }
        }
    }
}

/** The constitution: a picture in a small arch window, the title and what the document holds. */
@Composable
private fun ConstitutionCard(title: String, subtitle: String, onClick: () -> Unit) {
    val c = LocalAndarzColors.current
    val window = ArchShapes.arch(48.dp, 14.dp)
    val ring = c.ring(Tone.ALLOWED)
    Row(
        Modifier.fillMaxWidth()
            .archSurface(ArchShapes.arch(28.dp, 18.dp), c.sf1, c.divider, c.edge)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painterResource(R.drawable.art_tile_teal), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 96.dp, height = 104.dp).clip(window).drawWithContent {
                drawContent()
                drawOutline(window.createOutline(size, layoutDirection, this), ring, style = Stroke(1.dp.toPx()))
            },
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = c.muted)
        }
        GlyphIcon(Glyph.CHEVRON_RIGHT, null, tint = c.outline, size = 18.dp, strokeWidth = 2f)
    }
}

/** A tool of the grid: an arch tile with the glyph on the left of the title, 68 dp tall, growing with the font. */
@Composable
private fun ToolTile(title: String, glyph: Glyph, ink: ToolInk, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalAndarzColors.current
    val (tile, tint) = when (ink) {
        ToolInk.AMBER -> c.sfC to c.sf
        ToolInk.LAPIS -> c.lapisC to c.lapis
        ToolInk.NEUTRAL -> c.alt to c.hmuted
    }
    Row(
        modifier
            .archSurface(ArchShapes.arch(24.dp, 14.dp), c.sf1, c.divider, c.edge)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 68.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ArchTile(tile, width = 32.dp, height = 38.dp, bottom = 9.dp) { GlyphIcon(glyph, null, tint = tint, size = 20.dp, strokeWidth = 1.8f) }
        Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700), modifier = Modifier.weight(1f))
    }
}

/** A page of the program list: a small tile, the title, a drill-in chevron at the end edge (mirrored for RTL). */
@Composable
private fun ProgramRow(title: String, glyph: Glyph, onClick: () -> Unit) {
    val c = LocalAndarzColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArchTile(c.alt, width = 24.dp, height = 28.dp, bottom = 7.dp) { GlyphIcon(glyph, null, tint = c.hmuted, size = 17.dp, strokeWidth = 1.9f) }
        Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600), modifier = Modifier.weight(1f))
        GlyphIcon(Glyph.CHEVRON_RIGHT, null, tint = c.outline, size = 18.dp, strokeWidth = 2f)
    }
}
