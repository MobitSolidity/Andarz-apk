package ir.andarz.app.ui.analysis

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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(u.t("nav.more"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
        entries.forEachIndexed { i, (key, go) ->
            if (i > 0) HorizontalDivider()
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = go).padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) { Text(u.t(key), style = MaterialTheme.typography.bodyLarge) }
        }
    }
}
