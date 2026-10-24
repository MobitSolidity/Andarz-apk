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
import ir.andarz.app.ui.components.Button
import ir.andarz.app.ui.components.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import ir.andarz.app.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.components.KeyValue
import ir.andarz.app.ui.components.NumberField
import ir.andarz.app.ui.components.SectionCard
import ir.andarz.app.ui.components.Verdict
import ir.andarz.app.ui.components.VerdictBanner
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui
import ir.andarz.app.ui.wizard.WizardDraft
import ir.andarz.app.ui.wizard.WizardPrefill
import ir.andarz.data.EngineFacade
import ir.andarz.engine.Fmt
import ir.andarz.engine.GateReason
import ir.andarz.engine.Params
import ir.andarz.engine.Side
import ir.andarz.engine.Snapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.floor

/** One-shot hand-off into the calculator (5.13 `con.example.try`). */
@javax.inject.Singleton
class CalcPrefill @Inject constructor() {
    @Volatile private var pending: CalcDraft? = null
    fun offer(d: CalcDraft) { pending = d }
    fun take(): CalcDraft? = pending.also { pending = null }
}

/** Offers the worked example of article 3 (vector P01: 2000 / LONG / 100 / 96 / 108 / 5x, risk 1%). */
@HiltViewModel
class CalculatorPrefillViewModel @Inject constructor(private val prefill: CalcPrefill) : ViewModel() {
    fun workedExample() = prefill.offer(CalcDraft(Side.LONG, "2000", "100", "96", "108", "5", "1", ""))
}

@HiltViewModel
class CalculatorViewModel @Inject constructor(
    private val state: SavedStateHandle,
    private val facade: EngineFacade,
    private val prefill: WizardPrefill,
    calcPrefill: CalcPrefill,
) : ViewModel() {
    private val _draft = MutableStateFlow(calcPrefill.take()?.also { state[KEY] = it } ?: state.get<CalcDraft>(KEY) ?: CalcDraft())
    val draft: StateFlow<CalcDraft> = _draft
    private val _snapshot = MutableStateFlow<Snapshot?>(null)
    val snapshot: StateFlow<Snapshot?> = _snapshot

    init { safeLaunch { _snapshot.value = facade.snapshot() } }

    fun update(f: (CalcDraft) -> CalcDraft) { val d = f(_draft.value); _draft.value = d; state[KEY] = d }

    fun calc(d: CalcDraft, s: Snapshot?): CalcResult = CalcLogic.of(d, s?.params ?: Params.DEFAULTS, s?.governor?.riskCap ?: (s?.params ?: Params.DEFAULTS).riskPct)

    /** `calc.toPlan`: the wizard opens with these numbers and an empty symbol (5.7). */
    fun toPlan(c: CalcResult) {
        val d = _draft.value
        prefill.offer(
            WizardDraft(
                side = d.side, equity = d.equity, entry = d.entry, sl = d.sl, tp = d.tp, lev = d.lev,
                riskPct = Fmt.number(c.riskPct * 100, 4, fa = false, group = false, trim = true),
            ),
        )
    }

    companion object { const val KEY = "calc.draft" }
}

/** The stateless calculator of 5.7: nothing is saved and nothing touches the limits. */
@Composable
fun CalculatorScreen(onToPlan: () -> Unit, vm: CalculatorViewModel = hiltViewModel()) {
    // D-91: a working tool shows the numbers being typed, whatever the hide setting.
    val u = ui().unmasked()
    val d by vm.draft.collectAsState()
    val snap by vm.snapshot.collectAsState()
    val p = snap?.params ?: Params.DEFAULTS
    val c = vm.calc(d, snap)
    val muted = LocalAndarzColors.current.textMuted

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(u.t("calc.title"), style = MaterialTheme.typography.titleLarge)
        Text(u.t("calc.subtitle"), style = MaterialTheme.typography.bodyMedium, color = muted)
        Text(u.t("calc.demoNote"), style = MaterialTheme.typography.labelMedium, color = muted)

        Text(u.t("wiz.side"), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Side.entries.forEach { s -> FilterChip(selected = d.side == s, onClick = { vm.update { it.copy(side = s) } }, label = { Text(u.enumLabel("side", s.name)) }) }
        }
        val err = { text: String, v: Double? -> if (text.isBlank()) null else if (Fmt.normalizeInput(text) == null) u.t("err.number") else if (v == null) u.t("err.positive") else null }
        Row(verticalAlignment = Alignment.CenterVertically) {
            NumberField(u.t("wiz.equity"), d.equity, { v -> vm.update { it.copy(equity = v) } }, modifier = Modifier.weight(1f), error = err(d.equity, c.equity))
            snap?.let { s -> TextButton(onClick = { vm.update { it.copy(equity = CalcLogic.field(s.equity.now)) } }) { Text(u.t("calc.useEquity")) } }
        }
        // The symbol is used only for the live chip (K9.1 c); the calculator stores nothing.
        androidx.compose.material3.OutlinedTextField(d.symbol, { v -> vm.update { it.copy(symbol = v) } }, label = { Text(u.t("wiz.symbol")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        NumberField(u.t("wiz.entry"), d.entry, { v -> vm.update { it.copy(entry = v) } }, error = err(d.entry, c.entry))
        ir.andarz.app.ui.market.LivePriceChip(d.symbol, { v -> vm.update { it.copy(entry = v) } })
        NumberField(u.t("wiz.sl"), d.sl, { v -> vm.update { it.copy(sl = v) } }, error = err(d.sl, c.sl) ?: when {
            c.entryEqSl -> u.t("err.entryEqSl")
            c.slWrong -> u.t("err.slDirection", "dir" to u.t("wiz.gate.slWhere.${d.side.name}"))
            else -> null
        })
        NumberField(u.t("wiz.tp"), d.tp, { v -> vm.update { it.copy(tp = v) } }, error = err(d.tp, c.tp) ?: if (c.tpWrong) u.t("err.tpDirection", "dir" to u.t("wiz.gate.tpWhere.${d.side.name}")) else null)
        NumberField(u.t("wiz.leverage"), d.lev, { v -> vm.update { it.copy(lev = v) } }, error = err(d.lev, c.lev), suffix = "\u00D7")
        NumberField(
            u.t("calc.riskCap"), d.riskPct, { v -> vm.update { it.copy(riskPct = v) } }, suffix = "%",
            hint = u.pct(snap?.governor?.riskCap ?: p.riskPct, 2),
        )
        NumberField(u.t("calc.withFees"), d.feePct, { v -> vm.update { it.copy(feePct = v) } }, suffix = "%")

        if (!c.valid) {
            Text(u.t("calc.needInputs"), style = MaterialTheme.typography.bodyMedium, color = muted)
        } else {
            Result(u, c, p)
            Button(onClick = { vm.toPlan(c); onToPlan() }, modifier = Modifier.fillMaxWidth()) { Text(u.t("calc.toPlan")) }
        }
    }
}

@Composable
private fun Result(u: Ui, c: CalcResult, p: Params) {
    val pl = c.plan!!
    val state = if (c.allowed) Verdict.ALLOWED else Verdict.DENIED
    val title = if (c.allowed) u.t("wiz.gate.allowed") else u.t("wiz.gate.deniedWith", "reasons" to c.reasons.joinToString("\u060C ") { u.t("wiz.gate.reason.${it.name}") })
    val why = c.reasons.map { r -> "\u2022 " + u.t("wiz.gate.reason.${r.name}") + ": " + detail(u, r, c, p) + " " + u.t("wiz.gate.fix.${r.name}") }
    VerdictBanner(state, title, u.t("wiz.calc.riskUsd") + " " + u.money(pl.riskUsd), why)

    SectionCard(u.t("calc.result")) {
        KeyValue(u.t("wiz.calc.sld"), u.pct(pl.sld))
        KeyValue(u.t("wiz.calc.rr"), u.num(pl.rr))
        KeyValue(u.t("wiz.calc.riskUsd"), u.money(pl.riskUsd))
        KeyValue(u.t("wiz.calc.notionalMax"), u.money(pl.notionalMax))
        KeyValue(u.t("wiz.calc.margin"), u.money(pl.margin))
        KeyValue(u.t("wiz.calc.marginPct"), u.pct(pl.marginPct))
        KeyValue(u.t("wiz.calc.liq"), u.price(pl.liq))
        KeyValue(u.t("wiz.calc.liqDist"), u.pct(pl.liqDist))
        KeyValue(u.t("wiz.calc.liqX"), u.mult(pl.liqX))
        Text(u.t("calc.formula"), style = MaterialTheme.typography.labelMedium, color = LocalAndarzColors.current.textMuted)
        if (c.rrAfterFees != null) {
            Text(u.t("calc.feeImpact", "from" to u.num(pl.rr), "to" to u.num(c.rrAfterFees)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
        }
        Text(u.t("wiz.calc.note"), style = MaterialTheme.typography.labelMedium, color = LocalAndarzColors.current.textMuted)
    }

    c.advisor?.let { a ->
        SectionCard(u.t("wiz.adv.title")) {
            if (a.recommended != null) {
                Text(u.t("wiz.adv.range", "min" to u.int(a.feasibleMin!!), "max" to u.int(a.feasibleMax!!)))
                Text(u.t("wiz.adv.recommended", "lev" to u.int(a.recommended!!), "margin" to u.pct(c.recommendedMarginPct)))
            } else Text(u.t("wiz.adv.none"), color = LocalAndarzColors.current.redline)
            if (a.lMinReal > p.levMax) Text(u.t("wiz.adv.tight", "lev" to u.int(ceil(a.lMinReal).toInt())))
            if (a.lMaxLiqReal < p.levMax) Text(u.t("wiz.adv.far", "lev" to u.int(floor(a.lMaxLiqReal).toInt())), style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun detail(u: Ui, r: GateReason, c: CalcResult, p: Params): String = when (r) {
    GateReason.DIRECTION -> {
        val s = if (c.entry != null && c.sl != null && c.sl < c.entry) Side.LONG else Side.SHORT
        u.t("wiz.gate.detail.DIRECTION", "side" to u.enumLabel("side", s.name), "slWhere" to u.t("wiz.gate.slWhere.${s.name}"), "tpWhere" to u.t("wiz.gate.tpWhere.${s.name}"))
    }
    GateReason.CHECKLIST -> u.t("wiz.gate.detail.CHECKLIST", "n" to u.int(p.chkN.toInt()), "total" to u.int(p.chkN.toInt()))
    GateReason.LEVERAGE -> u.t("wiz.gate.detail.LEVERAGE", "lev" to u.mult(c.lev), "max" to u.mult(p.levMax))
    GateReason.RR -> u.t("wiz.gate.detail.RR", "rr" to u.num(c.plan?.rr), "min" to u.num(p.rrMin, 2, trim = true))
    GateReason.MARGIN -> u.t("wiz.gate.detail.MARGIN", "pct" to u.pct(c.plan?.marginPct), "max" to u.pct(p.marginMax, 0))
    GateReason.LIQUIDATION -> u.t("wiz.gate.detail.LIQUIDATION", "x" to u.mult(c.plan?.liqX), "min" to u.mult(p.liqMult))
}
