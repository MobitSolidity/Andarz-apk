package ir.andarz.app.ui.market

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.andarz.app.content.ContentRepository
import ir.andarz.app.ui.components.ArchTile
import ir.andarz.app.ui.components.EmptyState
import ir.andarz.app.ui.components.Text
import ir.andarz.app.ui.icons.Glyph
import ir.andarz.app.ui.icons.GlyphIcon
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.market.model.Coin

/**
 * My coins (March's `ui/WatchScreen.kt`, REIMPLEMENT): the starred coins as the same rows as the price list, in one
 * card with a header, and the releases still ahead that have a reminder (MK2) in a second card. The star of a row
 * takes the coin out of the list. There is no board for this section, so it is built from the parts of Prices.
 */
@Composable
internal fun MyCoinsScreen(
    content: ContentRepository,
    fmt: MarketFmt,
    coins: List<Coin>,
    watch: Set<String>,
    toman: Boolean,
    rate: Double?,
    onToggle: (String) -> Unit,
    onOpen: (String) -> Unit,
    onGoPrices: () -> Unit,
    onGoNews: () -> Unit,
    reminders: List<Pair<String, String>> = emptyList(),
) {
    val mine = remember(coins, watch) { coins.filter { it.id in watch } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = LocalDensity.current.fontScale
        val layout = when {
            maxWidth >= 360.dp && scale <= 1.15f -> RowLayout.FULL
            maxWidth >= 300.dp && scale <= 1.3f -> RowLayout.NO_SPARK
            else -> RowLayout.STACKED
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp)) {
            item(key = "coins-head") { CardHeaderRow(content.ui("mkt.tab.coins"), if (mine.isEmpty()) RowSlot.ONLY else RowSlot.FIRST) }
            if (mine.isEmpty()) {
                item(key = "coins-empty") { EmptyState(content.ui("mkt.coins.empty"), content.ui("mkt.tab.prices") to onGoPrices) }
            }
            itemsIndexed(mine, key = { _, c -> c.id }, contentType = { _, _ -> "coin" }) { i, c ->
                CoinRow(
                    content, fmt, c, true, toman, rate,
                    onToggle = { onToggle(c.id) }, onOpen = { onOpen(c.id) },
                    slot = slotOf(i, mine.size, headed = true), layout = layout,
                )
            }
            item(key = "gap") { Spacer(Modifier.height(14.dp)) }
            // March's block of reminded releases still ahead: title and time; tapping opens News.
            item(key = "rem-head") { CardHeaderRow(content.ui("mkt.coins.reminders"), if (reminders.isEmpty()) RowSlot.ONLY else RowSlot.FIRST) }
            if (reminders.isEmpty()) {
                item(key = "rem-empty") { EmptyState(content.ui("mkt.coins.remindersEmpty"), content.ui("mkt.tab.news") to onGoNews) }
            }
            itemsIndexed(reminders, key = { i, r -> "r$i-${r.first}" }, contentType = { _, _ -> "reminder" }) { i, (title, whenText) ->
                ReminderRow(title, whenText, slotOf(i, reminders.size, headed = true), onGoNews)
            }
        }
    }
}

/** One reminded release: a bell tile, its name and when it comes. The row opens News. */
@Composable
private fun ReminderRow(title: String, whenText: String, slot: RowSlot, onClick: () -> Unit) {
    val k = LocalAndarzColors.current
    Row(
        Modifier.fillMaxWidth()
            .cardRow(slot, k.sf1, k.divider)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ArchTile(k.lapisC, width = 32.dp, height = 36.dp, bottom = 9.dp) {
            GlyphIcon(Glyph.BELL_FILL, null, tint = k.lapis, size = 16.dp, strokeWidth = 1.8f)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(whenText, style = MaterialTheme.typography.labelMedium, color = k.muted)
        }
    }
}
