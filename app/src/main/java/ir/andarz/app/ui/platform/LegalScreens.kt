package ir.andarz.app.ui.platform

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import ir.andarz.app.content.Legal
import ir.andarz.app.content.LegalDoc
import ir.andarz.app.ui.components.Button
import ir.andarz.app.ui.components.ScreenHeader
import ir.andarz.app.ui.components.SectionCard
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui
import ir.andarz.engine.Fmt
import java.time.Instant

/** Tabs over the three legal documents; [selected] is the document id. */
@Composable
private fun LegalTabs(legal: Legal, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        legal.docs.forEach { d -> FilterChip(selected = d.id == selected, onClick = { onSelect(d.id) }, label = { Text(d.title) }) }
    }
}

@Composable
private fun LegalBody(doc: LegalDoc) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        doc.sections.forEach { s ->
            Text(s.heading, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            s.paragraphs.forEach { p -> Text(p, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun LegalFooter(legal: Legal) {
    val u = ui()
    val c = LocalAndarzColors.current
    Text(u.t("app.legal.updated", "date" to u.digits(legal.updated)), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
    Text(u.t("app.legal.online", "url" to Fmt.isolate(legal.url)), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
    Text(u.t("app.legal.contact", "email" to Fmt.isolate(legal.contact)), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
}

/** Terms, privacy and risk, always reachable from More, Settings and About (D-96; Myket and Bazaar privacy rules). */
@Composable
fun LegalScreen(onBack: () -> Unit, initial: String = "terms") {
    val u = ui()
    val legal = u.content.legal ?: return
    var selected by rememberSaveable { mutableStateOf(initial) }
    val doc = legal.doc(selected) ?: legal.docs.first()
    val c = LocalAndarzColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader(u.t("app.legal.title"), onBack = onBack)
        LegalTabs(legal, doc.id) { selected = it }
        SectionCard(doc.title) { LegalBody(doc) }
        val at = u.settings.legalAcceptedAt
        if (legal.isAccepted(u.settings.legalAcceptedVersion) && at > 0) {
            Text(u.t("app.legal.accepted", "date" to u.date(u.localDate(Instant.ofEpochMilli(at)), false)), style = MaterialTheme.typography.labelLarge, color = c.allowed)
        }
        LegalFooter(legal)
    }
}

/**
 * The first-run agreement (D-96). Shown before onboarding and again whenever the legal version rises. Acceptance
 * needs two direct actions, ticking the box and tapping the button, as Myket and Bazaar require; back, home or a
 * timeout never count as consent. There is no decline button: a user who does not agree simply leaves the app.
 */
@Composable
fun LegalGateScreen(onAccept: (Int) -> Unit) {
    val u = ui().unmasked()
    val legal = u.content.legal ?: return
    var selected by rememberSaveable { mutableStateOf("terms") }
    var checked by rememberSaveable { mutableStateOf(false) }
    val doc = legal.doc(selected) ?: legal.docs.first()
    val c = LocalAndarzColors.current
    val again = u.settings.legalAcceptedVersion in 1 until legal.version
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Text(u.t("app.legal.gate.title"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(u.t(if (again) "app.legal.gate.updated" else "app.legal.gate.body"), style = MaterialTheme.typography.bodyMedium, color = c.caution, modifier = Modifier.padding(vertical = 8.dp))
        LegalTabs(legal, doc.id) { selected = it }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(doc.title, style = MaterialTheme.typography.titleMedium)
            LegalBody(doc)
            LegalFooter(legal)
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Checkbox) { checked = !checked }.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = null)
            Text(u.t("app.legal.gate.check"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
        }
        Button(onClick = { onAccept(legal.version) }, enabled = checked, modifier = Modifier.fillMaxWidth()) { Text(u.t("app.legal.gate.accept")) }
    }
}
