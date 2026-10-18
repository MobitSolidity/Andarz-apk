package ir.andarz.app.ui.market

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import ir.andarz.app.content.ContentRepository
import ir.andarz.app.ui.nav.MarketSection
import ir.andarz.app.ui.theme.LocalAndarzColors

/**
 * The Market destination: one screen with four sections in a scrollable segmented row (K6.1).
 *
 * M0 ships the off state of Prices and the consent sheet it opens, and touches no network at all. Prices,
 * My coins, News and Alerts get their real content in MK1 and MK2.
 */
@Composable
fun MarketScreen(
    content: ContentRepository,
    modifier: Modifier = Modifier,
    viewModel: MarketViewModel = hiltViewModel(),
) {
    val gate by viewModel.gate.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf(MarketSection.PRICES) }
    var consentOpen by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        SectionRow(
            content = content,
            selected = section,
            onSelect = { section = it },
        )

        when (section) {
            // Prices and My coins need the network, so with the master switch off they show the off
            // state (K6.1). News is local and keeps working; Alerts shows its settings with a note.
            MarketSection.PRICES, MarketSection.COINS ->
                if (gate.mayUseNetwork) {
                    Placeholder(content.ui("mkt.tab." + if (section == MarketSection.PRICES) "prices" else "coins"))
                } else {
                    MarketOff(
                        content = content,
                        onTurnOn = {
                            // Consent first, exactly once; afterwards the switch alone is enough (K4).
                            if (gate.consentAt == null) consentOpen = true else viewModel.setEnabled(true)
                        },
                    )
                }

            MarketSection.NEWS -> Placeholder(content.ui("mkt.tab.news"))
            MarketSection.ALERTS -> Placeholder(content.ui("mkt.tab.alerts"))
        }
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
    val sections = remember { MarketSection.entries.toList() }
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(sections) { item ->
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
                    text = content.ui("mkt.consent.hosts", "hosts" to hosts.joinToString(ISOLATE_SEPARATOR) { isolate(it) }),
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

@Composable
private fun Placeholder(label: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

// A locale-neutral separator, so no punctuation of the UI language lives in code (law 3).
private const val ISOLATE_SEPARATOR = " · "

// The bidi isolate of Appendix C trap 9, built from code points so no literal bidirectional control
// character sits in a source file (lint BidiSpoofing). M1 moves this to Fmt.isolate in :engine.
private val LTR_ISOLATE: String = String(Character.toChars(0x2066))
private val POP_ISOLATE: String = String(Character.toChars(0x2069))

private fun isolate(text: String): String = LTR_ISOLATE + text + POP_ISOLATE
