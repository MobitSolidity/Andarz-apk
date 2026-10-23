package ir.andarz.app.ui.platform

import ir.andarz.app.ui.components.ScreenHeader
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
import ir.andarz.app.ui.components.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import ir.andarz.app.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import ir.andarz.app.BuildConfig
import ir.andarz.app.ui.components.SectionCard
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui
import ir.andarz.data.db.AndarzDatabase
import ir.andarz.data.db.SecurityCheckEntity
import ir.andarz.data.settings.Settings
import ir.andarz.data.settings.SettingsRepository
import ir.andarz.engine.Fmt
import ir.andarz.market.MarketModule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class SecurityViewModel @Inject constructor(private val db: AndarzDatabase, private val settings: SettingsRepository) : ViewModel() {
    val checks: StateFlow<List<SecurityCheckEntity>> = db.security().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val s: StateFlow<Settings?> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun toggle(id: String, on: Boolean) = safeLaunch { db.security().put(SecurityCheckEntity(id, on, if (on) System.currentTimeMillis() else null)) }

    /** `sec.markAll`: every clause checked and `lastSecurityReviewAt` set (5.13). */
    fun markAll(ids: List<String>) = safeLaunch {
        val now = System.currentTimeMillis()
        ids.forEach { db.security().put(SecurityCheckEntity(it, true, now)) }
        settings.setLastSecurityReviewAt(now)
    }
}

/** Security checklist of article 10 (5.13, P1). */
@Composable
fun SecurityScreen(onBack: () -> Unit, vm: SecurityViewModel = hiltViewModel()) {
    val u = ui()
    val checks by vm.checks.collectAsState()
    val s by vm.s.collectAsState()
    val items = u.content.arr("security").orEmpty().map { it.jsonObject }
    val ids = items.map { it["id"]!!.jsonPrimitive.content }
    val done = checks.filter { it.checked }.map { it.clauseId }.toSet()
    val c = LocalAndarzColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScreenHeader(u.t("sec.title"), onBack = onBack)
        Text(u.t("sec.subtitle"), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        val last = s?.lastSecurityReviewAt ?: 0
        Text(if (last > 0) u.t("sec.lastChecked", "date" to u.date(u.localDate(Instant.ofEpochMilli(last)), false)) else u.t("sec.never"), style = MaterialTheme.typography.labelLarge)
        val missing = ids.count { it !in done }
        Text(if (missing == 0) u.t("sec.complete") else u.t("sec.incomplete", "n" to u.int(missing)), color = if (missing == 0) c.allowed else c.caution)
        items.forEach { o ->
            val id = o["id"]!!.jsonPrimitive.content
            val on = id in done
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Checkbox) { vm.toggle(id, !on) }
                    .semantics(mergeDescendants = true) { stateDescription = u.t(if (on) "a11y.toggleOn" else "a11y.toggleOff") },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = on, onCheckedChange = null)
                Column(Modifier.padding(start = 8.dp)) {
                    Text(u.t("common.clause", "id" to u.digits(id)), style = MaterialTheme.typography.labelSmall, color = c.textMuted)
                    Text(u.digits(o["text"]!!.jsonPrimitive.content), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Button(onClick = { vm.markAll(ids) }, modifier = Modifier.fillMaxWidth()) { Text(u.t("sec.markAll")) }
    }
}

/**
 * About (5.15) with the network page of K6.9: the journal works offline; Market, when the user turns it on,
 * reads prices from the hosts listed here and sends nothing but coin symbols. The March credit line is the
 * one sanctioned mention (mkt.about.credit). Threat model (8.1): the app protects against someone who picks
 * up the phone, other apps and a stolen backup; it is not tamper-proof against its owner, and Market uses
 * the internet.
 */
@Composable
fun AboutScreen(onBack: () -> Unit, onLicenses: () -> Unit) {
    val u = ui()
    val c = LocalAndarzColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader(u.t("about.title"), onBack = onBack)
        Text(u.content.appName + " \u00B7 " + u.content.tagline, style = MaterialTheme.typography.titleMedium)
        Text(u.t("set.version", "v" to u.digits(BuildConfig.VERSION_NAME)) + " \u00B7 " + u.t("set.build", "b" to u.int(BuildConfig.VERSION_CODE)) + " \u00B7 " + Fmt.isolate(BuildConfig.GIT_COMMIT), style = MaterialTheme.typography.labelLarge)
        Text(u.t("about.meaning"), style = MaterialTheme.typography.bodyMedium)
        Text(u.t("about.madeFor"), style = MaterialTheme.typography.bodyMedium)
        SectionCard(null) { Text(u.t("about.privacy"), style = MaterialTheme.typography.bodyMedium) }
        SectionCard(u.t("mkt.net.title")) {
            Text(u.t("mkt.net.body"), style = MaterialTheme.typography.bodyMedium)
            Text(u.t("mkt.net.sent"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp))
            MarketModule.hosts.forEach { Text(Fmt.isolate(it), style = MaterialTheme.typography.labelMedium, color = c.textMuted) }
        }
        Text(u.t("about.disclaimer"), style = MaterialTheme.typography.bodyMedium, color = c.caution)
        Text(u.t("about.fonts"), style = MaterialTheme.typography.labelMedium)
        Text(u.t("mkt.about.credit"), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
        TextButton(onClick = onLicenses) { Text(u.t("set.licenses")) }
    }
}

/** Open-source licenses, from a hand-written offline list (8.4: no Play Services plugin). */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val u = ui()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScreenHeader(u.t("set.licenses"), onBack = onBack)
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LICENSES.forEach { (name, license) ->
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    Text(license, style = MaterialTheme.typography.labelMedium, color = LocalAndarzColors.current.textMuted)
                }
            }
        }
    }
}

/** Library names and their licenses (names, not wording). */
private val LICENSES = listOf(
    "AndroidX (Compose, Room, DataStore, WorkManager, Navigation, Biometric, Lifecycle)" to "Apache License 2.0",
    "Kotlin, kotlinx.coroutines, kotlinx.serialization" to "Apache License 2.0",
    "Dagger Hilt" to "Apache License 2.0",
    "OkHttp" to "Apache License 2.0",
    "Coil" to "Apache License 2.0",
    "Vazirmatn" to "SIL Open Font License 1.1",
)
