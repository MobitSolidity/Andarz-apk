package ir.andarz.app.ui.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.components.ArchShapes
import ir.andarz.app.ui.components.ArchTile
import ir.andarz.app.ui.components.FilterChip
import ir.andarz.app.ui.components.OutlinedButton
import ir.andarz.app.ui.components.Pill
import ir.andarz.app.ui.components.ScreenHeader
import ir.andarz.app.ui.components.SectionCard
import ir.andarz.app.ui.components.Text
import ir.andarz.app.ui.components.TextButton
import ir.andarz.app.ui.components.archSurface
import ir.andarz.app.ui.components.figure
import ir.andarz.app.ui.icons.Glyph
import ir.andarz.app.ui.icons.GlyphIcon
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

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

/** The target line of a `kpiDefs` entry, or null when the entry has none. */
private fun defTarget(u: Ui, d: JsonObject?, p: Params): String? {
    if (d == null) return null
    val format = d["format"]?.jsonPrimitive?.content ?: "dec"
    return target(
        u,
        d["targetTemplate"]?.jsonPrimitive?.takeIf { it.isString }?.content,
        d["targetParam"]?.jsonPrimitive?.takeIf { it.isString }?.content,
        format, p,
    )
}

// ============================================================================ status words

private enum class Tone { OK, CAUTION, DANGER, NEUTRAL }

private fun toneOf(s: KpiStatus?): Tone = when (s) {
    KpiStatus.ON_TARGET, KpiStatus.POSITIVE, KpiStatus.IN_RANGE, KpiStatus.COMPLETE, KpiStatus.ENOUGH_DATA -> Tone.OK
    KpiStatus.BELOW_TARGET, KpiStatus.INCOMPLETE -> Tone.CAUTION
    KpiStatus.NON_POSITIVE, KpiStatus.OVER_CAP -> Tone.DANGER
    else -> Tone.NEUTRAL
}

private class Badge(val text: String, val tone: Tone)

/** The word of a status; the long LOW_DATA sentence of the spec becomes the two words of the pill. */
private fun badge(u: Ui, s: KpiStatus?): Badge =
    if (s == null || s == KpiStatus.LOW_DATA) Badge(u.t("app.ins.lowData"), Tone.NEUTRAL) else Badge(u.t("ins.status.${s.name}"), toneOf(s))

/** The badge of a KPI: its status word, or "low data" when there is no value to judge. */
private fun badgeOf(u: Ui, s: KpiStatus?, hasValue: Boolean): Badge? = when {
    s != null -> badge(u, s)
    !hasValue -> badge(u, null)
    else -> null
}

@Composable
private fun BadgePill(b: Badge, modifier: Modifier = Modifier) {
    val k = LocalAndarzColors.current
    when (b.tone) {
        Tone.OK -> Pill(b.text, k.fzC, k.fz, Glyph.CHECK, 22.dp, modifier)
        Tone.CAUTION -> Pill(b.text, k.sfC, k.sf, Glyph.ALERT_CIRCLE, 22.dp, modifier)
        Tone.DANGER -> Pill(b.text, k.anC, k.an, Glyph.X, 22.dp, modifier)
        Tone.NEUTRAL -> Pill(b.text, k.high, k.hmuted, Glyph.DOTTED_CIRCLE, 22.dp, modifier)
    }
}

private fun plain(s: String) = AnnotatedString(s)

// ============================================================================ the screen

/**
 * Insights of 5.8 in the final canvas: period chips, a bento of the discipline ring with expectancy and R:R, the
 * equity curve with its drawdown strip, a second bento of four small gauges, the remaining figures, the older
 * charts and the setup table. With no closed trade the same frame shows dotted placeholders and one way forward.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InsightsScreen(onBack: () -> Unit, onNewTrade: (() -> Unit)? = null, vm: InsightsViewModel = hiltViewModel()) {
    val u = ui()
    val st by vm.state.collectAsState()
    val view by vm.view.collectAsState()
    val setups by vm.setups.collectAsState()
    val c = LocalAndarzColors.current
    val names = setups.associate { it.id to it.name }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 360.dp && LocalDensity.current.fontScale <= 1.15f
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ScreenHeader(u.t("app.ins.title"), onBack = onBack)
            PeriodRow(u, st) { f -> vm.set(f) }
            if (st.period == Period.CUSTOM && st.from != null && st.to != null) {
                DateStepper(u, u.t("common.from"), st.from!!) { d -> vm.set { it.copy(from = d) } }
                DateStepper(u, u.t("common.to"), st.to!!) { d -> vm.set { it.copy(to = d) } }
            }

            val v = view ?: return@Column
            val ins = v.insights
            val k = ins.kpis
            val p = v.params
            val empty = ins.empty
            val defs = u.content.arr("kpiDefs").orEmpty().map { it.jsonObject }
            fun def(id: String): JsonObject? = defs.firstOrNull { it["id"]?.jsonPrimitive?.content == id }

            // ---- bento 1: discipline ring, expectancy, real R:R
            val discipline: @Composable (Modifier) -> Unit = { m ->
                DisciplineCard(u, k.compliance, p["Compliance_Target"], k.n, p.minTrades.toInt(), k.status["compliance"], empty, m)
            }
            val expectancy: @Composable (Modifier) -> Unit = { m ->
                KpiCard(
                    label = u.t("app.ins.k.expectancy"),
                    value = k.expectancy?.let { plain(u.rChip(it)) },
                    badge = badgeOf(u, k.status["expectancy"], k.expectancy != null),
                    footer = defTarget(u, def("expectancy"), p),
                    modifier = m,
                )
            }
            val realRR: @Composable (Modifier) -> Unit = { m ->
                KpiCard(
                    label = u.t("ins.kpi.realRR"),
                    value = k.realRR?.let { plain(u.num(it, 2)) },
                    badge = badgeOf(u, k.status["realRR"], k.realRR != null),
                    footer = defTarget(u, def("realRR"), p),
                    modifier = m,
                )
            }
            if (wide) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    discipline(Modifier.weight(1f).fillMaxHeight())
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        expectancy(Modifier.weight(1f).fillMaxWidth())
                        realRR(Modifier.weight(1f).fillMaxWidth())
                    }
                }
            } else {
                discipline(Modifier.fillMaxWidth())
                expectancy(Modifier.fillMaxWidth())
                realRR(Modifier.fillMaxWidth())
            }

            // ---- the equity curve, or the empty card
            if (empty) {
                EmptyChartCard(u, k.n, p.minTrades.toInt(), onNewTrade)
            } else {
                EquityPanel(u, ins, p["DD_Cap"])
            }

            // ---- bento 2: profit factor, win rate, max drawdown, checklist
            val pf = k.profitFactor
            val profit: @Composable (Modifier) -> Unit = { m ->
                KpiCard(
                    label = u.t("ins.kpi.profitFactor"),
                    value = pf?.let { plain(u.num(it, 2)) },
                    badge = badgeOf(u, k.status["profitFactor"], pf != null),
                    footer = defTarget(u, def("profitFactor"), p),
                    visual = if (pf == null) null else ({
                        val gain = if (pf >= 1.0) 1f else pf.toFloat()
                        val loss = if (pf >= 1.0) (1.0 / pf).toFloat() else 1f
                        GainLossBars(gain, loss, u.t("app.ins.pf.desc", "pf" to u.num(pf, 2)))
                    }),
                    modifier = m,
                )
            }
            val winRate: @Composable (Modifier) -> Unit = { m ->
                val wr = k.winRate
                val be = k.realRR?.let { Stats.breakEvenWinRate(it) }
                val wins = if (wr == null) 0 else (wr * k.n).roundToInt()
                KpiCard(
                    label = u.t("app.ins.k.winRate"),
                    value = wr?.let { plain(u.pct(it, 0)) },
                    badge = if (wr == null) badge(u, null) else null,
                    footer = if (wr != null && be != null) {
                        u.t("app.ins.win.sub", "p" to u.pct(be, 0), "w" to u.int(wins), "n" to u.int(k.n))
                    } else {
                        u.t("app.ins.win.count", "w" to u.int(wins), "n" to u.int(k.n))
                    },
                    visual = if (wr == null) null else ({
                        MeterBar(
                            fill = wr.toFloat(), tick = be?.toFloat(), fillColor = c.muted, tickColor = c.text,
                            description = u.t("app.ins.win.desc", "p" to u.pct(wr, 0), "b" to u.pct(be, 0)),
                        )
                    }),
                    modifier = m,
                )
            }
            val maxDd: @Composable (Modifier) -> Unit = { m ->
                val dd = k.maxDD
                val cap = p["DD_Cap"]
                val ok = toneOf(k.status["maxDD"]) != Tone.DANGER
                KpiCard(
                    label = u.t("app.ins.k.maxDD"),
                    value = dd?.let { plain(u.pct(it, 2)) },
                    badge = badgeOf(u, k.status["maxDD"], dd != null),
                    footer = u.t("app.calc.cap", "v" to u.pct(cap, 0)),
                    visual = if (dd == null) null else ({
                        MeterBar(
                            fill = if (cap > 0) (dd / (cap * 1.5)).toFloat() else 0f, tick = 1f / 1.5f,
                            fillColor = if (ok) c.fzG else c.anG, tickColor = c.muted, tickWidth = 1.5.dp, tickHeight = 12.dp,
                            description = u.t("app.ins.dd.desc", "p" to u.pct(dd, 2), "c" to u.pct(cap, 0)),
                        )
                    }),
                    modifier = m,
                )
            }
            val checklist: @Composable (Modifier) -> Unit = { m ->
                val avg = k.avgChk
                val n = p["Chk_N"].roundToInt().coerceIn(1, 12)
                val tone = toneOf(k.status["avgChk"])
                KpiCard(
                    label = u.t("ins.kpi.avgChk"),
                    value = avg?.let { checklistValue(u, it, n) },
                    badge = badgeOf(u, k.status["avgChk"], avg != null),
                    footer = u.t("ins.kpi.target", "t" to (u.int(n) + " " + u.t("app.ins.chk.of") + " " + u.int(n))),
                    visual = if (avg == null) null else ({
                        SegmentBar(
                            avg, n, if (tone == Tone.OK) c.fzG else c.sfG,
                            u.t("app.ins.chk.desc", "v" to u.num(avg, 1), "n" to u.int(n)),
                        )
                    }),
                    modifier = m,
                )
            }
            TwoUp(wide, profit, winRate)
            TwoUp(wide, maxDd, checklist)

            if (empty) return@Column

            // ---- the remaining figures
            val extras = defs.filter { it["id"]?.jsonPrimitive?.content in EXTRA_IDS }
            extras.chunked(2).forEach { pair ->
                TwoUp(
                    wide,
                    { m -> ExtraCard(u, pair[0], k, p, m) },
                    if (pair.size > 1) ({ m -> ExtraCard(u, pair[1], k, p, m) }) else null,
                )
            }
            Text(u.digits(u.t("ins.expectancy.formula")), style = MaterialTheme.typography.labelMedium, color = c.textMuted)

            // ---- the charts of the older design, restyled by ChartCard
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
                    if (s.n < p.minTrades) Text(u.t("ins.setups.judge", "min" to u.int(p.minTrades.toInt())), style = MaterialTheme.typography.labelMedium, color = c.caution)
                }
            }
        }
    }
}

/** One of the plain figures under the four gauges, built from its `kpiDefs` entry. */
@Composable
private fun ExtraCard(u: Ui, d: JsonObject, k: Kpis, p: Params, modifier: Modifier) {
    val id = d["id"]!!.jsonPrimitive.content
    val format = d["format"]?.jsonPrimitive?.content ?: "dec"
    val statusKey = d["statusKey"]?.jsonPrimitive?.takeIf { it.isString }?.content
    val value = kpiValue(k, id)
    KpiCard(
        label = d["label"]?.jsonPrimitive?.content ?: id,
        value = value?.let { plain(formatKpi(u, format, it)) },
        badge = statusKey?.let { badgeOf(u, k.status[it], value != null) },
        footer = defTarget(u, d, p),
        modifier = modifier,
    )
}

/** The figures shown as plain cards under the four gauges (`kpiDefs` ids). */
private val EXTRA_IDS = setOf("n", "avgWinR", "avgLossR", "net", "sumR", "withdrawSuggest")

/** Bin codes of [InsightsLogic.R_BINS] with digits per the setting and a real minus sign. */
internal fun binLabel(u: Ui, code: String): String = u.digits(code.replace("-", "\u2212").replace(">=", "\u2265"))

// ============================================================================ period chips

@Composable
private fun PeriodRow(u: Ui, st: InsightsState, update: ((InsightsState) -> InsightsState) -> Unit) {
    val group = u.t("ins.period") + ": " + u.t("ins.period.exitDate")
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).semantics { contentDescription = group },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Period.entries.forEach { p ->
            val label = when (p) {
                Period.WEEK -> u.t("app.ins.p.week")
                Period.MONTH -> u.t("app.ins.p.month")
                Period.LAST30 -> u.t("app.ins.p.last30", "n" to u.int(30))
                Period.ALL -> u.t("app.ins.p.all")
                Period.CUSTOM -> u.t("app.ins.p.custom")
            }
            val full = when (p) {
                Period.WEEK -> u.t("ins.period.week")
                Period.MONTH -> u.t("ins.period.month")
                Period.LAST30 -> u.t("ins.period.last30")
                Period.ALL -> u.t("ins.period.all")
                Period.CUSTOM -> u.t("ins.period.custom")
            }
            FilterChip(
                selected = st.period == p,
                onClick = {
                    val today = LocalDate.now(u.zone)
                    update { s -> if (p == Period.CUSTOM && s.from == null) s.copy(period = p, from = today.minusDays(29), to = today) else s.copy(period = p) }
                },
                label = { Text(label) },
                modifier = Modifier.semantics { contentDescription = full },
            )
        }
    }
}

// ============================================================================ cards

/** Two cards side by side when there is room, else one above the other; [b] may be absent (an odd last card). */
@Composable
private fun TwoUp(wide: Boolean, a: @Composable (Modifier) -> Unit, b: (@Composable (Modifier) -> Unit)?) {
    if (wide) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            a(Modifier.weight(1f).fillMaxHeight())
            if (b != null) b(Modifier.weight(1f).fillMaxHeight()) else Spacer(Modifier.weight(1f))
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            a(Modifier.fillMaxWidth())
            if (b != null) b(Modifier.fillMaxWidth())
        }
    }
}

/**
 * One gauge of the bento: a muted label, the figure in Plex with its status pill at the other end (the pill wraps
 * below the figure on a narrow card), an optional mini visual and the target line. One TalkBack node.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KpiCard(
    label: String,
    value: AnnotatedString?,
    badge: Badge?,
    modifier: Modifier = Modifier,
    footer: String? = null,
    visual: (@Composable () -> Unit)? = null,
) {
    val u = ui()
    val c = LocalAndarzColors.current
    Column(
        modifier.archSurface(RoundedCornerShape(22.dp), c.sf1, c.divider, c.edge)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.muted)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(Modifier.heightIn(min = 27.dp).padding(end = 6.dp), contentAlignment = Alignment.CenterStart) {
                if (value != null) {
                    Text(value, style = figure(24, FontWeight.W600).copy(lineHeight = 27.sp), maxLines = 1, softWrap = false)
                } else {
                    val none = u.t("app.ins.noValue")
                    Box(Modifier.size(width = 18.dp, height = 3.dp).background(c.muted, RoundedCornerShape(2.dp)).semantics { contentDescription = none })
                }
            }
            if (badge != null) Box(Modifier.heightIn(min = 27.dp), contentAlignment = Alignment.Center) { BadgePill(badge) }
        }
        if (visual != null) visual()
        if (footer != null) Text(footer, style = MaterialTheme.typography.labelMedium, color = c.muted)
    }
}

/** The checklist average as "7.9 of 8": the figures in Plex 24, the word between them small and muted. */
@Composable
private fun checklistValue(u: Ui, avg: Double, n: Int): AnnotatedString {
    val c = LocalAndarzColors.current
    val body = MaterialTheme.typography.bodyMedium
    return buildAnnotatedString {
        append(u.num(avg, 1))
        withStyle(SpanStyle(fontFamily = body.fontFamily, fontSize = 14.sp, fontWeight = FontWeight.W500, color = c.muted)) {
            append(" " + u.t("app.ins.chk.of") + " ")
        }
        append(u.int(n))
    }
}

/** The tall card with the arch top: the discipline ring, its name, the sample size and the status. */
@Composable
private fun DisciplineCard(u: Ui, value: Double?, target: Double, n: Int, min: Int, status: KpiStatus?, empty: Boolean, modifier: Modifier = Modifier) {
    val c = LocalAndarzColors.current
    val dotted = empty || value == null
    val b = badge(u, status)
    val targetText = u.pct(target, 0)
    val desc = if (dotted) {
        u.t("app.ins.ring.desc.empty", "t" to targetText)
    } else {
        u.t("app.ins.ring.desc", "v" to u.pct(value, 0), "t" to targetText, "s" to b.text)
    }
    Column(
        modifier.archSurface(ArchShapes.arch(88.dp, 22.dp), c.sf1, c.divider, c.edge)
            .padding(start = 10.dp, top = 22.dp, end = 10.dp, bottom = 10.dp)
            .clearAndSetSemantics { contentDescription = desc },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ComplianceRing(if (dotted) null else value, target, b.tone, if (dotted) u.t("app.ins.lowData") else u.pct(value, 0))
        Spacer(Modifier.height(6.dp))
        Text(
            u.t("app.ins.k.compliance"),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700, lineHeight = 21.sp),
            textAlign = TextAlign.Center,
        )
        if (dotted) {
            Text(u.t("app.ins.sub.empty", "n" to u.int(n), "min" to u.int(min)), style = MaterialTheme.typography.labelMedium, color = c.muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(u.t("app.ins.target.short", "t" to targetText), style = MaterialTheme.typography.labelMedium, color = c.muted, textAlign = TextAlign.Center)
        } else {
            Text(u.t("app.ins.sub", "n" to u.int(n), "t" to targetText), style = MaterialTheme.typography.labelMedium, color = c.muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            BadgePill(b)
        }
    }
}

/**
 * The ring of the discipline card: a track, the share of rule-abiding trades as an arc from the top (clockwise in
 * LTR, counter-clockwise in RTL, so it reads from the start edge) and a tick at the target. Without data the
 * track is dotted and the centre says "low data".
 */
@Composable
private fun ComplianceRing(fraction: Double?, target: Double, tone: Tone, label: String) {
    val c = LocalAndarzColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val progress = when (tone) {
        Tone.OK -> c.fzG
        Tone.CAUTION -> c.sfG
        Tone.DANGER -> c.anG
        Tone.NEUTRAL -> c.hmuted
    }
    Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 8.25.dp.toPx()
            val r = 36.67.dp.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)
            val dir = if (rtl) -1f else 1f
            if (fraction == null) {
                drawCircle(
                    c.outline.copy(alpha = 0.55f), r, center,
                    style = Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.08.dp.toPx(), 4.31.dp.toPx()))),
                )
            } else {
                drawCircle(c.track, r, center, style = Stroke(sw))
                val f = fraction.coerceIn(0.0, 1.0).toFloat()
                if (f > 0f) {
                    drawArc(
                        progress, -90f, dir * 360f * f, false,
                        topLeft = Offset(center.x - r, center.y - r), size = Size(2f * r, 2f * r),
                        style = Stroke(sw, cap = StrokeCap.Round),
                    )
                }
            }
            val a = Math.toRadians(-90.0 + dir * 360.0 * target.coerceIn(0.0, 1.0))
            val ca = Math.cos(a).toFloat()
            val sa = Math.sin(a).toFloat()
            val inner = 30.25.dp.toPx()
            val outer = 43.1.dp.toPx()
            drawLine(
                c.text, Offset(center.x + inner * ca, center.y + inner * sa), Offset(center.x + outer * ca, center.y + outer * sa),
                strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
            )
        }
        // The centre text grows with the font setting up to a limit, so it stays inside the ring.
        CompositionLocalProvider(LocalDensity provides Density(density.density, minOf(density.fontScale, 1.3f))) {
            if (fraction == null) {
                Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700), color = c.hmuted, textAlign = TextAlign.Center)
            } else {
                Text(label, style = figure(26, FontWeight.W600), maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Profit factor: one bar of gains and one of losses, the longer one full width. */
@Composable
private fun GainLossBars(gain: Float, loss: Float, description: String) {
    val c = LocalAndarzColors.current
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(Modifier.fillMaxWidth(gain.coerceIn(0.02f, 1f)).height(4.dp).background(c.fzG, RoundedCornerShape(2.dp)))
        Box(Modifier.fillMaxWidth(loss.coerceIn(0.02f, 1f)).height(4.dp).background(c.anG, RoundedCornerShape(2.dp)))
    }
}

/** A track with a fill from the start edge and an optional tick (the win rate's break-even, the drawdown cap). */
@Composable
private fun MeterBar(
    fill: Float,
    tick: Float?,
    fillColor: Color,
    tickColor: Color,
    description: String,
    tickWidth: Dp = 2.dp,
    tickHeight: Dp = 14.dp,
) {
    val c = LocalAndarzColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).height(14.dp).clearAndSetSemantics { contentDescription = description },
    ) {
        val w = size.width
        val h = 8.dp.toPx()
        val top = (size.height - h) / 2f
        val radius = CornerRadius(4.dp.toPx())
        drawRoundRect(c.track, Offset(0f, top), Size(w, h), radius)
        val fw = fill.coerceIn(0f, 1f) * w
        if (fw > 0f) drawRoundRect(fillColor, Offset(if (rtl) w - fw else 0f, top), Size(fw, h), radius)
        if (tick != null) {
            val along = tick.coerceIn(0f, 1f) * w
            val x = if (rtl) w - along else along
            val half = tickHeight.toPx() / 2f
            drawLine(tickColor, Offset(x, size.height / 2f - half), Offset(x, size.height / 2f + half), strokeWidth = tickWidth.toPx(), cap = StrokeCap.Round)
        }
    }
}

/** The checklist average as [segments] short bars, each as full as the average reaches it. */
@Composable
private fun SegmentBar(avg: Double, segments: Int, fillColor: Color, description: String) {
    val c = LocalAndarzColors.current
    Row(
        Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 7.dp).clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for (i in 0 until segments) {
            val f = (avg - i).coerceIn(0.0, 1.0).toFloat()
            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.track)) {
                if (f > 0f) Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(fillColor))
            }
        }
    }
}

// ============================================================================ equity and drawdown

/** The height of a 12 sp label at the 1.4 line height of the canvas, in dp. */
private const val LABEL_H = 17f

/**
 * The equity panel: the P&L-only curve over a drawdown strip that shares its time axis, with the end value, the
 * start line, the deepest drawdown and the cap named in the chart. The list button swaps the picture for the
 * same numbers as text (6.5). Time runs left to right in both directions.
 */
@Composable
private fun EquityPanel(u: Ui, ins: Insights, cap: Double) {
    val c = LocalAndarzColors.current
    var table by rememberSaveable { mutableStateOf(false) }
    val eq = ins.equity
    val dd = ins.drawdown
    val maxDd = dd.maxOrNull() ?: 0.0
    val summary = u.t(
        "app.ins.equity.summary",
        "a" to u.money(eq.firstOrNull(), 0), "b" to u.money(eq.lastOrNull(), 0), "n" to u.int(dd.size), "dd" to u.pct(maxDd, 2),
    )
    val alt = u.t("ins.chart.alt", "title" to u.t("app.ins.equity.alt"), "summary" to summary)
    val title = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.W700)) { append(u.t("app.ins.equity")) }
        withStyle(SpanStyle(fontWeight = FontWeight.W400, color = c.muted)) { append(" \u00B7 " + u.t("app.ins.equity.note")) }
    }
    Column(
        Modifier.fillMaxWidth().archSurface(RoundedCornerShape(24.dp), c.sf1, c.divider, c.edge).padding(start = 14.dp, end = 6.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), modifier = Modifier.weight(1f).semantics { heading() })
            IconButton(onClick = { table = !table }, modifier = Modifier.size(48.dp)) {
                GlyphIcon(Glyph.LINES, u.t(if (table) "common.less" else "app.ins.table"), tint = if (table) c.fz else c.muted, size = 20.dp, strokeWidth = 2f)
            }
        }
        Column(Modifier.fillMaxWidth().padding(end = 8.dp)) {
            if (eq.size < 2) {
                Text(u.t("ins.chart.empty"), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            } else if (table) {
                Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(u.t("app.ins.row.start", "eq" to u.money(eq.first())), style = MaterialTheme.typography.bodyMedium)
                    for (i in 1 until eq.size) {
                        Text(
                            u.t("app.ins.row", "i" to u.int(i), "eq" to u.money(eq[i]), "dd" to u.pct(dd.getOrNull(i - 1), 1)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    EquityChart(u, eq, dd, cap, ins.firstExit, ins.lastExit, alt)
                }
            }
        }
    }
}

/** "2 Mehr" from a date: the display calendar's day and month, without the year. */
private fun dayMonth(u: Ui, d: LocalDate): String = u.date(d, false).substringBeforeLast(' ')

@Composable
private fun EquityChart(u: Ui, equity: List<Double>, drawdown: List<Double>, cap: Double, firstExit: LocalDate?, lastExit: LocalDate?, description: String) {
    val c = LocalAndarzColors.current
    val density = LocalDensity.current
    val eq = remember(equity) { decimate(equity) }
    // The strip starts flat at the first value so both series have the same time axis.
    val dd = remember(drawdown) { decimate(if (drawdown.isEmpty()) listOf(0.0, 0.0) else listOf(drawdown.first()) + drawdown) }
    val start = equity.first()
    val last = equity.last()
    val lo = minOf(eq.min(), start)
    val hi = maxOf(eq.max(), start)
    val span = (hi - lo).takeIf { it > 0.0 } ?: 1.0
    // Geometry in dp: the equity region is 0..92, the strip 98..134 (baseline at 100, the cap line at 134).
    fun eqY(v: Double): Float = 8f + (1f - ((v - lo) / span).toFloat()) * 78f
    fun ratio(v: Double): Float = if (cap > 0.0) (v / cap).coerceIn(0.0, 1.0).toFloat() else 0f
    fun ddY(v: Double): Float = 100f + ratio(v) * 34f

    val endY = eqY(last)
    val baseY = eqY(start)
    val rising = eq.size < 2 || eq.last() >= eq[eq.size - 2]
    fun fits(t: Float) = t >= 0f && t + LABEL_H <= 92f
    val endTops = (if (rising) listOf(endY - 22f, endY + 5f) else listOf(endY + 5f, endY - 22f)).filter { fits(it) }
    val startTops = (if (last >= start) listOf(baseY + 2f, baseY - 19f) else listOf(baseY - 19f, baseY + 2f)).filter { fits(it) }
    var endTop = endTops.firstOrNull() ?: (endY - 22f).coerceIn(0f, 92f - LABEL_H)
    var startTop: Float? = null
    pairs@ for (e in endTops) {
        for (s in startTops) {
            if (abs(e - s) >= LABEL_H) { endTop = e; startTop = s; break@pairs }
        }
    }

    val iMax = dd.indices.maxByOrNull { dd[it] } ?: 0
    val maxVal = dd.getOrElse(iMax) { 0.0 }
    val markerFrac = if (dd.size > 1) iMax.toFloat() / (dd.size - 1) else 0f
    val markerY = ddY(maxVal)
    val label = MaterialTheme.typography.labelMedium.copy(lineHeight = 17.sp)

    // Labels keep their size and place in the picture; the same numbers are in the description and the table.
    CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
        Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(136.dp)) {
                val w = maxWidth.value
                Canvas(Modifier.fillMaxSize()) {
                    fun px(v: Float): Float = v.dp.toPx()
                    val wpx = size.width
                    val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))

                    // equity: the start line, the shaded area, the curve and its end dot
                    val stepX = if (eq.size > 1) wpx / (eq.size - 1) else 0f
                    drawLine(c.outline.copy(alpha = 0.7f), Offset(0f, px(baseY)), Offset(wpx, px(baseY)), strokeWidth = 1.dp.toPx(), pathEffect = dash)
                    val line = Path()
                    eq.forEachIndexed { i, v -> if (i == 0) line.moveTo(0f, px(eqY(v))) else line.lineTo(i * stepX, px(eqY(v))) }
                    val area = Path().apply { addPath(line); lineTo(wpx, px(92f)); lineTo(0f, px(92f)); close() }
                    drawPath(area, Brush.verticalGradient(listOf(c.fzG.copy(alpha = 0.22f), c.fzG.copy(alpha = 0f)), startY = 0f, endY = px(92f)))
                    drawPath(line, c.fzG, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    val endDot = Offset(wpx, px(endY))
                    drawCircle(c.fzG.copy(alpha = 0.22f), 7.dp.toPx(), endDot)
                    drawCircle(c.fzG, 3.4.dp.toPx(), endDot)

                    // drawdown: the baseline, the cap line, the area, the curve and the marker at the deepest point
                    val stepD = if (dd.size > 1) wpx / (dd.size - 1) else 0f
                    drawLine(c.divider, Offset(0f, px(100f)), Offset(wpx, px(100f)), strokeWidth = 1.dp.toPx())
                    drawLine(c.anG.copy(alpha = 0.7f), Offset(0f, px(134f)), Offset(wpx, px(134f)), strokeWidth = 1.dp.toPx(), pathEffect = dash)
                    val dLine = Path()
                    dd.forEachIndexed { i, v -> if (i == 0) dLine.moveTo(0f, px(ddY(v))) else dLine.lineTo(i * stepD, px(ddY(v))) }
                    val dArea = Path().apply {
                        moveTo(0f, px(100f))
                        dd.forEachIndexed { i, v -> lineTo(i * stepD, px(ddY(v))) }
                        lineTo(wpx, px(100f))
                        close()
                    }
                    drawPath(dArea, c.anG.copy(alpha = 0.2f))
                    drawPath(dLine, c.anG, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawCircle(c.anG, 3.dp.toPx(), Offset(markerFrac * wpx, px(markerY)))
                }

                Text(u.money(last, 0), style = label.copy(fontWeight = FontWeight.W700), color = c.text, modifier = Modifier.align(Alignment.TopEnd).offset(y = endTop.dp))
                startTop?.let { t ->
                    Text(u.t("app.ins.start", "v" to u.money(start, 0)), style = label, color = c.muted, modifier = Modifier.align(Alignment.TopEnd).offset(y = t.dp))
                }
                Text(u.t("ins.chart.drawdown"), style = label, color = c.muted, modifier = Modifier.align(Alignment.TopStart).offset(y = 106.dp))
                Text(u.t("app.calc.cap", "v" to u.pct(cap, 0)), style = label, color = c.muted, modifier = Modifier.align(Alignment.TopEnd).offset(y = 116.dp))
                // The deepest drawdown is named beside its marker, on the side with room.
                val markerX = w * markerFrac
                val labelY = (markerY - 8.5f).coerceIn(100f, 119f).dp
                if (markerFrac > 0.6f) {
                    Box(Modifier.align(Alignment.TopStart).offset(x = (markerX - 8f - 120f).dp, y = labelY).width(120.dp), contentAlignment = Alignment.TopEnd) {
                        Text(u.pct(-maxVal, 2), style = label.copy(fontWeight = FontWeight.W700), color = c.an)
                    }
                } else {
                    Box(Modifier.align(Alignment.TopStart).offset(x = maxOf(markerX + 8f, 66f).dp, y = labelY).width(120.dp), contentAlignment = Alignment.TopStart) {
                        Text(u.pct(-maxVal, 2), style = label.copy(fontWeight = FontWeight.W700), color = c.an)
                    }
                }
            }
            if (firstExit != null && lastExit != null) {
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(dayMonth(u, firstExit), style = label, color = c.muted)
                    Text(dayMonth(u, lastExit), style = label, color = c.muted)
                }
            }
        }
    }
}

// ============================================================================ empty state

/** No closed trade yet: one sentence, a bar toward the number of trades needed to judge, and the way forward. */
@Composable
private fun EmptyChartCard(u: Ui, n: Int, min: Int, onNewTrade: (() -> Unit)?) {
    val c = LocalAndarzColors.current
    val progressText = u.t("app.ins.sub.empty", "n" to u.int(n), "min" to u.int(min))
    val barLabel = u.t("app.ins.empty.bar")
    val fraction = if (min > 0) (n.toFloat() / min).coerceIn(0f, 1f) else 0f
    Column(
        Modifier.fillMaxWidth().archSurface(ArchShapes.arch(28.dp, 18.dp), c.sf1, c.divider, c.edge).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ArchTile(c.alt, width = 44.dp, height = 52.dp, bottom = 12.dp) {
            GlyphIcon(Glyph.LINE_CHART, null, tint = c.lapis, size = 22.dp, strokeWidth = 2f)
        }
        Text(
            u.t("app.ins.empty.text"),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 25.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 290.dp),
        )
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(u.t("app.ins.empty.need", "min" to u.int(min)), style = MaterialTheme.typography.labelMedium, color = c.muted, modifier = Modifier.weight(1f))
                Text(u.t("app.ins.empty.count", "n" to u.int(n), "min" to u.int(min)), style = MaterialTheme.typography.labelMedium, color = c.muted, maxLines = 1, softWrap = false)
            }
            Box(
                Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.track).semantics {
                    contentDescription = barLabel
                    stateDescription = progressText
                    progressBarRangeInfo = ProgressBarRangeInfo(n.toFloat().coerceAtMost(min.toFloat()), 0f..min.coerceAtLeast(1).toFloat())
                },
            ) {
                if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(c.fzG))
            }
        }
        if (onNewTrade != null) {
            OutlinedButton(onClick = onNewTrade, modifier = Modifier.fillMaxWidth()) {
                GlyphIcon(Glyph.PLUS, null, size = 18.dp, strokeWidth = 2.4f)
                Spacer(Modifier.width(8.dp))
                Text(u.t("today.newTrade"))
            }
        }
    }
}

// ============================================================================ custom period

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
