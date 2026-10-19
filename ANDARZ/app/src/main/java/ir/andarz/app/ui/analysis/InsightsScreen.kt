package ir.andarz.app.ui.analysis

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import ir.andarz.app.ui.components.SectionCard
import ir.andarz.app.ui.components.StatTile
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui
import ir.andarz.data.EngineFacade
import ir.andarz.data.db.AndarzDatabase
import ir.andarz.data.db.SetupEntity
import ir.andarz.data.settings.CalendarMode
import ir.andarz.data.settings.SettingsRepository
import ir.andarz.engine.KpiStatus
import ir.andarz.engine.Kpis
import ir.andarz.engine.Params
import ir.andarz.engine.Stats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import javax.inject.Inject

data class InsightsState(val period: Period = Period.MONTH, val from: LocalDate? = null, val to: LocalDate? = null) : java.io.Serializable

data class InsightsView(val range: Pair<LocalDate, LocalDate>, val insights: Insights, val params: Params)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    db: AndarzDatabase,
    facade: EngineFacade,
    settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(saved.get<InsightsState>(KEY) ?: InsightsState())
    val state: StateFlow<InsightsState> = _state

    val setups: StateFlow<List<SetupEntity>> = db.setups().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val view: StateFlow<InsightsView?> = combine(db.trades().observeEngineRows(), settings.settings, _state) { t, s, st -> Triple(t, s, st) }
        .mapLatest { (trades, s, st) ->
            val today = LocalDate.now(s.zone)
            val custom = if (st.from != null && st.to != null) st.from to st.to else null
            val range = Periods.range(st.period, today, s.calendar == CalendarMode.JALALI, custom)
            val p = facade.paramsAt(java.time.Instant.now())
            InsightsView(range, InsightsLogic.of(trades, range.first, range.second, p, s.zone), p)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun set(f: (InsightsState) -> InsightsState) { val n = f(_state.value); _state.value = n; saved[KEY] = n }

    companion object { const val KEY = "insights.state" }
}

private fun statusOk(s: KpiStatus): Boolean? = when (s) {
    KpiStatus.ON_TARGET, KpiStatus.POSITIVE, KpiStatus.IN_RANGE, KpiStatus.COMPLETE, KpiStatus.ENOUGH_DATA -> true
    KpiStatus.LOW_DATA -> null
    else -> false
}

/** A KPI value by its `kpiDefs` id. */
private fun kpiValue(k: Kpis, id: String): Double? = when (id) {
    "n" -> k.n.toDouble()
    "compliance" -> k.compliance
    "expectancy" -> k.expectancy
    "avgWinR" -> k.avgWinR
    "avgLossR" -> k.avgLossR
    "realRR" -> k.realRR
    "winRate" -> k.winRate
    "profitFactor" -> k.profitFactor
    "maxDD" -> k.maxDD
    "avgChk" -> k.avgChk
    "net" -> k.net
    "sumR" -> k.sumR
    "withdrawSuggest" -> k.withdrawSuggest
    else -> null
}

internal fun formatKpi(u: Ui, format: String, v: Double?): String = when {
    v == null -> u.t("ins.kpi.na")
    format == "pct" -> u.pct(v, 1)
    format == "r" -> u.rChip(v)
    format == "usd" -> u.signedMoney(v)
    format == "int" -> u.int(v.toInt())
    else -> u.num(v, 2)
}

/** The live target of a KPI: `targetTemplate` with the parameter's current value (5.8). */
private fun target(u: Ui, template: String?, param: String?, format: String, p: Params): String? {
    if (template == null) return null
    if (param == null) return u.t("ins.kpi.target", "t" to template)
    val v = p[param]
    val shown = when (format) { "pct" -> u.pct(v, 0); "usd" -> u.pct(v, 0); else -> u.num(v, 2, trim = true) }
    return u.t("ins.kpi.target", "t" to template.replace("{v}", shown))
}

/** Insights of 5.8: period chips, the KPI grid, the charts, the setup table and the top mistake. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InsightsScreen(onBack: () -> Unit, vm: InsightsViewModel = hiltViewModel()) {
    val u = ui()
    val st by vm.state.collectAsState()
    val view by vm.view.collectAsState()
    val setups by vm.setups.collectAsState()
    val c = LocalAndarzColors.current
    val names = setups.associate { it.id to it.name }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(u.t("ins.title"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text(u.t("common.back")) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val keys = mapOf(Period.WEEK to "week", Period.MONTH to "month", Period.LAST30 to "last30", Period.ALL to "all", Period.CUSTOM to "custom")
            Period.entries.forEach { p ->
                FilterChip(selected = st.period == p, onClick = {
                    val today = LocalDate.now(u.zone)
                    vm.set { s -> if (p == Period.CUSTOM && s.from == null) s.copy(period = p, from = today.minusDays(29), to = today) else s.copy(period = p) }
                }, label = { Text(u.t("ins.period.${keys[p]}")) })
            }
        }
        if (st.period == Period.CUSTOM && st.from != null && st.to != null) {
            DateStepper(u, u.t("common.from"), st.from!!) { d -> vm.set { it.copy(from = d) } }
            DateStepper(u, u.t("common.to"), st.to!!) { d -> vm.set { it.copy(to = d) } }
        }
        Text(u.t("ins.period.exitDate"), style = MaterialTheme.typography.labelMedium, color = c.textMuted)

        val v = view ?: return@Column
        val ins = v.insights
        val k = ins.kpis

        SectionCard(u.t("ins.kpi.title")) {
            val defs = u.content.arr("kpiDefs").orEmpty().map { it.jsonObject }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2) {
                for (d in defs) {
                    val id = d["id"]!!.jsonPrimitive.content
                    val format = d["format"]?.jsonPrimitive?.content ?: "dec"
                    val statusKey = d["statusKey"]?.jsonPrimitive?.takeIf { it.isString }?.content
                    val status = statusKey?.let { k.status[it] }
                    StatTile(
                        label = d["label"]?.jsonPrimitive?.content ?: id,
                        value = formatKpi(u, format, kpiValue(k, id)),
                        status = status?.let { u.t("ins.status.${it.name}") },
                        statusOk = status?.let(::statusOk),
                        target = target(u, d["targetTemplate"]?.jsonPrimitive?.takeIf { it.isString }?.content, d["targetParam"]?.jsonPrimitive?.takeIf { it.isString }?.content, format, v.params),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            k.realRR?.let { rr ->
                Text(u.t("ins.breakEven", "r" to u.num(rr), "p" to u.pct(Stats.breakEvenWinRate(rr), 1)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }
            Text(u.digits(u.t("ins.expectancy.formula")), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
        }

        val summaryEq = if (ins.equity.isEmpty()) "" else u.t("ins.chart.point", "label" to u.t("common.from"), "value" to u.money(ins.equity.first())) + "\u060C " + u.t("ins.chart.point", "label" to u.t("common.to"), "value" to u.money(ins.equity.last()))
        ChartCard(u.t("ins.chart.equity"), summaryEq, ins.equity.size < 2, u.t("ins.chart.equity.note")) {
            LineChart(ins.equity, c.chartSeries[0], baseline = ins.equity.firstOrNull(), fillDown = true)
        }
        ChartCard(u.t("ins.chart.drawdown"), u.t("ins.chart.point", "label" to u.t("ins.kpi.maxDD"), "value" to u.pct(ins.drawdown.maxOrNull(), 1)), ins.drawdown.size < 2) {
            LineChart(ins.drawdown, c.redline, invert = true, fillDown = true)
        }
        ChartCard(u.t("ins.chart.rHist"), ins.rHistogram.joinToString("\u060C ") { u.t("ins.chart.point", "label" to binLabel(u, it.label), "value" to u.int(it.count)) }, ins.empty) {
            BarChart(ins.rHistogram, { binLabel(u, it.label) }, { u.int(it.count) }, signed = false, color = c.info)
        }
        ChartCard(u.t("ins.chart.compliance"), ins.weeklyCompliance.joinToString("\u060C ") { u.t("ins.chart.point", "label" to u.date(LocalDate.parse(it.label), false), "value" to u.pct(it.value, 0)) }, ins.weeklyCompliance.isEmpty()) {
            BarChart(ins.weeklyCompliance, { u.date(LocalDate.parse(it.label), false).substringBefore(' ') }, { u.pct(it.value, 0) }, signed = false, color = c.allowed)
        }
        ChartCard(u.t("ins.chart.byHour"), ins.byHour.joinToString("\u060C ") { u.t("ins.chart.point", "label" to u.digits(it.label), "value" to u.rChip(it.value)) }, ins.byHour.isEmpty()) {
            BarChart(ins.byHour, { u.digits(it.label) }, { u.rChip(it.value) })
        }
        ChartCard(u.t("ins.chart.bySymbol"), ins.bySymbol.joinToString("\u060C ") { u.t("ins.chart.point", "label" to it.label, "value" to u.rChip(it.value)) }, ins.bySymbol.isEmpty()) {
            RankBars(ins.bySymbol, { u.symbol(it.label) }, { u.rChip(it.value) })
        }
        ChartCard(u.t("ins.chart.bySetup"), ins.bySetup.joinToString("\u060C ") { u.t("ins.chart.point", "label" to (names[it.label] ?: u.t("common.dash")), "value" to u.rChip(it.value)) }, ins.bySetup.isEmpty()) {
            RankBars(ins.bySetup, { names[it.label] ?: u.t("common.dash") }, { u.rChip(it.value) })
        }
        ChartCard(u.t("ins.chart.mistakes"), ins.mistakes.joinToString("\u060C ") { u.t("ins.chart.point", "label" to u.enumLabel("mistake", it.label), "value" to u.int(it.count)) }, ins.mistakes.isEmpty()) {
            RankBars(ins.mistakes, { u.enumLabel("mistake", it.label) }, { u.int(it.count) }, signed = false)
        }
        ChartCard(u.t("ins.chart.violations"), ins.violations.joinToString("\u060C ") { u.t("ins.chart.point", "label" to it.label, "value" to u.int(it.count)) }, ins.violations.isEmpty()) {
            RankBars(ins.violations, { it.label + " " + u.enumLabel("violation", it.label) }, { u.int(it.count) }, signed = false)
        }

        SectionCard(u.t("ins.top.mistake")) {
            Text(ins.topMistake?.let { (id, n) -> u.t("ins.top.mistake.value", "label" to u.enumLabel("mistake", id), "n" to u.int(n)) } ?: u.t("ins.top.none"))
        }

        SectionCard(u.t("ins.setups.title")) {
            if (ins.setups.isEmpty()) Text(u.t("ins.chart.empty"), color = c.textMuted)
            ins.setups.forEachIndexed { i, s ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(names[s.setupId] ?: u.t("common.dash"), style = MaterialTheme.typography.titleSmall)
                        Text(u.t("ins.setups.n", "n" to u.int(s.n)), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(u.t("ins.kpi.winRate") + " " + u.pct(s.winRate, 0), style = MaterialTheme.typography.labelMedium)
                        Text(u.t("ins.kpi.expectancy") + " " + u.rChip(s.expectancy), style = MaterialTheme.typography.labelMedium)
                        Text(u.t("ins.kpi.profitFactor") + " " + u.num(s.profitFactor), style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (s.n < v.params.minTrades) Text(u.t("ins.setups.judge", "min" to u.int(v.params.minTrades.toInt())), style = MaterialTheme.typography.labelMedium, color = c.caution)
            }
        }
    }
}

/** Bin codes of [InsightsLogic.R_BINS] with digits per the setting and a real minus sign. */
internal fun binLabel(u: Ui, code: String): String = u.digits(code.replace("-", "\u2212").replace(">=", "\u2265"))

@Composable
private fun DateStepper(u: Ui, label: String, d: LocalDate, onChange: (LocalDate) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(48.dp))
        TextButton(onClick = { onChange(d.minusDays(30)) }) { Text(u.digits("\u2212" + "30")) }
        TextButton(onClick = { onChange(d.minusDays(1)) }) { Text(u.digits("\u2212" + "1")) }
        Text(u.date(d), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange(d.plusDays(1)) }) { Text(u.digits("+1")) }
        TextButton(onClick = { onChange(d.plusDays(30)) }) { Text(u.digits("+30")) }
    }
}
