package ir.andarz.app.ui.market

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.andarz.app.content.ContentRepository
import ir.andarz.app.ui.components.ArchShapes
import ir.andarz.app.ui.components.EmptyState
import ir.andarz.app.ui.components.FilterChip
import ir.andarz.app.ui.components.Pill
import ir.andarz.app.ui.components.Text
import ir.andarz.app.ui.components.archSurface
import ir.andarz.app.ui.icons.Glyph
import ir.andarz.app.ui.icons.GlyphIcon
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui
import ir.andarz.market.MarketError
import ir.andarz.market.model.Coin
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
 * Prices (the Prices board of the canvas): the overview card, the search pill, the sort chips, the coin rows as one
 * list card, and the disclaimer. While the first answer is on its way it shows the quiet loading card and placeholder
 * rows; after a failure it shows the saffron warning card, the failing service, and the last prices under a header
 * that says they are old (K6.2, K12). The unit toggle and the refresh button live in the header of Market.
 */
@Composable
internal fun PricesScreen(
    content: ContentRepository,
    fmt: MarketFmt,
    state: MarketState,
    watch: Set<String>,
    toman: Boolean,
    onToggle: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val u = ui()
    val k = LocalAndarzColors.current
    var q by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(PriceSort.RANK) }
    val rate = state.usdt?.toman
    val shown = remember(state.coins, q, sort) { shownCoins(state.coins, q, sort) }
    val err = state.error
    val firstLoad = state.coins.none { !it.isToman } && err == null
    val hasCoins = state.coins.isNotEmpty()
    val stale = err != null && shown.isNotEmpty()
    val skeletons = if (firstLoad) 5 else 0
    val total = shown.size + skeletons

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = LocalDensity.current.fontScale
        val layout = when {
            maxWidth >= 360.dp && scale <= 1.15f -> RowLayout.FULL
            maxWidth >= 300.dp && scale <= 1.3f -> RowLayout.NO_SPARK
            else -> RowLayout.STACKED
        }
        val compact = maxWidth < 340.dp || scale > 1.15f
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp)) {
            if (firstLoad) {
                item(key = "loading") { LoadingCard(Modifier.padding(bottom = 8.dp)) }
            }
            if (err != null) {
                item(key = "error") {
                    ErrorCard(
                        title = if (err == MarketError.NoNetwork) u.t("mkt.err.noNetwork") else u.t("app.mkt.err.title"),
                        body = u.t(if (hasCoins) "app.mkt.err.bodyOld" else "app.mkt.err.bodyNone"),
                        onRetry = onRefresh,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                val row = serviceRowOf(err)
                if (row != null) {
                    item(key = "services") { ServicesCard(row, Modifier.padding(bottom = 8.dp)) }
                }
            }
            if (state.overview != null || firstLoad) {
                item(key = "overview") { OverviewCard(content, fmt, state.overview, compact, Modifier.padding(bottom = 10.dp)) }
            }
            if (hasCoins || firstLoad) {
                item(key = "search") { SearchPill(q, { q = it }, enabled = !firstLoad, modifier = Modifier.padding(bottom = 6.dp)) }
                item(key = "sort") {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PriceSort.entries.forEach { s ->
                            FilterChip(selected = sort == s, onClick = { sort = s }, enabled = !firstLoad, label = { Text(u.t("mkt.sort." + s.name)) })
                        }
                    }
                }
            }
            if (stale) {
                item(key = "saved") {
                    CardHeaderRow(u.t("app.mkt.saved.title"), RowSlot.FIRST) {
                        Pill(u.t("app.mkt.stale"), container = k.sfC, ink = k.sf, glyph = Glyph.ALERT_CIRCLE, height = 22.dp)
                    }
                }
            }
            if (shown.isEmpty() && hasCoins) {
                item(key = "empty") { EmptyState(u.t("mkt.prices.empty")) }
            }
            itemsIndexed(shown, key = { _, c -> c.id }, contentType = { _, _ -> "coin" }) { i, c ->
                CoinRow(
                    content, fmt, c, c.id in watch, toman, rate,
                    onToggle = { onToggle(c.id) }, onOpen = { onOpen(c.id) },
                    slot = slotOf(i, total, stale), layout = layout,
                )
            }
            if (skeletons > 0) {
                itemsIndexed(List(skeletons) { it }, key = { _, j -> "sk$j" }, contentType = { _, _ -> "skeleton" }) { _, j ->
                    SkeletonRow(slotOf(shown.size + j, total, stale), j)
                }
            }
            item(key = "gap") { Spacer(Modifier.height(10.dp)) }
            if (err != null) {
                item(key = "offline") { NoteRow(u.t("app.mkt.err.offlineNote"), Modifier.padding(bottom = 8.dp)) }
            }
            item(key = "disclaimer") { NoteRow(u.t("mkt.disclaimer")) }
        }
    }
}

/** The search field of the canvas: a 48 dp pill with the search glyph, the query, and a clear button once there is one. */
@Composable
private fun SearchPill(q: String, onQ: (String) -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val u = ui()
    val k = LocalAndarzColors.current
    val focus = LocalFocusManager.current
    val label = u.t("app.mkt.search")
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp)
            .archSurface(ArchShapes.pill, k.sf1, if (enabled) k.outline else k.divider)
            .padding(start = 16.dp, end = if (q.isEmpty()) 16.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlyphIcon(Glyph.SEARCH, null, tint = k.muted, size = 20.dp, strokeWidth = 1.9f)
        BasicTextField(
            value = q,
            onValueChange = onQ,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = k.text),
            cursorBrush = SolidColor(k.fz),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            modifier = Modifier.weight(1f).semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                    if (q.isEmpty()) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = k.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
            },
        )
        if (q.isNotEmpty()) {
            IconButton(onClick = { onQ("") }, modifier = Modifier.size(48.dp)) {
                GlyphIcon(Glyph.X_SMALL, u.t("mkt.prices.clear"), tint = k.muted, size = 20.dp, strokeWidth = 2f)
            }
        }
    }
}
