package ir.andarz.app.ui.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.components.AndarzSwitch
import ir.andarz.app.ui.components.ArchShapes
import ir.andarz.app.ui.components.FilledAction
import ir.andarz.app.ui.components.HeaderTile
import ir.andarz.app.ui.components.NoticeBar
import ir.andarz.app.ui.components.NoticeTone
import ir.andarz.app.ui.components.Text
import ir.andarz.app.ui.components.Verdict
import ir.andarz.app.ui.components.VerdictBanner
import ir.andarz.app.ui.components.archSurface
import ir.andarz.app.ui.components.figure
import ir.andarz.app.ui.icons.Glyph
import ir.andarz.app.ui.icons.GlyphIcon
import ir.andarz.app.ui.market.LiveLinkViewModel
import ir.andarz.app.ui.market.LivePriceChip
import ir.andarz.app.ui.safeLaunch
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
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

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

/** A typical futures taker fee, both ways, in percent: what the fee switch starts with. The reader edits it. */
private const val STARTING_FEE = "0.12"

/**
 * The stateless calculator of 5.7: nothing is saved and nothing touches the limits. One page: the direction, the
 * inputs in one card, the leverage with its advisor on a rail, the verdict of the gate, the nine figures and the
 * effect of the fee, then the way into a plan.
 */
@Composable
fun CalculatorScreen(onToPlan: () -> Unit, vm: CalculatorViewModel = hiltViewModel(), live: LiveLinkViewModel = hiltViewModel()) {
    // D-91: a working tool shows the numbers being typed, whatever the hide setting.
    val u = ui().unmasked()
    val k = LocalAndarzColors.current
    val d by vm.draft.collectAsState()
    val snap by vm.snapshot.collectAsState()
    val gate by live.gate.collectAsState()
    val p = snap?.params ?: Params.DEFAULTS
    val c = vm.calc(d, snap)
    val cap = snap?.governor?.riskCap ?: p.riskPct
    val equityNow = snap?.let { s -> s.equity.now }
    // The switch is its own state: emptying the field while typing must not close the card under the finger.
    var feeOn by rememberSaveable { mutableStateOf(d.feePct.isNotBlank()) }
    var lastFee by rememberSaveable { mutableStateOf(d.feePct.ifBlank { STARTING_FEE }) }
    val problems = problemsOf(u, d, c)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Three columns need 360 dp and the normal type size; below that the cells stack.
        val wide = maxWidth >= 360.dp && LocalDensity.current.fontScale <= 1.15f
        Column(Modifier.fillMaxSize()) {
            CalcHeader(u) { vm.update { CalcDraft() }; feeOn = false; lastFee = STARTING_FEE }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SideToggle(u, d.side) { s -> vm.update { it.copy(side = s) } }
                CalcInputs(u, d, cap, equityNow, gate.mayUseNetwork, problems, wide, vm::update)
                problems.all().forEach { NoticeBar(it, NoticeTone.DANGER) }
                LeverageCard(u, d, c, p) { n -> vm.update { it.copy(lev = n.toString()) } }
                if (c.valid) {
                    Verdicts(u, c, p)
                    Figures(u, c, p, wide)
                    FeeCard(
                        u, c, p, on = feeOn, fee = d.feePct,
                        onToggle = { on ->
                            if (on) {
                                vm.update { it.copy(feePct = lastFee.ifBlank { STARTING_FEE }) }
                            } else {
                                if (d.feePct.isNotBlank()) lastFee = d.feePct
                                vm.update { it.copy(feePct = "") }
                            }
                            feeOn = on
                        },
                        onFee = { v -> vm.update { it.copy(feePct = v) }; if (v.isNotBlank()) lastFee = v },
                    )
                    Text(u.t("calc.formula"), style = MaterialTheme.typography.labelMedium, color = k.muted)
                    Text(u.t("wiz.calc.note"), style = MaterialTheme.typography.labelMedium, color = k.muted)
                } else {
                    Text(u.t("calc.needInputs"), style = MaterialTheme.typography.bodyMedium, color = k.muted, modifier = Modifier.padding(vertical = 4.dp))
                }
                FilledAction(
                    onClick = { vm.toPlan(c); onToPlan() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = c.valid,
                    radius = 20.dp,
                ) {
                    Text(u.t("app.calc.toPlan"))
                    Spacer(Modifier.width(10.dp))
                    GlyphIcon(Glyph.ARROW_RIGHT, null, size = 20.dp, strokeWidth = 2f)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ header and direction

@Composable
private fun CalcHeader(u: Ui, onClear: () -> Unit) {
    val k = LocalAndarzColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(u.t("calc.title"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(u.t("app.calc.note"), style = MaterialTheme.typography.labelMedium, color = k.muted)
        }
        HeaderTile(Glyph.REFRESH, u.t("app.calc.clear"), tint = k.hmuted, container = k.alt, onClick = onClear)
    }
}

/** Long or short: one arch track with two options, the chosen one raised in lapis. */
@Composable
private fun SideToggle(u: Ui, side: Side, onSide: (Side) -> Unit) {
    val k = LocalAndarzColors.current
    Row(
        Modifier.fillMaxWidth().height(52.dp)
            .archSurface(ArchShapes.arch(26.dp, 16.dp), k.sf1, k.divider)
            .padding(2.dp)
            .selectableGroup()
            .semantics { contentDescription = u.t("app.calc.side") },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Side.entries.forEach { s ->
            val on = s == side
            Row(
                Modifier.weight(1f).fillMaxHeight()
                    .then(if (on) Modifier.archSurface(ArchShapes.arch(24.dp, 14.dp), k.lapisC, Color.Transparent, k.edge) else Modifier)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSide(s) }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                GlyphIcon(if (s == Side.LONG) Glyph.TRI_UP else Glyph.TRI_DOWN, null, tint = if (on) k.lapis else k.muted, size = 14.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    u.enumLabel("side", s.name),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (on) FontWeight.W700 else FontWeight.W500),
                    color = if (on) k.lapis else k.muted,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ inputs

private class Problems(val equity: String?, val entry: String?, val sl: String?, val tp: String?, val lev: String?) {
    fun all(): List<String> = listOfNotNull(equity, entry, sl, tp, lev)
}

private fun problemsOf(u: Ui, d: CalcDraft, c: CalcResult): Problems {
    val bad = { text: String, v: Double? ->
        if (text.isBlank()) null else if (Fmt.normalizeInput(text) == null) u.t("err.number") else if (v == null) u.t("err.positive") else null
    }
    return Problems(
        equity = bad(d.equity, c.equity),
        entry = bad(d.entry, c.entry),
        sl = bad(d.sl, c.sl) ?: when {
            c.entryEqSl -> u.t("err.entryEqSl")
            c.slWrong -> u.t("err.slDirection", "dir" to u.t("wiz.gate.slWhere.${d.side.name}"))
            else -> null
        },
        tp = bad(d.tp, c.tp) ?: if (c.tpWrong) u.t("err.tpDirection", "dir" to u.t("wiz.gate.tpWhere.${d.side.name}")) else null,
        lev = bad(d.lev, c.lev),
    )
}

/** The percent sign of the digit setting, for the suffix of a field. */
private fun pctSign(u: Ui): String = if (u.fa) Fmt.FA_PERCENT.toString() else "%"

/** A fraction as a percent with only the decimals it needs: 1% and 0.5% and 0.75%, not 1.00%. */
private fun Ui.pctTrim(x: Double): String {
    val v = x * 100
    val digits = when {
        abs(v - round(v)) < 0.005 -> 0
        abs(v * 10 - round(v * 10)) < 0.05 -> 1
        else -> 2
    }
    return pct(x, digits)
}

@Composable
private fun CalcInputs(
    u: Ui,
    d: CalcDraft,
    cap: Double,
    equityNow: Double?,
    showSymbol: Boolean,
    problems: Problems,
    wide: Boolean,
    update: ((CalcDraft) -> CalcDraft) -> Unit,
) {
    val k = LocalAndarzColors.current
    val slGlyph = if (d.side == Side.LONG) Glyph.TRI_DOWN else Glyph.TRI_UP
    val tpGlyph = if (d.side == Side.LONG) Glyph.TRI_UP else Glyph.TRI_DOWN
    Column(Modifier.fillMaxWidth().archSurface(RoundedCornerShape(20.dp), k.sf1, k.divider, k.edge)) {
        CellRow(
            wide, listOf(1.15f, 1f),
            listOf<@Composable (Modifier) -> Unit>(
                { m ->
                    Row(m, verticalAlignment = Alignment.CenterVertically) {
                        CalcCell(
                            u.t("app.calc.equity"), d.equity, { v -> update { it.copy(equity = v) } },
                            Modifier.weight(1f), problem = problems.equity, height = 60.dp,
                        )
                        HeaderTile(
                            Glyph.WALLET, u.t("app.calc.useEquity"), tint = k.text, container = k.high, enabled = equityNow != null,
                            onClick = { if (equityNow != null) update { it.copy(equity = CalcLogic.field(equityNow)) } },
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                },
                { m ->
                    CalcCell(
                        u.t("app.calc.risk", "cap" to u.pctTrim(cap)), d.riskPct, { v -> update { it.copy(riskPct = v) } },
                        m, suffix = pctSign(u), height = 60.dp,
                    )
                },
            ),
        )
        Rule()
        CellRow(
            wide, listOf(1f, 1f, 1f),
            listOf<@Composable (Modifier) -> Unit>(
                { m -> CalcCell(u.t("wiz.entry"), d.entry, { v -> update { it.copy(entry = v) } }, m, glyph = Glyph.DIAMOND, problem = problems.entry) },
                { m -> CalcCell(u.t("app.wizard.sl"), d.sl, { v -> update { it.copy(sl = v) } }, m, glyph = slGlyph, problem = problems.sl) },
                { m -> CalcCell(u.t("app.wizard.tp"), d.tp, { v -> update { it.copy(tp = v) } }, m, glyph = tpGlyph, problem = problems.tp) },
            ),
        )
        if (showSymbol) {
            // The symbol is used only for the live chip (K9.1 c); the calculator stores nothing.
            Rule()
            CalcCell(u.t("wiz.symbol"), d.symbol, { v -> update { it.copy(symbol = v) } }, Modifier.fillMaxWidth(), keyboard = KeyboardOptions.Default)
            if (d.symbol.isNotBlank()) {
                Box(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp)) {
                    LivePriceChip(d.symbol, { v -> update { it.copy(entry = v) } })
                }
            }
        }
    }
}

/** The cells of one row of the inputs card: side by side with hairlines between them, or stacked when the width is short. */
@Composable
private fun CellRow(wide: Boolean, weights: List<Float>, cells: List<@Composable (Modifier) -> Unit>) {
    val k = LocalAndarzColors.current
    if (wide) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            cells.forEachIndexed { i, cell ->
                if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(k.divider))
                cell(Modifier.weight(weights[i]))
            }
        }
    } else {
        Column(Modifier.fillMaxWidth()) {
            cells.forEachIndexed { i, cell ->
                if (i > 0) Rule()
                cell(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Rule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalAndarzColors.current.divider))
}

/**
 * One cell: a small label (with a glyph) over the figure in Plex 20, left-to-right and flush to the start edge of
 * the page. The whole cell is the field, so a tap on the label focuses it; the label and the text are one TalkBack
 * node, and a problem is announced with it.
 */
@Composable
private fun CalcCell(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    suffix: String? = null,
    problem: String? = null,
    height: Dp = 56.dp,
    keyboard: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
) {
    val k = LocalAndarzColors.current
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value, onValueChange = onValue, singleLine = true,
        textStyle = figure(20).copy(color = if (problem != null) k.redline else k.text, textDirection = TextDirection.Ltr, textAlign = TextAlign.Right),
        keyboardOptions = keyboard,
        cursorBrush = SolidColor(k.lapis),
        modifier = modifier
            .heightIn(min = height)
            .onFocusChanged { focused = it.hasFocus }
            .drawBehind {
                if (focused) {
                    val g = 3.dp.toPx()
                    drawRoundRect(k.lapis, Offset(g, g), Size(size.width - 2 * g, size.height - 2 * g), CornerRadius(14.dp.toPx()), style = Stroke(2.dp.toPx()))
                }
            }
            .semantics(mergeDescendants = true) { if (problem != null) error(problem) },
        decorationBox = { inner ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (glyph != null) GlyphIcon(glyph, null, tint = k.muted, size = 11.dp)
                    Text(label, style = MaterialTheme.typography.labelMedium, color = k.muted)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (suffix != null) Text(suffix, style = figure(16, FontWeight.W500), color = k.muted, modifier = Modifier.padding(end = 4.dp))
                    Box(Modifier.weight(1f)) { inner() }
                }
            }
        },
    )
}

// ------------------------------------------------------------------ leverage

/** The leverage and its advisor: the allowed band and the suggestion on a rail, with a step button at each end. */
@Composable
private fun LeverageCard(u: Ui, d: CalcDraft, c: CalcResult, p: Params, onLev: (Int) -> Unit) {
    val k = LocalAndarzColors.current
    val a = c.advisor
    val cur = Fmt.normalizeInput(d.lev)?.takeIf { it > 0 }
    val lo = a?.feasibleMin
    val hi = a?.feasibleMax
    val rec = a?.recommended
    val railMax = maxOf(12.0, p.levMax + 2.0, ceil(cur ?: 0.0))
    val base = cur ?: 5.0
    val range = if (rec != null && lo != null && hi != null) {
        u.t("app.calc.lev.range", "min" to u.mult(lo.toDouble()), "max" to u.mult(hi.toDouble()), "rec" to u.mult(rec.toDouble()))
    } else {
        null
    }
    val description = (
        if (lo != null && hi != null) {
            u.t("app.calc.lev.desc", "from" to u.mult(1.0), "to" to u.mult(railMax), "min" to u.mult(lo.toDouble()), "max" to u.mult(hi.toDouble()), "lev" to u.mult(cur))
        } else {
            u.t("app.calc.lev.desc.none", "from" to u.mult(1.0), "to" to u.mult(railMax), "lev" to u.mult(cur))
        }
        ) + if (cur != null && rec != null && abs(cur - rec) < 1e-9) u.t("app.calc.lev.isRec") else ""
    Column(Modifier.fillMaxWidth().background(k.lapisC, RoundedCornerShape(20.dp)).padding(start = 8.dp, top = 8.dp, end = 12.dp, bottom = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 34.dp).padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(u.t("wiz.leverage"), style = MaterialTheme.typography.labelMedium, color = k.lapis)
                Text(u.mult(cur), style = figure(22, FontWeight.W800), color = k.text)
            }
            if (range != null) {
                Text(range, modifier = Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelMedium, color = k.lapis)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StepButton(Glyph.MINUS, u.t("app.calc.lev.dec"), enabled = base > 1.0 + 1e-9) { onLev(maxOf(1, ceil(base).toInt() - 1)) }
            LeverageRail(
                lev = cur, railMax = railMax, from = lo, to = hi,
                description = description,
                curLabel = u.mult(cur), fromLabel = lo?.let { u.mult(it.toDouble()) }, toLabel = hi?.let { u.mult(it.toDouble()) },
                modifier = Modifier.weight(1f),
            )
            StepButton(Glyph.PLUS, u.t("app.calc.lev.inc"), enabled = base < railMax - 1e-9) { onLev(minOf(railMax.toInt(), floor(base).toInt() + 1)) }
        }
        if (a != null) {
            val note = MaterialTheme.typography.labelMedium
            if (a.recommended == null) Text(u.t("wiz.adv.none"), style = note, color = k.redline, modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp))
            if (a.lMinReal > p.levMax) {
                Text(u.t("wiz.adv.tight", "lev" to u.int(ceil(a.lMinReal).toInt())), style = note, color = k.lapis, modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp))
            }
            if (a.lMaxLiqReal < p.levMax) {
                Text(u.t("wiz.adv.far", "lev" to u.int(floor(a.lMaxLiqReal).toInt())), style = note, color = k.lapis, modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp))
            }
        }
    }
}

@Composable
private fun StepButton(glyph: Glyph, description: String, enabled: Boolean, onClick: () -> Unit) {
    val k = LocalAndarzColors.current
    Box(
        Modifier.size(48.dp).background(k.high, RoundedCornerShape(16.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph, description, tint = if (enabled) k.text else k.muted, size = 22.dp, strokeWidth = 2f) }
}

/**
 * The rail from 1x to the top of the scale: the allowed band in lapis, the chosen leverage as a turquoise thumb with
 * its value above it, and the two ends of the band below. Drawn from the start edge, so it runs right to left in
 * Persian. The figures are drawn at the normal type size, whatever the system scale, and the rail is one TalkBack
 * node with the whole story in its description.
 */
@Composable
private fun LeverageRail(
    lev: Double?,
    railMax: Double,
    from: Int?,
    to: Int?,
    description: String,
    curLabel: String,
    fromLabel: String?,
    toLabel: String?,
    modifier: Modifier = Modifier,
) {
    val k = LocalAndarzColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val fixed = Density(LocalDensity.current.density, 1f)
    BoxWithConstraints(modifier.height(44.dp).clearAndSetSemantics { contentDescription = description }) {
        val pad = 12.dp
        val total = maxWidth
        fun frac(x: Double): Float = ((x - 1.0) / (railMax - 1.0)).coerceIn(0.0, 1.0).toFloat()
        fun at(x: Double): Dp = pad + (total - pad * 2) * frac(x)
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height / 2f
            val half = 3.dp.toPx()
            val usable = size.width - 2 * pad.toPx()
            fun px(x: Double): Float {
                val dist = pad.toPx() + usable * frac(x)
                return if (rtl) size.width - dist else dist
            }
            drawRoundRect(k.lapisSoft, Offset(pad.toPx(), y - half), Size(usable, 2 * half), CornerRadius(half))
            if (from != null && to != null) {
                val a = px(from.toDouble())
                val b = px(to.toDouble())
                drawRoundRect(k.lapis, Offset(minOf(a, b), y - half), Size(abs(b - a), 2 * half), CornerRadius(half))
            }
            if (lev != null) {
                val center = Offset(px(lev), y)
                drawCircle(k.lapisC, 12.dp.toPx(), center)
                drawCircle(k.fzFill, 9.dp.toPx(), center)
                val tick = Path().apply {
                    moveTo(center.x - 4.dp.toPx(), center.y + 0.5.dp.toPx())
                    lineTo(center.x - 1.3.dp.toPx(), center.y + 3.dp.toPx())
                    lineTo(center.x + 4.dp.toPx(), center.y - 2.8.dp.toPx())
                }
                drawPath(tick, k.onFz, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        CompositionLocalProvider(LocalDensity provides fixed) {
            if (lev != null) RailTag(at(lev), total, bottom = false, text = curLabel, color = k.fz, weight = FontWeight.W700)
            if (from != null && fromLabel != null) RailTag(at(from.toDouble()), total, bottom = true, text = fromLabel, color = k.lapis, weight = FontWeight.W400)
            if (to != null && toLabel != null && to != from) RailTag(at(to.toDouble()), total, bottom = true, text = toLabel, color = k.lapis, weight = FontWeight.W400)
        }
    }
}

@Composable
private fun BoxScope.RailTag(center: Dp, total: Dp, bottom: Boolean, text: String, color: Color, weight: FontWeight) {
    val w = 36.dp
    val left = maxOf(0.dp, minOf(total - w, center - w / 2))
    Text(
        text,
        modifier = Modifier.align(if (bottom) Alignment.BottomStart else Alignment.TopStart).offset(x = left).width(w),
        textAlign = TextAlign.Center, color = color, maxLines = 1, softWrap = false, style = figure(12, weight),
    )
}

// ------------------------------------------------------------------ verdict, figures, fee

/** The verdict of the gate on the numbers, with the reasons and what to change when it says no. */
@Composable
private fun Verdicts(u: Ui, c: CalcResult, p: Params) {
    val pl = c.plan ?: return
    val title = if (c.allowed) {
        u.t("wiz.gate.allowed")
    } else {
        u.t("wiz.gate.deniedWith", "reasons" to c.reasons.joinToString("\u060C ") { u.t("wiz.gate.reason.${it.name}") })
    }
    val line = u.t(
        "app.calc.verdict.line",
        "risk" to u.pctTrim(c.riskPct), "usd" to u.money(pl.riskUsd),
        "margin" to (pl.marginPct?.let { u.pctTrim(it) } ?: u.t("common.dash")), "liq" to u.mult(pl.liqX),
    )
    val why = c.reasons.map { r -> "• " + u.t("wiz.gate.reason.${r.name}") + ": " + detail(u, r, c, p) + " " + u.t("wiz.gate.fix.${r.name}") }
    VerdictBanner(if (c.allowed) Verdict.ALLOWED else Verdict.DENIED, title, line, why)
}

private class Fig(val label: String, val value: String, val limit: String? = null, val pass: Boolean? = null)

/** The nine figures, three to a row (two when the width is short or the type is large). A limit shows as pass or fail. */
@Composable
private fun Figures(u: Ui, c: CalcResult, p: Params, wide: Boolean) {
    val pl = c.plan ?: return
    val figs = listOf(
        Fig(u.t("wiz.calc.sld"), u.pct(pl.sld)),
        Fig(u.t("wiz.calc.rr"), u.num(pl.rr), u.t("app.calc.min", "v" to u.num(p.rrMin, 2, trim = true)), GateReason.RR !in c.reasons),
        Fig(u.t("app.calc.f.risk"), u.money(pl.riskUsd)),
        Fig(u.t("app.calc.f.notional"), u.money(pl.notionalMax)),
        Fig(u.t("app.calc.f.margin"), u.money(pl.margin)),
        Fig(u.t("app.calc.f.marginPct"), u.pct(pl.marginPct), u.t("app.calc.cap", "v" to u.pct(p.marginMax, 0)), GateReason.MARGIN !in c.reasons),
        Fig(u.t("app.calc.f.liq"), u.price(pl.liq)),
        Fig(u.t("wiz.calc.liqDist"), u.pct(pl.liqDist)),
        Fig(u.t("app.calc.f.liqX"), u.mult(pl.liqX), u.t("app.calc.min", "v" to u.mult(p.liqMult)), GateReason.LIQUIDATION !in c.reasons),
    )
    val columns = if (wide) 3 else 2
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        figs.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { f -> FigureTile(u, f, Modifier.weight(1f).fillMaxHeight()) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun FigureTile(u: Ui, f: Fig, modifier: Modifier) {
    val k = LocalAndarzColors.current
    val state = when (f.pass) {
        true -> u.t("wiz.gate.allowed")
        false -> u.t("wiz.gate.denied")
        null -> null
    }
    Column(
        modifier.archSurface(ArchShapes.tile, k.sf1, k.divider).padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) { if (state != null) stateDescription = state },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(f.label, style = MaterialTheme.typography.labelMedium, color = k.muted, modifier = Modifier.weight(1f, fill = false))
            if (f.pass != null) {
                GlyphIcon(if (f.pass) Glyph.CHECK else Glyph.ALERT_CIRCLE, null, tint = if (f.pass) k.fz else k.an, size = 13.dp, strokeWidth = 2.4f)
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(f.value, style = figure(16), color = if (f.pass == false) k.redline else k.text)
            if (f.limit != null) Text(f.limit, style = MaterialTheme.typography.labelMedium, color = k.muted)
        }
    }
}

/** What the round-trip fee does to R:R: a switch and the fee in percent, the two ratios, and a bar against the minimum. */
@Composable
private fun FeeCard(u: Ui, c: CalcResult, p: Params, on: Boolean, fee: String, onToggle: (Boolean) -> Unit, onFee: (String) -> Unit) {
    val k = LocalAndarzColors.current
    val pl = c.plan ?: return
    val before = pl.rr
    val after = c.rrAfterFees
    Column(
        Modifier.fillMaxWidth().archSurface(RoundedCornerShape(20.dp), k.sf1, k.divider, k.edge)
            .padding(start = 12.dp, end = 12.dp, bottom = if (on) 8.dp else 0.dp),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.weight(1f).heightIn(min = 48.dp)
                    .toggleable(value = on, role = Role.Switch, onValueChange = onToggle)
                    .semantics(mergeDescendants = true) { stateDescription = u.t(if (on) "a11y.toggleOn" else "a11y.toggleOff") },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AndarzSwitch(checked = on, onCheckedChange = null)
                Text(u.t("app.calc.fee.switch"), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700), modifier = Modifier.weight(1f))
            }
            if (on) FeeInput(u, fee, onFee)
        }
        if (on && before != null && after != null) {
            val feeUsd = pl.notionalMax?.let { n -> c.feePct?.let { f -> n * f } }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    u.t("app.calc.fee.rr", "from" to u.num(before), "to" to u.num(after)),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700), modifier = Modifier.weight(1f),
                )
                if (feeUsd != null) Text(u.t("app.calc.fee.total", "usd" to u.money(feeUsd)), style = MaterialTheme.typography.labelMedium, color = k.muted)
            }
            val minText = u.num(p.rrMin, 2, trim = true)
            FeeBar(
                before = before, after = after, min = p.rrMin,
                minLabel = u.t("app.calc.min", "v" to minText),
                description = u.t("app.calc.fee.bar", "from" to u.num(before), "to" to u.num(after), "min" to minText),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun FeeInput(u: Ui, fee: String, onFee: (String) -> Unit) {
    val k = LocalAndarzColors.current
    BasicTextField(
        value = fee, onValueChange = onFee, singleLine = true,
        textStyle = figure(15).copy(color = k.text, textDirection = TextDirection.Ltr, textAlign = TextAlign.Right),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        cursorBrush = SolidColor(k.lapis),
        modifier = Modifier.height(48.dp).semantics { contentDescription = u.t("app.calc.fee.input") },
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxHeight().background(k.alt, RoundedCornerShape(14.dp)).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(Modifier.width(48.dp)) { inner() }
                Text(pctSign(u), style = MaterialTheme.typography.bodyMedium, color = k.muted)
            }
        },
    )
}

/**
 * R:R before and after the fee against the minimum: a bar from 0 to twice the minimum, red below the minimum and
 * turquoise above it, a dashed line at the minimum, a ring where R:R stood and a dot where the fee leaves it. Drawn
 * left to right whatever the layout direction; the description carries the numbers.
 */
@Composable
private fun FeeBar(before: Double, after: Double, min: Double, minLabel: String, description: String, modifier: Modifier = Modifier) {
    val k = LocalAndarzColors.current
    val tone = if (after >= min) k.fzG else k.anG
    val scale = if (min > 0) min * 2 else maxOf(before, after, 1.0) * 1.5
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Ltr,
        LocalDensity provides Density(LocalDensity.current.density, 1f),
    ) {
        Box(modifier.fillMaxWidth().height(30.dp).clearAndSetSemantics { contentDescription = description }) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val mid = w / 2f
                val top = 4.dp.toPx()
                val h = 8.dp.toPx()
                val r = CornerRadius(4.dp.toPx())
                val z = CornerRadius.Zero
                drawPath(Path().apply { addRoundRect(RoundRect(Rect(0f, top, mid, top + h), r, z, z, r)) }, k.anSoft)
                drawPath(Path().apply { addRoundRect(RoundRect(Rect(mid, top, w, top + h), z, r, r, z)) }, k.fzSoft)
                drawLine(
                    k.outline, Offset(mid, 0f), Offset(mid, 16.dp.toPx()), strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
                )
                val xb = w * (before / scale).coerceIn(0.0, 1.0).toFloat()
                val xa = w * (after / scale).coerceIn(0.0, 1.0).toFloat()
                val cy = 8.dp.toPx()
                drawCircle(k.sf1, 8.dp.toPx(), Offset(xb, cy))
                drawCircle(tone, 7.dp.toPx(), Offset(xb, cy), style = Stroke(2.dp.toPx()))
                drawCircle(tone, 4.dp.toPx(), Offset(xa, cy))
            }
            Text(
                minLabel, modifier = Modifier.align(Alignment.BottomCenter),
                style = MaterialTheme.typography.labelMedium, color = k.muted, maxLines = 1, softWrap = false,
            )
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
