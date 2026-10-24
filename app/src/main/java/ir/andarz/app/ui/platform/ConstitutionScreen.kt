package ir.andarz.app.ui.platform

import ir.andarz.app.ui.components.ScreenHeader
import ir.andarz.app.ui.safeLaunch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import ir.andarz.app.ui.components.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import ir.andarz.app.ui.components.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import ir.andarz.app.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.components.SectionCard
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui
import ir.andarz.data.EngineFacade
import ir.andarz.data.PledgeRepository
import ir.andarz.data.db.AndarzDatabase
import ir.andarz.data.db.PledgeEntity
import ir.andarz.data.settings.SettingsRepository
import ir.andarz.engine.Clause
import ir.andarz.engine.Params
import ir.andarz.engine.Ref
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ConstitutionViewModel @Inject constructor(
    db: AndarzDatabase,
    facade: EngineFacade,
    private val settings: SettingsRepository,
    private val pledges: PledgeRepository,
) : ViewModel() {
    val pledge: StateFlow<PledgeEntity?> = db.pledges().observeLatest().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val read: StateFlow<Set<String>> = kotlinx.coroutines.flow.flow { settings.settings.collect { emit(it.readArticles) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    private val _p = MutableStateFlow(Params.DEFAULTS)
    val params: StateFlow<Params> = _p

    init { safeLaunch { _p.value = facade.paramsAt(Instant.now()) } }

    fun markRead(n: String) = safeLaunch { settings.markArticleRead(n) }

    fun sign(version: Int, name: String, text: String) = safeLaunch {
        val p = _p.value
        pledges.sign(version, name, p.startEquity, p.riskPct, text)
    }
}

/** The refs of a clause or list item. */
private fun refsOf(o: JsonObject): List<Ref> = (o["refs"] as? JsonArray).orEmpty().map {
    val r = it.jsonObject
    Ref(r["param"]!!.jsonPrimitive.content, r["literal"]!!.jsonPrimitive.content, r["template"]!!.jsonPrimitive.content, r["fmt"]!!.jsonPrimitive.content)
}

/**
 * A clause rendered with live parameters (3.11). Rendered values are bold and coloured, and the tap target
 * opens Rules ("comes from setting X"). Static Persian digits follow the digit setting (3.10).
 */
@Composable
private fun LiveClause(u: Ui, id: String?, text: String, refs: List<Ref>, p: Params, onRules: () -> Unit) {
    val rendered = runCatching { Clause.render(text, refs, p, u.content.numberWords, fa = !u.content.isLtr) }.getOrDefault(text)
    val values = refs.map { r -> r.template.replace("{v}", runCatching { Clause.formatRef(if (r.param.startsWith("@")) Clause.slFloor(p) else p[r.param], r.fmt, u.content.numberWords, fa = !u.content.isLtr) }.getOrDefault("")) }
    val shown = u.digits(rendered)
    val color = LocalAndarzColors.current.info
    val annotated = buildAnnotatedString {
        var rest = shown
        for (v in values.map { u.digits(it) }) {
            val at = rest.indexOf(v)
            if (at < 0 || v.isEmpty()) continue
            append(rest.substring(0, at))
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(v) }
            rest = rest.substring(at + v.length)
        }
        append(rest)
    }
    Column(Modifier.fillMaxWidth().then(if (refs.isNotEmpty()) Modifier.clickable(role = Role.Button, onClick = onRules) else Modifier).padding(vertical = 4.dp)) {
        if (id != null) Text(u.t("common.clause", "id" to u.digits(id)), style = MaterialTheme.typography.labelMedium, color = LocalAndarzColors.current.textMuted)
        Text(annotated, style = MaterialTheme.typography.bodyLarge)
        if (refs.isNotEmpty()) Text(u.t("con.live.hint"), style = MaterialTheme.typography.labelSmall, color = LocalAndarzColors.current.textMuted)
    }
}

/** Constitution reader of 5.13: intro, contents, live articles, search, read tracking, example, pledge. */
@Composable
fun ConstitutionScreen(onBack: () -> Unit, onRules: () -> Unit, onExample: () -> Unit, onAmend: () -> Unit, vm: ConstitutionViewModel = hiltViewModel()) {
    val u = ui()
    val p by vm.params.collectAsState()
    val read by vm.read.collectAsState()
    val pledge by vm.pledge.collectAsState()
    var article by rememberSaveable { mutableStateOf<Int?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var pledgePage by rememberSaveable { mutableStateOf(false) }
    val con = u.content.obj("constitution") ?: return
    val articles = con["articles"]!!.jsonArray.map { it.jsonObject }
    val c = LocalAndarzColors.current

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader(u.t("con.title"), onBack = { if (article != null || pledgePage) { article = null; pledgePage = false } else onBack() })
        when {
            pledgePage -> PledgePage(u, vm, pledge, con)
            article != null -> {
                val a = articles.first { it["n"]!!.jsonPrimitive.content.toInt() == article }
                val n = a["n"]!!.jsonPrimitive.content
                Text(u.t("con.article", "n" to u.digits(n)) + " \u00B7 " + a["title"]!!.jsonPrimitive.content, style = MaterialTheme.typography.titleMedium)
                for (b in a["blocks"]!!.jsonArray.map { it.jsonObject }) Block(u, b, p, onRules, onExample)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (n !in read) Button(onClick = { vm.markRead(n) }) { Text(u.t("con.mark.read")) }
                    OutlinedButton(onClick = onAmend) { Text(u.t("con.amend")) }
                }
                Text(u.t("con.amend.hint"), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
            }
            else -> {
                Text(u.digits(con["intro"]!!.jsonPrimitive.content), style = MaterialTheme.typography.bodyLarge)
                Text(u.t("con.readProgress", "n" to u.int(read.size), "total" to u.int(articles.size)), style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(query, { query = it }, label = { Text(u.t("con.search")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (query.isNotBlank()) {
                    val hits = articles.flatMap { a ->
                        a["blocks"]!!.jsonArray.map { it.jsonObject }.flatMap { b ->
                            val items = if (b["type"]!!.jsonPrimitive.content == "list") b["items"]!!.jsonArray.map { it.jsonObject } else listOf(b)
                            items.mapNotNull { o -> o["text"]?.jsonPrimitive?.content?.takeIf { it.contains(query.trim()) }?.let { a to o } }
                        }
                    }
                    if (hits.isEmpty()) Text(u.t("con.empty"), color = c.textMuted)
                    hits.take(30).forEach { (a, o) ->
                        Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { article = a["n"]!!.jsonPrimitive.content.toInt() }.padding(vertical = 6.dp)) {
                            Text(u.t("con.article", "n" to u.digits(a["n"]!!.jsonPrimitive.content)), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                            Text(u.digits(o["text"]!!.jsonPrimitive.content), style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                        }
                    }
                } else {
                    SectionCard(u.t("con.toc")) {
                        articles.forEachIndexed { i, a ->
                            if (i > 0) HorizontalDivider()
                            val n = a["n"]!!.jsonPrimitive.content
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { article = n.toInt() }, verticalAlignment = Alignment.CenterVertically) {
                                Text(u.t("con.article", "n" to u.digits(n)) + " \u00B7 " + a["title"]!!.jsonPrimitive.content, modifier = Modifier.weight(1f))
                                if (n in read) Text(u.t("con.mark.read"), style = MaterialTheme.typography.labelSmall, color = c.allowed)
                            }
                        }
                    }
                    OutlinedButton(onClick = { pledgePage = true }, modifier = Modifier.fillMaxWidth()) { Text(u.t("con.pledge")) }
                }
            }
        }
    }
}

@Composable
private fun Block(u: Ui, b: JsonObject, p: Params, onRules: () -> Unit, onExample: () -> Unit) {
    val c = LocalAndarzColors.current
    when (b["type"]!!.jsonPrimitive.content) {
        "clause" -> LiveClause(u, b["id"]?.jsonPrimitive?.content, b["text"]!!.jsonPrimitive.content, refsOf(b), p, onRules)
        "p" -> Text(u.digits(b["text"]!!.jsonPrimitive.content), style = MaterialTheme.typography.bodyLarge)
        "formula" -> CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            // Formulas sit in an LTR box with a monospace-friendly face (5.13).
            Column(Modifier.fillMaxWidth().background(c.surfaceAlt, RoundedCornerShape(8.dp)).padding(12.dp)) {
                Text(u.t("con.formula"), style = MaterialTheme.typography.labelSmall, color = c.textMuted)
                Text(b["text"]!!.jsonPrimitive.content, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
            }
        }
        "list" -> b["items"]!!.jsonArray.map { it.jsonObject }.forEach { o ->
            LiveClause(u, o["id"]?.jsonPrimitive?.content, o["text"]!!.jsonPrimitive.content, refsOf(o), p, onRules)
        }
        "example" -> SectionCard(b["title"]?.jsonPrimitive?.content ?: u.t("con.example")) {
            b["paragraphs"]!!.jsonArray.forEach { Text(u.digits(it.jsonPrimitive.content), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 6.dp)) }
            TextButton(onClick = onExample) { Text(u.t("con.example.try")) }
        }
    }
}

@Composable
private fun PledgePage(u: Ui, vm: ConstitutionViewModel, pledge: PledgeEntity?, con: JsonObject) {
    val p by vm.params.collectAsState()
    val pl = con["pledge"]!!.jsonObject
    val version = pl["version"]!!.jsonPrimitive.content.toInt()
    var name by rememberSaveable { mutableStateOf(pledge?.name ?: "") }
    val text = pl["template"]!!.jsonPrimitive.content
        .replace("{name}", name.ifBlank { u.t("common.dash") })
        .replace("{date}", u.date(LocalDate.now(u.zone), withWeekday = false))
        .replace("{startEquity}", u.money(p.startEquity))
        .replace("{riskPct}", u.pct(p.riskPct))
    SectionCard(u.t("con.pledge")) {
        Text(u.t("con.pledge.version", "v" to u.int(version)), style = MaterialTheme.typography.labelMedium)
        if (pledge != null) {
            Text(u.t("con.pledge.signed", "date" to u.date(u.localDate(Instant.ofEpochMilli(pledge.signedAt)), false)), color = LocalAndarzColors.current.allowed)
            Text(u.digits(pledge.textSnapshot), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
        }
        if (pledge == null || pledge.version != version) {
            OutlinedTextField(name, { name = it }, label = { Text(u.t("onb.setup.name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(u.digits(text), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
            Text(u.t("con.pledge.fields"), style = MaterialTheme.typography.labelMedium, color = LocalAndarzColors.current.textMuted)
            Button(onClick = { vm.sign(version, name.trim(), text) }, enabled = name.isNotBlank()) { Text(u.t(if (pledge == null) "con.pledge.sign" else "con.pledge.resign")) }
        }
    }
}
