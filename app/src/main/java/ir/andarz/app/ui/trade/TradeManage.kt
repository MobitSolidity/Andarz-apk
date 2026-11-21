package ir.andarz.app.ui.trade

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.components.AndarzTextField
import ir.andarz.app.ui.components.Button
import ir.andarz.app.ui.components.FilterChip
import ir.andarz.app.ui.components.KeyValue
import ir.andarz.app.ui.components.NumberField
import ir.andarz.app.ui.components.OutlinedButton
import ir.andarz.app.ui.components.SectionCard
import ir.andarz.app.ui.components.Text
import ir.andarz.app.ui.components.TextButton
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.data.db.TradeEntity
import ir.andarz.engine.Fmt
import ir.andarz.engine.Management
import ir.andarz.engine.Params
import ir.andarz.engine.Side

/*
 * What the bar's Manage button opens: the stop, the stepped exit and the two rule logs, in cards of the canvas.
 * The notes, news and feelings of the old management card moved to the buttons of the events card.
 */

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ManageSheet(u: Ui, vm: TradeViewModel, t: TradeEntity, p: Params, priceText: String) {
    val k = LocalAndarzColors.current
    val side = Side.valueOf(t.side)
    val entry = t.entry ?: return
    var newSl by rememberSaveable { mutableStateOf("") }
    var awayConfirm by remember { mutableStateOf<Double?>(null) }
    var partialPrice by rememberSaveable { mutableStateOf(priceText) }
    var partialPct by rememberSaveable { mutableStateOf(Fmt.number(p.partialExitPct * 100, 0, false, false)) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(u.t("trade.section.management"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })

        SectionCard(u.t("app.wizard.sl")) {
            KeyValue(u.t("trade.sl.current"), u.price(t.slCurrent))
            KeyValue(u.t("trade.sl.initial"), u.price(t.slInitial))
            Spacer(Modifier.height(8.dp))
            NumberField(u.t("trade.moveSl"), newSl, { newSl = it })
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val v = Fmt.normalizeInput(newSl)
                    if (v != null) {
                        if (Management.isAwayMove(side, t.slCurrent, v)) awayConfirm = v else vm.moveStop(v)
                        newSl = ""
                    }
                }) { Text(u.t("trade.moveSl.toward")) }
                OutlinedButton(onClick = { vm.moveStop(Management.breakEven(side, entry)) }) { Text(u.t("trade.moveSl.toBreakeven")) }
            }
        }

        SectionCard(u.t("trade.partial")) {
            Text(
                u.t("trade.partial.hint", "lo" to u.num(p.partialRLo, 1, trim = true), "hi" to u.num(p.partialRHi, 1, trim = true)),
                style = MaterialTheme.typography.labelMedium, color = k.muted,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(u.t("close.price"), partialPrice, { partialPrice = it }, modifier = Modifier.weight(1f))
                NumberField(u.t("close.fraction"), partialPct, { partialPct = it }, suffix = "%", modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                val px = Fmt.normalizeInput(partialPrice)
                val f = Fmt.normalizeInput(partialPct)
                if (px != null && f != null) { vm.partial(px, f / 100, 0.0); partialPrice = "" }
            }) { Text(u.t("trade.partial.add")) }
            Spacer(Modifier.height(8.dp))
            Text(
                u.t("trade.trail") + " " + u.t("trade.trail.lock", "r" to u.num(p.trailLockR, 1, trim = true)),
                style = MaterialTheme.typography.labelMedium, color = k.muted,
            )
        }

        SectionCard(u.t("app.trade.manage.log")) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.event("PRAYER", null) }) { Text(u.t("trade.prayer")) }
                OutlinedButton(onClick = { vm.reportAdd() }) { Text(u.t("trade.noAdd.report")) }
            }
            Spacer(Modifier.height(8.dp))
            Text(u.t("trade.prayer.hint"), style = MaterialTheme.typography.labelMedium, color = k.muted)
            Spacer(Modifier.height(4.dp))
            Text(u.t("trade.noAdd"), style = MaterialTheme.typography.labelMedium, color = k.muted)
        }
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

/** A note or a piece of news as text, or the feeling of this moment as one tap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddEventSheet(u: Ui, vm: TradeViewModel, kind: String, onDone: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val title = when (kind) {
        "NEWS" -> u.t("trade.news")
        "EMOTION" -> u.t("trade.emotionNow")
        else -> u.t("trade.note")
    }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        if (kind == "EMOTION") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                u.enumIds("emotion").forEach { id ->
                    FilterChip(selected = false, onClick = { vm.event("EMOTION", id); onDone() }, label = { Text(u.enumLabel("emotion", id)) })
                }
            }
        } else {
            AndarzTextField(title, text, { text = it }, singleLine = false, minLines = 2)
            Button(
                onClick = { vm.event(kind, text.trim()); onDone() },
                enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth(),
            ) { Text(u.t("common.save")) }
        }
    }
}
