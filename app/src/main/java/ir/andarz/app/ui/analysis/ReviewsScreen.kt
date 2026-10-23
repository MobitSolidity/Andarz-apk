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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.components.KeyValue
import ir.andarz.app.ui.components.SectionCard
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui
import ir.andarz.data.EngineFacade
import ir.andarz.data.LedgerRepository
import ir.andarz.data.ParamsRepository
import ir.andarz.data.ReviewRepository
import ir.andarz.data.db.AndarzDatabase
import ir.andarz.data.db.ReviewEntity
import ir.andarz.data.db.TradeEntity
import ir.andarz.data.settings.CalendarMode
import ir.andarz.data.settings.SettingsRepository
import ir.andarz.engine.Jalali
import ir.andarz.engine.Kpis
import ir.andarz.engine.LockCode
import ir.andarz.engine.LockNeed
import ir.andarz.engine.Snapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

enum class ReviewKind { DAILY, WEEKLY, MONTHLY, DRAWDOWN }

/** KPIs frozen into `kpiSnapshot` (law 6): numbers and codes only. */
fun kpiJson(k: Kpis): String {
    fun n(x: Double?) = x?.let { JsonPrimitive(it) } ?: JsonNull
    return JsonObject(
        mapOf(
            "n" to JsonPrimitive(k.n), "compliance" to n(k.compliance), "expectancy" to n(k.expectancy), "realRR" to n(k.realRR),
            "winRate" to n(k.winRate), "profitFactor" to n(k.profitFactor), "maxDD" to n(k.maxDD), "net" to JsonPrimitive(k.net),
            "sumR" to JsonPrimitive(k.sumR), "withdrawSuggest" to JsonPrimitive(k.withdrawSuggest),
            "mistakes" to JsonObject(k.mistakeTally.mapValues { JsonPrimitive(it.value) }),
        ),
    ).toString()
}

/** Today's trades that are still incomplete (5.9 daily): closed without mistake/lesson, or open without stops on the exchange. */
fun incompleteToday(trades: List<TradeEntity>, today: LocalDate, zone: java.time.ZoneId): List<TradeEntity> = trades.filter { t ->
    val day = Instant.ofEpochMilli(t.entryTime ?: t.createdAt).atZone(zone).toLocalDate()
    day == today && when (t.status) {
        "CLOSED" -> t.mistake.isNullOrEmpty() || t.lesson.isNullOrBlank()
        "OPEN" -> !t.slTpOnExchange
        else -> false
    }
}

@HiltViewModel
class ReviewsViewModel @Inject constructor(
    private val db: AndarzDatabase,
    private val reviews: ReviewRepository,
    private val ledger: LedgerRepository,
    private val params: ParamsRepository,
    private val settings: SettingsRepository,
    facade: EngineFacade,
) : ViewModel() {
    val history: StateFlow<List<ReviewEntity>> = db.reviews().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val trades: StateFlow<List<TradeEntity>> = db.trades().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val snapshot: StateFlow<Snapshot?> = facade.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val message = MutableStateFlow<String?>(null)

    private suspend fun kpis(from: LocalDate, to: LocalDate): Kpis {
        val s = settings.current()
        return InsightsLogic.of(db.trades().engineRows(), from, to, params.current(), s.zone).kpis
    }

    suspend fun kpisFor(from: LocalDate, to: LocalDate): Kpis = kpis(from, to)

    suspend fun jalali(): Boolean = settings.current().calendar == CalendarMode.JALALI

    private fun payload(m: Map<String, Any?>): String = JsonObject(m.mapValues { (_, v) ->
        when (v) {
            null -> JsonNull
            is Boolean -> JsonPrimitive(v)
            is Number -> JsonPrimitive(v)
            is List<*> -> JsonArray(v.map { JsonPrimitive(it.toString()) })
            else -> JsonPrimitive(v.toString())
        }
    }).toString()

    fun saveDaily(day: LocalDate, ticks: List<Boolean>, onDone: () -> Unit) = safeLaunch {
        reviews.save("DAILY", day, day, payload(mapOf("items" to ticks.map { it.toString() })), kpiJson(kpis(day, day)))
        message.value = "rev.daily.done"; onDone()
    }

    fun saveWeekly(weekStart: LocalDate, answers: List<String>, topMistake: String?, rule: String, onDone: () -> Unit) = safeLaunch {
        val end = weekStart.plusDays(6)
        reviews.save("WEEKLY", weekStart, end, payload(mapOf("answers" to answers, "topMistake" to topMistake, "rule" to rule.trim())), kpiJson(kpis(weekStart, end)))
        message.value = "common.saved"; onDone()
    }

    fun saveMonthly(start: LocalDate, end: LocalDate, security: Boolean, withdrawRecorded: Boolean, onDone: () -> Unit) = safeLaunch {
        if (security) settings.setLastSecurityReviewAt(System.currentTimeMillis())
        reviews.save("MONTHLY", start, end, payload(mapOf("security" to security, "withdrawRecorded" to withdrawRecorded)), kpiJson(kpis(start, end)))
        message.value = "common.saved"; onDone()
    }

    /** `rev.monthly.withdraw.record`: a WITHDRAWAL ledger row after the user confirmed (5.9). */
    fun recordWithdrawal(amount: Double, onDone: (Boolean) -> Unit) = safeLaunch {
        val err = ledger.add("WITHDRAWAL", amount, Instant.now(), "")
        message.value = if (err == null) "common.saved" else "ledger.negative"
        onDone(err == null)
    }

    fun saveDrawdown(q1: String, q2: String, onDone: () -> Unit) = safeLaunch {
        val today = LocalDate.now(settings.current().zone)
        reviews.save("DRAWDOWN", today, today, payload(mapOf("q1" to q1.trim(), "q2" to q2.trim())), kpiJson(kpis(LocalDate.of(1970, 1, 1), today)))
        message.value = "common.saved"; onDone()
    }
}

/** Reviews hub of 5.9: due items, the session, the four review forms and the history. */
@Composable
fun ReviewsScreen(onBack: () -> Unit, onOpenTrade: (String) -> Unit, onRules: () -> Unit, onThresholds: () -> Unit, vm: ReviewsViewModel = hiltViewModel(), gov: GovernanceViewModel = hiltViewModel()) {
    val u = ui()
    val history by vm.history.collectAsState()
    val trades by vm.trades.collectAsState()
    val snap by vm.snapshot.collectAsState()
    val message by vm.message.collectAsState()
    val session by gov.session.collectAsState()
    val blocked by gov.blocked.collectAsState()
    var open by rememberSaveable { mutableStateOf<ReviewKind?>(null) }
    val c = LocalAndarzColors.current
    val today = LocalDate.now(u.zone)
    val jalali = u.settings.calendar == CalendarMode.JALALI
    val due = ReviewLogic.due(today, jalali, history.map { it.type to LocalDate.parse(it.periodStart) })
    val ddNeedsReview = snap?.governor?.locks?.any { it.code == LockCode.DRAWDOWN && LockNeed.REVIEW in it.needs } == true
    val weeklyLock = snap?.governor?.locks?.any { it.code == LockCode.WEEKLY_LOSS && LockNeed.WEEKLY_REVIEW in it.needs } == true
    LaunchedEffect(ddNeedsReview) { if (ddNeedsReview && open == null) open = ReviewKind.DRAWDOWN }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(u.t("rev.title"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { if (open != null) open = null else onBack() }) { Text(u.t("common.back")) }
        }
        message?.let { Text(u.t(it), color = c.allowed, modifier = Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button) { vm.message.value = null }) }

        when (open) {
            ReviewKind.DAILY -> DailyForm(u, vm, trades, today, onOpenTrade) { open = null }
            ReviewKind.WEEKLY -> WeeklyForm(u, vm, history, today, weeklyLock) { open = null }
            ReviewKind.MONTHLY -> MonthlyForm(u, vm, today, jalali, onThresholds = { gov.start(); onThresholds() }) { open = null }
            ReviewKind.DRAWDOWN -> DrawdownForm(u, vm) { open = null }
            null -> {
                val dueItems = buildList {
                    if (ddNeedsReview) add(u.t("rev.drawdown.title") to ReviewKind.DRAWDOWN)
                    if (weeklyLock) add(u.t("rev.weekly.required") to ReviewKind.WEEKLY)
                    if (due.daily) add(u.t("rev.due.daily") to ReviewKind.DAILY)
                    if (due.weekly != null && !weeklyLock) add(u.t("rev.due.weekly") to ReviewKind.WEEKLY)
                    if (due.monthly != null) add(u.t("rev.due.monthly") to ReviewKind.MONTHLY)
                }
                if (dueItems.isNotEmpty()) SectionCard(null) {
                    dueItems.forEach { (label, kind) ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { open = kind }, verticalAlignment = Alignment.CenterVertically) {
                            Text(label, color = if (kind == ReviewKind.DRAWDOWN || (kind == ReviewKind.WEEKLY && weeklyLock)) c.redline else c.caution, modifier = Modifier.weight(1f))
                            Text(u.t("common.open"), color = c.info)
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { open = ReviewKind.DAILY }, modifier = Modifier.fillMaxWidth()) { Text(u.t("rev.daily.title")) }
                    OutlinedButton(onClick = { open = ReviewKind.WEEKLY }, modifier = Modifier.fillMaxWidth()) { Text(u.t("rev.weekly.title")) }
                    OutlinedButton(onClick = { open = ReviewKind.MONTHLY }, modifier = Modifier.fillMaxWidth()) { Text(u.t("rev.monthly.title")) }
                    OutlinedButton(onClick = { open = ReviewKind.DRAWDOWN }, modifier = Modifier.fillMaxWidth()) { Text(u.t("rev.drawdown.title")) }
                }
                SessionCard(u, session, blocked, gov::start, gov::end)
                if (session != null) TextButton(onClick = onRules) { Text(u.t("params.title")) }

                SectionCard(u.t("rev.history")) {
                    if (history.isEmpty()) Text(u.t("rev.empty"), color = c.textMuted)
                    history.take(60).forEachIndexed { i, r ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Row {
                            Text(u.enumLabel("reviewType", r.type), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(u.date(LocalDate.parse(r.periodStart), false), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val u = ui()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Checkbox) { onChange(!checked) }
            .semantics(mergeDescendants = true) { stateDescription = u.t(if (checked) "a11y.toggleOn" else "a11y.toggleOff") },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun KpiSummary(u: Ui, k: Kpis?) {
    if (k == null) return
    KeyValue(u.t("ins.kpi.n"), u.int(k.n))
    KeyValue(u.t("ins.kpi.compliance"), formatKpi(u, "pct", k.compliance))
    KeyValue(u.t("ins.kpi.expectancy"), formatKpi(u, "r", k.expectancy))
    KeyValue(u.t("ins.kpi.winRate"), formatKpi(u, "pct", k.winRate))
    KeyValue(u.t("ins.kpi.net"), formatKpi(u, "usd", k.net))
    KeyValue(u.t("ins.kpi.sumR"), formatKpi(u, "r", k.sumR))
}

@Composable
private fun DailyForm(u: Ui, vm: ReviewsViewModel, trades: List<TradeEntity>, today: LocalDate, onOpenTrade: (String) -> Unit, onDone: () -> Unit) {
    val items = listOf("trades", "emotion", "sltp", "calendar")
    val ticks = rememberSaveable(saver = boolListSaver) { mutableStateListOf(false, false, false, false) }
    val incomplete = incompleteToday(trades, today, u.zone)
    SectionCard(u.t("rev.daily.title")) {
        items.forEachIndexed { i, k -> CheckRow(u.t("rev.daily.item.$k"), ticks[i]) { ticks[i] = it } }
        if (incomplete.isNotEmpty()) {
            Text(u.t("today.closeDay.hint"), color = LocalAndarzColors.current.caution, modifier = Modifier.padding(top = 8.dp))
            incomplete.forEach { t -> TextButton(onClick = { onOpenTrade(t.id) }) { Text(u.symbol(t.symbol) + " \u00B7 " + u.t("trade.status.${t.status}")) } }
        }
        Button(onClick = { vm.saveDaily(today, ticks.toList(), onDone) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(u.t("rev.daily.save")) }
    }
}

@Composable
private fun WeeklyForm(u: Ui, vm: ReviewsViewModel, history: List<ReviewEntity>, today: LocalDate, required: Boolean, onDone: () -> Unit) {
    val reviewed = history.filter { it.type == "WEEKLY" }.map { LocalDate.parse(it.periodStart) }.toSet()
    val choices = ReviewLogic.weekChoices(today, reviewed)
    val default = ReviewLogic.due(today, u.settings.calendar == CalendarMode.JALALI, history.map { it.type to LocalDate.parse(it.periodStart) }).weekly
        ?: choices.firstOrNull() ?: Jalali.weekStart(today)
    var weekStart by rememberSaveable { mutableStateOf(default.toString()) }
    val ws = LocalDate.parse(weekStart)
    var kpis by androidx.compose.runtime.remember { mutableStateOf<Kpis?>(null) }
    LaunchedEffect(ws) { kpis = vm.kpisFor(ws, ws.plusDays(6)) }
    val questions = u.content.obj("learn", "journal")?.get("weeklyQuestions")?.let { it as? JsonArray }?.map { it.jsonObject["text"]!!.jsonPrimitive.content }.orEmpty()
    val answers = rememberSaveable(saver = stringListSaver) { List(questions.size) { "" }.toMutableStateList() }
    var rule by rememberSaveable { mutableStateOf("") }
    val top = kpis?.mistakeTally?.filterKeys { it != "M00" }?.maxByOrNull { it.value }?.key

    SectionCard(u.t("rev.weekly.title")) {
        if (required) Text(u.t("rev.weekly.required"), color = LocalAndarzColors.current.redline)
        Text(u.t("rev.weekly.start"), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf(ws) + choices.filter { it != ws }).take(3).forEach { d ->
                FilterChip(selected = d == ws, onClick = { weekStart = d.toString() }, label = { Text(u.date(d, false)) })
            }
        }
        KeyValue(u.t("rev.weekly.end"), u.date(ws.plusDays(6), false))
        Text(u.t("rev.weekly.kpis"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        KpiSummary(u, kpis)
        KeyValue(u.t("rev.weekly.topMistake"), top?.let { u.enumLabel("mistake", it) } ?: u.t("ins.top.none"))
        questions.forEachIndexed { i, q ->
            OutlinedTextField(answers[i], { answers[i] = it }, label = { Text(q) }, placeholder = { Text(u.t("rev.weekly.q.hint")) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), minLines = 2)
        }
        OutlinedTextField(rule, { rule = it }, label = { Text(u.t("rev.weekly.rule") + " (" + u.t("common.required") + ")") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        Button(onClick = { vm.saveWeekly(ws, answers.toList(), top, rule, onDone) }, enabled = rule.isNotBlank() && ws !in reviewed, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(u.t("rev.weekly.save")) }
    }
}

@Composable
private fun MonthlyForm(u: Ui, vm: ReviewsViewModel, today: LocalDate, jalali: Boolean, onThresholds: () -> Unit, onDone: () -> Unit) {
    val (start, end) = Periods.previousMonth(today, jalali)
    var kpis by androidx.compose.runtime.remember { mutableStateOf<Kpis?>(null) }
    LaunchedEffect(start) { kpis = vm.kpisFor(start, end) }
    var security by rememberSaveable { mutableStateOf(false) }
    var recorded by rememberSaveable { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val monthName = u.date(start, false).substringAfter(' ')
    SectionCard(u.t("rev.monthly.title")) {
        Text(u.date(start, false) + " \u2013 " + u.date(end, false), style = MaterialTheme.typography.labelMedium)
        Text(u.t("rev.monthly.kpis"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        KpiSummary(u, kpis)
        TextButton(onClick = onThresholds) { Text(u.t("rev.monthly.thresholds")) }
        Text(u.t("rev.monthly.withdraw"), style = MaterialTheme.typography.titleSmall)
        kpis?.let { k ->
            val amount = ReviewLogic.withdrawAmount(k)
            if (amount <= 0) Text(u.t("rev.monthly.withdraw.none"))
            else {
                Text(u.t("rev.monthly.withdraw.hint", "month" to monthName, "net" to u.signedMoney(k.net), "pct" to u.pct(k.withdrawSuggest / k.net, 0), "amount" to u.money(amount)))
                if (!recorded) {
                    if (confirm) Row {
                        Text(u.t("common.confirm"), modifier = Modifier.weight(1f).align(Alignment.CenterVertically))
                        TextButton(onClick = { vm.recordWithdrawal(amount) { ok -> recorded = ok; confirm = false } }) { Text(u.t("common.yes")) }
                        TextButton(onClick = { confirm = false }) { Text(u.t("common.no")) }
                    } else OutlinedButton(onClick = { confirm = true }) { Text(u.t("rev.monthly.withdraw.record")) }
                } else Text(u.t("common.saved"), color = LocalAndarzColors.current.allowed)
            }
        }
        CheckRow(u.t("rev.monthly.security"), security) { security = it }
        Button(onClick = { vm.saveMonthly(start, end, security, recorded, onDone) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(u.t("rev.monthly.save")) }
    }
}

@Composable
private fun DrawdownForm(u: Ui, vm: ReviewsViewModel, onDone: () -> Unit) {
    var q1 by rememberSaveable { mutableStateOf("") }
    var q2 by rememberSaveable { mutableStateOf("") }
    SectionCard(u.t("rev.drawdown.title")) {
        OutlinedTextField(q1, { q1 = it }, label = { Text(u.t("rev.drawdown.q1")) }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        OutlinedTextField(q2, { q2 = it }, label = { Text(u.t("rev.drawdown.q2")) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), minLines = 3)
        Button(onClick = { vm.saveDrawdown(q1, q2, onDone) }, enabled = q1.isNotBlank() && q2.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(u.t("rev.drawdown.save")) }
    }
}

private val boolListSaver: Saver<SnapshotStateList<Boolean>, Any> = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })
private val stringListSaver: Saver<SnapshotStateList<String>, Any> = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })
