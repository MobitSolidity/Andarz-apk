package ir.andarz.app.ui.wizard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.components.AndarzSwitch
import ir.andarz.app.ui.components.ArchCard
import ir.andarz.app.ui.components.ArchShapes
import ir.andarz.app.ui.components.ArchTile
import ir.andarz.app.ui.components.ChecklistRow
import ir.andarz.app.ui.components.FilterChip
import ir.andarz.app.ui.components.FitText
import ir.andarz.app.ui.components.KeyValue
import ir.andarz.app.ui.components.LoadingState
import ir.andarz.app.ui.components.LockBanner
import ir.andarz.app.ui.components.NumberField
import ir.andarz.app.ui.components.OutlinedButton
import ir.andarz.app.ui.components.SectionCard
import ir.andarz.app.ui.components.SwitchRow
import ir.andarz.app.ui.components.TagChip
import ir.andarz.app.ui.components.Text
import ir.andarz.app.ui.components.TextButton
import ir.andarz.app.ui.components.Verdict
import ir.andarz.app.ui.components.VerdictBanner
import ir.andarz.app.ui.components.archSurface
import ir.andarz.app.ui.icons.Glyph
import ir.andarz.app.ui.icons.GlyphIcon
import ir.andarz.app.ui.journal.LockTexts
import ir.andarz.app.ui.theme.AndarzFonts
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui
import ir.andarz.data.db.SetupEntity
import ir.andarz.data.db.TradeEntity
import ir.andarz.engine.Advisor
import ir.andarz.engine.DashStatus
import ir.andarz.engine.Fmt
import ir.andarz.engine.GateReason
import ir.andarz.engine.Params
import ir.andarz.engine.Plan
import ir.andarz.engine.Planner
import ir.andarz.engine.Side
import ir.andarz.engine.Snapshot
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private const val STEPS = 5
private const val DOT = " · "
private val CardShape = RoundedCornerShape(18.dp)

/** The five-step wizard of 5.3. Every number comes from [WizardViewModel.calc], i.e. from the engine. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardScreen(pledgeSigned: Boolean, onDone: (String) -> Unit, onCancel: () -> Unit, vm: WizardViewModel = hiltViewModel()) {
    // D-91: a working tool shows the numbers being typed, whatever the hide setting.
    val u = ui().unmasked()
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
    val scroll = rememberScrollState()
    LaunchedEffect(d.step) { scroll.scrollTo(0) }

    val step = d.step
    val blocked = (step == 1 && (locked || !pledgeSigned)) ||
        (step == 2 && (d.symbol.isBlank() || d.setupId == null || (c.counterTrend && d.counterTrendReason.isBlank())))
    val action = when {
        step < STEPS -> FooterAction(u.t("common.next"), !blocked, true) { vm.update { it.copy(step = it.step + 1) } }
        locked -> null
        c.allowed -> FooterAction(u.t("wiz.commit.enter"), true, false) { vm.savePlan(rejected = false, thenEnter = true) }
        c.numbersValid -> FooterAction(u.t("wiz.commit.reject"), true, false) { vm.savePlan(rejected = true, thenEnter = false) }
        else -> null
    }

    Column(Modifier.fillMaxSize()) {
        WizardHeader(u, d, onClose = onCancel, onToggleDemo = { vm.update { it.copy(demo = !it.demo) } })
        StepDashes(u, step)
        if (step >= 2) VerdictBar(u, c, locked, p, expandable = step != 4, modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp))
        Column(
            Modifier.weight(1f).verticalScroll(scroll).padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (step == 4) 8.dp else 12.dp),
        ) {
            when (step) {
                1 -> StepGate(u, snap, pledgeSigned)
                2 -> StepSetup(u, vm, d, c, setups.filter { !it.archived }, watch.map { it.symbol }, snap?.governor?.reductions.orEmpty().map { LockTexts.reduction(u, it) })
                3 -> StepChecklist(u, vm, d, c)
                4 -> StepNumbers(u, vm, d, c, p, snap?.equity?.now)
                5 -> StepReview(u, vm, d, c, p, locked, onCancel)
            }
        }
        WizardFooter(
            backIcon = step > 1,
            backLabel = u.t(if (step == 1) "common.cancel" else "common.previous"),
            onBack = { if (step == 1) onCancel() else vm.update { it.copy(step = it.step - 1) } },
            action = action,
        )
    }

    pending?.let { t ->
        ModalBottomSheet(onDismissRequest = { vm.pendingEntry.value = null }) { ConfirmSheet(u, vm, t, d) }
    }
}

private fun stepKey(n: Int) = when (n) {
    1 -> "wiz.step.gate"; 2 -> "wiz.step.setup"; 3 -> "wiz.step.checklist"; 4 -> "wiz.step.numbers"; else -> "wiz.step.review"
}

// ---------------------------------------------------------------------------------------------------------------
// Frame: header, progress dashes, verdict bar, footer
// ---------------------------------------------------------------------------------------------------------------

/** The 52 dp header: close at the start edge, the title with the step line, and a menu holding the demo switch. */
@Composable
private fun WizardHeader(u: Ui, d: WizardDraft, onClose: () -> Unit, onToggleDemo: () -> Unit) {
    val k = LocalAndarzColors.current
    var menu by remember { mutableStateOf(false) }
    val subtitle = u.t("wiz.step.n", "n" to u.int(d.step), "total" to u.int(STEPS)) + DOT + u.t(stepKey(d.step)) +
        (if (d.demo) DOT + u.t("common.demo") else "")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            GlyphIcon(Glyph.X, u.t("common.close"), tint = k.text, size = 24.dp, strokeWidth = 2f)
        }
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(u.t("wiz.title"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = k.muted)
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) {
                GlyphIcon(Glyph.DOTS_V, u.t("app.wizard.menu"), tint = k.muted, size = 22.dp, strokeWidth = 2f)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(u.t("wiz.demo.toggle"), style = MaterialTheme.typography.bodyMedium)
                            Text(u.t("wiz.demo.hint"), style = MaterialTheme.typography.labelMedium, color = k.muted)
                        }
                    },
                    trailingIcon = { AndarzSwitch(checked = d.demo, onCheckedChange = null) },
                    onClick = onToggleDemo,
                    modifier = Modifier.semantics { stateDescription = u.t(if (d.demo) "a11y.toggleOn" else "a11y.toggleOff") },
                )
            }
        }
    }
}

/** Five 4 dp dashes: the finished and the current steps in turquoise, the rest on the track. */
@Composable
private fun StepDashes(u: Ui, step: Int) {
    val k = LocalAndarzColors.current
    val description = u.t("a11y.progress", "n" to u.int(step), "total" to u.int(STEPS))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(4.dp)
            .semantics { contentDescription = description; role = Role.Image },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (i in 1..STEPS) {
            Box(Modifier.weight(1f).fillMaxHeight().background(if (i <= step) k.fzG else k.track, RoundedCornerShape(2.dp)))
        }
    }
}

private class BarPalette(val container: Color, val ink: Color, val tile: Color, val ring: Color)

@Composable
private fun barPalette(v: Verdict): BarPalette {
    val k = LocalAndarzColors.current
    return when (v) {
        Verdict.ALLOWED -> BarPalette(k.fzC, k.fz, k.fzSoft, k.fz.copy(alpha = 0.4f))
        Verdict.CAUTION -> BarPalette(k.sfC, k.sf, k.sfSoft, k.sfLine)
        Verdict.DENIED, Verdict.LOCKED -> BarPalette(k.anC, k.an, k.anSoft, k.anLine)
    }
}

private fun barGlyph(v: Verdict): Glyph = when (v) {
    Verdict.ALLOWED -> Glyph.CHECK
    Verdict.CAUTION -> Glyph.ALERT_CIRCLE
    Verdict.DENIED -> Glyph.X
    Verdict.LOCKED -> Glyph.LOCK
}

/**
 * The verdict bar that stays from step 2 on: a 48 dp row with the state tile, the verdict in one line (the reasons
 * when it is a no) and a chevron that opens the reasons. On the numbers step the reasons are listed below, so the
 * bar is a plain status there.
 */
@Composable
private fun VerdictBar(u: Ui, c: WizardResult, locked: Boolean, p: Params, expandable: Boolean, modifier: Modifier = Modifier) {
    val k = LocalAndarzColors.current
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
    val numbers = c.plan?.takeIf { state == Verdict.ALLOWED }?.let { pl ->
        u.t("wiz.calc.riskUsd") + " " + u.money(pl.riskUsd) + DOT + u.t("wiz.calc.marginPct") + " " + u.pct(pl.marginPct) + DOT + u.t("wiz.calc.liqX") + " " + u.mult(pl.liqX)
    }
    val why = if (state == Verdict.DENIED && expandable) c.reasons.map { reasonLine(u, it, c, p) } else emptyList()
    var open by rememberSaveable { mutableStateOf(false) }
    val palette = barPalette(state)
    val verdictState = u.t(
        when (state) {
            Verdict.ALLOWED -> "a11y.allowedState"; Verdict.CAUTION -> "a11y.cautionState"
            Verdict.DENIED -> "a11y.redState"; Verdict.LOCKED -> "a11y.lockedState"
        },
    )
    Column(
        modifier.fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite; stateDescription = verdictState }
            .archSurface(RoundedCornerShape(16.dp), palette.container, palette.ring),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp))
                .then(
                    if (why.isNotEmpty()) {
                        Modifier.clickable(role = Role.Button, onClickLabel = u.t(if (open) "common.less" else "common.details"), onClick = { open = !open })
                    } else {
                        Modifier
                    },
                )
                .padding(start = 12.dp, top = 4.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ArchTile(palette.tile, width = 24.dp, height = 28.dp, bottom = 7.dp) {
                GlyphIcon(barGlyph(state), null, tint = palette.ink, size = 14.dp, strokeWidth = 2.4f)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700), color = palette.ink)
                if (numbers != null) Text(numbers, style = MaterialTheme.typography.labelMedium, color = k.text)
            }
            if (why.isNotEmpty()) {
                GlyphIcon(Glyph.CHEVRON_DOWN, null, modifier = Modifier.rotate(if (open) 180f else 0f), tint = palette.ink, size = 22.dp, strokeWidth = 2f)
            }
        }
        if (open) why.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 46.dp, end = 12.dp, bottom = 8.dp))
        }
    }
}

private class FooterAction(val label: String, val enabled: Boolean, val arrow: Boolean, val onClick: () -> Unit)

/** The 72 dp bar: Back (outlined) and the primary action (filled, 1.4 times wider) on the `sf1` step. */
@Composable
private fun WizardFooter(backIcon: Boolean, backLabel: String, onBack: () -> Unit, action: FooterAction?) {
    val k = LocalAndarzColors.current
    Row(
        Modifier.fillMaxWidth().background(k.sf1)
            .drawBehind { drawLine(k.divider, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
            if (backIcon) {
                GlyphIcon(Glyph.ARROW_LEFT, null, size = 20.dp, strokeWidth = 2f)
                Spacer(Modifier.width(8.dp))
            }
            Text(backLabel)
        }
        if (action != null) {
            androidx.compose.material3.Button(
                onClick = action.onClick,
                enabled = action.enabled,
                modifier = Modifier.weight(1.4f).heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = k.fzFill, contentColor = k.onFz, disabledContainerColor = k.high, disabledContentColor = k.muted,
                ),
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) {
                ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W700)) {
                    Text(action.label)
                    if (action.arrow) {
                        Spacer(Modifier.width(8.dp))
                        GlyphIcon(Glyph.ARROW_RIGHT, null, size = 20.dp, strokeWidth = 2f)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Small shared pieces
// ---------------------------------------------------------------------------------------------------------------

private enum class NoticeTone { DANGER, CAUTION }

/** A message with a glyph and a word, never a colour alone: an arch tile, then the text, on the soft tone. */
@Composable
private fun Notice(text: String, tone: NoticeTone, modifier: Modifier = Modifier) {
    val k = LocalAndarzColors.current
    val danger = tone == NoticeTone.DANGER
    val container = if (danger) k.anC else k.sfC
    val ink = if (danger) k.an else k.sf
    val tile = if (danger) k.anSoft else k.sfSoft
    val ring = if (danger) k.anLine else k.sfLine
    Row(
        modifier.fillMaxWidth().archSurface(RoundedCornerShape(16.dp), container, ring).padding(start = 10.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArchTile(tile, width = 24.dp, height = 28.dp, bottom = 7.dp) {
            GlyphIcon(if (danger) Glyph.X else Glyph.ALERT_CIRCLE, null, tint = ink, size = 14.dp, strokeWidth = 2.4f)
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = ink, modifier = Modifier.weight(1f))
    }
}

/** A text input in the arch field of the canvas (the same anatomy as the number field): label above, value below. */
@Composable
private fun TextBox(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
) {
    val k = LocalAndarzColors.current
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxWidth()
            .onFocusChanged { focused = it.hasFocus }
            .drawBehind {
                if (focused) {
                    val g = 3.dp.toPx()
                    drawRoundRect(k.lapis, Offset(-g, -g), Size(size.width + 2 * g, size.height + 2 * g), CornerRadius(22.dp.toPx()), style = Stroke(2.dp.toPx()))
                }
            }
            .archSurface(ArchShapes.field, k.alt, k.outline)
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 8.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = k.muted)
        Box(Modifier.fillMaxWidth().heightIn(min = 30.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty() && placeholder != null) {
                Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = k.hmuted)
            }
            BasicTextField(
                value = value, onValueChange = onValue, singleLine = singleLine,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = k.text),
                cursorBrush = SolidColor(k.lapis),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(title: String, ids: List<String>, selected: String?, label: (String) -> String, onPick: (String) -> Unit) {
    val k = LocalAndarzColors.current
    Text(title, style = MaterialTheme.typography.labelLarge, color = k.muted, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ids.forEach { id -> FilterChip(selected = id == selected, onClick = { onPick(id) }, label = { Text(label(id)) }) }
    }
}

/** Reason detail with the numbers and the article (5.3 reasons list); the label and the fix are added by the callers. */
private fun reasonDetail(u: Ui, r: GateReason, c: WizardResult, p: Params): String = when (r) {
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

/** One line of the bar's expanded list: label, detail and fix. */
private fun reasonLine(u: Ui, r: GateReason, c: WizardResult, p: Params): String =
    "• " + u.t("wiz.gate.reason.${r.name}") + ": " + reasonDetail(u, r, c, p) + " " + u.t("wiz.gate.fix.${r.name}")

// ---------------------------------------------------------------------------------------------------------------
// Step 1: permission to enter
// ---------------------------------------------------------------------------------------------------------------

@Composable
private fun StepGate(u: Ui, snap: Snapshot?, pledgeSigned: Boolean) {
    if (snap == null) { LoadingState(); return }
    if (!pledgeSigned) Notice(u.t("err.noPledge"), NoticeTone.DANGER)
    val g = snap.governor
    if (g.locks.isNotEmpty()) {
        Text(u.t("wiz.gate.blocked"), style = MaterialTheme.typography.titleMedium)
        g.locks.forEach { l -> LockBanner(LockTexts.title(u, l), LockTexts.body(u, l, snap.params, g.tradesToday), LockTexts.until(u, l, Instant.now()), emptyList()) }
    } else {
        val red = snap.dashboard.status.let { s -> listOf("trades" to s.trades, "daily" to s.daily, "weekly" to s.weekly, "drawdown" to s.drawdown, "positions" to s.positions, "openRisk" to s.openRisk) }
            .filter { it.second != DashStatus.OK }
        if (red.isNotEmpty()) {
            red.forEach { Notice(u.t("today.status.${it.second.name}", "days" to u.int(snap.params.ddLockDays.toInt())), NoticeTone.DANGER) }
        } else {
            VerdictBanner(Verdict.ALLOWED, u.t("wiz.gate.fine"), null)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Step 2: setup and feeling
// ---------------------------------------------------------------------------------------------------------------

@Composable
private fun StepSetup(u: Ui, vm: WizardViewModel, d: WizardDraft, c: WizardResult, setups: List<SetupEntity>, watch: List<String>, reductions: List<String>) {
    val k = LocalAndarzColors.current
    var newSetup by rememberSaveable { mutableStateOf("") }
    ArchCard(Modifier.fillMaxWidth()) {
        TextBox(u.t("wiz.symbol"), d.symbol, { v -> vm.update { it.copy(symbol = v) } }, placeholder = u.t("wiz.symbol.hint"))
        if (d.symbol.isNotBlank() && watch.none { it.equals(d.symbol.trim(), ignoreCase = true) }) {
            Notice(u.t("wiz.symbol.notInList"), NoticeTone.CAUTION, Modifier.padding(top = 8.dp))
        }
        ChipGroup(u.t("wiz.side"), u.enumIds("side"), d.side.name, { u.enumLabel("side", it) }) { id -> vm.update { it.copy(side = Side.valueOf(id)) } }
        ChipGroup(u.t("wiz.kind"), u.enumIds("kind"), d.kind, { u.enumLabel("kind", it) }) { id -> vm.update { it.copy(kind = id) } }
        Text(u.t("wiz.kind.hint"), style = MaterialTheme.typography.labelMedium, color = k.muted, modifier = Modifier.padding(top = 6.dp))
    }
    ArchCard(Modifier.fillMaxWidth()) {
        ChipGroup(u.t("wiz.setup"), setups.map { it.id }, d.setupId, { id -> setups.first { it.id == id }.name }) { id -> vm.update { it.copy(setupId = id) } }
        if (setups.isEmpty()) Text(u.t("wiz.setup.none"), style = MaterialTheme.typography.labelMedium, color = k.muted, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextBox(u.t("wiz.setup.new"), newSetup, { newSetup = it }, modifier = Modifier.weight(1f))
            TextButton(onClick = { if (newSetup.isNotBlank()) { vm.createSetup(newSetup); newSetup = "" } }, enabled = newSetup.isNotBlank()) { Text(u.t("common.add")) }
        }
        if (d.setupId == null) Notice(u.t("err.setupRequired"), NoticeTone.CAUTION, Modifier.padding(top = 8.dp))
        if (reductions.isNotEmpty()) {
            FlowRow8 { reductions.forEach { TagChip(it, k.caution) } }
        }
    }
    ArchCard(Modifier.fillMaxWidth()) {
        ChipGroup(u.t("wiz.timeframe"), u.enumIds("timeframe"), d.timeframe, { u.enumLabel("timeframe", it) }) { id -> vm.update { it.copy(timeframe = id) } }
        ChipGroup(u.t("wiz.htf"), u.enumIds("htfTrend"), d.htfTrend, { u.enumLabel("htfTrend", it) }) { id -> vm.update { it.copy(htfTrend = id) } }
        if (c.counterTrend) {
            Notice(u.t("wiz.counterTrend"), NoticeTone.CAUTION, Modifier.padding(top = 10.dp))
            TextBox(u.t("wiz.counterTrend.reason"), d.counterTrendReason, { v -> vm.update { it.copy(counterTrendReason = v) } }, modifier = Modifier.padding(top = 8.dp), singleLine = false)
        }
    }
    ArchCard(Modifier.fillMaxWidth()) {
        ChipGroup(u.t("wiz.emotion"), u.enumIds("emotion"), d.emotion, { u.enumLabel("emotion", it) }) { id -> vm.update { it.copy(emotion = id) } }
        if (!c.calm) Notice(u.t("wiz.emotion.warn", "emotion" to u.enumLabel("emotion", d.emotion)), NoticeTone.CAUTION, Modifier.padding(top = 10.dp))
    }
}

/** A wrapping row with 8 dp gaps for the small tags. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow8(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

// ---------------------------------------------------------------------------------------------------------------
// Step 3: checklist
// ---------------------------------------------------------------------------------------------------------------

@Composable
private fun StepChecklist(u: Ui, vm: WizardViewModel, d: WizardDraft, c: WizardResult) {
    val k = LocalAndarzColors.current
    val items = u.content.arr("checklist")?.map { it.jsonObject }.orEmpty()
    Text(u.t("wiz.checklist.title"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    items.forEach { o ->
        val n = o["n"]!!.jsonPrimitive.content.toInt()
        val derived = n == 6 || n == 7
        val disabled = derived || (n == 8 && !c.calm)
        val checked = when (n) {
            6 -> c.item6
            7 -> c.item7
            else -> (d.manualMask shr (n - 1)) and 1 == 1 && !(n == 8 && !c.calm)
        }
        ChecklistRow(
            title = o["short"]!!.jsonPrimitive.content, detail = o["full"]!!.jsonPrimitive.content, checked = checked, enabled = !disabled,
            warning = if (n == 8 && !c.calm) u.t("wiz.emotion.warn", "emotion" to u.enumLabel("emotion", d.emotion)) else null,
            tag = if (derived) u.t("common.details") else null, onToggle = { vm.toggleItem(n) },
        )
    }
    Text(u.t("wiz.checklist.score", "n" to u.int(c.chk), "total" to u.int(items.size)), style = MaterialTheme.typography.titleMedium)
    Text(u.t(if (c.chk < items.size) "wiz.checklist.incomplete" else "wiz.checklist.rule"), style = MaterialTheme.typography.bodyMedium, color = k.muted)
}

// ---------------------------------------------------------------------------------------------------------------
// Step 4: the numbers
// ---------------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepNumbers(u: Ui, vm: WizardViewModel, d: WizardDraft, c: WizardResult, p: Params, ledgerEquity: Double?) {
    val k = LocalAndarzColors.current
    val err = { text: String, v: Double? -> if (text.isBlank()) null else if (Fmt.normalizeInput(text) == null) u.t("err.number") else if (v == null) u.t("err.positive") else null }
    NumberField(u.t("wiz.equity"), d.equity, { v -> vm.update { it.copy(equity = v) } }, error = err(d.equity, c.equity), hint = ledgerEquity?.let { u.t("wiz.equity.fromLedger", "v" to u.money(it)) })
    if (ledgerEquity != null && c.equity != null && abs(c.equity - ledgerEquity) > 0.01 * ledgerEquity) {
        Notice(u.t("wiz.equity.mismatch", "v" to u.money(ledgerEquity)), NoticeTone.CAUTION)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { vm.update { it.copy(equity = Fmt.number(ledgerEquity, 2, false, false, true)) } }) { Text(u.t("common.ok")) }
        }
    }

    val entryErr = err(d.entry, c.entry)
    val slErr = err(d.sl, c.sl) ?: when {
        c.entryEqSl -> u.t("err.entryEqSl")
        c.slWrong -> u.t("err.slDirection", "dir" to u.t("wiz.gate.slWhere.${d.side.name}"))
        else -> null
    }
    val tpErr = err(d.tp, c.tp) ?: if (c.tpWrong) u.t("err.tpDirection", "dir" to u.t("wiz.gate.tpWhere.${d.side.name}")) else null
    PriceCard(u, d, vm, entryErr, slErr, tpErr)
    listOfNotNull(entryErr, slErr, tpErr).forEach { Notice(it, NoticeTone.DANGER) }
    ir.andarz.app.ui.market.LivePriceChip(d.symbol, { v -> vm.update { it.copy(entry = v) } })

    Steppers(u, vm, d, c)

    LadderAndCalc(u, c)
    if (c.plan != null) Text(u.t("wiz.calc.note"), style = MaterialTheme.typography.labelMedium, color = k.muted, modifier = Modifier.padding(horizontal = 4.dp))

    c.advisor?.let { a -> AdvisorCard(u, vm, d, c, p, a) }

    if (c.numbersValid && c.reasons.isNotEmpty()) {
        Text(
            u.t("wiz.gate.reasons.title"),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W800),
            color = k.muted,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp).semantics { heading() },
        )
        c.reasons.forEach { r -> ReasonCard(u, r, c, p) }
    }

    ArchCard(Modifier.fillMaxWidth()) {
        Text(u.t("wiz.entryOrder"), style = MaterialTheme.typography.labelLarge, color = k.muted, modifier = Modifier.padding(bottom = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            u.enumIds("entryOrder").forEach { id -> FilterChip(selected = d.entryOrder == id, onClick = { vm.update { it.copy(entryOrder = id) } }, label = { Text(u.t("wiz.entryOrder.$id")) }) }
        }
        if (d.entryOrder == "MARKET_OTHER") Notice(u.t("wiz.entryOrder.warn"), NoticeTone.CAUTION, Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(4.dp))
        SwitchRow(u.t("wiz.slTpOnExchange"), d.slTpOnExchange, { v -> vm.update { it.copy(slTpOnExchange = v) } })
        SwitchRow(u.t("wiz.canMonitor"), d.canMonitor, { v -> vm.update { it.copy(canMonitor = v) } })
        SwitchRow(u.t("wiz.isolated"), d.isolated, { v -> vm.update { it.copy(isolated = v) } })
    }
}

/** Plex 700 figures of the numbers step (the Peyda family has no digits of its own in the canvas). */
@Composable
private fun figureStyle(size: Int): TextStyle = MaterialTheme.typography.bodyMedium.copy(
    fontFamily = AndarzFonts.plex, fontWeight = FontWeight.W700, fontSize = size.sp, lineHeight = (size * 1.4f).sp, fontFeatureSettings = "tnum",
)

private class PriceField(val label: String, val value: String, val glyph: Glyph, val tint: Color, val error: String?, val onChange: (String) -> Unit)

/** Entry, stop loss and take profit in one card of three equal columns (stacked at large font sizes). */
@Composable
private fun PriceCard(u: Ui, d: WizardDraft, vm: WizardViewModel, entryErr: String?, slErr: String?, tpErr: String?) {
    val k = LocalAndarzColors.current
    val stacked = LocalDensity.current.fontScale > 1.3f
    val fields = listOf(
        PriceField(u.t("wiz.entry"), d.entry, Glyph.DIAMOND, k.text, entryErr) { v -> vm.update { it.copy(entry = v) } },
        PriceField(u.t("app.wizard.sl"), d.sl, Glyph.TRI_DOWN, k.an, slErr) { v -> vm.update { it.copy(sl = v) } },
        PriceField(u.t("app.wizard.tp"), d.tp, Glyph.TRI_UP, k.fz, tpErr) { v -> vm.update { it.copy(tp = v) } },
    )
    Column(Modifier.fillMaxWidth().archSurface(CardShape, k.sf1, k.divider, k.edge)) {
        if (stacked) {
            fields.forEachIndexed { i, f ->
                if (i > 0) HorizontalDivider(color = k.divider)
                PriceColumn(f, Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                fields.forEachIndexed { i, f ->
                    if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(k.divider))
                    PriceColumn(f, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PriceColumn(f: PriceField, modifier: Modifier) {
    val k = LocalAndarzColors.current
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier.heightIn(min = 62.dp)
            .drawBehind {
                if (focused) drawRect(k.lapis, Offset(14.dp.toPx(), size.height - 3.dp.toPx()), Size(size.width - 28.dp.toPx(), 2.dp.toPx()))
            }
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { if (f.error != null) error(f.error) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            GlyphIcon(f.glyph, null, tint = f.tint, size = 12.dp, strokeWidth = 2.2f)
            Text(f.label, style = MaterialTheme.typography.labelMedium, color = if (f.error != null) k.an else k.muted, maxLines = 2)
        }
        BasicTextField(
            value = f.value, onValueChange = f.onChange, singleLine = true,
            textStyle = figureStyle(20).copy(color = k.text),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            cursorBrush = SolidColor(k.lapis),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        )
    }
}

private class StepperSpec(val label: String, val value: String, val decLabel: String, val incLabel: String, val onDec: () -> Unit, val onInc: () -> Unit)

/** Leverage and risk as two steppers: a 48 dp minus tile, the label over the figure, a 48 dp plus tile. */
@Composable
private fun Steppers(u: Ui, vm: WizardViewModel, d: WizardDraft, c: WizardResult) {
    val k = LocalAndarzColors.current
    val stacked = LocalDensity.current.fontScale > 1.3f
    val lev = (c.lev ?: Fmt.normalizeInput(d.lev) ?: 1.0).roundToInt()
    val riskPct = c.riskPct * 100
    val capPct = c.maxRisk * 100
    fun setLev(v: Int) = vm.update { it.copy(lev = v.coerceIn(1, 125).toString()) }
    fun setRisk(v: Double) {
        val hi = capPct
        val lo = minOf(0.1, hi)
        vm.update { it.copy(riskPct = Fmt.number(((v.coerceIn(lo, hi)) * 100).roundToInt() / 100.0, 2, false, false, true)) }
    }
    val levSpec = StepperSpec(u.t("wiz.leverage"), u.mult(c.lev), u.t("app.wizard.lev.dec"), u.t("app.wizard.lev.inc"), { setLev(lev - 1) }, { setLev(lev + 1) })
    val riskSpec = StepperSpec(u.t("app.wizard.risk"), u.pct(c.riskPct, 2), u.t("app.wizard.risk.dec"), u.t("app.wizard.risk.inc"), { setRisk(riskPct - 0.25) }, { setRisk(riskPct + 0.25) })
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StepperCard(levSpec, Modifier.fillMaxWidth())
            StepperCard(riskSpec, Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepperCard(levSpec, Modifier.weight(1f))
            StepperCard(riskSpec, Modifier.weight(1f))
        }
    }
    Text(
        u.t("wiz.riskPct") + ": " + u.t("today.cap", "v" to u.pct(c.maxRisk, 2)),
        style = MaterialTheme.typography.labelMedium, color = k.muted, modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun StepperCard(s: StepperSpec, modifier: Modifier) {
    val k = LocalAndarzColors.current
    Row(
        modifier.heightIn(min = 56.dp).archSurface(CardShape, k.sf1, k.divider).padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StepTile(Glyph.MINUS, s.decLabel, s.onDec)
        Column(Modifier.weight(1f).padding(horizontal = 2.dp).semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
            FitText(s.label, MaterialTheme.typography.labelMedium, color = k.muted)
            FitText(s.value, figureStyle(19), color = k.text)
        }
        StepTile(Glyph.PLUS, s.incLabel, s.onInc)
    }
}

@Composable
private fun StepTile(glyph: Glyph, label: String, onClick: () -> Unit) {
    val k = LocalAndarzColors.current
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(k.high).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph, label, tint = k.text, size = 20.dp, strokeWidth = 2.2f) }
}

// ---- the price ladder and the live calculation

private const val LIQ_NONE = 0
private const val LIQ_NEAR = 1
private const val LIQ_FAR = 2

/** Where the lines of the ladder sit on its 40 by 188 grid: the board's scale is 36 units per R, reward above. */
private class LadderGeometry(val tpY: Float, val entryY: Float, val slY: Float, val liqMode: Int, val liqY: Float, val liqInside: Boolean)

private fun ladderGeometry(rr: Double?, liqX: Double?): LadderGeometry {
    val r = (rr ?: 1.0).coerceIn(0.3, 7.0).toFloat()
    val scale = minOf(36f, 82.8f / (r + 1f))
    val tpY = 24f
    val entryY = tpY + r * scale
    val slY = entryY + scale
    if (liqX == null) return LadderGeometry(tpY, entryY, slY, LIQ_NONE, 0f, false)
    val gap = maxOf((liqX.toFloat() - 1f) * scale, 36f)
    return if (gap <= 53.2f) LadderGeometry(tpY, entryY, slY, LIQ_NEAR, slY + gap, liqX < 1.0)
    else LadderGeometry(tpY, entryY, slY, LIQ_FAR, slY + 53.2f, false)
}

/** The ladder next to the calculation at 390 dp and wider, one above the other on narrower or enlarged screens. */
@Composable
private fun LadderAndCalc(u: Ui, c: WizardResult) {
    val plan = c.plan
    val entry = c.entry
    val sl = c.sl
    val tp = c.tp
    if (plan == null || entry == null || sl == null || tp == null) {
        FillHint(u.t("app.wizard.fill"))
        return
    }
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 390.dp && fontScale <= 1.15f) {
            Row(Modifier.fillMaxWidth().height(188.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LadderCard(u, entry, sl, tp, plan, Modifier.width(156.dp).fillMaxHeight())
                CalcCard(u, c, plan, Modifier.weight(1f).fillMaxHeight(), tight = true)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LadderCard(u, entry, sl, tp, plan, Modifier.fillMaxWidth().height(188.dp))
                CalcCard(u, c, plan, Modifier.fillMaxWidth(), tight = false)
            }
        }
    }
}

@Composable
private fun FillHint(text: String) {
    val k = LocalAndarzColors.current
    Row(
        Modifier.fillMaxWidth().archSurface(CardShape, k.sf1, k.divider).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArchTile(k.alt, width = 24.dp, height = 28.dp, bottom = 7.dp) { GlyphIcon(Glyph.INFO, null, tint = k.muted, size = 14.dp, strokeWidth = 2f) }
        Text(text, style = MaterialTheme.typography.bodySmall, color = k.muted, modifier = Modifier.weight(1f))
    }
}

private class LadderLabel(val top: Float, val height: Float, val color: Color, val first: String, val second: String?)

@Composable
private fun LadderCard(u: Ui, entry: Double, sl: Double, tp: Double, plan: Plan, modifier: Modifier) {
    val k = LocalAndarzColors.current
    val geometry = remember(plan.rr, plan.liqX) { ladderGeometry(plan.rr, plan.liqX) }
    val liq = plan.liq
    val tpMove = u.pct((tp - entry) / entry, 2, signed = true) + DOT + u.rChip(plan.rr)
    val slMove = u.pct((sl - entry) / entry, 2, signed = true) + DOT + u.rChip(-1.0)
    val liqMove = liq?.let { u.pct((it - entry) / entry, 2, signed = true) }
    val description = u.t(
        "app.wizard.ladder.desc",
        "tp" to u.price(tp), "tpR" to u.rChip(plan.rr), "entry" to u.price(entry), "sl" to u.price(sl), "slR" to u.rChip(-1.0), "liq" to u.price(liq),
    )
    val labels = buildList<LadderLabel> {
        add(LadderLabel(geometry.tpY - 17f, 34f, k.fz, u.t("app.wizard.tp.short") + " " + u.price(tp), tpMove))
        add(LadderLabel(geometry.entryY - 8.5f, 17f, k.text, u.t("app.wizard.ladder.entry") + " " + u.price(entry), null))
        add(LadderLabel(geometry.slY - 17f, 34f, k.an, u.t("app.wizard.sl.short") + " " + u.price(sl), slMove))
        if (geometry.liqMode != LIQ_NONE && liq != null) {
            add(LadderLabel(geometry.liqY - 17f, 34f, k.an, u.t("wiz.gate.reason.LIQUIDATION") + " " + u.price(liq), liqMove))
        }
    }
    val density = LocalDensity.current
    val fixed = remember(density.density) { Density(density.density, 1f) }
    Box(
        modifier.archSurface(CardShape, k.sf1, k.divider).clearAndSetSemantics { contentDescription = description; role = Role.Image },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(Modifier.size(width = 156.dp, height = 188.dp)) {
            LadderCanvas(geometry, Modifier.padding(start = 6.dp).size(width = 40.dp, height = 188.dp))
            // The labels are part of a diagram with fixed geometry, so they keep a fixed text size; every figure
            // they carry is also in the calculation card and in the description of the whole ladder.
            CompositionLocalProvider(LocalDensity provides fixed) {
                var floor = 0f
                labels.forEach { l ->
                    val top = maxOf(l.top, floor)
                    floor = top + l.height + 1f
                    Column(Modifier.offset(x = 52.dp, y = top.dp).width(98.dp)) {
                        FitText(l.first, MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700, lineHeight = 17.sp), color = l.color, textAlign = TextAlign.Start)
                        if (l.second != null) {
                            FitText(l.second, MaterialTheme.typography.labelMedium.copy(lineHeight = 17.sp), color = k.muted, textAlign = TextAlign.Start)
                        }
                    }
                }
            }
        }
    }
}

/** The capsules of the profit and loss zones, the lines, the entry dot and the liquidation marker. */
@Composable
private fun LadderCanvas(g: LadderGeometry, modifier: Modifier) {
    val k = LocalAndarzColors.current
    Canvas(modifier) {
        val s = size.width / 40f
        val zoneStroke = Stroke(width = 1.5f * s, join = StrokeJoin.Round)
        val dash = PathEffect.dashPathEffect(floatArrayOf(2f * s, 4f * s), 0f)
        fun line(color: Color, x1: Float, y1: Float, x2: Float, y2: Float, width: Float, effect: PathEffect? = null) {
            drawLine(color, Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), strokeWidth = width * s, cap = StrokeCap.Round, pathEffect = effect)
        }

        val reward = Path().apply {
            moveTo(10f * s, g.entryY * s)
            lineTo(10f * s, (g.tpY + 10f) * s)
            arcTo(Rect(10f * s, g.tpY * s, 30f * s, (g.tpY + 20f) * s), 180f, 180f, false)
            lineTo(30f * s, g.entryY * s)
            close()
        }
        drawPath(reward, k.fzC)
        drawPath(reward, k.fzG, style = zoneStroke)

        val risk = Path().apply {
            moveTo(10f * s, g.entryY * s)
            lineTo(30f * s, g.entryY * s)
            lineTo(30f * s, (g.slY - 10f) * s)
            arcTo(Rect(10f * s, (g.slY - 20f) * s, 30f * s, g.slY * s), 0f, 180f, false)
            close()
        }
        drawPath(risk, k.anC)
        drawPath(risk, k.anG, style = zoneStroke)

        line(k.fzG, 5f, g.tpY, 35f, g.tpY, 1.5f)
        line(k.text, 5f, g.entryY, 35f, g.entryY, 2f)
        drawCircle(k.sf1, 6.5f * s, Offset(20f * s, g.entryY * s))
        drawCircle(k.text, 3.5f * s, Offset(20f * s, g.entryY * s))
        line(k.anG, 5f, g.slY, 35f, g.slY, 1.5f)

        fun diamond(cy: Float) {
            val shape = Path().apply {
                moveTo(20f * s, (cy - 6f) * s)
                lineTo(25.5f * s, cy * s)
                lineTo(20f * s, (cy + 6f) * s)
                lineTo(14.5f * s, cy * s)
                close()
            }
            drawPath(shape, if (g.liqInside) k.an else k.sf1)
            drawPath(shape, k.anG, style = Stroke(width = 2f * s, join = StrokeJoin.Round))
        }
        when (g.liqMode) {
            LIQ_NEAR -> {
                line(k.anG, 20f, g.slY + 2.2f, 20f, g.liqY - 8f, 2f, dash)
                diamond(g.liqY)
            }
            LIQ_FAR -> {
                line(k.anG, 20f, g.slY + 2.2f, 20f, g.slY + 14.2f, 2f, dash)
                line(k.muted, 13f, g.slY + 21.2f, 27f, g.slY + 16.7f, 1.5f)
                line(k.muted, 13f, g.slY + 27.2f, 27f, g.slY + 22.7f, 1.5f)
                line(k.anG, 20f, g.slY + 30.2f, 20f, g.slY + 46.2f, 2f, dash)
                diamond(g.liqY)
            }
        }
    }
}

/** The seven live figures with dotted leaders; a row that breaks a rule is on the red container with an X before the value. */
@Composable
private fun CalcCard(u: Ui, c: WizardResult, plan: Plan, modifier: Modifier, tight: Boolean) {
    val k = LocalAndarzColors.current
    val flagRr = GateReason.RR in c.reasons
    val flagMargin = GateReason.MARGIN in c.reasons
    val flagLiq = GateReason.LIQUIDATION in c.reasons
    Column(
        modifier.archSurface(CardShape, k.sf1, k.divider).padding(horizontal = 4.dp, vertical = 6.dp).semantics { contentDescription = u.t("wiz.calc.title") },
        verticalArrangement = if (tight) Arrangement.SpaceBetween else Arrangement.spacedBy(2.dp),
    ) {
        CalcRow(u.t("wiz.calc.sld"), u.pct(plan.sld), false)
        CalcRow(u.t("wiz.calc.rr"), u.num(plan.rr), flagRr)
        CalcRow(u.t("app.wizard.calc.riskUsd"), u.money(plan.riskUsd), false)
        CalcRow(u.t("app.wizard.calc.notional"), u.money(plan.notionalMax), false)
        CalcRow(u.t("app.wizard.calc.margin"), u.money(plan.margin, if (tight) 0 else 2) + " (" + u.pct(plan.marginPct, 1) + ")", flagMargin)
        CalcRow(u.t("app.wizard.calc.liq"), u.price(plan.liq), flagLiq)
        CalcRow(u.t("wiz.calc.liqX"), u.mult(plan.liqX), flagLiq)
    }
}

@Composable
private fun CalcRow(label: String, value: String, flagged: Boolean) {
    val k = LocalAndarzColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 24.dp)
            .then(if (flagged) Modifier.background(k.anC, RoundedCornerShape(8.dp)) else Modifier)
            .padding(horizontal = 6.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label, maxLines = 1, softWrap = false,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (flagged) FontWeight.W700 else FontWeight.W400),
            color = if (flagged) k.an else k.muted,
        )
        Leader(if (flagged) k.anG.copy(alpha = 0.6f) else k.outline, Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (flagged) GlyphIcon(Glyph.X, null, tint = k.an, size = 14.dp, strokeWidth = 2.6f)
            Text(value, maxLines = 1, softWrap = false, style = figureStyle(13), color = if (flagged) k.an else k.text)
        }
    }
}

/** The dotted line between a label and its figure, sitting on the text baseline. */
@Composable
private fun Leader(color: Color, modifier: Modifier) {
    Canvas(modifier.widthIn(min = 6.dp).height(2.dp).offset(y = 4.dp)) {
        val w = 1.5.dp.toPx()
        drawLine(
            color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = w, cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, 3.dp.toPx()), 0f),
        )
    }
}

// ---- leverage advisor

@Composable
private fun AdvisorCard(u: Ui, vm: WizardViewModel, d: WizardDraft, c: WizardResult, p: Params, a: Advisor) {
    val k = LocalAndarzColors.current
    val rec = a.recommended
    val fmin = a.feasibleMin
    val fmax = a.feasibleMax
    val eq = c.equity
    val e = c.entry
    val s = c.sl
    val t = c.tp
    Column(Modifier.fillMaxWidth().background(k.lapisC, CardShape).padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlyphIcon(if (rec != null) Glyph.INFO else Glyph.X, null, tint = if (rec != null) k.lapis else k.an, size = 22.dp, strokeWidth = 2f)
            if (rec != null) {
                val margin = if (eq != null && e != null && s != null && t != null) Planner.plan(d.side, eq, e, s, t, rec.toDouble(), p, c.riskPct).marginPct else null
                val range = if (fmin != null && fmax != null) u.t("wiz.adv.range", "min" to u.int(fmin), "max" to u.int(fmax)) else null
                val suggestion = u.t("app.wizard.adv.recommended", "lev" to u.int(rec), "margin" to u.pct(margin, 0))
                Text(listOfNotNull(range, suggestion).joinToString(DOT), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                UseButton(u.t("wiz.adv.use")) { vm.update { it.copy(lev = rec.toString()) } }
            } else {
                Text(u.t("wiz.adv.none"), style = MaterialTheme.typography.bodySmall, color = k.an, modifier = Modifier.weight(1f))
            }
        }
        if (rec != null && fmin != null && fmax != null) {
            val railMax = maxOf(12.0, p.levMax + 2.0, c.lev ?: 1.0)
            AdvisorRail(u, c.lev, rec, fmin, fmax, railMax, Modifier.padding(top = 2.dp, end = 12.dp))
        }
        if (a.lMinReal > p.levMax) Text(u.t("wiz.adv.tight", "lev" to u.int(ceil(a.lMinReal).toInt())), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 4.dp, bottom = 2.dp))
        if (a.lMaxLiqReal < p.levMax) Text(u.t("wiz.adv.far", "lev" to u.int(floor(a.lMaxLiqReal).toInt())), style = MaterialTheme.typography.labelMedium, color = k.muted, modifier = Modifier.padding(end = 4.dp, bottom = 2.dp))
    }
}

@Composable
private fun UseButton(label: String, onClick: () -> Unit) {
    val k = LocalAndarzColors.current
    Box(
        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(k.lapis).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700), color = k.bg) }
}

/**
 * The leverage rail: the allowed range as a bar, the suggestion as a turquoise dot with a check, the current value as
 * a diamond (red when it is outside the range). Larger values run toward the end edge.
 */
@Composable
private fun AdvisorRail(u: Ui, cur: Double?, rec: Int, lo: Int, hi: Int, railMax: Double, modifier: Modifier) {
    val k = LocalAndarzColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val inRange = cur != null && cur >= lo && cur <= hi
    val sameAsRec = cur != null && cur.roundToInt() == rec
    val state = when {
        cur == null -> u.t("app.wizard.rail.unknown")
        inRange -> u.t("app.wizard.rail.in")
        else -> u.t("app.wizard.rail.out")
    }
    val description = u.t(
        "app.wizard.rail.desc",
        "max" to u.int(railMax.roundToInt()), "min" to u.int(lo), "hi" to u.int(hi), "cur" to u.mult(cur), "state" to state, "rec" to u.int(rec),
    )
    val density = LocalDensity.current
    val fixed = remember(density.density) { Density(density.density, 1f) }
    fun frac(v: Double): Float = ((v - 1.0) / (railMax - 1.0)).coerceIn(0.0, 1.0).toFloat()
    BoxWithConstraints(modifier.fillMaxWidth().height(42.dp).padding(start = 12.dp).clearAndSetSemantics { contentDescription = description; role = Role.Image }) {
        val width = maxWidth
        fun centerX(v: Double): Dp = width * (if (rtl) 1f - frac(v) else frac(v))
        Canvas(Modifier.matchParentSize()) {
            fun px(v: Double): Float = size.width * (if (rtl) 1f - frac(v) else frac(v))
            val trackY = 18.dp.toPx()
            val trackH = 6.dp.toPx()
            val radius = CornerRadius(3.dp.toPx())
            val cy = 21.dp.toPx()
            drawRoundRect(k.lapisSoft, Offset(0f, trackY), Size(size.width, trackH), radius)
            val a = px(lo.toDouble())
            val b = px(hi.toDouble())
            drawRoundRect(k.lapis, Offset(minOf(a, b), trackY), Size(abs(b - a), trackH), radius)
            if (cur != null && !sameAsRec) {
                val x = px(cur)
                rotate(45f, Offset(x, cy)) {
                    drawRoundRect(k.lapisC, Offset(x - 9.dp.toPx(), cy - 9.dp.toPx()), Size(18.dp.toPx(), 18.dp.toPx()), CornerRadius(5.dp.toPx()))
                    drawRoundRect(if (inRange) k.text else k.anG, Offset(x - 6.dp.toPx(), cy - 6.dp.toPx()), Size(12.dp.toPx(), 12.dp.toPx()), CornerRadius(2.dp.toPx()))
                }
            }
            val rx = px(rec.toDouble())
            drawCircle(k.lapisC, 12.dp.toPx(), Offset(rx, cy))
            drawCircle(k.fzFill, 9.dp.toPx(), Offset(rx, cy))
            val check = Path().apply {
                moveTo(rx - 4.dp.toPx(), cy)
                lineTo(rx - 1.dp.toPx(), cy + 3.dp.toPx())
                lineTo(rx + 4.dp.toPx(), cy - 3.dp.toPx())
            }
            drawPath(check, k.onFz, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        CompositionLocalProvider(LocalDensity provides fixed) {
            val labelStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700, lineHeight = 16.sp)
            if (cur != null && !sameAsRec) {
                RailLabel(u.mult(cur), centerX(cur), 0.dp, if (inRange) k.text else k.an, labelStyle)
            }
            RailLabel(u.mult(rec.toDouble()), centerX(rec.toDouble()), 0.dp, k.fz, labelStyle)
            val edge = MaterialTheme.typography.labelMedium.copy(lineHeight = 16.sp)
            RailLabel(u.mult(lo.toDouble()), centerX(lo.toDouble()), 27.dp, k.lapis, edge)
            if (hi != lo) RailLabel(u.mult(hi.toDouble()), centerX(hi.toDouble()), 27.dp, k.lapis, edge)
        }
    }
}

@Composable
private fun RailLabel(text: String, centerX: Dp, y: Dp, color: Color, style: TextStyle) {
    Box(Modifier.absoluteOffset(x = centerX - 18.dp, y = y).width(36.dp), contentAlignment = Alignment.TopCenter) {
        Text(text, style = style, color = color, maxLines = 1, softWrap = false)
    }
}

// ---- the reasons

@Composable
private fun ReasonCard(u: Ui, r: GateReason, c: WizardResult, p: Params) {
    val k = LocalAndarzColors.current
    Column(
        Modifier.fillMaxWidth().archSurface(CardShape, k.sf1, k.divider).padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 10.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(Modifier.heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ArchTile(k.anC, width = 20.dp, height = 24.dp, bottom = 6.dp) { GlyphIcon(Glyph.X, null, tint = k.an, size = 12.dp, strokeWidth = 2.6f) }
            Text(u.t("wiz.gate.reason.${r.name}"), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.W700), color = k.an)
        }
        Text(reasonDetail(u, r, c, p), style = MaterialTheme.typography.bodySmall)
        Text(u.t("wiz.gate.fix.${r.name}"), style = MaterialTheme.typography.labelMedium, color = k.muted)
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Step 5: review and commit
// ---------------------------------------------------------------------------------------------------------------

@Composable
private fun StepReview(u: Ui, vm: WizardViewModel, d: WizardDraft, c: WizardResult, p: Params, locked: Boolean, onCancel: () -> Unit) {
    val k = LocalAndarzColors.current
    SectionCard(u.t("wiz.review.title")) {
        KeyValue(u.t("wiz.symbol"), u.symbol(d.symbol.uppercase(java.util.Locale.ROOT)))
        KeyValue(u.t("wiz.side"), u.enumLabel("side", d.side.name))
        KeyValue(u.t("wiz.entry"), u.price(c.entry)); KeyValue(u.t("wiz.sl"), u.price(c.sl)); KeyValue(u.t("wiz.tp"), u.price(c.tp))
        KeyValue(u.t("wiz.leverage"), u.mult(c.lev)); KeyValue(u.t("wiz.riskPct"), u.pct(c.riskPct))
        KeyValue(u.t("wiz.checklist.title"), u.t("wiz.checklist.score", "n" to u.int(c.chk), "total" to u.int(p.chkN.toInt())))
        Spacer(Modifier.height(8.dp))
        Text(u.t("wiz.review.size") + ": " + u.money(c.plan?.notionalMax), style = MaterialTheme.typography.titleMedium)
        Text(u.t("wiz.review.formula"), style = MaterialTheme.typography.labelMedium, color = k.muted)
    }
    when {
        locked -> Notice(u.t("wiz.gate.blocked"), NoticeTone.DANGER)
        c.allowed -> OutlinedButton(onClick = { vm.savePlan(rejected = false, thenEnter = false) }, modifier = Modifier.fillMaxWidth()) { Text(u.t("wiz.commit.plan")) }
        // D-28: a de-emphasized path; history stays honest
        c.numbersValid -> TextButton(onClick = { vm.savePlan(rejected = true, thenEnter = true) }) { Text(u.t("wiz.confirm.title")) }
    }
    TextButton(onClick = { vm.clear(); onCancel() }) { Text(u.t("wiz.commit.cancel")) }
}

@Composable
private fun ConfirmSheet(u: Ui, vm: WizardViewModel, t: TradeEntity, d: WizardDraft) {
    val k = LocalAndarzColors.current
    var entry by rememberSaveable { mutableStateOf(Fmt.number(t.plannedEntry, Ui.priceDecimals(t.plannedEntry), false, false, true)) }
    var notional by rememberSaveable { mutableStateOf(t.plan.notionalMax?.let { Fmt.number(it, 2, false, false, true) } ?: "") }
    var anyway by remember { mutableStateOf(false) }
    // Late logging (D-26, D-67): the entry may have happened earlier; minutes ago, like the close sheet.
    var minutesAgo by rememberSaveable { mutableStateOf("0") }
    val ago = Fmt.normalizeInput(minutesAgo)?.takeIf { it >= 0 && it <= 60.0 * 24 * 30 }
    val at = Instant.now().minus(java.time.Duration.ofMinutes(ago?.toLong() ?: 0))
    val e = Fmt.normalizeInput(entry)?.takeIf { it > 0 }
    val n = Fmt.normalizeInput(notional)?.takeIf { it > 0 }
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(u.t("wiz.confirm.title"), style = MaterialTheme.typography.titleLarge)
        NumberField(u.t("wiz.confirm.entry"), entry, { entry = it }, error = if (entry.isNotBlank() && e == null) u.t("err.positive") else null)
        NumberField(u.t("wiz.confirm.notional"), notional, { notional = it }, error = if (notional.isNotBlank() && n == null) u.t("err.notionalZero") else null, hint = u.t("wiz.confirm.note"))
        if (e != null) Text(u.t("wiz.confirm.slippage", "v" to u.pct(vm.slippage(t.plannedEntry, e, d.side), 2, signed = true)), style = MaterialTheme.typography.labelMedium, color = k.muted)
        if (e != null && n != null) {
            val rriskp = n * abs(e - t.slInitial) / e / t.equity
            Text(u.t("size.actualPct") + " " + u.pct(rriskp))
            if (rriskp > t.riskPctCap * 1.05) Notice(u.t("size.silentGrowth"), NoticeTone.CAUTION)
        }
        NumberField(
            u.t("wiz.confirm.time"), minutesAgo, { minutesAgo = it },
            error = if (ago == null) u.t("err.number") else null, hint = u.dateTime(at),
        )
        if (!t.slTpOnExchange) {
            Notice(u.t("err.slTpRequired"), NoticeTone.DANGER)
            SwitchRow(u.t("review.autoViolation.V01"), anyway, { anyway = it })
        }
        androidx.compose.material3.Button(
            onClick = { vm.confirm(e!!, n!!, at, withoutStops = !t.slTpOnExchange) },
            enabled = e != null && n != null && ago != null && t.canMonitor && (t.slTpOnExchange || anyway),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = k.fzFill, contentColor = k.onFz, disabledContainerColor = k.high, disabledContentColor = k.muted),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        ) { ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W700)) { Text(u.t("common.confirm")) } }
        Spacer(Modifier.height(24.dp))
    }
}
