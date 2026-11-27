package ir.andarz.app.ui.trade

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.components.Button
import ir.andarz.app.ui.components.FilterChip
import ir.andarz.app.ui.components.KeyValue
import ir.andarz.app.ui.components.NumberField
import ir.andarz.app.ui.components.RChip
import ir.andarz.app.ui.components.SectionCard
import ir.andarz.app.ui.components.Text
import ir.andarz.app.ui.components.TextButton
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.data.ExitLeg
import ir.andarz.data.db.TradeEntity
import ir.andarz.engine.Fmt
import ir.andarz.engine.Leg
import ir.andarz.engine.Management
import ir.andarz.engine.Planner
import ir.andarz.engine.Side
import java.time.Duration
import java.time.Instant

private class LegForm(price: String, pct: String, val fixed: Boolean) {
    var price by mutableStateOf(price)
    var pct by mutableStateOf(pct)
}

/** The close sheet (5.5): the exit legs, the fee, the time and the reason, with the result previewed before it is saved. */
@Composable
internal fun CloseSheet(u: Ui, t: TradeEntity, onSave: (List<ExitLeg>, Double, String, Instant) -> Unit) {
    val k = LocalAndarzColors.current
    val side = Side.valueOf(t.side)
    val entry = t.entry ?: return
    val notional = t.notional ?: return
    val entryTime = t.entryTime ?: return
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
    val guessed = newLegs.lastOrNull()?.let { Management.guessExitReason(side, entry, t.slCurrent, t.tp, it.price) }
    val reason = reasonPick ?: guessed ?: "MANUAL"
    val feeV = Fmt.normalizeInput(fee) ?: 0.0
    val exitAt = Instant.now().minus(Duration.ofMinutes(Fmt.normalizeInput(minutesAgo)?.toLong() ?: 0))
    val timeOk = !exitAt.isBefore(Instant.ofEpochMilli(entryTime))
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(u.t("close.title"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(u.t("close.legs.hint"), style = MaterialTheme.typography.labelMedium, color = k.muted)
        legs.forEach { l ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(u.t("close.price"), l.price, { if (!l.fixed) l.price = it }, modifier = Modifier.weight(1f))
                NumberField(u.t("close.fraction"), l.pct, { if (!l.fixed) l.pct = it }, suffix = "%", modifier = Modifier.weight(1f))
            }
        }
        TextButton(onClick = { legs.add(LegForm("", "", false)) }) { Text(u.t("close.addLeg")) }
        Text(u.t("close.sum", "v" to u.pct(sum)), style = MaterialTheme.typography.bodyMedium, color = if (sumOk) k.allowed else k.redline)
        if (!sumOk) Text(u.t("err.legsSum"), color = k.redline, style = MaterialTheme.typography.labelMedium)
        NumberField(u.t("close.fee"), fee, { fee = it })
        NumberField(u.t("close.time"), minutesAgo, { minutesAgo = it }, hint = u.dateTime(exitAt))
        if (!timeOk) Text(u.t("err.dateOrder"), color = k.redline, style = MaterialTheme.typography.labelMedium)
        Text(u.t("close.reason"), style = MaterialTheme.typography.labelLarge, color = k.muted)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            u.enumIds("exitReason").forEach { id -> FilterChip(selected = reason == id, onClick = { reasonPick = id }, label = { Text(u.t("close.reason.$id")) }) }
        }
        if (reasonPick == null && guessed != null) {
            Text(u.t("close.autoReason", "reason" to u.t("close.reason.$guessed")), style = MaterialTheme.typography.labelMedium, color = k.muted)
        }
        if (sumOk) {
            val all = t.legs.map { Leg(it.price, it.fraction) } + newLegs.map { Leg(it.price, it.fraction) }
            val f = Planner.fill(side, entry, t.slInitial, t.equity, notional, all, t.legs.sumOf { it.fee } + feeV)
            SectionCard(u.t("close.preview")) {
                KeyValue(u.t("result.gross"), u.signedMoney(f.gross))
                KeyValue(u.t("result.net"), u.signedMoney(f.net))
                Row(verticalAlignment = Alignment.CenterVertically) { Text(u.t("result.r"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = k.muted); RChip(f.r) }
                KeyValue(u.t("result.equityAfter"), u.money(f.equityAfter))
            }
        }
        Spacer(Modifier.height(4.dp))
        Button(onClick = { onSave(newLegs, feeV, reason, exitAt) }, enabled = sumOk && timeOk, modifier = Modifier.fillMaxWidth()) { Text(u.t("close.confirm")) }
    }
}
