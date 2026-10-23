package ir.andarz.app.ui.analysis

import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ir.andarz.app.ui.ui

/**
 * The More destination (D-M07): Insights lives here, with the governance screens of M5. Settings,
 * constitution and the rest arrive with M6; their entries appear then. Labels are the screens' own titles.
 */
@Composable
fun MoreScreen(entries: List<Pair<String, () -> Unit>>) {
    val u = ui()
    val c = ir.andarz.app.ui.theme.LocalAndarzColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        ir.andarz.app.ui.components.ScreenHeader(u.t("nav.more"), modifier = Modifier.padding(bottom = 8.dp))
        // v2 (D-UI2): one tonal card holding the rows, each row with an end-edge chevron (mirrored for RTL).
        androidx.compose.material3.Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, c.divider.copy(alpha = if (c.isDark) 0.6f else 1f)),
        ) {
            Column {
                entries.forEachIndexed { i, (key, go) ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = c.divider)
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = go).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(u.t(key), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = null, tint = c.textMuted,
                        )
                    }
                }
            }
        }
    }
}
