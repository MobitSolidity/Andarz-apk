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
import android.widget.Toast
import ir.andarz.app.ui.components.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.andarz.app.content.ContentRepository
import ir.andarz.app.ui.components.NumberField
import ir.andarz.app.ui.components.StatTile
import ir.andarz.app.ui.components.SwitchRow
import ir.andarz.app.ui.ui
import ir.andarz.market.model.CoinRule
import kotlin.math.roundToInt
import ir.andarz.app.ui.components.TagChip
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.engine.Fmt
import ir.andarz.market.model.Coin
import ir.andarz.market.model.Source

/**
 * The read-only part of March's coin sheet (MK1): price, alternate price, 24 h / 7 d change, source, the
 * 7-day chart (or the session chart for USDT/Toman), key stats and the star; and (MK2) the per-coin
 * rule editor with its own threshold and the one-shot above/below targets.
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
    rule: CoinRule? = null,
    defaultPct: Float = 5f,
    onSaveRule: ((CoinRule) -> Unit)? = null,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            SheetBody(content, fmt, c, toman, rate, starred, session, onToggleStar)
            if (onSaveRule != null) RuleEditor(content, c, rule, defaultPct, onSaveRule)
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

/**
 * March's per-coin alert: an own threshold (slider 0.5..20 in steps of 0.5) and one-shot targets above and
 * below, in USD or in Toman for USDT/Toman. Input is parsed with `Fmt.normalizeInput` (§3.10), not March's
 * `parseNum`; an empty or invalid field means "no target".
 */
@Composable
private fun RuleEditor(content: ContentRepository, c: Coin, rule: CoinRule?, defaultPct: Float, onSave: (CoinRule) -> Unit) {
    val u = ui()
    val ctx = LocalContext.current
    val muted = LocalAndarzColors.current.textMuted
    var custom by remember(c.id, rule) { mutableStateOf(rule?.pct != null) }
    var pct by remember(c.id, rule) { mutableFloatStateOf(rule?.pct ?: defaultPct) }
    var above by remember(c.id, rule) { mutableStateOf(rule?.above?.let { plain(it) } ?: "") }
    var below by remember(c.id, rule) { mutableStateOf(rule?.below?.let { plain(it) } ?: "") }
    HorizontalDivider(Modifier.padding(vertical = 16.dp), color = LocalAndarzColors.current.divider)
    Text(content.ui("mkt.rule.title") + Fmt.isolate(c.symbol), style = MaterialTheme.typography.titleMedium)
    SwitchRow(
        content.ui("mkt.rule.custom"), custom, { custom = it },
        hint = if (custom) null else content.ui("mkt.rule.usingDefault", "pct" to Fmt.isolate(u.num(defaultPct.toDouble(), 1, group = false, trim = true))),
    )
    if (custom) {
        Text(pctText(u, pct.toDouble()), style = MaterialTheme.typography.titleMedium, color = LocalAndarzColors.current.caution)
        Slider(value = pct, onValueChange = { pct = (it * 2).roundToInt() / 2f }, valueRange = 0.5f..20f)
    }
    val unitLabel = content.ui(if (c.isToman) "mkt.unit.toman" else "mkt.unit.usd")
    NumberField(content.ui("mkt.rule.aboveField", "unit" to unitLabel), above, { above = it }, Modifier.fillMaxWidth().padding(top = 8.dp))
    NumberField(content.ui("mkt.rule.belowField", "unit" to unitLabel), below, { below = it }, Modifier.fillMaxWidth().padding(top = 8.dp))
    Text(content.ui("mkt.rule.once"), style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(top = 6.dp))
    Button(
        onClick = {
            onSave(ruleOf(c.id, custom, pct, above, below))
            Toast.makeText(ctx, content.ui("mkt.rule.saved", "symbol" to Fmt.isolate(c.symbol)), Toast.LENGTH_SHORT).show()
        },
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) { Text(content.ui("mkt.rule.save")) }
}

/** The rule a save produces; a field that is not a positive number is no target (March). */
internal fun ruleOf(id: String, custom: Boolean, pct: Float, above: String, below: String): CoinRule =
    CoinRule(
        id = id,
        pct = if (custom) pct else null,
        above = Fmt.normalizeInput(above)?.takeIf { it > 0 },
        below = Fmt.normalizeInput(below)?.takeIf { it > 0 },
    )

private fun plain(v: Double): String = java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString()
