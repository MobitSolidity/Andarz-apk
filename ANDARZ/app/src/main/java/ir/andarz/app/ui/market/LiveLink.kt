package ir.andarz.app.ui.market

import ir.andarz.app.ui.safeLaunch

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.andarz.app.market.LinkLevel
import ir.andarz.app.market.TradeLink
import ir.andarz.app.market.TradeWatcher
import ir.andarz.app.ui.components.SectionCard
import ir.andarz.app.ui.components.SwitchRow
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui
import ir.andarz.data.db.AndarzDatabase
import ir.andarz.data.db.TradeAlertEntity
import ir.andarz.data.db.TradeEntity
import ir.andarz.engine.Fmt
import ir.andarz.engine.Params
import ir.andarz.market.MarketGate
import ir.andarz.market.MarketModule
import ir.andarz.market.model.Coin
import ir.andarz.market.model.MarketState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Read access to the market state for journal screens (K9.1 c, d). It never acquires the hub itself: the chip
 * and the watch-list rows show a price only when something else already demands the stream (D-M06, D-M22).
 */
@HiltViewModel
class LiveLinkViewModel @Inject constructor(private val db: AndarzDatabase, private val watcher: TradeWatcher) : ViewModel() {
    val gate: StateFlow<MarketGate> = (if (MarketModule.isInstalled) MarketModule.gate else flowOf(MarketGate()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MarketGate())
    val market: StateFlow<MarketState> = if (MarketModule.isInstalled) MarketModule.repository.state else MutableStateFlow(MarketState())
    fun alertsFor(tradeId: String): StateFlow<List<TradeAlertEntity>> =
        db.tradeAlerts().observeFor(tradeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun setArmed(tradeId: String, level: LinkLevel, on: Boolean) = safeLaunch { watcher.setArmed(tradeId, level, on) }
}

/**
 * The live price chip (K9.1 c): shown when Market is on and the quote is younger than 60 s. Tapping copies the
 * number into the field as typed text; nothing is stored as "from market".
 */
@Composable
fun LivePriceChip(symbol: String, onUse: (String) -> Unit, vm: LiveLinkViewModel = hiltViewModel()) {
    val u = ui()
    val gate by vm.gate.collectAsState()
    val state by vm.market.collectAsState()
    val now by ir.andarz.app.ui.rememberNowMillis(5_000)
    if (!gate.mayUseNetwork || symbol.isBlank()) return
    val coin = TradeLink.resolve(symbol, state.coins) ?: return
    Column {
        if (TradeLink.fresh(coin, now)) {
            AssistChip(
                onClick = { onUse(Fmt.number(coin.price, Ui.decimalsFor(coin.price), fa = false, group = false, trim = true)) },
                label = { Text(u.t("mkt.chip.live", "symbol" to u.symbol(coin.symbol), "price" to u.price(coin.price))) },
            )
            Text(
                u.t("mkt.chip.age", "age" to u.duration(((now - coin.tickAt) / 60_000).coerceAtLeast(0)), "source" to u.t(if (coin.source == ir.andarz.market.model.Source.COINGECKO) "mkt.live.POLLING" else "mkt.live.${coin.source.name}")) +
                    " \u00B7 " + u.t("mkt.chip.note"),
                style = MaterialTheme.typography.labelSmall, color = LocalAndarzColors.current.textMuted,
            )
        } else {
            Text(u.t("mkt.chip.stale"), style = MaterialTheme.typography.labelSmall, color = LocalAndarzColors.current.textMuted)
        }
    }
}

private object Ui { fun decimalsFor(x: Double) = ir.andarz.app.ui.Ui.priceDecimals(x).coerceAtLeast(2) + 2 }

/** Trade-linked price alerts on an OPEN trade (K9.1 b). */
@Composable
fun TradeLinkCard(t: TradeEntity, p: Params, vm: LiveLinkViewModel = hiltViewModel()) {
    val u = ui()
    val gate by vm.gate.collectAsState()
    val state by vm.market.collectAsState()
    val alerts by remember(t.id) { vm.alertsFor(t.id) }.collectAsState()
    val c = LocalAndarzColors.current
    SectionCard(u.t("mkt.link.title")) {
        if (!gate.mayUseNetwork) { Text(u.t("mkt.link.needMarket"), color = c.caution); return@SectionCard }
        if (state.coins.isNotEmpty() && TradeLink.resolve(t.symbol, state.coins) == null) Text(u.t("mkt.link.nocoin"), color = c.caution)
        val armed = alerts.associateBy { it.level }
        for (level in LinkLevel.entries) {
            val price = TradeLink.levelPrice(t, level, p)
            val key = when (level) { LinkLevel.TP -> "mkt.link.tp"; LinkLevel.PARTIAL -> "mkt.link.partial"; LinkLevel.STOP -> "mkt.link.stop" }
            val a = armed[level.name]
            SwitchRow(u.t(key, "price" to u.price(a?.price ?: price)), a != null && a.firedAt == null, { on -> vm.setArmed(t.id, level, on) })
        }
        Text(u.t("mkt.link.note"), style = MaterialTheme.typography.labelMedium, color = c.textMuted, modifier = Modifier.padding(top = 4.dp))
        // K9.1 b: these alerts arrive only while the app is open or the monitor runs (D-M57).
        Text(u.t("mkt.alerts.bg.hint"), style = MaterialTheme.typography.labelSmall, color = c.textMuted)
    }
}

/** Watch-list row price and 24 h change, display only (K9.1 d). */
@Composable
fun WatchPrice(symbol: String, vm: LiveLinkViewModel = hiltViewModel()) {
    val u = ui()
    val gate by vm.gate.collectAsState()
    val state by vm.market.collectAsState()
    if (!gate.mayUseNetwork) return
    val coin: Coin = TradeLink.resolve(symbol, state.coins) ?: return
    val ch = coin.change24h / 100
    Text(
        u.price(coin.price) + " \u00B7 " + u.pct(ch, 2, signed = true),
        style = MaterialTheme.typography.bodyMedium,
        color = if (ch < 0) LocalAndarzColors.current.redline else LocalAndarzColors.current.allowed,
    )
}
