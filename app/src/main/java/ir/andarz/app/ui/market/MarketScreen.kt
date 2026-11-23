package ir.andarz.app.ui.market

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.andarz.app.content.ContentRepository
import ir.andarz.app.ui.nav.MarketSection
import ir.andarz.app.ui.ui

/**
 * The Market destination: the header with its one status line, the four sections as one segmented control, and the
 * section below (K6.1).
 *
 * The header and the tabs stay in every state: with the master switch off, News (local) and Alerts (its settings)
 * must stay reachable, while Prices and My coins show the off state. The unit toggle and the refresh button belong to
 * the two price sections only. The section and the open sheet survive process death.
 */
@Composable
fun MarketScreen(
    content: ContentRepository,
    modifier: Modifier = Modifier,
    initialCoin: String? = null,
    initialSection: MarketSection? = null,
    viewModel: MarketViewModel = hiltViewModel(),
) {
    val gate by viewModel.gate.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val watch by viewModel.watch.collectAsStateWithLifecycle()
    val toman by viewModel.toman.collectAsStateWithLifecycle()
    val sectionName by viewModel.section.collectAsStateWithLifecycle()
    val openCoin by viewModel.openCoin.collectAsStateWithLifecycle()
    val alertsVm: AlertsNewsViewModel = hiltViewModel()
    val alertSettings by alertsVm.settings.collectAsStateWithLifecycle()
    val rules by alertsVm.rules.collectAsStateWithLifecycle()
    val reminded by alertsVm.reminded.collectAsStateWithLifecycle()
    val calendarData by alertsVm.calendarData.collectAsStateWithLifecycle()
    val section = MarketSection.entries.firstOrNull { it.name == sectionName } ?: MarketSection.PRICES
    var consentOpen by rememberSaveable { mutableStateOf(false) }
    val u = ui()
    val fmt = remember(u.fa, u.zone) { MarketFmt(u.fa, content.ui("mkt.unit.tomanSuffix"), u.zone) }
    val on = gate.mayUseNetwork
    val pricing = section == MarketSection.PRICES || section == MarketSection.COINS

    // A deep link `market/coin/{id}` opens the sheet once (K6.11).
    LaunchedEffect(initialCoin) { if (initialCoin != null) viewModel.openCoin(initialCoin) }
    LaunchedEffect(initialSection) { if (initialSection != null) viewModel.setSection(initialSection) }

    // K6.4 reason (a): the hub is held only while this screen is at least STARTED. Leaving the screen, or
    // sending the app to the background, gives the demand back and the socket closes.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> viewModel.onVisible()
                Lifecycle.Event.ON_STOP -> viewModel.onHidden()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose {
            owner.lifecycle.removeObserver(obs)
            viewModel.onHidden()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        MarketHeader(
            title = u.t("app.mkt.title"),
            status = marketStatus(u, fmt, state, on),
            controls = if (on && pricing) ({ HeaderControls(toman, viewModel::setToman, state.loading, viewModel::refresh) }) else null,
        )
        SectionTabs(section, { viewModel.setSection(it) }, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (section) {
                // Prices and My coins need the network, so with the master switch off they show the off
                // state (K6.1). News is local and keeps working; Alerts shows its settings with a note.
                MarketSection.PRICES, MarketSection.COINS ->
                    if (on) {
                        if (section == MarketSection.PRICES) {
                            PricesScreen(
                                content = content, fmt = fmt, state = state, watch = watch, toman = toman,
                                onToggle = viewModel::toggleStar, onRefresh = viewModel::refresh,
                                onOpen = { viewModel.openCoin(it) },
                            )
                        } else {
                            MyCoinsScreen(
                                content = content, fmt = fmt, coins = state.coins, watch = watch, toman = toman,
                                rate = state.usdt?.toman, onToggle = viewModel::toggleStar,
                                onOpen = { viewModel.openCoin(it) },
                                onGoPrices = { viewModel.setSection(MarketSection.PRICES) },
                                onGoNews = { viewModel.setSection(MarketSection.NEWS) },
                                reminders = calendarData.events
                                    .filter { it.id in reminded && it.at > System.currentTimeMillis() }
                                    .map { e -> e.title to (u.date(u.localDate(java.time.Instant.ofEpochMilli(e.at))) + SEP + u.time(java.time.Instant.ofEpochMilli(e.at))) },
                            )
                        }
                    } else {
                        MarketOff(
                            onTurnOn = {
                                // Consent first, exactly once; afterwards the switch alone is enough (K4).
                                if (gate.consentAt == null) consentOpen = true else viewModel.setEnabled(true)
                            },
                            onWhat = { consentOpen = true },
                        )
                    }

                // News is local and keeps working with Market off (K4).
                MarketSection.NEWS -> NewsScreen(
                    content = content, data = calendarData, reminded = reminded,
                    onToggle = { alertsVm.toggleReminder(it) }, onClock = { alertsVm.setClockAlarm(it) },
                )
                // Alerts shows its settings with Market off, with a note that price alerts need it on (K4).
                MarketSection.ALERTS -> AlertsScreen(
                    content = content, fmt = fmt, marketOn = on, s = alertSettings, rules = rules,
                    coins = state.coins, vm = alertsVm, onOpen = { viewModel.openCoin(it) },
                )
            }
        }
    }

    val coin = openCoin?.let { id -> state.coins.firstOrNull { it.id == id } }
    if (coin != null && on) {
        CoinSheet(
            content = content, fmt = fmt, c = coin, toman = toman, rate = state.usdt?.toman,
            starred = coin.id in watch, session = viewModel.session(coin.id),
            onToggleStar = { viewModel.toggleStar(coin.id) },
            onDismiss = { viewModel.openCoin(null) },
            rule = rules[coin.id],
            defaultPct = alertSettings.thresholdPct,
            onSaveRule = { alertsVm.setRule(it) },
        )
    }

    if (consentOpen) {
        ConsentSheet(
            hosts = viewModel.hosts,
            onAccept = {
                viewModel.acceptConsent()
                consentOpen = false
            },
            onDecline = { consentOpen = false },
        )
    }
}
