package ir.andarz.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.andarz.app.ui.components.ArchShapes
import ir.andarz.app.ui.components.FitText
import ir.andarz.app.ui.icons.GlyphIcon
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui

/**
 * The bottom bar of the final canvas: a flat `sf1` bar with a 1 dp divider on top (76 dp, 8/8/10 padding), five
 * equal items, each a 48 by 34 dp icon area over a one line label. The selected item gets an arch of the turquoise
 * container behind its glyph and its label in the text colour; the others are muted. Every item is at least
 * 48 dp tall and as wide as its share of the bar, so the whole column is the touch target, not just the pill.
 * Labels shrink to fit ([FitText], D-93) so none loses a letter on a narrow phone at a large font.
 */
@Composable
fun AndarzBottomBar(
    items: List<AndarzDestination>,
    currentRoute: String?,
    onSelect: (AndarzDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAndarzColors.current
    Row(
        modifier
            .fillMaxWidth()
            .background(c.sf1)
            .drawBehind { drawLine(c.divider, Offset(0f, 0.5f), Offset(size.width, 0.5f), strokeWidth = 1.dp.toPx()) }
            // edge to edge: the bar runs under the system navigation bar and keeps its content above it
            .windowInsetsPadding(NavigationBarDefaults.windowInsets)
            .heightIn(min = 76.dp)
            .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 10.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (d in items) BarItem(d, selected = currentRoute?.startsWith(d.route) == true) { onSelect(d) }
    }
}

@Composable
private fun RowScope.BarItem(d: AndarzDestination, selected: Boolean, onClick: () -> Unit) {
    val c = LocalAndarzColors.current
    val label = ui().t(d.labelKey)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Column(
        Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .selectable(selected = selected, interactionSource = source, indication = null, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        // The arch is drawn, not clipped (see ArchShapes): a clipped unequal-corner layer breaks hit-testing in tests.
        Box(
            Modifier.size(width = 48.dp, height = 34.dp)
                .background(
                    when {
                        selected -> c.fzC
                        pressed -> c.high
                        else -> Color.Transparent
                    },
                    ArchShapes.arch(24.dp, 12.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(d.glyph, null, tint = if (selected) c.fz else c.muted, size = 22.dp, strokeWidth = if (selected) 1.9f else 1.8f)
        }
        // D-93: one line that shrinks to fit, so no label loses its last letter on a narrow phone.
        FitText(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.W700 else FontWeight.W500),
            modifier = Modifier.fillMaxWidth(),
            color = if (selected) c.text else c.muted,
        )
    }
}
