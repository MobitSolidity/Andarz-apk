package ir.andarz.app.ui.market

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.andarz.app.content.ContentRepository
import ir.andarz.app.ui.components.StatTile
import ir.andarz.app.ui.components.TagChip
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.engine.Fmt
import ir.andarz.market.model.Coin
import ir.andarz.market.model.Source

/**
 * The read-only part of March's coin sheet (MK1): price, alternate price, 24 h / 7 d change, source, the
 * 7-day chart (or the session chart for USDT/Toman), key stats and the star. The per-coin rule editor
 * arrives in MK2 (MERGE_MAP row `ui/CoinSheet.kt`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CoinSheet(
    content: ContentRepository,
    fmt: MarketFmt,
    c: Coin,
    toman: Boolean,
    rate: Double?,
    starred: Boolean,
    session: List<Double>,
    onToggleStar: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            SheetBody(content, fmt, c, toman, rate, starred, session, onToggleStar)
        }
    }
}

@Composable
private fun SheetBody(
    content: ContentRepository,
    fmt: MarketFmt,
    c: Coin,
    toman: Boolean,
    rate: Double?,
    starred: Boolean,
    session: List<Double>,
    onToggleStar: () -> Unit,
) {
    val muted = LocalAndarzColors.current.textMuted
    val tone = toneOf(c.change24h)
    val unit: (Double) -> String = { v -> if (c.isToman) fmt.toman(v) else fmt.usd(v) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(Fmt.isolate(c.symbol), style = MaterialTheme.typography.headlineSmall)
            // March: "name · rank N"; the rank text carries its own leading separator.
            Text(
                if (c.isToman) coinName(content, c) else c.name + content.ui("mkt.sheet.rank") + fmt.int(c.rank),
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
            )
        }
        StarButton(starred, content.ui("mkt.sheet.star"), onToggleStar)
    }

    Text(fmt.coinPrice(c, toman, rate), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
    fmt.altPrice(c, toman, rate)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = muted) }

    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TagChip(content.ui("mkt.sheet.h24") + fmt.pct(c.change24h), tone)
        if (!c.isToman && c.change7d != 0.0) TagChip(content.ui("mkt.sheet.d7") + fmt.pct(c.change7d), toneOf(c.change7d))
        TagChip(
            when (c.source) {
                Source.BINANCE -> content.ui("mkt.live.BINANCE")
                Source.NOBITEX -> content.ui("mkt.sheet.liveIran")
                Source.COINGECKO -> Fmt.isolate("CoinGecko")
            },
            LocalAndarzColors.current.info,
        )
    }

    val weekly = !c.isToman && c.spark.size >= 2
    val chart = if (weekly) c.spark else session
    Text(
        content.ui(if (weekly) "mkt.sheet.chart7d" else "mkt.sheet.chartSession"),
        style = MaterialTheme.typography.labelMedium,
        color = muted,
        modifier = Modifier.padding(top = 16.dp),
    )
    if (chart.size >= 2) {
        Sparkline(chart, toneOf(chart.last() - chart.first()), Modifier.fillMaxWidth().height(160.dp).padding(top = 8.dp), fill = true)
    } else {
        Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
            Text(content.ui("mkt.sheet.collecting"), style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }

    val stats = buildList {
        if (c.high24h > 0) add(content.ui("mkt.sheet.high24") to unit(c.high24h))
        if (c.low24h > 0) add(content.ui("mkt.sheet.low24") to unit(c.low24h))
        if (c.volume24h > 0) add(content.ui("mkt.sheet.vol24") to fmt.cap(c.volume24h))
        if (c.marketCap > 0) add(content.ui("mkt.sheet.cap") to fmt.cap(c.marketCap))
        if (c.ath > 0) add(content.ui("mkt.sheet.ath") to unit(c.ath))
        if (c.ath > 0) add(content.ui("mkt.sheet.athDist") to fmt.pct(c.athChange))
    }
    stats.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (k, v) -> StatTile(k, v, null, null, null, Modifier.weight(1f)) }
            if (row.size == 1) Box(Modifier.weight(1f))
        }
    }
}
