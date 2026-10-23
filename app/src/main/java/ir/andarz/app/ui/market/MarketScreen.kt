package ir.andarz.app.ui.market

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.horizontalScroll

import ir.andarz.engine.Fmt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import ir.andarz.app.content.ContentRepository
import ir.andarz.app.ui.nav.MarketSection
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui

/**
 * The Market destination: one screen with four sections in a scrollable segmented row (K6.1).
 *
 * M0 shipped the off state and the consent sheet; MK1 adds Prices, My coins and the read-only coin sheet.
 * News and Alerts get their content in MK2. The section and the open sheet survive process death.
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
        SectionRow(
            content = content,
            selected = section,
            onSelect = { viewModel.setSection(it) },
        )

        when (section) {
            // Prices and My coins need the network, so with the master switch off they show the off
            // state (K6.1). News is local and keeps working; Alerts shows its settings with a note.
            MarketSection.PRICES, MarketSection.COINS ->
                if (gate.mayUseNetwork) {
                    if (section == MarketSection.PRICES) {
                        PricesScreen(
                            content = content, fmt = fmt, state = state, watch = watch, toman = toman,
                            onToman = viewModel::setToman, onToggle = viewModel::toggleStar,
                            onRefresh = viewModel::refresh, onOpen = { viewModel.openCoin(it) },
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
                        content = content,
                        onTurnOn = {
                            // Consent first, exactly once; afterwards the switch alone is enough (K4).
                            if (gate.consentAt == null) consentOpen = true else viewModel.setEnabled(true)
                        },
                    )
                }

            // News is local and keeps working with Market off (K4).
            MarketSection.NEWS -> NewsScreen(
                content = content, data = calendarData, reminded = reminded,
                onToggle = { alertsVm.toggleReminder(it) }, onClock = { alertsVm.setClockAlarm(it) },
            )
            // Alerts shows its settings with Market off, with a note that price alerts need it on (K4).
            MarketSection.ALERTS -> AlertsScreen(
                content = content, fmt = fmt, marketOn = gate.mayUseNetwork, s = alertSettings, rules = rules,
                coins = state.coins, vm = alertsVm, onOpen = { viewModel.openCoin(it) },
            )
        }
    }

    val coin = openCoin?.let { id -> state.coins.firstOrNull { it.id == id } }
    if (coin != null && gate.mayUseNetwork) {
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
            content = content,
            hosts = viewModel.hosts,
            onAccept = {
                viewModel.acceptConsent()
                consentOpen = false
            },
            onDecline = { consentOpen = false },
        )
    }
}

@Composable
private fun SectionRow(
    content: ContentRepository,
    selected: MarketSection,
    onSelect: (MarketSection) -> Unit,
) {
    // A small, fixed set of chips: a plain scrolling Row, no lazy virtualization (compose-performance, D-79).
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MarketSection.entries.forEach { item ->
            FilterChip(
                selected = item == selected,
                onClick = { onSelect(item) },
                label = { Text(content.ui(item.labelKey)) },
            )
        }
    }
}

/**
 * The off state (`mkt.off.*`): it says plainly that nothing is sent while Market is off, which is the
 * literal truth — no socket is opened before consent (K4).
 */
@Composable
private fun MarketOff(
    content: ContentRepository,
    onTurnOn: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = content.ui("mkt.off.title"),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = content.ui("mkt.off.body"),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalAndarzColors.current.textMuted,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onTurnOn) {
            Text(content.ui("mkt.off.action"))
        }
    }
}

/**
 * The one-time consent sheet (K4): it names every host, says what is and is not sent, and starts nothing
 * until the user accepts. "Not now" leaves Market off and silent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConsentSheet(
    content: ContentRepository,
    hosts: List<String>,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDecline, sheetState = state) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(content.ui("mkt.consent.title"), style = MaterialTheme.typography.titleMedium)
            Text(
                content.ui("mkt.consent.body"),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalAndarzColors.current.textMuted,
            )
            Card(colors = CardDefaults.cardColors(containerColor = LocalAndarzColors.current.surfaceAlt)) {
                Text(
                    // Hosts are Latin tokens inside Persian text: each one is an LTR isolate (trap 9).
                    text = content.ui("mkt.consent.hosts", "hosts" to hosts.joinToString(ISOLATE_SEPARATOR) { Fmt.isolate(it) }),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
                Text(content.ui("mkt.consent.accept"))
            }
            TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
                Text(content.ui("mkt.consent.decline"))
            }
        }
    }
}


// A locale-neutral separator, so no punctuation of the UI language lives in code (law 3).
private const val ISOLATE_SEPARATOR = " · "
