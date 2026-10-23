package ir.andarz.app.ui.wizard

import ir.andarz.app.ui.safeLaunch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.andarz.data.EngineFacade
import ir.andarz.data.EntryConfirm
import ir.andarz.data.JournalRepository
import ir.andarz.data.PlanDraft
import ir.andarz.data.SetupRepository
import ir.andarz.data.db.AndarzDatabase
import ir.andarz.data.db.SetupEntity
import ir.andarz.data.db.TradeEntity
import ir.andarz.data.db.WatchSymbolEntity
import ir.andarz.engine.Advisor
import ir.andarz.engine.DashStatus
import ir.andarz.engine.Fmt
import ir.andarz.engine.GateReason
import ir.andarz.engine.Plan
import ir.andarz.engine.Planner
import ir.andarz.engine.Side
import ir.andarz.engine.Snapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/** One-shot hand-off from the calculator to the wizard (5.7). Process-local; ids and numbers only. */
@javax.inject.Singleton
class WizardPrefill @Inject constructor() {
    @Volatile private var pending: WizardDraft? = null
    fun offer(d: WizardDraft) { pending = d }
    fun take(): WizardDraft? = pending.also { pending = null }
}

/** Wizard draft (5.3). Plain strings for numbers keep the user's raw text; ids only, so it survives death. */
data class WizardDraft(
    val step: Int = 1,
    val demo: Boolean = false,
    val symbol: String = "",
    val side: Side = Side.LONG,
    val kind: String = "INTRADAY",
    val setupId: String? = null,
    val timeframe: String = "15m",
    val htfTrend: String = "RANGE",
    val counterTrendReason: String = "",
    val emotion: String = "CALM",
    /** Manual ticks only (items 1–5 and 8); items 6 and 7 are derived (D-09). */
    val manualMask: Int = 0,
    val equity: String = "",
    val entry: String = "",
    val sl: String = "",
    val tp: String = "",
    val lev: String = "5",
    val riskPct: String = "",
    val entryOrder: String = "LIMIT",
    val slTpOnExchange: Boolean = false,
    val canMonitor: Boolean = false,
    val isolated: Boolean = false,
) : java.io.Serializable

/** Everything the wizard shows, computed by the engine from the draft (no formula in a composable). */
data class WizardResult(
    val equity: Double?,
    val entry: Double?,
    val sl: Double?,
    val tp: Double?,
    val lev: Double?,
    val riskPct: Double,
    val maxRisk: Double,
    val plan: Plan?,
    val reasons: List<GateReason>,
    val chk: Int,
    val item6: Boolean,
    val item7: Boolean,
    val advisor: Advisor?,
    val numbersValid: Boolean,
    val entryEqSl: Boolean,
    val slWrong: Boolean,
    val tpWrong: Boolean,
    val counterTrend: Boolean,
    val calm: Boolean,
) {
    val allowed: Boolean get() = numbersValid && reasons.isEmpty()
}

@HiltViewModel
class WizardViewModel @Inject constructor(
    private val state: SavedStateHandle,
    private val facade: EngineFacade,
    private val journal: JournalRepository,
    private val setups: SetupRepository,
    db: AndarzDatabase,
    prefill: WizardPrefill,
) : ViewModel() {
    private val _draft = MutableStateFlow(state.get<WizardDraft>(KEY) ?: WizardDraft())
    val draft: StateFlow<WizardDraft> = _draft

    private val _snapshot = MutableStateFlow<Snapshot?>(null)
    val snapshot: StateFlow<Snapshot?> = _snapshot

    val setupList: StateFlow<List<SetupEntity>> = db.setups().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val watchList: StateFlow<List<WatchSymbolEntity>> = db.watch().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The confirmation sheet's target, set after "enter" or "entered anyway". */
    val pendingEntry = MutableStateFlow<TradeEntity?>(null)
    val done = MutableStateFlow<String?>(null)

    init {
        // The calculator's numbers (5.7 `calc.toPlan`): taken once, symbol left empty.
        prefill.take()?.let { pre -> _draft.value = pre; state[KEY] = pre }
        refresh()
    }

    fun refresh() = safeLaunch {
        val s = facade.snapshot(Instant.now(), _draft.value.setupId)
        _snapshot.value = s
        if (_draft.value.equity.isEmpty()) update { it.copy(equity = Fmt.number(s.equity.now, 2, fa = false, group = false, trim = true)) }
        if (_draft.value.riskPct.isEmpty()) update { it.copy(riskPct = Fmt.number(s.governor.riskCap * 100, 2, fa = false, group = false, trim = true)) }
    }

    fun update(f: (WizardDraft) -> WizardDraft) {
        val before = _draft.value
        val after = f(before)
        _draft.value = after
        state[KEY] = after
        if (after.setupId != before.setupId) refresh()
    }

    fun toggleItem(n: Int) = update { it.copy(manualMask = it.manualMask xor (1 shl (n - 1))) }

    fun calc(d: WizardDraft, s: Snapshot?): WizardResult = calculator.of(d, s)

    fun checklistMask(d: WizardDraft, c: WizardResult): Int = calculator.mask(d, c)

    private val calculator = WizardCalc()

    private fun toPlan(d: WizardDraft, c: WizardResult) = PlanDraft(
        account = if (d.demo) "DEMO" else "REAL", symbol = d.symbol, side = d.side, kind = d.kind, setupId = d.setupId,
        timeframe = d.timeframe, htfTrend = d.htfTrend, counterTrendReason = d.counterTrendReason.takeIf { c.counterTrend },
        emotion = d.emotion, checklistMask = checklistMask(d, c), equity = c.equity!!, plannedEntry = c.entry!!, sl = c.sl!!,
        tp = c.tp!!, lev = c.lev!!, riskPctChosen = c.riskPct, entryOrder = d.entryOrder, isolated = d.isolated,
        slTpOnExchange = d.slTpOnExchange, canMonitor = d.canMonitor,
    )

    fun savePlan(rejected: Boolean, thenEnter: Boolean) = safeLaunch {
        val d = _draft.value
        val c = calc(d, _snapshot.value)
        if (!c.numbersValid) return@safeLaunch
        val t = journal.savePlan(toPlan(d, c), rejected)
        if (thenEnter) pendingEntry.value = t else done.value = t.id
        if (!thenEnter) clear()
    }

    fun confirm(entry: Double, notional: Double, at: Instant, withoutStops: Boolean) = safeLaunch {
        val t = pendingEntry.value ?: return@safeLaunch
        journal.confirmEntry(t.id, EntryConfirm(entry, notional, at, withoutStops))
        pendingEntry.value = null
        done.value = t.id
        clear()
    }

    fun createSetup(name: String) = safeLaunch {
        val s = setups.create(name)
        update { it.copy(setupId = s.id) }
    }

    fun clear() { _draft.value = WizardDraft(); state.remove<WizardDraft>(KEY) }

    /** Slippage `(entry − planned) / planned`, signed by side (5.3). */
    fun slippage(planned: Double, entry: Double, side: Side): Double =
        (if (side == Side.LONG) 1 else -1) * (entry - planned) / planned


    companion object { const val KEY = "wizard.draft" }
}

/** The wizard's numbers from a draft: plan, gate, derived checklist items and the advisor (5.3, D-09, D-25). */
class WizardCalc {
    fun of(d: WizardDraft, s: Snapshot?): WizardResult {
        val p = s?.params ?: ir.andarz.engine.Params.DEFAULTS
        val g = s?.governor
        val equity = Fmt.normalizeInput(d.equity)?.takeIf { it > 0 }
        val entry = Fmt.normalizeInput(d.entry)?.takeIf { it > 0 }
        val sl = Fmt.normalizeInput(d.sl)?.takeIf { it > 0 }
        val tp = Fmt.normalizeInput(d.tp)?.takeIf { it > 0 }
        val lev = Fmt.normalizeInput(d.lev)?.takeIf { it > 0 }
        val cap = g?.riskCap ?: p.riskPct
        val maxRisk = if (g?.gradeAAllowed == true) p.riskMax else cap
        var risk = (Fmt.normalizeInput(d.riskPct) ?: (cap * 100)) / 100
        val counter = (d.side == Side.LONG && d.htfTrend == "DOWN") || (d.side == Side.SHORT && d.htfTrend == "UP")
        // P1 D-25: counter-trend trades are capped at half the risk cap
        val effMax = if (counter) maxRisk / 2 else maxRisk
        risk = risk.coerceIn(0.001, effMax)
        val valid = equity != null && entry != null && sl != null && tp != null && lev != null
        val entryEqSl = entry != null && sl != null && entry == sl
        val slWrong = entry != null && sl != null && !entryEqSl && (if (d.side == Side.LONG) sl >= entry else sl <= entry)
        val tpWrong = entry != null && tp != null && (if (d.side == Side.LONG) tp <= entry else tp >= entry)
        val calm = d.emotion == "CALM"
        val manual = d.manualMask and if (calm) 0xFF else 0x7F
        if (!valid || entryEqSl) {
            return WizardResult(equity, entry, sl, tp, lev, risk, effMax, null, emptyList(), Integer.bitCount(manual and 0x9F), false, false, null, false, entryEqSl, slWrong, tpWrong, counter, calm)
        }
        val plan = Planner.plan(d.side, equity!!, entry!!, sl!!, tp!!, lev!!, p, risk)
        val pre = Planner.gate(d.side, entry, sl, tp, lev, p.chkN.toInt(), plan, p)
        val item6 = GateReason.DIRECTION !in pre && GateReason.RR !in pre
        val item7 = pre.none { it == GateReason.LEVERAGE || it == GateReason.MARGIN || it == GateReason.LIQUIDATION } &&
            d.isolated && (s?.dashboard?.status?.openRisk ?: DashStatus.OK) == DashStatus.OK
        var mask = manual and 0x9F
        if (item6) mask = mask or (1 shl 5)
        if (item7) mask = mask or (1 shl 6)
        val chk = Integer.bitCount(mask)
        val reasons = Planner.gate(d.side, entry, sl, tp, lev, chk, plan, p)
        val adv = runCatching { Planner.leverageAdvisor(d.side, equity, entry, sl, tp, p.chkN.toInt(), p, risk) }.getOrNull()
        return WizardResult(equity, entry, sl, tp, lev, risk, effMax, plan, reasons, chk, item6, item7, adv, true, false, slWrong, tpWrong, counter, calm)
    }

    fun mask(d: WizardDraft, c: WizardResult): Int {
        var mask = d.manualMask and (if (c.calm) 0xFF else 0x7F) and 0x9F
        if (c.item6) mask = mask or (1 shl 5)
        if (c.item7) mask = mask or (1 shl 6)
        return mask
    }
}
