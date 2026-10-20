package ir.andarz.app.ui.trade

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.components.KeyValue
import ir.andarz.app.ui.components.NumberField
import ir.andarz.app.ui.components.RChip
import ir.andarz.app.ui.components.ReasonField
import ir.andarz.app.ui.components.SectionCard
import ir.andarz.app.ui.components.TagChip
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui
import ir.andarz.data.ExitLeg
import ir.andarz.data.ReviewInput
import ir.andarz.data.db.TradeEntity
import ir.andarz.engine.Fmt
import ir.andarz.engine.Leg
import ir.andarz.engine.Management
import ir.andarz.engine.Params
import ir.andarz.engine.Planner
import ir.andarz.engine.Side
import java.time.Duration
import java.time.Instant
import kotlinx.serialization.json.jsonPrimitive

/** Trade detail (5.4), close sheet (5.5), result card and post-trade review. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeScreen(onBack: () -> Unit, vm: TradeViewModel = hiltViewModel()) {
    val u = ui()
    val t by vm.trade.collectAsState()
    val events by vm.events.collectAsState()
    val p by vm.params.collectAsState()
    val err by vm.error.collectAsState()
    val deleted by vm.deleted.collectAsState()
    LaunchedEffect(deleted) { if (deleted) onBack() }
    var closing by rememberSaveable { mutableStateOf(false) }
    var reviewing by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val trade = t ?: return

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(u.symbol(trade.symbol), style = MaterialTheme.typography.titleLarge)
            TagChip(u.enumLabel("side", trade.side))
            TagChip(u.t("trade.status.${trade.status}"))
            TagChip(u.t("trade.account.${trade.account}"))
            if (trade.imported) TagChip(u.t("trade.imported"))
            Spacer(Modifier.weight(1f))
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val setupName by vm.setupName.collectAsState()
            // 9.5 share note: plain text from the quick template, filled by line index.
            TextButton(onClick = {
                val tpl = u.content.obj("learn", "journal")?.get("quickTemplate")?.jsonPrimitive?.content ?: return@TextButton
                val text = ir.andarz.app.platform.ShareNote.fill(tpl, ir.andarz.app.platform.ShareNote.values(u, trade, setupName))
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text)
                ctx.startActivity(android.content.Intent.createChooser(send, u.t("trade.shareNote")))
            }) { Text(u.t("trade.shareNote")) }
        }
        if (err != null) Text(u.t("err.generic"), color = LocalAndarzColors.current.redline)

        PlanSection(u, trade)
        if (trade.entryTime != null) ExecutionSection(u, trade)
        if (trade.status == "OPEN") ManagementSection(u, vm, trade, p) { closing = true }
        if (trade.status == "OPEN") ir.andarz.app.ui.market.TradeLinkCard(trade, p)
        if (trade.status == "CLOSED") {
            ResultCard(u, trade)
            SectionCard(u.t("trade.section.review")) {
                if (trade.reviewedAt == null) {
                    Text(u.t("review.pending", "n" to u.int(1)), color = LocalAndarzColors.current.caution)
                } else {
                    KeyValue(u.t("review.mistake"), u.enumLabel("mistake", trade.mistake))
                    trade.lesson?.let { KeyValue(u.t("review.lesson"), it) }
                    if (trade.violations.isNotEmpty()) KeyValue(u.t("review.violations"), trade.violations.joinToString("\u060C ") { u.enumLabel("violation", it) })
                }
                OutlinedButton(onClick = { reviewing = true }) { Text(u.t("review.title")) }
            }
        }
        if (trade.status == "PLANNED") {
            Text(u.t("wiz.planned.expire", "hours" to u.int(12)), style = MaterialTheme.typography.labelMedium)
            OutlinedButton(onClick = { vm.cancelPlan() }) { Text(u.t("wiz.commit.cancel")) }
        }
        if (events.isNotEmpty()) SectionCard(u.t("trade.events")) {
            events.forEach { e -> Text(u.time(Instant.ofEpochMilli(e.time)) + "  " + u.t("trade.event.${e.type}"), style = MaterialTheme.typography.bodyMedium) }
        }
        TextButton(onClick = { confirmDelete = true }) { Text(u.t("common.delete"), color = LocalAndarzColors.current.redline) }
        Spacer(Modifier.height(24.dp))
    }

    if (closing) ModalBottomSheet(onDismissRequest = { closing = false }) {
        CloseSheet(u, trade) { legs, fee, reason, at -> vm.close(legs, fee, reason, at); closing = false; reviewing = true }
    }
    if (reviewing) ModalBottomSheet(onDismissRequest = { reviewing = false }) { ReviewSheet(u, trade, p) { vm.review(it); reviewing = false } }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        text = { Text(u.t("trade.delete.confirm")) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text(u.t("common.delete")) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(u.t("common.cancel")) } },
    )
}

@Composable
private fun PlanSection(u: Ui, t: TradeEntity) = SectionCard(u.t("trade.section.plan")) {
    KeyValue(u.t("wiz.kind"), u.enumLabel("kind", t.kind))
    KeyValue(u.t("wiz.timeframe"), u.enumLabel("timeframe", t.timeframe))
    KeyValue(u.t("wiz.entry"), u.price(t.plannedEntry))
    KeyValue(u.t("trade.sl.initial"), u.price(t.slInitial))
    KeyValue(u.t("wiz.tp"), u.price(t.tp))
    KeyValue(u.t("wiz.leverage"), u.mult(t.lev))
    KeyValue(u.t("wiz.calc.rr"), u.num(t.plan.rr))
    KeyValue(u.t("wiz.calc.notionalMax"), u.money(t.plan.notionalMax))
    KeyValue(u.t("wiz.checklist.title"), u.t("wiz.checklist.score", "n" to u.int(t.chk), "total" to u.int(8)))
    if (t.gateReasons.isNotEmpty()) KeyValue(u.t("wiz.gate.reasons.title"), t.gateReasons.split("|").joinToString("\u060C ") { u.t("wiz.gate.reason.$it") })
}

@Composable
private fun ExecutionSection(u: Ui, t: TradeEntity) = SectionCard(u.t("trade.section.execution")) {
    KeyValue(u.t("wiz.confirm.entry"), u.price(t.entry))
    KeyValue(u.t("wiz.confirm.notional"), u.money(t.notional))
    KeyValue(u.t("wiz.confirm.time"), u.dateTime(Instant.ofEpochMilli(t.entryTime!!)))
    KeyValue(u.t("size.actual"), u.money(t.rrisk) + " (" + u.pct(t.rriskp) + ")")
    t.verdict?.let { KeyValue(u.t("size.actualPct"), u.t("size.short.$it")) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManagementSection(u: Ui, vm: TradeViewModel, t: TradeEntity, p: Params, onClose: () -> Unit) {
    val side = Side.valueOf(t.side)
    var price by rememberSaveable { mutableStateOf("") }
    var newSl by rememberSaveable { mutableStateOf("") }
    var awayConfirm by remember { mutableStateOf<Double?>(null) }
    var partialPrice by rememberSaveable { mutableStateOf("") }
    var partialPct by rememberSaveable { mutableStateOf(Fmt.number(p.partialExitPct * 100, 0, false, false)) }
    var note by rememberSaveable { mutableStateOf("") }
    val legs = t.legs.map { Leg(it.price, it.fraction) }
    SectionCard(u.t("trade.section.management")) {
        NumberField(u.t("trade.currentPrice"), price, { price = it }, hint = u.t("trade.currentPrice.hint"))
        // K9.1 c: a copy aid only; the typed number is what the screen uses (D-M06).
        ir.andarz.app.ui.market.LivePriceChip(t.symbol, { price = it })
        Fmt.normalizeInput(price)?.takeIf { it > 0 }?.let { px ->
            val (pnl, r) = Management.floating(side, t.entry!!, t.slInitial, t.notional!!, legs, px)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(u.t("trade.floatingR")); RChip(r); Text(u.t("trade.floatingPnl") + " " + u.signedMoney(pnl))
            }
            if (r != null && r >= p.partialRLo && t.legs.isEmpty()) Text(u.t("trade.partial.due", "r" to u.num(r)), color = LocalAndarzColors.current.caution)
            if (r != null && r >= p.trailLockR) Text(u.t("trade.greed"), color = LocalAndarzColors.current.caution)
        }
        KeyValue(u.t("trade.sl.current"), u.price(t.slCurrent))
        KeyValue(u.t("trade.sl.initial"), u.price(t.slInitial))
        val ts = Management.timeStop(side, t.entry!!, t.slCurrent, Instant.ofEpochMilli(t.entryTime!!), t.timeframe, t.legs.isNotEmpty(), Instant.now(), p)
        Text(if (ts.due) u.t("trade.timeStop.due", "n" to u.int(p.timeStopCandles.toInt())) else u.t("trade.timeStop.left", "n" to u.int(ts.left.toInt())))

        Spacer(Modifier.height(8.dp))
        NumberField(u.t("trade.moveSl"), newSl, { newSl = it })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val v = Fmt.normalizeInput(newSl)
                if (v != null) {
                    if (Management.isAwayMove(side, t.slCurrent, v)) awayConfirm = v else vm.moveStop(v)
                    newSl = ""
                }
            }) { Text(u.t("trade.moveSl.toward")) }
            OutlinedButton(onClick = { vm.moveStop(Management.breakEven(side, t.entry!!)) }) { Text(u.t("trade.moveSl.toBreakeven")) }
        }

        Spacer(Modifier.height(8.dp))
        Text(u.t("trade.partial"), style = MaterialTheme.typography.titleSmall)
        Text(u.t("trade.partial.hint", "lo" to u.num(p.partialRLo, 1, trim = true), "hi" to u.num(p.partialRHi, 1, trim = true)), style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(u.t("close.price"), partialPrice, { partialPrice = it }, modifier = Modifier.weight(1f))
            NumberField(u.t("close.fraction"), partialPct, { partialPct = it }, suffix = "%", modifier = Modifier.weight(1f))
        }
        OutlinedButton(onClick = {
            val px = Fmt.normalizeInput(partialPrice)
            val f = Fmt.normalizeInput(partialPct)
            if (px != null && f != null) { vm.partial(px, f / 100, 0.0); partialPrice = "" }
        }) { Text(u.t("trade.partial.add")) }
        Text(u.t("trade.trail") + " " + u.t("trade.trail.lock", "r" to u.num(p.trailLockR, 1, trim = true)), style = MaterialTheme.typography.labelMedium)

        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.event("PRAYER", null) }) { Text(u.t("trade.prayer")) }
            OutlinedButton(onClick = { vm.reportAdd() }) { Text(u.t("trade.noAdd.report")) }
            OutlinedButton(onClick = { vm.monitor() }) { Text(u.t("trade.monitor.check")) }
        }
        Text(u.t("trade.prayer.hint"), style = MaterialTheme.typography.labelMedium)
        OutlinedTextField(note, { note = it }, label = { Text(u.t("trade.note")) }, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { if (note.isNotBlank()) { vm.event("NOTE", note); note = "" } }) { Text(u.t("trade.note")) }
            TextButton(onClick = { if (note.isNotBlank()) { vm.event("NEWS", note); note = "" } }) { Text(u.t("trade.news.add")) }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(u.t("trade.close")) }
    }
    awayConfirm?.let { v ->
        AlertDialog(
            onDismissRequest = { awayConfirm = null },
            text = { Text(u.t("trade.moveSl.away")) },
            confirmButton = { TextButton(onClick = { vm.moveStop(v); awayConfirm = null }) { Text(u.t("trade.moveSl.away.confirm")) } },
            dismissButton = { TextButton(onClick = { awayConfirm = null }) { Text(u.t("common.cancel")) } },
        )
    }
}

/** The neutral result card (5.5, law 8): the same layout for a win and a loss, no animation. */
@Composable
fun ResultCard(u: Ui, t: TradeEntity) = SectionCard(u.t("result.title")) {
    KeyValue(u.t("result.net"), u.signedMoney(t.net))
    KeyValue(u.t("result.gross"), u.signedMoney(t.gross))
    Row(verticalAlignment = Alignment.CenterVertically) { Text(u.t("result.r"), modifier = Modifier.weight(1f)); RChip(t.r) }
    Text(u.t("result.rVsInitial"), style = MaterialTheme.typography.labelMedium, color = LocalAndarzColors.current.textMuted)
    KeyValue(u.t("result.equityAfter"), u.money(t.equityAfter))
    KeyValue(u.t("close.reason"), u.t("close.reason.${t.exitReason}"))
    val ok = t.ok == true
    Text(u.t(if (ok) "result.compliant.yes" else "result.compliant.no"), style = MaterialTheme.typography.titleSmall)
    if (!ok) {
        val why = buildList {
            if (!t.gateAllowed) add(u.t("result.why.GATE"))
            if (t.violations.isNotEmpty()) add(u.t("result.why.VIOLATION"))
            if (t.verdict == "OVER_CAP" || t.verdict == "RED_LINE") add(u.t("result.why.SIZE"))
        }
        if (why.isNotEmpty()) Text(u.t("result.why", "reasons" to why.joinToString("\u060C ")))
    }
    if (ok && (t.net ?: 0.0) < 0) Text(u.t("result.goodLoss"))
    if (!ok && (t.net ?: 0.0) > 0) Text(u.t("result.badWin"))
}

private class LegForm(price: String, pct: String, val fixed: Boolean) {
    var price by mutableStateOf(price)
    var pct by mutableStateOf(pct)
}

@Composable
private fun CloseSheet(u: Ui, t: TradeEntity, onSave: (List<ExitLeg>, Double, String, Instant) -> Unit) {
    val side = Side.valueOf(t.side)
    val recorded = t.legs.sumOf { it.fraction }
    val legs = remember {
        mutableStateListOf<LegForm>().apply {
            t.legs.forEach { add(LegForm(Fmt.number(it.price, 8, false, false, true), Fmt.number(it.fraction * 100, 4, false, false, true), true)) }
            add(LegForm("", Fmt.number((1 - recorded) * 100, 4, false, false, true), false))
        }
    }
    var fee by rememberSaveable { mutableStateOf("0") }
    var reasonPick by rememberSaveable { mutableStateOf<String?>(null) }
    var minutesAgo by rememberSaveable { mutableStateOf("0") }
    val open = legs.filter { !it.fixed }
    val newLegs = open.mapNotNull { l ->
        val px = Fmt.normalizeInput(l.price)
        val f = Fmt.normalizeInput(l.pct)
        if (px != null && px > 0 && f != null && f > 0) ExitLeg(px, f / 100) else null
    }
    val sum = recorded + newLegs.sumOf { it.fraction }
    val sumOk = kotlin.math.abs(sum - 1.0) < 1e-9 && newLegs.size == open.size
    val guessed = newLegs.lastOrNull()?.let { Management.guessExitReason(side, t.entry!!, t.slCurrent, t.tp, it.price) }
    val reason = reasonPick ?: guessed ?: "MANUAL"
    val feeV = Fmt.normalizeInput(fee) ?: 0.0
    val exitAt = Instant.now().minus(Duration.ofMinutes(Fmt.normalizeInput(minutesAgo)?.toLong() ?: 0))
    val timeOk = !exitAt.isBefore(Instant.ofEpochMilli(t.entryTime!!))
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(u.t("close.title"), style = MaterialTheme.typography.titleLarge)
        Text(u.t("close.legs.hint"), style = MaterialTheme.typography.labelMedium)
        legs.forEach { l ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(u.t("close.price"), l.price, { if (!l.fixed) l.price = it }, modifier = Modifier.weight(1f))
                NumberField(u.t("close.fraction"), l.pct, { if (!l.fixed) l.pct = it }, suffix = "%", modifier = Modifier.weight(1f))
            }
        }
        TextButton(onClick = { legs.add(LegForm("", "", false)) }) { Text(u.t("close.addLeg")) }
        Text(u.t("close.sum", "v" to u.pct(sum)), color = if (sumOk) LocalAndarzColors.current.allowed else LocalAndarzColors.current.redline)
        if (!sumOk) Text(u.t("err.legsSum"), color = LocalAndarzColors.current.redline, style = MaterialTheme.typography.labelMedium)
        NumberField(u.t("close.fee"), fee, { fee = it })
        NumberField(u.t("close.time"), minutesAgo, { minutesAgo = it }, hint = u.dateTime(exitAt))
        if (!timeOk) Text(u.t("err.dateOrder"), color = LocalAndarzColors.current.redline)
        Text(u.t("close.reason"), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            u.enumIds("exitReason").forEach { id -> FilterChip(selected = reason == id, onClick = { reasonPick = id }, label = { Text(u.t("close.reason.$id")) }) }
        }
        if (reasonPick == null && guessed != null) Text(u.t("close.autoReason", "reason" to u.t("close.reason.$guessed")), style = MaterialTheme.typography.labelMedium)
        if (sumOk) {
            val all = t.legs.map { Leg(it.price, it.fraction) } + newLegs.map { Leg(it.price, it.fraction) }
            val f = Planner.fill(side, t.entry!!, t.slInitial, t.equity, t.notional!!, all, t.legs.sumOf { it.fee } + feeV)
            SectionCard(u.t("close.preview")) {
                KeyValue(u.t("result.gross"), u.signedMoney(f.gross))
                KeyValue(u.t("result.net"), u.signedMoney(f.net))
                Row(verticalAlignment = Alignment.CenterVertically) { Text(u.t("result.r"), Modifier.weight(1f)); RChip(f.r) }
                KeyValue(u.t("result.equityAfter"), u.money(f.equityAfter))
            }
        }
        Button(onClick = { onSave(newLegs, feeV, reason, exitAt) }, enabled = sumOk && timeOk, modifier = Modifier.fillMaxWidth()) { Text(u.t("close.confirm")) }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewSheet(u: Ui, t: TradeEntity, p: Params, onSave: (ReviewInput) -> Unit) {
    val auto = t.violations.toSet()
    val suggested = Management.suggestedViolations(
        t.emotion, enteredInCooldown = false, losing = (t.net ?: 0.0) < 0,
        heldMinutes = ((t.exitTime ?: 0) - (t.entryTime ?: 0)) / 60_000, timeframe = t.timeframe, p = p,
    ).filter { it !in auto }
    val chosen = remember { mutableStateListOf<String>().apply { addAll(suggested) } }
    var report by rememberSaveable { mutableStateOf(t.violationReport ?: "") }
    var mistake by rememberSaveable { mutableStateOf(t.mistake) }
    var emotionAfter by rememberSaveable { mutableStateOf(t.emotionAfter) }
    var lesson by rememberSaveable { mutableStateOf(t.lesson ?: "") }
    val min = p.reportMinChars.toInt()
    val anyViolation = auto.isNotEmpty() || chosen.isNotEmpty()
    val canSave = mistake != null && (!anyViolation || report.trim().length >= min)
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(u.t("review.title"), style = MaterialTheme.typography.titleLarge)
        Text(u.t("review.ruleFirst"), style = MaterialTheme.typography.titleSmall)
        Text(u.t("review.violations"), style = MaterialTheme.typography.labelLarge)
        u.enumIds("violation").forEach { id ->
            val isAuto = id in auto
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isAuto || id in chosen, onCheckedChange = { on -> if (!isAuto) { if (on) chosen.add(id) else chosen.remove(id) } }, enabled = !isAuto)
                Column(Modifier.weight(1f)) {
                    Text(u.enumLabel("violation", id))
                    if (isAuto) Text(u.t("review.autoViolation", "label" to u.enumLabel("violation", id)), style = MaterialTheme.typography.labelMedium, color = LocalAndarzColors.current.caution)
                }
            }
        }
        if (anyViolation) ReasonField(u.t("review.report"), report, { report = it }, min, { n -> u.t("review.report.min", "min" to u.int(min), "n" to u.int(n)) }, u.t("review.report.hint"))
        Text(u.t("review.mistake"), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            u.enumIds("mistake").forEach { id -> FilterChip(selected = mistake == id, onClick = { mistake = id }, label = { Text(u.enumLabel("mistake", id)) }) }
        }
        Text(u.t("review.emotionAfter"), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            u.enumIds("emotion").forEach { id -> FilterChip(selected = emotionAfter == id, onClick = { emotionAfter = id }, label = { Text(u.enumLabel("emotion", id)) }) }
        }
        OutlinedTextField(lesson, { lesson = it }, label = { Text(u.t("review.lesson")) }, placeholder = { Text(u.t("review.lesson.hint")) }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = { onSave(ReviewInput(chosen.toList(), report.takeIf { anyViolation }, mistake!!, emotionAfter, lesson)) },
            enabled = canSave, modifier = Modifier.fillMaxWidth(),
        ) { Text(u.t("review.save")) }
        Spacer(Modifier.height(24.dp))
    }
}
