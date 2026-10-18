package ir.andarz.app.ui.market

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.andarz.app.content.ContentRepository
import ir.andarz.app.ui.components.EmptyState
import ir.andarz.app.ui.components.StatTile
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.engine.Fmt
import ir.andarz.market.MarketModule
import ir.andarz.market.model.Coin
import ir.andarz.market.model.LiveMode
import ir.andarz.market.model.MarketOverview
import ir.andarz.market.model.MarketState

/** March's sort pills; labels are `mkt.sort.<name>`. */
internal enum class PriceSort { RANK, GAIN, LOSS, VOLUME }

/** Filter and sort exactly as March: the USDT/Toman card stays pinned on top. Pure, so it is unit-tested. */
internal fun shownCoins(coins: List<Coin>, query: String, sort: PriceSort): List<Coin> {
    val needle = query.trim()
    val f = coins.filter { needle.isEmpty() || it.symbol.contains(needle, true) || it.name.contains(needle, true) }
    val (pinned, rest) = f.partition { it.isToman }
    return pinned + when (sort) {
        PriceSort.RANK -> rest
        PriceSort.GAIN -> rest.sortedByDescending { it.change24h }
        PriceSort.LOSS -> rest.sortedBy { it.change24h }
        PriceSort.VOLUME -> rest.sortedByDescending { it.volume24h }
    }
}

/** alternative.me's raw class to a content key (March's `fgLabel`); unknown classes show raw. */
internal fun fearKey(raw: String): String? = when (raw) {
    "Extreme Fear" -> "mkt.fg.ExtremeFear"
    "Fear" -> "mkt.fg.Fear"
    "Neutral" -> "mkt.fg.Neutral"
    "Greed" -> "mkt.fg.Greed"
    "Extreme Greed" -> "mkt.fg.ExtremeGreed"
    else -> null
}

/**
 * Prices (`ui/MarketScreen.kt` of March, REIMPLEMENT): header with the live-mode label, refresh and the
 * data age; the overview strip; search; sort; the coin rows; the disclaimer at the bottom (K6.2).
 */
@Composable
internal fun PricesScreen(
    content: ContentRepository,
    fmt: MarketFmt,
    state: MarketState,
    watch: Set<String>,
    toman: Boolean,
    onToman: (Boolean) -> Unit,
    onToggle: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
) {
    var q by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(PriceSort.RANK) }
    val rate = state.usdt?.toman
    val shown = remember(state.coins, q, sort) { shownCoins(state.coins, q, sort) }
    val firstLoad = state.coins.none { !it.isToman } && state.error == null

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "head") { Header(content, fmt, state, toman, onToman, onRefresh) }
        state.overview?.let { ov -> item(key = "overview") { OverviewStrip(content, fmt, ov) } }
        item(key = "search") {
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                singleLine = true,
                placeholder = { Text(content.ui("mkt.prices.search")) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (q.isNotEmpty()) {
                        IconButton(onClick = { q = "" }) { Icon(Icons.Filled.Close, contentDescription = content.ui("mkt.prices.clear")) }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        item(key = "sort") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PriceSort.entries.forEach { s ->
                    FilterChip(selected = sort == s, onClick = { sort = s }, label = { Text(content.ui("mkt.sort." + s.name)) })
                }
            }
        }
        if (firstLoad) {
            item(key = "loading") {
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            }
        }
        if (shown.isEmpty() && state.coins.isNotEmpty()) {
            item(key = "empty") { EmptyState(content.ui("mkt.prices.empty")) }
        }
        items(shown, key = { it.id }) { c ->
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                CoinRow(content, fmt, c, c.id in watch, toman, rate, onToggle = { onToggle(c.id) }, onOpen = { onOpen(c.id) })
            }
        }
        item(key = "disclaimer") {
            Text(
                content.ui("mkt.disclaimer"),
                style = MaterialTheme.typography.bodySmall,
                color = LocalAndarzColors.current.textMuted,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/**
 * The live-mode label and the age of the last data (March's `MarketHeader`). When the list never loaded,
 * the typed error is shown in its place; when an older list is still shown, the error sits under it, so
 * the screen keeps working offline with the time of the last data (K6.2, K12).
 */
@Composable
private fun Header(
    content: ContentRepository,
    fmt: MarketFmt,
    state: MarketState,
    toman: Boolean,
    onToman: (Boolean) -> Unit,
    onRefresh: () -> Unit,
) {
    val c = LocalAndarzColors.current
    val liveNow = state.live == LiveMode.BINANCE || state.live == LiveMode.NOBITEX
    val live = content.ui("mkt.live." + state.live.name)
    val err = state.error?.let { MarketModule.texts.error(it) }
    val failed = err != null && state.coins.size <= 1
    val status = when {
        failed -> content.ui("mkt.prices.failed") + err
        state.updatedAt > 0 -> live + SEP + fmt.clock(state.updatedAt)
        else -> live
    }
    val tone = when {
        failed -> c.redline
        liveNow -> c.allowed
        else -> c.textMuted
    }
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(content.ui("mkt.prices.title"), style = MaterialTheme.typography.titleMedium)
                Text(status, style = MaterialTheme.typography.bodySmall, color = tone, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (err != null && !failed) {
                    Text(Fmt.isolate("CoinGecko") + ": " + err, style = MaterialTheme.typography.bodySmall, color = c.redline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onRefresh) {
                if (state.loading) {
                    CircularProgressIndicator(Modifier.padding(4.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = content.ui("mkt.prices.refresh"))
                }
            }
        }
        // USD/Toman toggle (K6.2). No language or theme switch: Andarz Settings own both.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            FilterChip(selected = !toman, onClick = { onToman(false) }, label = { Text(content.ui("mkt.unit.usd")) })
            FilterChip(selected = toman, onClick = { onToman(true) }, label = { Text(content.ui("mkt.unit.toman")) })
        }
    }
}

@Composable
private fun OverviewStrip(content: ContentRepository, fmt: MarketFmt, ov: MarketOverview) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (ov.totalCap > 0) {
            StatTile(content.ui("mkt.ov.cap"), fmt.cap(ov.totalCap), fmt.pct(ov.capChange24h), ov.capChange24h >= 0, null, Modifier.weight(1f))
        }
        if (ov.btcDominance > 0) {
            StatTile(content.ui("mkt.ov.dominance"), fmt.share(ov.btcDominance), null, null, null, Modifier.weight(1f))
        }
        val fg = ov.fearGreed
        if (fg != null) {
            val label = fearKey(ov.fearLabel)?.let { content.ui(it) } ?: ov.fearLabel
            // March's tones: below 45 is fear (caution), above 55 greed; never color alone, the label says it.
            val ok: Boolean? = when {
                fg < 45 -> false
                fg <= 55 -> null
                else -> true
            }
            StatTile(content.ui("mkt.ov.fg"), fmt.int(fg), label, ok, null, Modifier.weight(1f))
        }
    }
}
