package ir.andarz.app.ui.trade

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.components.BottomBar
import ir.andarz.app.ui.components.KeyValue
import ir.andarz.app.ui.components.NoticeBar
import ir.andarz.app.ui.components.NoticeTone
import ir.andarz.app.ui.components.OutlinedButton
import ir.andarz.app.ui.components.SectionCard
import ir.andarz.app.ui.components.Text
import ir.andarz.app.ui.components.TextButton
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui
import ir.andarz.data.db.TradeEntity
import ir.andarz.data.db.TradeEventEntity
import ir.andarz.engine.Params
import java.time.Instant

/**
 * One trade, in the page that fits where it stands: the open trade (price ladder, floating result, time stop,
 * management), the closed one (ruler, receipt, compliance) and its review, or a plan that was never entered.
 * The close sheet and the delete confirmation belong to every page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeScreen(onBack: () -> Unit, vm: TradeViewModel = hiltViewModel()) {
    val u = ui()
    val t by vm.trade.collectAsState()
    val events by vm.events.collectAsState()
    val p by vm.params.collectAsState()
    val err by vm.error.collectAsState()
    val deleted by vm.deleted.collectAsState()
    val setupName by vm.setupName.collectAsState()
    LaunchedEffect(deleted) { if (deleted) onBack() }
    var closing by rememberSaveable { mutableStateOf(false) }
    var reviewing by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val trade = t ?: return

    // A-02: an OPEN row from an old backup or a hand-edited import may lack the execution fields that the
    // management maths dereferences; such a row is shown read-only instead of crashing the screen.
    val executed = trade.entry != null && trade.notional != null && trade.entryTime != null
    val inReview = reviewing && trade.status == "CLOSED"
    BackHandler(enabled = inReview) { reviewing = false }

    when {
        inReview -> ReviewPage(u, trade, p, setupName, onClose = { reviewing = false }, onSave = { vm.review(it); reviewing = false })
        trade.status == "OPEN" && executed -> OpenPage(
            u, vm, trade, events, p, setupName, failed = err != null,
            onBack = onBack, onClose = { closing = true }, onDelete = { confirmDelete = true },
        )
        trade.status == "CLOSED" -> ResultPage(u, trade, events, setupName, onBack = onBack, onReview = { reviewing = true }, onDelete = { confirmDelete = true })
        else -> PlannedPage(u, vm, trade, events, p, setupName, failed = err != null || trade.status == "OPEN", onBack = onBack, onDelete = { confirmDelete = true })
    }

    if (closing && trade.status == "OPEN" && executed) {
        ModalBottomSheet(onDismissRequest = { closing = false }) {
            CloseSheet(u, trade) { legs, fee, reason, at -> vm.close(legs, fee, reason, at); closing = false }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        text = { Text(u.t("trade.delete.confirm")) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text(u.t("common.delete")) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(u.t("common.cancel")) } },
    )
}

/** A plan that was not entered (planned, rejected, cancelled), or an open row without its execution numbers. */
@Composable
private fun PlannedPage(
    u: Ui,
    vm: TradeViewModel,
    t: TradeEntity,
    events: List<TradeEventEntity>,
    p: Params,
    setupName: String?,
    failed: Boolean,
    onBack: () -> Unit,
    onDelete: () -> Unit,
) {
    val k = LocalAndarzColors.current
    val ctx = LocalContext.current
    val subtitle = listOfNotNull(
        if (t.account == "DEMO") u.t("trade.account.DEMO") else null,
        if (t.imported) u.t("trade.imported") else null,
        setupName,
    ).joinToString(DOT).ifEmpty { null }
    Column(Modifier.fillMaxSize()) {
        TradeHeader(
            u, onBack = onBack, subtitle = subtitle,
            menu = listOf(
                MenuEntry(u.t("trade.shareNote")) { shareNote(ctx, u, t, setupName) },
                MenuEntry(u.t("common.delete"), danger = true, onClick = onDelete),
            ),
        ) {
            Text(u.symbol(t.symbol), style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), maxLines = 1)
            SidePill(u, t.side)
            StatusPill(u, t.status)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (failed) NoticeBar(u.t("err.generic"), NoticeTone.DANGER)
            if (t.status == "PLANNED") {
                Text(u.t("wiz.planned.expire", "hours" to u.int(12)), style = MaterialTheme.typography.labelMedium, color = k.muted)
            }
            PlanSection(u, t)
            if (t.entryTime != null) ExecutionSection(u, t)
            if (t.status == "OPEN") ir.andarz.app.ui.market.TradeLinkCard(t, p)
            EventsCard(u, t, events, onAdd = null)
        }
        if (t.status == "PLANNED") {
            BottomBar {
                OutlinedButton(onClick = { vm.cancelPlan() }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(u.t("wiz.commit.cancel")) }
            }
        }
    }
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
    t.entryTime?.let { KeyValue(u.t("wiz.confirm.time"), u.dateTime(Instant.ofEpochMilli(it))) }
    KeyValue(u.t("size.actual"), u.money(t.rrisk) + " (" + u.pct(t.rriskp) + ")")
    t.verdict?.let { KeyValue(u.t("size.actualPct"), u.t("size.short.$it")) }
}
