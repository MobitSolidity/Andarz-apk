package ir.andarz.app.ui.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import ir.andarz.app.ui.components.EmptyState
import ir.andarz.app.ui.components.NumberField
import ir.andarz.app.ui.components.SectionCard
import ir.andarz.app.ui.components.SwitchRow
import ir.andarz.app.ui.components.TagChip
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui
import ir.andarz.data.EngineFacade
import ir.andarz.data.ParamsRepository
import ir.andarz.data.SetupRepository
import ir.andarz.data.WatchError
import ir.andarz.data.WatchRepository
import ir.andarz.data.db.AndarzDatabase
import ir.andarz.data.db.SetupEntity
import ir.andarz.data.db.WatchSymbolEntity
import ir.andarz.data.settings.Settings
import ir.andarz.data.settings.SettingsRepository
import ir.andarz.engine.Fmt
import ir.andarz.engine.GradeA
import ir.andarz.engine.Governor
import ir.andarz.engine.Params
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/** Per-setup numbers the list shows (5.11): closed REAL trades, expectancy, grade-A eligibility. */
data class SetupStat(val closedReal: Int, val enteredReal: Int, val expectancy: Double?, val gradeA: GradeA)

@HiltViewModel
class SetupsViewModel @Inject constructor(
    db: AndarzDatabase,
    private val repo: SetupRepository,
    private val watchRepo: WatchRepository,
    private val facade: EngineFacade,
    private val params: ParamsRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {
    val setups: StateFlow<List<SetupEntity>> = db.setups().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val watch: StateFlow<List<WatchSymbolEntity>> = db.watch().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings: StateFlow<Settings?> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _stats = MutableStateFlow<Map<String, SetupStat>>(emptyMap())
    val stats: StateFlow<Map<String, SetupStat>> = _stats
    private val _p = MutableStateFlow(Params.DEFAULTS)
    val p: StateFlow<Params> = _p
    private val _session = MutableStateFlow(false)
    val sessionOpen: StateFlow<Boolean> = _session
    val message = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch { db.trades().observeEngineRows().collect { refresh() } }
    }

    fun refresh() = viewModelScope.launch {
        val p = params.current()
        _p.value = p
        _session.value = params.openSession() != null
        val trades = facade.resolvedTrades()
        _stats.value = trades.filter { it.isReal }.groupBy { it.raw.setupId ?: "" }.mapValues { (_, list) ->
            val closed = list.filter { it.exitTime != null }
            val rs = closed.map { it.r }
            SetupStat(closed.size, list.size, if (closed.isEmpty()) null else rs.filterNotNull().sum() / closed.size, Governor.gradeAEligible(rs, p))
        }
    }

    fun save(existing: SetupEntity?, name: String, entry: String, inval: String, target: String, notes: String, waived: Boolean) = viewModelScope.launch {
        if (existing == null) {
            val s = repo.create(name, entry, inval, target, notes)
            if (waived) repo.update(s.copy(testWaived = true))
        } else repo.update(existing.copy(name = name.trim(), entryRule = entry, invalidation = inval, target = target, notes = notes, testWaived = waived))
    }

    fun grade(s: SetupEntity, grade: String) = viewModelScope.launch {
        val ok = repo.setGrade(s.id, grade, _stats.value[s.id]?.gradeA?.eligible == true)
        if (!ok) message.value = "setups.notEligibleA"
    }

    fun archive(s: SetupEntity, archived: Boolean) = viewModelScope.launch { repo.setArchived(s.id, archived) }

    // ---- watch-list
    fun addWatch(symbol: String, vol: String, rel: String, levels: String, notes: String) = viewModelScope.launch {
        message.value = when (watchRepo.add(symbol, Fmt.normalizeInput(vol), Fmt.normalizeInput(rel), levels, notes)) {
            WatchError.FULL -> "wl.full"
            WatchError.EMPTY_SYMBOL -> "err.symbolRequired"
            // Already listed: nothing to add and nothing to say beyond the list itself.
            WatchError.DUPLICATE -> null
            null -> null
        }
    }

    fun updateWatch(w: WatchSymbolEntity, vol: String, rel: String, levels: String, notes: String) = viewModelScope.launch {
        val v = Fmt.normalizeInput(vol)
        val r = Fmt.normalizeInput(rel)
        watchRepo.update(w.copy(volume24hUsd = v, relVolume = r, levels = levels.trim(), notes = notes.trim()), liquidityChanged = v != w.volume24hUsd || r != w.relVolume || v != null || r != null)
    }

    fun deleteWatch(w: WatchSymbolEntity) = viewModelScope.launch { watchRepo.delete(w) }

    /** D-22 thresholds: editable only inside a review session, with no delay (5.11). */
    fun setThresholds(vol: Double, lo: Double, hi: Double) = viewModelScope.launch {
        if (params.openSession() == null) { message.value = "params.needsSession"; return@launch }
        if (vol <= 0 || lo <= 0 || hi <= lo) { message.value = "err.positive"; return@launch }
        settingsRepo.setLiquidity(vol, lo, hi)
        message.value = "common.saved"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupsScreen(onBack: () -> Unit, vm: SetupsViewModel = hiltViewModel()) {
    val u = ui()
    val setups by vm.setups.collectAsState()
    val stats by vm.stats.collectAsState()
    val p by vm.p.collectAsState()
    val message by vm.message.collectAsState()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    val c = LocalAndarzColors.current
    val active = setups.filter { !it.archived }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(u.t("setups.title"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text(u.t("common.back")) }
        }
        Text(u.t("setups.none"), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        if (active.size > 2) Text(u.t("setups.limit"), color = c.caution, style = MaterialTheme.typography.bodyMedium)
        message?.let { Text(u.t(it, "n" to "", "min" to u.int(p.minTrades.toInt())), color = c.caution) }
        Button(onClick = { editing = "" }, modifier = Modifier.fillMaxWidth()) { Text(u.t("setups.new")) }
        if (setups.isEmpty()) EmptyState(u.t("setups.none"))
        for (s in setups.filter { showArchived || !it.archived }) {
            SetupCard(u, s, stats[s.id], p, onEdit = { editing = s.id }, onGrade = { vm.grade(s, it) }, onArchive = { vm.archive(s, !s.archived) })
        }
        if (setups.any { it.archived }) TextButton(onClick = { showArchived = !showArchived }) { Text(u.t("setups.archived") + " (" + u.int(setups.count { it.archived }) + ")") }
    }

    editing?.let { id ->
        val s = setups.firstOrNull { it.id == id }
        ModalBottomSheet(onDismissRequest = { editing = null }) { SetupForm(u, s) { name, e, i, t, n, w -> vm.save(s, name, e, i, t, n, w); editing = null } }
    }
}

@Composable
private fun SetupCard(u: Ui, s: SetupEntity, st: SetupStat?, p: Params, onEdit: () -> Unit, onGrade: (String) -> Unit, onArchive: () -> Unit) {
    val c = LocalAndarzColors.current
    SectionCard(s.name, action = { TagChip(u.t("setups.grade.${s.grade}"), if (s.grade == "A") c.caution else c.info) }) {
        val n = st?.closedReal ?: 0
        Text(u.t("ins.setups.n", "n" to u.int(n)) + " \u00B7 " + u.t("ins.kpi.expectancy") + " " + u.rChip(st?.expectancy), style = MaterialTheme.typography.bodyMedium)
        if (s.archived) TagChip(u.t("setups.archived"))
        val entered = st?.enteredReal ?: 0
        if (!s.testWaived && entered < p.setupTestTrades) {
            Text(u.t("setups.test", "risk" to u.pct(p.setupTestRisk, 2), "n" to u.int((p.setupTestTrades - entered).toInt())), color = c.caution, style = MaterialTheme.typography.bodyMedium)
        }
        val ga = st?.gradeA
        if (s.grade != "A") {
            Text(
                if (ga?.eligible == true) u.t("setups.eligibleA", "min" to u.int(p.minTrades.toInt()))
                else u.t("setups.notEligibleA", "n" to u.int(n), "min" to u.int(p.minTrades.toInt())),
                style = MaterialTheme.typography.labelMedium, color = c.textMuted,
            )
        }
        if (s.entryRule.isNotBlank()) Text(u.t("setups.entryRule") + ": " + s.entryRule, style = MaterialTheme.typography.bodyMedium)
        if (s.invalidation.isNotBlank()) Text(u.t("setups.invalidation") + ": " + s.invalidation, style = MaterialTheme.typography.bodyMedium)
        if (s.target.isNotBlank()) Text(u.t("setups.target") + ": " + s.target, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onEdit) { Text(u.t("common.edit")) }
            if (s.grade == "A") TextButton(onClick = { onGrade("B") }) { Text(u.t("setups.demote")) }
            else TextButton(onClick = { onGrade("A") }, enabled = ga?.eligible == true) { Text(u.t("setups.promote")) }
            TextButton(onClick = onArchive) { Text(u.t(if (s.archived) "common.undo" else "setups.archive")) }
        }
    }
}

@Composable
private fun SetupForm(u: Ui, s: SetupEntity?, onSave: (String, String, String, String, String, Boolean) -> Unit) {
    var name by rememberSaveable { mutableStateOf(s?.name ?: "") }
    var entry by rememberSaveable { mutableStateOf(s?.entryRule ?: "") }
    var inval by rememberSaveable { mutableStateOf(s?.invalidation ?: "") }
    var target by rememberSaveable { mutableStateOf(s?.target ?: "") }
    var notes by rememberSaveable { mutableStateOf(s?.notes ?: "") }
    var waived by rememberSaveable { mutableStateOf(s?.testWaived ?: false) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(u.t(if (s == null) "setups.new" else "common.edit"), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(name, { name = it }, label = { Text(u.t("setups.name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(entry, { entry = it }, label = { Text(u.t("setups.entryRule")) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        OutlinedTextField(inval, { inval = it }, label = { Text(u.t("setups.invalidation")) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        OutlinedTextField(target, { target = it }, label = { Text(u.t("setups.target")) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(notes, { notes = it }, label = { Text(u.t("setups.notes")) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        // P1 D-21: the test phase may be run on DEMO trades instead.
        SwitchRow(u.t("setups.demo.test"), waived, { waived = it })
        Button(onClick = { onSave(name, entry, inval, target, notes, waived) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(u.t("common.save")) }
    }
}

// ==================================================================== watch-list

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(onBack: () -> Unit, vm: SetupsViewModel = hiltViewModel()) {
    val u = ui()
    val list by vm.watch.collectAsState()
    val p by vm.p.collectAsState()
    val s by vm.settings.collectAsState()
    val session by vm.sessionOpen.collectAsState()
    val message by vm.message.collectAsState()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val c = LocalAndarzColors.current
    val st = s ?: Settings()
    val max = p.watchlistMax.toInt()
    val now = Instant.now()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(u.t("wl.title"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text(u.t("common.back")) }
        }
        Text(u.t("wl.limit", "max" to u.int(max)) + " \u00B7 " + u.int(list.size) + "/" + u.int(max), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        message?.let { Text(u.t(it), color = c.caution) }
        Button(onClick = { editing = "" }, enabled = list.size < max, modifier = Modifier.fillMaxWidth()) { Text(u.t("wl.add")) }
        if (list.size >= max) Text(u.t("wl.full"), color = c.caution)
        if (list.isEmpty()) EmptyState(u.t("wl.empty"))
        list.forEachIndexed { i, w ->
            if (i > 0) HorizontalDivider()
            val liq = WatchLogic.liquidity(w.volume24hUsd, w.relVolume, st.liqVolMin, st.relVolLo, st.relVolHi)
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(u.symbol(w.symbol), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    liq.pass?.let { ok -> TagChip(u.t(if (ok) "wl.filter.pass" else "wl.filter.fail").substringBefore('\u061B'), if (ok) c.allowed else c.caution) }
                }
                w.volume24hUsd?.let { Text(u.t("mkt.sheet.vol24") + ": " + u.money(it, 0), style = MaterialTheme.typography.bodyMedium) }
                w.relVolume?.let { Text(u.mult(it), style = MaterialTheme.typography.bodyMedium) }
                if (liq.pass == false) Text(u.t("wl.filter.fail"), style = MaterialTheme.typography.labelMedium, color = c.caution)
                if (w.levels.isNotBlank()) Text(u.t("wl.levels") + ": " + w.levels, style = MaterialTheme.typography.bodyMedium)
                if (w.notes.isNotBlank()) Text(u.t("wl.notes") + ": " + w.notes, style = MaterialTheme.typography.bodyMedium)
                w.checkedAt?.let { Text(u.t("wl.checkedAt", "date" to u.date(u.localDate(Instant.ofEpochMilli(it)), false)), style = MaterialTheme.typography.labelMedium, color = c.textMuted) }
                if (WatchLogic.needsRecheck(w.checkedAt, now)) Text(u.t("wl.recheck"), style = MaterialTheme.typography.labelMedium, color = c.caution)
                Row {
                    TextButton(onClick = { editing = w.id }) { Text(u.t("common.edit")) }
                    TextButton(onClick = { vm.deleteWatch(w) }) { Text(u.t("common.remove")) }
                }
            }
        }

        SectionCard(u.t("rev.monthly.thresholds")) {
            Text(u.t("wl.volume", "usd" to u.num(st.liqVolMin, 0)), style = MaterialTheme.typography.bodyMedium)
            Text(u.t("wl.relVolume", "lo" to u.num(st.relVolLo, 2, trim = true), "hi" to u.num(st.relVolHi, 2, trim = true)), style = MaterialTheme.typography.bodyMedium)
            if (!session) Text(u.t("params.needsSession"), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
            else {
                var vol by rememberSaveable { mutableStateOf(CalcLogic.field(st.liqVolMin)) }
                var lo by rememberSaveable { mutableStateOf(CalcLogic.field(st.relVolLo)) }
                var hi by rememberSaveable { mutableStateOf(CalcLogic.field(st.relVolHi)) }
                NumberField(u.t("mkt.sheet.vol24"), vol, { vol = it }, suffix = "$")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(u.t("common.from"), lo, { lo = it }, modifier = Modifier.weight(1f), suffix = "\u00D7")
                    NumberField(u.t("common.to"), hi, { hi = it }, modifier = Modifier.weight(1f), suffix = "\u00D7")
                }
                OutlinedButton(onClick = {
                    val v = Fmt.normalizeInput(vol); val l = Fmt.normalizeInput(lo); val h = Fmt.normalizeInput(hi)
                    if (v != null && l != null && h != null) vm.setThresholds(v, l, h)
                }) { Text(u.t("common.save")) }
            }
        }
    }

    editing?.let { id ->
        val w = list.firstOrNull { it.id == id }
        ModalBottomSheet(onDismissRequest = { editing = null }) {
            WatchForm(u, w, u.t("wl.relVolume", "lo" to u.num(st.relVolLo, 2, trim = true), "hi" to u.num(st.relVolHi, 2, trim = true))) { sym, vol, rel, lv, n ->
                if (w == null) vm.addWatch(sym, vol, rel, lv, n) else vm.updateWatch(w, vol, rel, lv, n)
                editing = null
            }
        }
    }
}

@Composable
private fun WatchForm(u: Ui, w: WatchSymbolEntity?, relLabel: String, onSave: (String, String, String, String, String) -> Unit) {
    var sym by rememberSaveable { mutableStateOf(w?.symbol ?: "") }
    var vol by rememberSaveable { mutableStateOf(w?.volume24hUsd?.let(CalcLogic::field) ?: "") }
    var rel by rememberSaveable { mutableStateOf(w?.relVolume?.let(CalcLogic::field) ?: "") }
    var levels by rememberSaveable { mutableStateOf(w?.levels ?: "") }
    var notes by rememberSaveable { mutableStateOf(w?.notes ?: "") }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(sym, { sym = it }, label = { Text(u.t("wl.symbol")) }, singleLine = true, enabled = w == null, modifier = Modifier.fillMaxWidth())
        NumberField(u.t("mkt.sheet.vol24"), vol, { vol = it }, suffix = "$")
        // No document names this field alone, so the D-22 sentence with the live band labels it (D-58).
        NumberField(relLabel, rel, { rel = it }, suffix = "\u00D7")
        OutlinedTextField(levels, { levels = it }, label = { Text(u.t("wl.levels")) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(notes, { notes = it }, label = { Text(u.t("wl.notes")) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Text(u.t("wl.recheck"), style = MaterialTheme.typography.labelMedium, color = LocalAndarzColors.current.textMuted)
        Button(onClick = { onSave(sym, vol, rel, levels, notes) }, enabled = sym.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(u.t("common.save")) }
    }
}
