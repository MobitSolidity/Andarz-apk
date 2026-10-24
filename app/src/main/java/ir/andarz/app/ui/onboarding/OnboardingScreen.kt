package ir.andarz.app.ui.onboarding

import ir.andarz.app.ui.safeLaunch

import androidx.compose.foundation.layout.safeDrawingPadding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import ir.andarz.app.ui.components.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import ir.andarz.app.ui.components.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import ir.andarz.app.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.components.NumberField
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui
import ir.andarz.data.LedgerRepository
import ir.andarz.data.PledgeRepository
import ir.andarz.data.settings.Settings
import ir.andarz.data.settings.SettingsRepository
import ir.andarz.engine.Fmt
import ir.andarz.engine.Params
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val ledger: LedgerRepository,
    private val settings: SettingsRepository,
    private val pledges: PledgeRepository,
) : ViewModel() {
    /** Writes BASELINE + NEUTRAL Start_Equity/Risk_Pct (5.1, D-18), the pledge when signed, then finishes. */
    fun finish(equity: Double, riskPct: Double, zone: String, pledgeText: String?, name: String, onDone: () -> Unit) = safeLaunch {
        settings.setZone(zone)
        ledger.onboard(equity, riskPct, ZoneId.of(zone))
        if (pledgeText != null) pledges.sign(1, name, equity, riskPct, pledgeText)
        settings.setOnboardingDone(true)
        onDone()
    }
}

private const val PAGES = 7

/** Seven pages (5.1): welcome, privacy, how, numbers, pledge, lock, done. Never asks for notifications. */
@Composable
fun OnboardingScreen(onDone: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val u = ui().unmasked()
    var page by rememberSaveable { mutableStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var equity by rememberSaveable { mutableStateOf("") }
    var risk by rememberSaveable { mutableStateOf(Fmt.number(Params.DEFAULTS.riskPct * 100, 2, false, false, true)) }
    var zone by rememberSaveable { mutableStateOf(Settings.DEFAULT_ZONE) }
    var signed by rememberSaveable { mutableStateOf(false) }
    val eq = Fmt.normalizeInput(equity)?.takeIf { it > 0 }
    val riskBounds = u.content.arr("params")?.map { it.jsonObject }?.firstOrNull { it["key"]?.jsonPrimitive?.content == "Risk_Pct" }
    val rMin = riskBounds?.get("min")?.jsonPrimitive?.content?.toDouble() ?: 0.001
    val rMax = riskBounds?.get("max")?.jsonPrimitive?.content?.toDouble() ?: 0.02
    val rp = Fmt.normalizeInput(risk)?.div(100)?.takeIf { it in rMin..rMax }
    val zoneOk = runCatching { ZoneId.of(zone) }.isSuccess

    // Outside the nav Scaffold: keep clear of the system bars and the keyboard (edge-to-edge, D-79).
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        LinearProgressIndicator(progress = { (page + 1) / PAGES.toFloat() }, modifier = Modifier.fillMaxWidth())
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (page) {
                0 -> { Title(u.t("onb.welcome.title")); Text(u.t("onb.welcome.body")); Text(u.content.tagline, style = MaterialTheme.typography.titleMedium, color = LocalAndarzColors.current.allowed) }
                1 -> { Title(u.t("onb.privacy.title")); Text(u.t("onb.privacy.body")) }
                2 -> { Title(u.t("onb.how.title")); listOf("1", "2", "3").forEach { Text(u.digits(it) + ". " + u.t("onb.how.$it")) } }
                3 -> {
                    Title(u.t("onb.setup.title"))
                    OutlinedTextField(name, { name = it }, label = { Text(u.t("onb.setup.name")) }, placeholder = { Text(u.t("onb.setup.name.hint")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    NumberField(u.t("onb.setup.equity"), equity, { equity = it }, hint = u.t("onb.setup.equity.hint"), error = if (equity.isNotBlank() && eq == null) u.t("err.positive") else null)
                    NumberField(u.t("onb.setup.risk"), risk, { risk = it }, suffix = "%", hint = u.t("onb.setup.risk.hint", "default" to u.pct(Params.DEFAULTS.riskPct, 0)), error = if (rp == null) u.t("err.above") else null)
                    OutlinedTextField(zone, { zone = it }, label = { Text(u.t("onb.setup.zone")) }, singleLine = true, isError = !zoneOk, modifier = Modifier.fillMaxWidth())
                }
                4 -> {
                    Title(u.t("onb.pledge.title"))
                    Text(u.t("onb.pledge.body"))
                    Text(pledgeText(u, name, eq, rp), style = MaterialTheme.typography.bodyLarge)
                    if (!signed) Button(onClick = { signed = true }, enabled = eq != null && rp != null) { Text(u.t("onb.pledge.sign")) }
                    else Text(u.t("common.saved"), color = LocalAndarzColors.current.allowed)
                    TextButton(onClick = { page++ }) { Text(u.t("onb.pledge.later")) }
                }
                5 -> { Title(u.t("onb.lock.title")); Text(u.t("onb.lock.body")) }
                else -> { Title(u.t("onb.done.title")); Text(u.t("onb.done.body")) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (page > 0) OutlinedButton(onClick = { page-- }, modifier = Modifier.weight(1f)) { Text(u.t("common.back")) }
            val canNext = page != 3 || (eq != null && rp != null && zoneOk)
            Button(
                onClick = {
                    if (page < PAGES - 1) page++ else vm.finish(eq!!, rp!!, zone, if (signed) pledgeText(u, name, eq, rp) else null, name, onDone)
                },
                enabled = canNext && (page < PAGES - 1 || (eq != null && rp != null)),
                modifier = Modifier.weight(1f),
            ) { Text(u.t(if (page == 0) "onb.welcome.start" else if (page == PAGES - 1) "onb.done.cta" else "common.next")) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable private fun Title(s: String) = Text(s, style = MaterialTheme.typography.headlineSmall)

private fun pledgeText(u: Ui, name: String, equity: Double?, riskPct: Double?): String {
    val tpl = u.content.obj("constitution", "pledge")?.get("template")?.jsonPrimitive?.content ?: return ""
    return tpl.replace("{name}", name.ifBlank { u.t("common.dash") })
        .replace("{date}", u.date(java.time.LocalDate.now(u.zone), withWeekday = false))
        .replace("{startEquity}", u.money(equity))
        .replace("{riskPct}", u.pct(riskPct))
}
