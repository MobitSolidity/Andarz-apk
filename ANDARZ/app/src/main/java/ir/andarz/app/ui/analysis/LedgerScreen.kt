package ir.andarz.app.ui.analysis

import ir.andarz.app.ui.safeLaunch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.components.NumberField
import ir.andarz.app.ui.components.SectionCard
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui
import ir.andarz.data.EngineFacade
import ir.andarz.data.LedgerError
import ir.andarz.data.LedgerRepository
import ir.andarz.data.ParamsRepository
import ir.andarz.data.db.AndarzDatabase
import ir.andarz.data.db.LedgerEntryEntity
import ir.andarz.data.settings.CalendarMode
import ir.andarz.data.settings.SettingsRepository
import ir.andarz.engine.Fmt
import ir.andarz.engine.Kpis
import ir.andarz.engine.Snapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class LedgerViewModel @Inject constructor(
    private val db: AndarzDatabase,
    private val ledger: LedgerRepository,
    private val facade: EngineFacade,
    private val params: ParamsRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val entries: StateFlow<List<LedgerEntryEntity>> = db.ledger().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val snapshot: StateFlow<Snapshot?> = facade.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _prevMonth = MutableStateFlow<Pair<LocalDate, Kpis>?>(null)
    val prevMonth: StateFlow<Pair<LocalDate, Kpis>?> = _prevMonth
    private val _session = MutableStateFlow(false)
    val sessionOpen: StateFlow<Boolean> = _session
    val message = MutableStateFlow<String?>(null)

    init { refresh() }

    fun refresh() = safeLaunch {
        val s = settings.current()
        val (from, to) = Periods.previousMonth(LocalDate.now(s.zone), s.calendar == CalendarMode.JALALI)
        val k = InsightsLogic.of(db.trades().engineRows(), from, to, params.current(), s.zone).kpis
        _prevMonth.value = from to k
        _session.value = params.openSession() != null
    }

    fun add(type: String, amount: Double, at: Instant, note: String) = safeLaunch {
        message.value = when (ledger.add(type, amount, at, note)) {
            LedgerError.NEGATIVE_EQUITY -> "ledger.negative"
            LedgerError.NOT_POSITIVE -> "err.positive"
            null -> "common.saved"
            else -> "err.generic"
        }
    }

    fun delete(e: LedgerEntryEntity) = safeLaunch { if (ledger.delete(e.id) == LedgerError.BASELINE_LOCKED) message.value = "ledger.baseline.locked" }

    fun editBaseline(amount: Double, reason: String) = safeLaunch {
        message.value = when (runCatching { ledger.editBaseline(amount, null, reason) }.getOrElse { LedgerError.BASELINE_LOCKED }) {
            null -> "common.saved"
            LedgerError.NOT_POSITIVE -> "err.positive"
            else -> "ledger.baseline.locked"
        }
    }
}

/** Ledger and withdrawals of 5.12. */
@Composable
fun LedgerScreen(onBack: () -> Unit, vm: LedgerViewModel = hiltViewModel()) {
    val u = ui()
    val entries by vm.entries.collectAsState()
    val snap by vm.snapshot.collectAsState()
    val prev by vm.prevMonth.collectAsState()
    val session by vm.sessionOpen.collectAsState()
    val message by vm.message.collectAsState()
    val c = LocalAndarzColors.current
    var type by rememberSaveable { mutableStateOf("DEPOSIT") }
    var amount by rememberSaveable { mutableStateOf("") }
    var daysAgo by rememberSaveable { mutableStateOf(0) }
    var note by rememberSaveable { mutableStateOf("") }
    var baselineEdit by rememberSaveable { mutableStateOf<String?>(null) }
    var askDelete by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(u.t("ledger.title"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text(u.t("common.back")) }
        }
        SectionCard(u.t("ledger.equity")) {
            Text(u.money(snap?.equity?.now), style = MaterialTheme.typography.headlineMedium)
            Text(u.t("ledger.note.curve"), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
        }
        val p = snap?.params
        SectionCard(null) {
            Text(u.t("ledger.withdraw.rule", "pct" to u.pct(p?.withdrawPct, 0)), style = MaterialTheme.typography.bodyMedium)
            prev?.let { (_, k) ->
                Text(u.t("ledger.withdraw.hint", "net" to u.signedMoney(k.net), "amount" to u.money(ReviewLogic.withdrawAmount(k))), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            }
        }
        message?.let { Text(u.t(it), color = if (it == "common.saved") c.allowed else c.caution) }

        SectionCard(u.t("ledger.add")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("DEPOSIT", "WITHDRAWAL", "ADJUSTMENT").forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(u.t("ledger.type.$t")) }) }
            }
            if (type == "ADJUSTMENT") Text(u.t("ledger.adjust.hint"), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
            val parsed = Fmt.normalizeInput(amount)
            NumberField(u.t("ledger.amount"), amount, { amount = it }, error = if (amount.isNotBlank() && parsed == null) u.t("err.number") else null, suffix = "$")
            DayPicker(u, daysAgo) { daysAgo = it }
            OutlinedTextField(note, { note = it }, label = { Text(u.t("ledger.note")) }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    val at = LocalDate.now(u.zone).minusDays(daysAgo.toLong()).let { d -> if (daysAgo == 0) Instant.now() else d.atTime(12, 0).atZone(u.zone).toInstant() }
                    vm.add(type, parsed!!, at, note.trim()); amount = ""; note = ""
                },
                enabled = parsed != null && (type == "ADJUSTMENT" || parsed > 0),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) { Text(u.t("ledger.add")) }
        }

        SectionCard(null) {
            if (entries.isEmpty()) Text(u.t("ledger.empty"), color = c.textMuted)
            entries.forEachIndexed { i, e ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(u.t("ledger.type.${e.type}"), style = MaterialTheme.typography.titleSmall)
                        Text(u.date(u.localDate(Instant.ofEpochMilli(e.time)), false), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                        if (e.note.isNotBlank()) Text(e.note, style = MaterialTheme.typography.labelMedium)
                    }
                    Text(entryAmount(u, e), style = MaterialTheme.typography.titleMedium)
                }
                if (e.type == "BASELINE") {
                    if (session) TextButton(onClick = { baselineEdit = CalcLogic.field(e.amount) }) { Text(u.t("common.edit")) }
                    else Text(u.t("ledger.baseline.locked"), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                } else TextButton(onClick = { askDelete = e.id }) { Text(u.t("common.delete"), color = c.redline) }
            }
        }
    }

    // UI review (D-81): a ledger row moves equity and every limit, so deleting asks first, like a trade (5.5).
    askDelete?.let { id ->
        val e = entries.firstOrNull { it.id == id }
        if (e == null) { askDelete = null; return@let }
        AlertDialog(
            onDismissRequest = { askDelete = null },
            title = { Text(u.t("common.delete")) },
            text = { Text(u.t("ledger.type.${e.type}") + " \u00B7 " + entryAmount(u, e) + "\n" + u.t("common.confirm")) },
            confirmButton = { TextButton(onClick = { vm.delete(e); askDelete = null }) { Text(u.t("common.delete"), color = c.redline) } },
            dismissButton = { TextButton(onClick = { askDelete = null }) { Text(u.t("common.cancel")) } },
        )
    }

    baselineEdit?.let { text ->
        var t by rememberSaveable { mutableStateOf(text) }
        var reason by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { baselineEdit = null },
            title = { Text(u.t("ledger.type.BASELINE")) },
            text = {
                Column {
                    NumberField(u.t("ledger.amount"), t, { t = it }, suffix = "$")
                    OutlinedTextField(reason, { reason = it }, label = { Text(u.t("params.reason")) }, placeholder = { Text(u.t("params.reason.hint")) })
                }
            },
            confirmButton = {
                TextButton(onClick = { Fmt.normalizeInput(t)?.let { vm.editBaseline(it, reason) }; baselineEdit = null }, enabled = Fmt.normalizeInput(t)?.let { it > 0 } == true && reason.isNotBlank()) { Text(u.t("common.save")) }
            },
            dismissButton = { TextButton(onClick = { baselineEdit = null }) { Text(u.t("common.cancel")) } },
        )
    }
}

private fun entryAmount(u: Ui, e: LedgerEntryEntity): String = when (e.type) {
    "WITHDRAWAL" -> u.signedMoney(-e.amount)
    "BASELINE" -> u.money(e.amount)
    else -> u.signedMoney(e.amount)
}

/** Today / yesterday / earlier by day steps; the stepper avoids a free-form date field (M6 adds pickers). */
@Composable
internal fun DayPicker(u: Ui, daysAgo: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(u.t("ledger.date"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange(daysAgo + 1) }) { Text(u.digits("\u2212" + "1")) }
        Text(
            when (daysAgo) { 0 -> u.t("common.today"); 1 -> u.t("common.yesterday"); else -> u.date(LocalDate.now(u.zone).minusDays(daysAgo.toLong()), false) },
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = { onChange((daysAgo - 1).coerceAtLeast(0)) }, enabled = daysAgo > 0) { Text(u.digits("+1")) }
    }
}
