package ir.andarz.app.ui.wizard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.components.KeyValue
import ir.andarz.app.ui.components.LockBanner
import ir.andarz.app.ui.components.NumberField
import ir.andarz.app.ui.components.SectionCard
import ir.andarz.app.ui.components.SwitchRow
import ir.andarz.app.ui.components.TagChip
import ir.andarz.app.ui.components.Verdict
import ir.andarz.app.ui.components.VerdictBanner
import ir.andarz.app.ui.journal.LockTexts
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui
import ir.andarz.engine.Fmt
import ir.andarz.engine.GateReason
import ir.andarz.engine.Params
import ir.andarz.engine.Side
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.floor

private const val STEPS = 5

/** The five-step wizard of 5.3. Every number comes from [WizardViewModel.calc], i.e. from the engine. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardScreen(pledgeSigned: Boolean, onDone: (String) -> Unit, onCancel: () -> Unit, vm: WizardViewModel = hiltViewModel()) {
    val u = ui()
    val d by vm.draft.collectAsState()
    val snap by vm.snapshot.collectAsState()
    val setups by vm.setupList.collectAsState()
    val watch by vm.watchList.collectAsState()
    val pending by vm.pendingEntry.collectAsState()
    val done by vm.done.collectAsState()
    LaunchedEffect(done) { done?.let { vm.done.value = null; onDone(it) } }
    val c = vm.calc(d, snap)
    val p = snap?.params ?: Params.DEFAULTS
    val locked = snap?.governor?.locks?.isNotEmpty() == true

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(u.t("wiz.title"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                FilterChip(selected = d.demo, onClick = { vm.update { it.copy(demo = !it.demo) } }, label = { Text(u.t("wiz.demo.toggle")) })
            }
            Text(u.t("wiz.step.n", "n" to u.int(d.step), "total" to u.int(STEPS)) + " \u00B7 " + u.t(stepKey(d.step)), style = MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(progress = { d.step / STEPS.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            if (d.step >= 2) VerdictStrip(u, c, locked, p)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (d.step) {
                1 -> StepGate(u, snap, pledgeSigned)
                2 -> StepSetup(u, vm, d, c, setups.filter { !it.archived }, watch.map { it.symbol }, snap?.governor?.reductions.orEmpty().map { LockTexts.reduction(u, it) })
                3 -> StepChecklist(u, vm, d, c)
                4 -> StepNumbers(u, vm, d, c, p, snap?.equity?.now)
                5 -> StepReview(u, vm, d, c, p, locked, onCancel)
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { if (d.step == 1) onCancel() else vm.update { it.copy(step = it.step - 1) } }, modifier = Modifier.weight(1f)) {
                Text(u.t(if (d.step == 1) "common.cancel" else "common.back"))
            }
            if (d.step < STEPS) {
                val blocked = (d.step == 1 && (locked || !pledgeSigned)) ||
                    (d.step == 2 && (d.symbol.isBlank() || d.setupId == null || (c.counterTrend && d.counterTrendReason.isBlank())))
                Button(onClick = { vm.update { it.copy(step = it.step + 1) } }, enabled = !blocked, modifier = Modifier.weight(1f)) { Text(u.t("common.next")) }
            }
        }
    }

    pending?.let { t ->
        ModalBottomSheet(onDismissRequest = { vm.pendingEntry.value = null }) { ConfirmSheet(u, vm, t, d) }
    }
}

private fun stepKey(n: Int) = when (n) {
    1 -> "wiz.step.gate"; 2 -> "wiz.step.setup"; 3 -> "wiz.step.checklist"; 4 -> "wiz.step.numbers"; else -> "wiz.step.review"
}

@Composable
private fun VerdictStrip(u: Ui, c: WizardResult, locked: Boolean, p: Params) {
    val state = when {
        locked -> Verdict.LOCKED
        !c.numbersValid -> Verdict.CAUTION
        c.allowed -> Verdict.ALLOWED
        else -> Verdict.DENIED
    }
    val title = when (state) {
        Verdict.ALLOWED -> u.t("wiz.gate.allowed")
        Verdict.DENIED -> u.t("wiz.gate.deniedWith", "reasons" to c.reasons.joinToString("\u060C ") { u.t("wiz.gate.reason.${it.name}") })
        Verdict.LOCKED -> u.t("lock.title")
        Verdict.CAUTION -> u.t("wiz.step.numbers")
    }
    val numbers = c.plan?.let { pl -> u.t("wiz.calc.riskUsd") + " " + u.money(pl.riskUsd) + " \u00B7 " + u.t("wiz.calc.marginPct") + " " + u.pct(pl.marginPct) + " \u00B7 " + u.t("wiz.calc.liqX") + " " + u.mult(pl.liqX) }
    VerdictBanner(state, title, numbers, why = if (state == Verdict.DENIED) c.reasons.map { reasonDetail(u, it, c, p) } else emptyList(), modifier = Modifier.padding(top = 8.dp))
}

/** Reason label, detail with numbers and article, and the fix (5.3 reasons list). */
private fun reasonDetail(u: Ui, r: GateReason, c: WizardResult, p: Params): String {
    val detail = when (r) {
        GateReason.DIRECTION -> {
            val s = if (c.entry != null && c.sl != null && c.sl < c.entry) Side.LONG else Side.SHORT
            u.t("wiz.gate.detail.DIRECTION", "side" to u.enumLabel("side", s.name), "slWhere" to u.t("wiz.gate.slWhere.${s.name}"), "tpWhere" to u.t("wiz.gate.tpWhere.${s.name}"))
        }
        GateReason.CHECKLIST -> u.t("wiz.gate.detail.CHECKLIST", "n" to u.int(c.chk), "total" to u.int(p.chkN.toInt()))
        GateReason.LEVERAGE -> u.t("wiz.gate.detail.LEVERAGE", "lev" to u.mult(c.lev), "max" to u.mult(p.levMax))
        GateReason.RR -> u.t("wiz.gate.detail.RR", "rr" to u.num(c.plan?.rr), "min" to u.num(p.rrMin, 2, trim = true))
        GateReason.MARGIN -> u.t("wiz.gate.detail.MARGIN", "pct" to u.pct(c.plan?.marginPct), "max" to u.pct(p.marginMax, 0))
        GateReason.LIQUIDATION -> u.t("wiz.gate.detail.LIQUIDATION", "x" to u.mult(c.plan?.liqX), "min" to u.mult(p.liqMult))
    }
    return "\u2022 " + u.t("wiz.gate.reason.${r.name}") + ": " + detail + " " + u.t("wiz.gate.fix.${r.name}")
}

@Composable
private fun StepGate(u: Ui, snap: ir.andarz.engine.Snapshot?, pledgeSigned: Boolean) {
    if (snap == null) return
    if (!pledgeSigned) Text(u.t("err.noPledge"), color = LocalAndarzColors.current.redline)
    val g = snap.governor
    if (g.locks.isNotEmpty()) {
        Text(u.t("wiz.gate.blocked"), style = MaterialTheme.typography.titleMedium)
        g.locks.forEach { l -> LockBanner(LockTexts.title(u, l), LockTexts.body(u, l, snap.params, g.tradesToday), LockTexts.until(u, l, Instant.now()), emptyList()) }
    } else {
        val red = snap.dashboard.status.let { s -> listOf("trades" to s.trades, "daily" to s.daily, "weekly" to s.weekly, "drawdown" to s.drawdown, "positions" to s.positions, "openRisk" to s.openRisk) }
            .filter { it.second != ir.andarz.engine.DashStatus.OK }
        if (red.isNotEmpty()) red.forEach { Text(u.t("today.status.${it.second.name}", "days" to u.int(snap.params.ddLockDays.toInt())), color = LocalAndarzColors.current.redline) }
        else VerdictBanner(Verdict.ALLOWED, u.t("wiz.gate.fine"), null)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(title: String, ids: List<String>, selected: String?, label: (String) -> String, onPick: (String) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ids.forEach { id -> FilterChip(selected = id == selected, onClick = { onPick(id) }, label = { Text(label(id)) }) }
    }
}

@Composable
private fun StepSetup(u: Ui, vm: WizardViewModel, d: WizardDraft, c: WizardResult, setups: List<ir.andarz.data.db.SetupEntity>, watch: List<String>, reductions: List<String>) {
    var newSetup by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(d.symbol, { v -> vm.update { it.copy(symbol = v) } }, label = { Text(u.t("wiz.symbol")) }, placeholder = { Text(u.t("wiz.symbol.hint")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    if (d.symbol.isNotBlank() && watch.none { it.equals(d.symbol.trim(), ignoreCase = true) }) Text(u.t("wiz.symbol.notInList"), color = LocalAndarzColors.current.caution, style = MaterialTheme.typography.labelMedium)
    ChipGroup(u.t("wiz.side"), u.enumIds("side"), d.side.name, { u.enumLabel("side", it) }) { id -> vm.update { it.copy(side = Side.valueOf(id)) } }
    ChipGroup(u.t("wiz.kind"), u.enumIds("kind"), d.kind, { u.enumLabel("kind", it) }) { id -> vm.update { it.copy(kind = id) } }
    Text(u.t("wiz.kind.hint"), style = MaterialTheme.typography.labelMedium, color = LocalAndarzColors.current.textMuted)
    ChipGroup(u.t("wiz.setup"), setups.map { it.id }, d.setupId, { id -> setups.first { it.id == id }.name }) { id -> vm.update { it.copy(setupId = id) } }
    if (setups.isEmpty()) Text(u.t("wiz.setup.none"), style = MaterialTheme.typography.labelMedium)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(newSetup, { newSetup = it }, label = { Text(u.t("wiz.setup.new")) }, singleLine = true, modifier = Modifier.weight(1f))
        TextButton(onClick = { if (newSetup.isNotBlank()) { vm.createSetup(newSetup); newSetup = "" } }, enabled = newSetup.isNotBlank()) { Text(u.t("common.add")) }
    }
    if (d.setupId == null) Text(u.t("err.setupRequired"), color = LocalAndarzColors.current.caution, style = MaterialTheme.typography.labelMedium)
    reductions.forEach { TagChip(it, LocalAndarzColors.current.caution) }
    ChipGroup(u.t("wiz.timeframe"), u.enumIds("timeframe"), d.timeframe, { u.enumLabel("timeframe", it) }) { id -> vm.update { it.copy(timeframe = id) } }
    ChipGroup(u.t("wiz.htf"), u.enumIds("htfTrend"), d.htfTrend, { u.enumLabel("htfTrend", it) }) { id -> vm.update { it.copy(htfTrend = id) } }
    if (c.counterTrend) {
        Text(u.t("wiz.counterTrend"), color = LocalAndarzColors.current.caution)
        OutlinedTextField(d.counterTrendReason, { v -> vm.update { it.copy(counterTrendReason = v) } }, label = { Text(u.t("wiz.counterTrend.reason")) }, modifier = Modifier.fillMaxWidth())
    }
    ChipGroup(u.t("wiz.emotion"), u.enumIds("emotion"), d.emotion, { u.enumLabel("emotion", it) }) { id -> vm.update { it.copy(emotion = id) } }
    if (!c.calm) Text(u.t("wiz.emotion.warn", "emotion" to u.enumLabel("emotion", d.emotion)), color = LocalAndarzColors.current.caution)
}

@Composable
private fun StepChecklist(u: Ui, vm: WizardViewModel, d: WizardDraft, c: WizardResult) {
    val items = u.content.arr("checklist")?.map { it.jsonObject }.orEmpty()
    Text(u.t("wiz.checklist.title"), style = MaterialTheme.typography.titleMedium)
    items.forEach { o ->
        val n = o["n"]!!.jsonPrimitive.content.toInt()
        val derived = n == 6 || n == 7
        val disabled = derived || (n == 8 && !c.calm)
        val checked = when (n) {
            6 -> c.item6
            7 -> c.item7
            else -> (d.manualMask shr (n - 1)) and 1 == 1 && !(n == 8 && !c.calm)
        }
        val state = u.t(if (checked) "a11y.toggleOn" else "a11y.toggleOff")
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = !disabled, role = Role.Checkbox) { vm.toggleItem(n) }
                .semantics(mergeDescendants = true) { stateDescription = state },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = null, enabled = !disabled)
            Column(Modifier.weight(1f)) {
                Text(o["short"]!!.jsonPrimitive.content, style = MaterialTheme.typography.bodyLarge)
                Text(o["full"]!!.jsonPrimitive.content, style = MaterialTheme.typography.labelMedium, color = LocalAndarzColors.current.textMuted)
                if (n == 8 && !c.calm) Text(u.t("wiz.emotion.warn", "emotion" to u.enumLabel("emotion", d.emotion)), style = MaterialTheme.typography.labelMedium, color = LocalAndarzColors.current.caution)
            }
            if (derived) TagChip(u.t("common.details"))
        }
    }
    Text(u.t("wiz.checklist.score", "n" to u.int(c.chk), "total" to u.int(items.size)), style = MaterialTheme.typography.titleMedium)
    Text(u.t(if (c.chk < items.size) "wiz.checklist.incomplete" else "wiz.checklist.rule"), style = MaterialTheme.typography.bodyMedium)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepNumbers(u: Ui, vm: WizardViewModel, d: WizardDraft, c: WizardResult, p: Params, ledgerEquity: Double?) {
    val err = { text: String, v: Double? -> if (text.isBlank()) null else if (Fmt.normalizeInput(text) == null) u.t("err.number") else if (v == null) u.t("err.positive") else null }
    NumberField(u.t("wiz.equity"), d.equity, { v -> vm.update { it.copy(equity = v) } }, error = err(d.equity, c.equity), hint = ledgerEquity?.let { u.t("wiz.equity.fromLedger", "v" to u.money(it)) })
    if (ledgerEquity != null && c.equity != null && kotlin.math.abs(c.equity - ledgerEquity) > 0.01 * ledgerEquity) {
        Text(u.t("wiz.equity.mismatch", "v" to u.money(ledgerEquity)), color = LocalAndarzColors.current.caution, style = MaterialTheme.typography.labelMedium)
        TextButton(onClick = { vm.update { it.copy(equity = Fmt.number(ledgerEquity, 2, false, false, true)) } }) { Text(u.t("common.ok")) }
    }
    NumberField(u.t("wiz.entry"), d.entry, { v -> vm.update { it.copy(entry = v) } }, error = err(d.entry, c.entry))
    NumberField(u.t("wiz.sl"), d.sl, { v -> vm.update { it.copy(sl = v) } }, error = err(d.sl, c.sl) ?: when {
        c.entryEqSl -> u.t("err.entryEqSl")
        c.slWrong -> u.t("err.slDirection", "dir" to u.t("wiz.gate.slWhere.${d.side.name}"))
        else -> null
    })
    NumberField(u.t("wiz.tp"), d.tp, { v -> vm.update { it.copy(tp = v) } }, error = err(d.tp, c.tp) ?: if (c.tpWrong) u.t("err.tpDirection", "dir" to u.t("wiz.gate.tpWhere.${d.side.name}")) else null)
    NumberField(u.t("wiz.leverage"), d.lev, { v -> vm.update { it.copy(lev = v) } }, error = err(d.lev, c.lev), suffix = "\u00D7")
    NumberField(
        u.t("wiz.riskPct"), d.riskPct, { v -> vm.update { it.copy(riskPct = v) } }, suffix = "%",
        hint = u.t("today.cap", "v" to u.pct(c.maxRisk, 2)),
    )

    c.advisor?.let { a ->
        SectionCard(u.t("wiz.adv.title")) {
            if (a.recommended != null) {
                Text(u.t("wiz.adv.range", "min" to u.int(a.feasibleMin!!), "max" to u.int(a.feasibleMax!!)))
                val margin = c.equity?.let { eq -> ir.andarz.engine.Planner.plan(d.side, eq, c.entry!!, c.sl!!, c.tp!!, a.recommended!!.toDouble(), p, c.riskPct).marginPct }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(u.t("wiz.adv.recommended", "lev" to u.int(a.recommended!!), "margin" to u.pct(margin)), modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.update { it.copy(lev = a.recommended.toString()) } }) { Text(u.t("wiz.adv.use")) }
                }
            } else Text(u.t("wiz.adv.none"), color = LocalAndarzColors.current.redline)
            if (a.lMinReal > p.levMax) Text(u.t("wiz.adv.tight", "lev" to u.int(ceil(a.lMinReal).toInt())))
            if (a.lMaxLiqReal < p.levMax) Text(u.t("wiz.adv.far", "lev" to u.int(floor(a.lMaxLiqReal).toInt())), style = MaterialTheme.typography.labelMedium)
        }
    }

    c.plan?.let { pl ->
        SectionCard(u.t("wiz.calc.title")) {
            KeyValue(u.t("wiz.calc.sld"), u.pct(pl.sld))
            KeyValue(u.t("wiz.calc.rr"), u.num(pl.rr))
            KeyValue(u.t("wiz.calc.riskUsd"), u.money(pl.riskUsd))
            KeyValue(u.t("wiz.calc.notionalMax"), u.money(pl.notionalMax))
            KeyValue(u.t("wiz.calc.margin"), u.money(pl.margin) + " (" + u.pct(pl.marginPct) + ")")
            KeyValue(u.t("wiz.calc.liq"), u.price(pl.liq) + " (" + u.pct(pl.liqDist) + ")")
            KeyValue(u.t("wiz.calc.liqX"), u.mult(pl.liqX))
            Text(u.t("wiz.calc.note"), style = MaterialTheme.typography.labelMedium, color = LocalAndarzColors.current.textMuted)
        }
    }

    Text(u.t("wiz.entryOrder"), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        u.enumIds("entryOrder").forEach { id -> FilterChip(selected = d.entryOrder == id, onClick = { vm.update { it.copy(entryOrder = id) } }, label = { Text(u.t("wiz.entryOrder.$id")) }) }
    }
    if (d.entryOrder == "MARKET_OTHER") Text(u.t("wiz.entryOrder.warn"), color = LocalAndarzColors.current.caution)
    SwitchRow(u.t("wiz.slTpOnExchange"), d.slTpOnExchange, { v -> vm.update { it.copy(slTpOnExchange = v) } })
    SwitchRow(u.t("wiz.canMonitor"), d.canMonitor, { v -> vm.update { it.copy(canMonitor = v) } })
    SwitchRow(u.t("wiz.isolated"), d.isolated, { v -> vm.update { it.copy(isolated = v) } })
}

@Composable
private fun StepReview(u: Ui, vm: WizardViewModel, d: WizardDraft, c: WizardResult, p: Params, locked: Boolean, onCancel: () -> Unit) {
    SectionCard(u.t("wiz.review.title")) {
        KeyValue(u.t("wiz.symbol"), u.symbol(d.symbol.uppercase(java.util.Locale.ROOT)))
        KeyValue(u.t("wiz.side"), u.enumLabel("side", d.side.name))
        KeyValue(u.t("wiz.entry"), u.price(c.entry)); KeyValue(u.t("wiz.sl"), u.price(c.sl)); KeyValue(u.t("wiz.tp"), u.price(c.tp))
        KeyValue(u.t("wiz.leverage"), u.mult(c.lev)); KeyValue(u.t("wiz.riskPct"), u.pct(c.riskPct))
        KeyValue(u.t("wiz.checklist.title"), u.t("wiz.checklist.score", "n" to u.int(c.chk), "total" to u.int(p.chkN.toInt())))
        Spacer(Modifier.height(8.dp))
        Text(u.t("wiz.review.size") + ": " + u.money(c.plan?.notionalMax), style = MaterialTheme.typography.titleMedium)
        Text(u.t("wiz.review.formula"), style = MaterialTheme.typography.labelMedium, color = LocalAndarzColors.current.textMuted)
    }
    when {
        locked -> Text(u.t("wiz.gate.blocked"), color = LocalAndarzColors.current.redline)
        c.allowed -> {
            Button(onClick = { vm.savePlan(rejected = false, thenEnter = true) }, modifier = Modifier.fillMaxWidth()) { Text(u.t("wiz.commit.enter")) }
            OutlinedButton(onClick = { vm.savePlan(rejected = false, thenEnter = false) }, modifier = Modifier.fillMaxWidth()) { Text(u.t("wiz.commit.plan")) }
        }
        c.numbersValid -> {
            Button(onClick = { vm.savePlan(rejected = true, thenEnter = false) }, modifier = Modifier.fillMaxWidth()) { Text(u.t("wiz.commit.reject")) }
            // D-28: a de-emphasized path; history stays honest
            TextButton(onClick = { vm.savePlan(rejected = true, thenEnter = true) }) { Text(u.t("wiz.confirm.title")) }
        }
    }
    TextButton(onClick = { vm.clear(); onCancel() }) { Text(u.t("wiz.commit.cancel")) }
}

@Composable
private fun ConfirmSheet(u: Ui, vm: WizardViewModel, t: ir.andarz.data.db.TradeEntity, d: WizardDraft) {
    var entry by rememberSaveable { mutableStateOf(Fmt.number(t.plannedEntry, Ui.priceDecimals(t.plannedEntry), false, false, true)) }
    var notional by rememberSaveable { mutableStateOf(t.plan.notionalMax?.let { Fmt.number(it, 2, false, false, true) } ?: "") }
    var anyway by remember { mutableStateOf(false) }
    val e = Fmt.normalizeInput(entry)?.takeIf { it > 0 }
    val n = Fmt.normalizeInput(notional)?.takeIf { it > 0 }
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(u.t("wiz.confirm.title"), style = MaterialTheme.typography.titleLarge)
        NumberField(u.t("wiz.confirm.entry"), entry, { entry = it }, error = if (entry.isNotBlank() && e == null) u.t("err.positive") else null)
        NumberField(u.t("wiz.confirm.notional"), notional, { notional = it }, error = if (notional.isNotBlank() && n == null) u.t("err.notionalZero") else null, hint = u.t("wiz.confirm.note"))
        if (e != null) Text(u.t("wiz.confirm.slippage", "v" to u.pct(vm.slippage(t.plannedEntry, e, d.side), 2, signed = true)), style = MaterialTheme.typography.labelMedium)
        if (e != null && n != null) {
            val rriskp = n * kotlin.math.abs(e - t.slInitial) / e / t.equity
            Text(u.t("size.actualPct") + " " + u.pct(rriskp))
            if (rriskp > t.riskPctCap * 1.05) Text(u.t("size.silentGrowth"), color = LocalAndarzColors.current.caution)
        }
        if (!t.slTpOnExchange) {
            Text(u.t("err.slTpRequired"), color = LocalAndarzColors.current.redline)
            SwitchRow(u.t("review.autoViolation.V01"), anyway, { anyway = it })
        }
        Button(
            onClick = { vm.confirm(e!!, n!!, Instant.now(), withoutStops = !t.slTpOnExchange) },
            enabled = e != null && n != null && t.canMonitor && (t.slTpOnExchange || anyway),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(u.t("common.confirm")) }
        Spacer(Modifier.height(24.dp))
    }
}
