package ir.andarz.app.ui.trade

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.components.Text
import ir.andarz.app.ui.components.archSurface
import ir.andarz.app.ui.icons.Glyph
import ir.andarz.app.ui.icons.GlyphIcon
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.data.db.TradeEntity
import kotlinx.serialization.json.jsonPrimitive

/** The panels of the trade screens: 22 dp corners on the `sf1` step with the divider ring and the top highlight. */
internal val PanelShape = RoundedCornerShape(22.dp)

@Composable
internal fun Modifier.panel(): Modifier {
    val k = LocalAndarzColors.current
    return archSurface(PanelShape, k.sf1, k.divider, k.edge)
}

internal const val DOT = " · "

internal class MenuEntry(val label: String, val danger: Boolean = false, val onClick: () -> Unit)

/**
 * The header of the trade screens: the back arrow at the start edge, the title slot with an optional line under
 * it, and a menu (share as a note, delete) at the end edge.
 */
@Composable
internal fun TradeHeader(
    u: Ui,
    onBack: () -> Unit,
    menu: List<MenuEntry>,
    subtitle: String? = null,
    title: @Composable RowScope.() -> Unit,
) {
    val k = LocalAndarzColors.current
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            GlyphIcon(Glyph.ARROW_LEFT, u.t("common.back"), tint = k.text, strokeWidth = 2f)
        }
        Column(Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 4.dp)) {
            Row(
                Modifier.semantics { heading() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) { title() }
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = k.muted)
        }
        if (menu.isNotEmpty()) {
            Box {
                IconButton(onClick = { open = true }, modifier = Modifier.size(48.dp)) {
                    GlyphIcon(Glyph.DOTS_V, u.t("app.trade.menu"), tint = k.muted, size = 22.dp, strokeWidth = 2f)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    menu.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m.label, style = MaterialTheme.typography.bodyMedium, color = if (m.danger) k.redline else k.text) },
                            onClick = { open = false; m.onClick() },
                        )
                    }
                }
            }
        }
    }
}

/** A small pill: text with an optional glyph, on a container colour. */
@Composable
internal fun Pill(text: String, container: Color, ink: Color, glyph: Glyph? = null, height: Dp = 24.dp) {
    Row(
        Modifier.heightIn(min = height).background(container, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (glyph != null) GlyphIcon(glyph, null, tint = ink, size = 10.dp)
        Text(text, color = ink, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700), maxLines = 1)
    }
}

@Composable
internal fun SidePill(u: Ui, side: String) {
    val k = LocalAndarzColors.current
    Pill(u.enumLabel("side", side), k.lapisC, k.lapis, if (side == "LONG") Glyph.TRI_UP else Glyph.TRI_DOWN)
}

@Composable
internal fun StatusPill(u: Ui, status: String) {
    val k = LocalAndarzColors.current
    val (container, ink) = when (status) {
        "OPEN" -> k.lapisC to k.lapis
        "PLANNED" -> k.sfC to k.sf
        else -> k.high to k.muted
    }
    Pill(u.t("trade.status.$status"), container, ink)
}

/** A button of a bottom bar in the inverted colours (text on the page colour): the one action that ends something. */
@Composable
internal fun InvertedButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val k = LocalAndarzColors.current
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = k.text, contentColor = k.bg),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) { ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W700)) { content() } }
}

/** "25 minutes ago" in the unit that fits: minutes under an hour, hours under a day, then days. */
internal fun ago(u: Ui, minutes: Long): String = when {
    minutes < 60 -> u.t("app.trade.ago.min", "n" to u.int(minutes.coerceAtLeast(0).toInt()))
    minutes < 60 * 24 -> u.t("app.trade.ago.hour", "n" to u.int((minutes / 60).toInt()))
    else -> u.t("app.trade.ago.day", "n" to u.int((minutes / (60 * 24)).toInt()))
}

/** The share-as-note intent (9.5): plain text from the quick template, filled by line index. */
internal fun shareNote(ctx: android.content.Context, u: Ui, trade: TradeEntity, setupName: String?) {
    val tpl = u.content.obj("learn", "journal")?.get("quickTemplate")?.jsonPrimitive?.content ?: return
    val text = ir.andarz.app.platform.ShareNote.fill(tpl, ir.andarz.app.platform.ShareNote.values(u, trade, setupName))
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text)
    ctx.startActivity(android.content.Intent.createChooser(send, u.t("trade.shareNote")))
}
