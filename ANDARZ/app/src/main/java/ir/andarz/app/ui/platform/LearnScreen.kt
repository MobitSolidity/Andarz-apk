package ir.andarz.app.ui.platform

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.components.NumberField
import ir.andarz.app.ui.components.SectionCard
import ir.andarz.app.ui.components.TagChip
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui
import ir.andarz.engine.Fmt
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The expectancy explainer of 5.13 (`E = p × R − (1 − p)`), pure so it is unit-tested (D-69). */
object LearnLogic {
    /** Expectancy in R per trade for win rate [p] (0..1) and reward-to-risk [r]; null when out of range. */
    fun expectancy(p: Double?, r: Double?): Double? {
        if (p == null || r == null || !p.isFinite() || !r.isFinite()) return null
        if (p < 0 || p > 1 || r <= 0) return null
        return p * r - (1 - p)
    }

    /** Break-even win rate `1 / (1 + R)`. */
    fun breakEven(r: Double?): Double? = if (r == null || !r.isFinite() || r <= 0) null else 1 / (1 + r)

    /** The calculator takes the win rate in percent, as people say it ("40"). */
    fun fromPercent(text: String): Double? = Fmt.normalizeInput(text)?.let { it / 100 }
}

/** The five learn sections (5.13). */
private enum class LearnTab(val key: String) { SUMMARY("learn.summary"), REVIEW("learn.review"), ADDITIONS("learn.additions"), JOURNAL("learn.journalGuide"), EXPECTANCY("learn.expectancy") }

/**
 * Learn pages (5.13, P1): summary (thesis, four axes, twelve lessons), the review of experiences, the ten
 * additions, the journal guide and the expectancy explainer with its mini calculator. All wording is
 * `content.learn`; static digits follow the digit setting (3.10).
 */
@Composable
fun LearnScreen(onBack: () -> Unit) {
    val u = ui()
    var tab by rememberSaveable { mutableStateOf(LearnTab.SUMMARY) }
    val learn = u.content.obj("learn")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(u.t("learn.title"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
            TextButton(onClick = onBack) { Text(u.t("common.back")) }
        }
        // A vertical list of section buttons instead of tabs: it wraps at 200% font (6.8).
        LearnTab.entries.forEach { t ->
            val on = t == tab
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Tab) { tab = t }
                    .semantics(mergeDescendants = true) { stateDescription = u.t(if (on) "a11y.toggleOn" else "a11y.toggleOff") },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    u.t(t.key), style = MaterialTheme.typography.bodyLarge,
                    color = if (on) MaterialTheme.colorScheme.primary else LocalAndarzColors.current.textMuted,
                )
            }
        }
        if (learn == null) {
            Text(u.t("err.generic"))
            return@Column
        }
        when (tab) {
            LearnTab.SUMMARY -> Summary(u, learn["summary"]?.jsonObject)
            LearnTab.REVIEW -> Review(u, learn["review"]?.jsonObject)
            LearnTab.ADDITIONS -> Additions(u, learn["additions"]?.jsonObject)
            LearnTab.JOURNAL -> JournalGuide(u, learn["journal"]?.jsonObject)
            LearnTab.EXPECTANCY -> Expectancy(u, learn["additions"]?.jsonObject?.get("expectancy")?.jsonObject)
        }
        Text(u.digits(str(learn["journal"]?.jsonObject, "disclaimer")), style = MaterialTheme.typography.labelMedium, color = LocalAndarzColors.current.textMuted)
    }
}

private fun str(o: JsonObject?, key: String): String = (o?.get(key) as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""

private fun list(o: JsonObject?, key: String): List<JsonObject> = (o?.get(key) as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

@Composable
private fun Para(u: Ui, text: String, muted: Boolean = false) {
    if (text.isBlank()) return
    Text(u.digits(text), style = MaterialTheme.typography.bodyMedium, color = if (muted) LocalAndarzColors.current.textMuted else androidx.compose.ui.graphics.Color.Unspecified)
}

@Composable
private fun Summary(u: Ui, o: JsonObject?) {
    SectionCard(u.t("learn.summary")) { Para(u, str(o, "thesis")) }
    SectionCard(u.t("learn.axes")) {
        Para(u, str(o, "axesIntro"), muted = true)
        list(o, "axes").forEach { a ->
            Text(str(a, "title"), style = MaterialTheme.typography.titleSmall)
            Para(u, str(a, "text"))
        }
    }
    SectionCard(u.t("learn.lessons")) {
        Para(u, str(o, "lessonsIntro"), muted = true)
        list(o, "lessons").forEach { l -> Para(u, str(l, "n") + ". " + str(l, "text")) }
    }
    SectionCard(null) { Para(u, str(o, "keyInsights")) }
}

@Composable
private fun Review(u: Ui, o: JsonObject?) {
    Para(u, str(o, "intro"), muted = true)
    list(o, "items").forEach { it ->
        val title = u.digits(str(it, "n")) + ". " + str(it, "title")
        SectionCard(title) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (it["needsFix"]?.jsonPrimitive?.booleanOrNull == true) TagChip(u.t("learn.needsFix"), LocalAndarzColors.current.caution)
                if (it["important"]?.jsonPrimitive?.booleanOrNull == true) TagChip(u.t("learn.important"), LocalAndarzColors.current.redline)
            }
            Labelled(u, "learn.experience", str(it, "experience"))
            Labelled(u, "learn.correction", str(it, "correction"))
            Labelled(u, "learn.reason", str(it, "reason"))
            Labelled(u, "learn.solution", str(it, "solution"))
        }
    }
}

@Composable
private fun Labelled(u: Ui, key: String, text: String) {
    if (text.isBlank()) return
    Text(u.t(key), style = MaterialTheme.typography.labelLarge, color = LocalAndarzColors.current.textMuted)
    Para(u, text)
}

@Composable
private fun Additions(u: Ui, o: JsonObject?) {
    Para(u, str(o, "intro"), muted = true)
    list(o, "items").forEach { it ->
        SectionCard(u.digits(str(it, "n")) + ". " + str(it, "title")) {
            Labelled(u, "learn.reason", str(it, "reason"))
            Labelled(u, "learn.solution", str(it, "solution"))
        }
    }
}

@Composable
private fun JournalGuide(u: Ui, o: JsonObject?) {
    SectionCard(u.t("learn.journalGuide")) {
        Para(u, str(o, "intro"))
        Para(u, str(o, "flowCaption"), muted = true)
        Para(u, str(o, "flowNote"))
    }
    SectionCard(u.t("learn.stages")) {
        list(o, "stages").forEach { s ->
            Text(str(s, "stage"), style = MaterialTheme.typography.titleSmall)
            Para(u, str(s, "fields"))
        }
        Para(u, str(o, "stagesNote"), muted = true)
    }
    SectionCard(u.t("learn.mistakes")) {
        Para(u, str(o, "mistakeNote"))
        u.enumIds("mistake").forEach { id -> Para(u, u.enumLabel("mistake", id)) }
    }
    SectionCard(u.t("learn.quickTemplate")) { Para(u, str(o, "quickTemplate")) }
}

@Composable
private fun Expectancy(u: Ui, o: JsonObject?) {
    SectionCard(u.t("learn.expectancy")) {
        Para(u, str(o, "lead"))
        Text(Fmt.isolate(str(o, "formula")), style = MaterialTheme.typography.titleMedium)
        Para(u, str(o, "example"))
        Para(u, str(o, "breakEvenNote"), muted = true)
    }
    var pText by rememberSaveable { mutableStateOf("40") }
    var rText by rememberSaveable { mutableStateOf("2") }
    val p = LearnLogic.fromPercent(pText)
    val r = Fmt.normalizeInput(rText)
    val e = LearnLogic.expectancy(p, r)
    SectionCard(u.t("learn.calc.expectancy")) {
        NumberField(u.t("learn.calc.p"), pText, { pText = it }, suffix = "%", error = if (p == null || p < 0 || p > 1) u.t("err.number") else null)
        NumberField(u.t("learn.calc.r"), rText, { rText = it }, error = if (r == null || r <= 0) u.t("err.positive") else null)
        if (e != null) {
            val c = LocalAndarzColors.current
            Text(
                u.t("learn.calc.result", "e" to Fmt.isolate(Fmt.signed(e, 2, u.fa))),
                style = MaterialTheme.typography.titleMedium, color = if (e > 0) c.allowed else c.redline,
                modifier = Modifier.semantics { stateDescription = u.t(if (e > 0) "a11y.positive" else "a11y.negative") },
            )
            LearnLogic.breakEven(r)?.let { be ->
                Text(u.t("ins.breakEven", "r" to u.num(r, 2, trim = true), "p" to u.pct(be, 0)), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
