package ir.andarz.app.ui.market

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.andarz.app.content.ContentRepository
import ir.andarz.app.ui.components.EmptyState
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.engine.Fmt
import ir.andarz.market.model.Coin

/**
 * My coins (March's `ui/WatchScreen.kt`, REIMPLEMENT): starred coins as cards, two per row. The block of
 * reminded releases still ahead is kept (MK2).
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
    val mine = coins.filter { it.id in watch }
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        item { SectionHeader(content.ui("mkt.tab.coins")) }
        if (mine.isEmpty()) {
            item { EmptyState(content.ui("mkt.coins.empty"), content.ui("mkt.tab.prices") to onGoPrices) }
        }
        mine.chunked(2).forEach { pair ->
            item(key = pair.joinToString { it.id }) {
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { c ->
                        WatchCard(content, fmt, c, toman, rate, Modifier.weight(1f), onRemove = { onToggle(c.id) }, onOpen = { onOpen(c.id) })
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
        item { SectionHeader(content.ui("mkt.coins.reminders")) }
        // March's block of reminded releases still ahead: title and time; tapping opens News.
        if (reminders.isEmpty()) {
            item { EmptyState(content.ui("mkt.coins.remindersEmpty"), content.ui("mkt.tab.news") to onGoNews) }
        } else {
            reminders.forEach { (title, whenText) ->
                item {
                    Surface(Modifier.fillMaxWidth().padding(bottom = 6.dp).clickable(onClick = onGoNews), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.padding(12.dp)) {
                            Text(title, style = MaterialTheme.typography.bodyLarge)
                            Text(whenText, style = MaterialTheme.typography.bodySmall, color = LocalAndarzColors.current.textMuted)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SectionHeader(t: String) =
    Text(t, style = MaterialTheme.typography.titleSmall, color = LocalAndarzColors.current.textMuted, modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp))

@Composable
private fun WatchCard(
    content: ContentRepository,
    fmt: MarketFmt,
    c: Coin,
    toman: Boolean,
    rate: Double?,
    modifier: Modifier,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
) {
    val tone = toneOf(c.change24h)
    Surface(modifier.clickable(onClick = onOpen), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SymbolBadge(c.symbol, c.isToman)
                Spacer(Modifier.width(8.dp))
                Text(Fmt.isolate(c.symbol), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = content.ui("mkt.coins.remove"), tint = LocalAndarzColors.current.textMuted)
                }
            }
            if (c.isToman) Text(content.ui("mkt.coins.usdtPair"), style = MaterialTheme.typography.bodySmall, color = LocalAndarzColors.current.textMuted)
            Text(fmt.coinPrice(c, toman, rate), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp), maxLines = 1)
            Text(fmt.pct(c.change24h), style = MaterialTheme.typography.labelLarge, color = tone)
            Sparkline(c.spark.takeLast(if (c.isToman) 120 else 48), tone, Modifier.fillMaxWidth().height(36.dp).padding(top = 6.dp))
        }
    }
}
