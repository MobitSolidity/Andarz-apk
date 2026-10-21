package ir.andarz.app.ui.analysis

import ir.andarz.app.ui.safeLaunch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.andarz.app.content.ContentRepository
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.components.NumberField
import ir.andarz.app.ui.components.SectionCard
import ir.andarz.app.ui.components.TagChip
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui
import ir.andarz.data.ChangeResult
import ir.andarz.data.ParamsRepository
import ir.andarz.data.db.AndarzDatabase
import ir.andarz.data.db.ParamChangeEntity
import ir.andarz.data.db.ReviewSessionEntity
import ir.andarz.engine.ChangeKind
import ir.andarz.engine.Fmt
import ir.andarz.engine.XlMath
import ir.andarz.engine.Params
import ir.andarz.engine.SessionCheck
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import javax.inject.Inject

/** `params[]` of the content JSON as typed definitions, in document order (5.10). */
fun paramDefs(content: ir.andarz.app.content.ContentBundle): List<ParamDef> = content.arr("params").orEmpty().map {
    val o = it.jsonObject
    fun s(k: String) = o[k]?.jsonPrimitive?.content ?: ""
    fun d(k: String) = o[k]!!.jsonPrimitive.content.toDouble()
    ParamDef(s("key"), s("label"), s("note"), s("kind"), d("default"), d("min"), d("max"), d("step"), s("group"), s("article"), s("locked") == "true")
}

/** Review session (D-06) plus the Rules pipeline (5.10). Shared by Rules, Reviews and Ledger. */
@HiltViewModel
class GovernanceViewModel @Inject constructor(
    private val params: ParamsRepository,
    content: ContentRepository,
    db: AndarzDatabase,
) : ViewModel() {
    val defs: List<ParamDef> = paramDefs(content.bundle)

    private val _session = MutableStateFlow<ReviewSessionEntity?>(null)
    val session: StateFlow<ReviewSessionEntity?> = _session
    private val _current = MutableStateFlow(Params.DEFAULTS)
    val current: StateFlow<Params> = _current
    private val _pending = MutableStateFlow<List<ParamChangeEntity>>(emptyList())
    val pending: StateFlow<List<ParamChangeEntity>> = _pending
    val history: StateFlow<List<ParamChangeEntity>> = db.paramChanges().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The last refusal to start a session, shown alone (5.9). */
    val blocked = MutableStateFlow<SessionCheck?>(null)
    val message = MutableStateFlow<String?>(null)

    init {
        // The 60-minute timeout is applied lazily by the repository; RulesScreen re-polls while visible (P-01).
        safeLaunch { refresh() }
    }

    suspend fun refresh() {
        _session.value = params.openSession()
        _current.value = params.current()
        _pending.value = params.pending()
    }

    fun start() = safeLaunch {
        val (check, s) = params.startSession()
        blocked.value = if (s == null) check else null
        refresh()
    }

    fun end() = safeLaunch { params.endSession(); refresh() }

    /** Returns through [message] the key of the outcome; [onDone] runs on success. */
    fun apply(key: String, value: Double, reason: String, onDone: () -> Unit) = safeLaunch {
        when (val r = params.apply(key, value, reason)) {
            is ChangeResult.Applied -> { message.value = "common.saved"; onDone() }
            is ChangeResult.Rejected -> message.value = when (r.reason) {
                ir.andarz.data.ChangeError.NO_SESSION -> "params.needsSession"
                ir.andarz.data.ChangeError.LOCKED -> "params.locked"
                ir.andarz.data.ChangeError.RISK_ORDER -> "params.invalid.riskOrder"
                ir.andarz.data.ChangeError.REASON_REQUIRED -> "params.reason"
                ir.andarz.data.ChangeError.NO_CHANGE -> "params.change.NONE"
                else -> "err.generic"
            }
        }
        refresh()
    }

    fun cancel(id: String) = safeLaunch { params.cancel(id); refresh() }

    /** Reset to defaults: ordinary changes through the same pipeline, tightening first (5.10). */
    fun reset(reason: String) = safeLaunch {
        val p = params.current()
        for ((d, v) in RulesLogic.resetOrder(RulesLogic.resetChanges(defs, p), p)) params.apply(d.key, v, reason)
        message.value = "common.saved"
        refresh()
    }
}

/** A parameter value formatted by its `kind` (5.10). */
fun paramValue(u: Ui, kind: String, v: Double): String = when (kind) {
    "usd" -> u.money(v)
    // The clause format of 3.11: up to two decimals, trailing zeros trimmed (1%, 0.25%).
    "pct" -> Fmt.isolate(Fmt.number(XlMath.round(v * 100, 2), 2, u.fa, group = false, trim = true) + if (u.fa) Fmt.FA_PERCENT else '%')
    "mult" -> u.mult(v)
    "r" -> Fmt.isolate((if (v < 0) Fmt.MINUS.toString() else "") + Fmt.number(kotlin.math.abs(v), 2, u.fa, group = false, trim = true) + "R")
    else -> u.num(v, 2, trim = true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(onBack: () -> Unit, vm: GovernanceViewModel = hiltViewModel()) {
    val u = ui()
    val session by vm.session.collectAsState()
    val p by vm.current.collectAsState()
    val pending by vm.pending.collectAsState()
    val history by vm.history.collectAsState()
    val blocked by vm.blocked.collectAsState()
    val message by vm.message.collectAsState()
    ir.andarz.app.ui.WhileStarted(30_000) { vm.refresh() }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var resetAsk by rememberSaveable { mutableStateOf(false) }
    val c = LocalAndarzColors.current
    val labels = vm.defs.associate { it.key to it }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(u.t("params.title"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onBack) { Text(u.t("common.back")) }
            }
            Text(u.t("params.subtitle"), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        }
        item { SessionCard(u, session, blocked, vm::start, vm::end) }
        message?.let { m -> item { Text(u.t(m), color = c.info, modifier = Modifier.clickable { vm.message.value = null }) } }
        if (pending.isNotEmpty()) item {
            SectionCard(u.t("params.pending.title")) {
                pending.forEach { ch ->
                    val def = labels[ch.key]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            u.t("params.pending", "label" to (def?.label ?: ch.key), "old" to paramValue(u, def?.kind ?: "dec", ch.oldValue),
                                "new" to paramValue(u, def?.kind ?: "dec", ch.newValue), "at" to u.dateTime(Instant.ofEpochMilli(ch.effectiveAt))),
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { vm.cancel(ch.id) }) { Text(u.t("params.pending.cancel")) }
                    }
                }
            }
        }
        for (group in listOf("core", "governor")) {
            item(key = "group-$group", contentType = "header") { Text(u.t("params.group.$group"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            items(vm.defs.filter { it.group == group }, key = { it.key }, contentType = { "param" }) { d ->
                ParamRow(u, d, p[d.key], session != null) { editing = d.key }
                HorizontalDivider()
            }
        }
        item {
            OutlinedButton(onClick = { resetAsk = true }, enabled = session != null, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(u.t("params.reset")) }
        }
        item {
            SectionCard(u.t("params.audit")) {
                if (history.isEmpty()) Text(u.t("common.none"), color = c.textMuted)
                history.take(50).forEach { ch ->
                    val def = labels[ch.key]
                    val kind = u.enumLabel("paramChangeKind", ch.kind).replace("{hours}", u.int(p.loosenDelayH.toInt()))
                    Text(
                        u.dateTime(Instant.ofEpochMilli(ch.appliedAt)) + " \u00B7 " +
                            u.t("params.audit.row", "label" to (def?.label ?: ch.key), "old" to paramValue(u, def?.kind ?: "dec", ch.oldValue), "new" to paramValue(u, def?.kind ?: "dec", ch.newValue), "kind" to kind) +
                            (if (ch.cancelledAt != null) " \u00B7 " + u.t("params.pending.cancel") else ""),
                        style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.heightIn(min = 24.dp))
        }
    }

    editing?.let { key ->
        val def = labels[key] ?: return@let
        ModalBottomSheet(onDismissRequest = { editing = null }) { EditSheet(u, vm, def, p) { editing = null } }
    }
    if (resetAsk) {
        var reason by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { resetAsk = false },
            title = { Text(u.t("params.reset")) },
            text = {
                Column {
                    Text(u.t("params.warn.loosen"))
                    OutlinedTextField(reason, { reason = it }, label = { Text(u.t("params.reason")) }, placeholder = { Text(u.t("params.reason.hint")) })
                }
            },
            confirmButton = { TextButton(onClick = { vm.reset(reason); resetAsk = false }, enabled = reason.isNotBlank()) { Text(u.t("common.confirm")) } },
            dismissButton = { TextButton(onClick = { resetAsk = false }) { Text(u.t("common.cancel")) } },
        )
    }
}

@Composable
internal fun SessionCard(u: Ui, session: ReviewSessionEntity?, blocked: SessionCheck?, onStart: () -> Unit, onEnd: () -> Unit) {
    val c = LocalAndarzColors.current
    SectionCard(u.t("rev.session.title")) {
        Text(u.t("rev.session.rule"), style = MaterialTheme.typography.bodyMedium)
        if (session != null) {
            val left = (ParamsRepository.SESSION_MAX.toMillis() - (System.currentTimeMillis() - session.startedAt)).coerceAtLeast(0) / 60_000
            Text(u.t("rev.session.open") + " \u00B7 " + u.duration(left), color = c.allowed, modifier = Modifier.padding(top = 6.dp))
            OutlinedButton(onClick = onEnd, modifier = Modifier.padding(top = 6.dp)) { Text(u.t("rev.session.end")) }
        } else {
            Text(u.t("params.needsSession"), style = MaterialTheme.typography.labelMedium, color = c.textMuted, modifier = Modifier.padding(top = 6.dp))
            when (blocked?.reason) {
                ir.andarz.engine.SessionBlock.OPEN_POSITION -> Text(u.t("rev.session.blocked.OPEN_POSITION"), color = c.redline)
                ir.andarz.engine.SessionBlock.RECENT_LOSS -> Text(u.t("rev.session.blocked.RECENT_LOSS", "until" to (blocked.until?.let { u.dateTime(it) } ?: u.t("common.dash"))), color = c.redline)
                null -> {}
            }
            Button(onClick = onStart, modifier = Modifier.padding(top = 6.dp)) { Text(u.t("rev.session.start")) }
        }
    }
}

@Composable
private fun ParamRow(u: Ui, d: ParamDef, value: Double, editable: Boolean, onEdit: () -> Unit) {
    val c = LocalAndarzColors.current
    val locked = d.locked || d.key == "Start_Equity"
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(enabled = editable && !locked, role = Role.Button, onClick = onEdit)
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(d.label, style = MaterialTheme.typography.bodyLarge)
            Text(u.t("params.article", "a" to u.digits(d.article)) + " \u00B7 " + u.t("params.default", "v" to paramValue(u, d.kind, d.default)), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
            if (d.key == "Start_Equity") Text(u.t("ledger.baseline.locked"), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
        }
        if (locked) Icon(Icons.Filled.Lock, contentDescription = u.t("params.locked"), tint = c.textMuted, modifier = Modifier.padding(end = 6.dp))
        Text(paramValue(u, d.kind, value), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun EditSheet(u: Ui, vm: GovernanceViewModel, d: ParamDef, p: Params, onDone: () -> Unit) {
    val c = LocalAndarzColors.current
    var text by rememberSaveable { mutableStateOf(RulesLogic.fieldText(d, p[d.key])) }
    var reason by rememberSaveable { mutableStateOf("") }
    val check = RulesLogic.check(d, text, p)
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(d.label, style = MaterialTheme.typography.titleMedium)
        if (d.note.isNotBlank()) Text(d.note, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        val err = when (check.invalid) {
            ParamInvalid.NUMBER -> u.t("err.number")
            ParamInvalid.BELOW_MIN -> u.t("params.invalid.min", "min" to paramValue(u, d.kind, d.min))
            ParamInvalid.ABOVE_MAX -> u.t("params.invalid.max", "max" to paramValue(u, d.kind, d.max))
            ParamInvalid.RISK_ORDER -> u.t("params.invalid.riskOrder")
            ParamInvalid.LOCKED -> u.t("params.locked")
            null -> null
        }
        NumberField(d.label, text, { text = it }, error = err, suffix = if (d.kind == "pct") "%" else null)
        if (check.invalid == null && check.kind != ChangeKind.NONE) {
            val chip = u.t("params.change.${check.kind.name}", "hours" to u.int(p.loosenDelayH.toInt()))
            TagChip(chip, when (check.kind) { ChangeKind.TIGHTEN -> c.allowed; ChangeKind.LOOSEN -> c.caution; else -> c.info })
            if (check.kind == ChangeKind.LOOSEN) Text(u.t("params.warn.loosen"), color = c.caution, style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedTextField(reason, { reason = it }, label = { Text(u.t("params.reason")) }, placeholder = { Text(u.t("params.reason.hint")) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) { Text(u.t("common.cancel")) }
            Button(
                onClick = { vm.apply(d.key, check.value!!, reason, onDone) },
                enabled = check.invalid == null && check.kind != ChangeKind.NONE && reason.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) { Text(u.t("params.apply")) }
        }
    }
}
