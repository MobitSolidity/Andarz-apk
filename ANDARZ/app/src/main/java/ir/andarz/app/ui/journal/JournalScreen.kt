package ir.andarz.app.ui.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.andarz.app.ui.components.EmptyState
import ir.andarz.app.ui.components.RChip
import ir.andarz.app.ui.components.TagChip
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui
import ir.andarz.data.db.AndarzDatabase
import ir.andarz.data.db.SetupEntity
import ir.andarz.data.db.TradeEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class JournalViewModel @Inject constructor(db: AndarzDatabase) : ViewModel() {
    val trades: StateFlow<List<TradeEntity>> = db.trades().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val setups: StateFlow<List<SetupEntity>> = db.setups().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}

enum class JournalSort { NEWEST, OLDEST, BEST_R, WORST_R }

/** Filter state of the journal (5.6). Pure, so it is tested on the JVM. */
data class JournalFilter(
    val status: String? = null,
    val result: String? = null,
    val account: String? = null,
    val compliance: Boolean? = null,
    val hasViolation: Boolean = false,
    val query: String = "",
    val sort: JournalSort = JournalSort.NEWEST,
) {
    fun apply(rows: List<TradeEntity>, setupName: (String?) -> String): List<TradeEntity> {
        val q = query.trim().lowercase(java.util.Locale.ROOT)
        val out = rows.filter { t ->
            (status == null || t.status == status) &&
                (result == null || t.result == result) &&
                (account == null || t.account == account) &&
                (compliance == null || t.ok == compliance) &&
                (!hasViolation || t.violations.isNotEmpty()) &&
                (q.isEmpty() || listOf(t.symbol, setupName(t.setupId), t.lesson ?: "", t.counterTrendReason ?: "").any { it.lowercase(java.util.Locale.ROOT).contains(q) })
        }
        val time = { t: TradeEntity -> t.entryTime ?: t.createdAt }
        return when (sort) {
            JournalSort.NEWEST -> out.sortedByDescending(time)
            JournalSort.OLDEST -> out.sortedBy(time)
            JournalSort.BEST_R -> out.sortedByDescending { it.r ?: Double.NEGATIVE_INFINITY }
            JournalSort.WORST_R -> out.sortedBy { it.r ?: Double.POSITIVE_INFINITY }
        }
    }

    val active: Boolean get() = status != null || result != null || account != null || compliance != null || hasViolation || query.isNotBlank()
}

/** Journal list (5.6): grouped by display-calendar day with the day net, searchable and filterable. */
@Composable
fun JournalScreen(onOpen: (String) -> Unit, onInsights: () -> Unit, vm: JournalViewModel = hiltViewModel()) {
    val u = ui()
    val trades by vm.trades.collectAsState()
    val setups by vm.setups.collectAsState()
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(JournalSort.NEWEST) }
    var violation by rememberSaveable { mutableStateOf(false) }
    val names = setups.associate { it.id to it.name }
    val filter = JournalFilter(status = status, hasViolation = violation, query = query, sort = sort)
    val rows = filter.apply(trades) { names[it] ?: "" }
    val dayOf = { t: TradeEntity -> u.localDate(Instant.ofEpochMilli(t.entryTime ?: t.createdAt)) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(u.t("journal.title"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onInsights) { Text(u.t("nav.insights")) }
        }
        OutlinedTextField(query, { query = it }, placeholder = { Text(u.t("journal.search.hint")) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = status == null, onClick = { status = null }, label = { Text(u.t("common.all")) })
            listOf("OPEN", "CLOSED", "PLANNED", "REJECTED").forEach { s -> FilterChip(selected = status == s, onClick = { status = s }, label = { Text(u.t("trade.status.$s")) }) }
            FilterChip(selected = violation, onClick = { violation = !violation }, label = { Text(u.t("journal.filter.violation")) })
            JournalSort.entries.forEach { s ->
                val key = when (s) { JournalSort.NEWEST -> "newest"; JournalSort.OLDEST -> "oldest"; JournalSort.BEST_R -> "bestR"; JournalSort.WORST_R -> "worstR" }
                FilterChip(selected = sort == s, onClick = { sort = s }, label = { Text(u.t("journal.sort.$key")) })
            }
            if (filter.active) TextButton(onClick = { status = null; query = ""; violation = false }) { Text(u.t("journal.filter.clear")) }
        }
        Text(u.t("journal.count", "n" to u.int(rows.size)), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp))
        if (rows.isEmpty()) EmptyState(u.t(if (trades.isEmpty()) "journal.empty" else "journal.emptyFilter"))
        LazyColumn(Modifier.fillMaxSize()) {
            val groups: Map<LocalDate, List<TradeEntity>> = rows.groupBy(dayOf)
            groups.forEach { (day, list) ->
                item(key = "h$day") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(u.date(day), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        val net = list.filter { it.account == "REAL" }.mapNotNull { it.net }
                        if (net.isNotEmpty()) Text(u.signedMoney(net.sum()), style = MaterialTheme.typography.titleSmall)
                    }
                }
                items(list, key = { it.id }) { t -> Row(t, names[t.setupId], onOpen); HorizontalDivider() }
            }
        }
    }
}

@Composable
private fun Row(t: TradeEntity, setup: String?, onOpen: (String) -> Unit) {
    val u = ui()
    val c = LocalAndarzColors.current
    Column(Modifier.fillMaxWidth().clickable { onOpen(t.id) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(u.symbol(t.symbol), style = MaterialTheme.typography.titleSmall)
            TagChip(u.enumLabel("side", t.side))
            if (t.status == "CLOSED") RChip(t.r)
            if (t.net != null) Text(u.signedMoney(t.net), style = MaterialTheme.typography.bodyMedium)
            if (t.ok == true) Icon(Icons.Filled.Check, u.t("result.compliant.yes"), tint = c.allowed)
            if (t.ok == false) Icon(Icons.Filled.Close, u.t("result.compliant.no"), tint = c.redline)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            setup?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = c.textMuted) }
            if (t.account == "DEMO") TagChip(u.t("common.demo"))
            if (t.imported) TagChip(u.t("trade.imported"))
            if (t.status == "OPEN") TagChip(u.t("journal.row.open"), c.info)
            if (t.status == "CLOSED" && t.reviewedAt == null) TagChip(u.t("journal.row.pending"), c.caution)
            if (t.status == "PLANNED" || t.status == "REJECTED" || t.status == "CANCELLED") TagChip(u.t("trade.status.${t.status}"))
        }
    }
}
