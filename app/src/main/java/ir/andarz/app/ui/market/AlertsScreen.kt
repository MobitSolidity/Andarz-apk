package ir.andarz.app.ui.market

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ir.andarz.app.content.ContentRepository
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.components.SectionCard
import ir.andarz.app.ui.components.SwitchRow
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui
import ir.andarz.engine.Fmt
import ir.andarz.market.model.AlertDirection
import ir.andarz.market.model.AlertScope
import ir.andarz.market.model.AlertSettings
import ir.andarz.market.model.AlertWindow
import ir.andarz.market.model.Coin
import ir.andarz.market.model.CoinRule
import ir.andarz.market.model.RingMode
import ir.andarz.market.model.USDT_IRT_ID
import kotlin.math.roundToInt

/** The cooldown choices of March's Alerts screen. */
internal val COOLDOWNS = listOf(5, 15, 30, 60)

/** "5", "2.5": the threshold number March shows, digits per setting. */
internal fun pctText(u: Ui, v: Double): String = Fmt.isolate(u.num(v, 1, group = false, trim = true)) + if (u.fa) Fmt.FA_PERCENT else '%'

/**
 * Alerts (March's `ui/AlertsScreen.kt`, REIMPLEMENT): the sharp-move switch, ring mode with the Clock-app
 * option and the permission rows, threshold, window, direction, scope, cooldown, USDT/Toman, the name for
 * the greeting with a preview and a test, the background monitor, and the per-coin rules.
 *
 * Every special permission is asked only when its switch is on, through the system screen, with March's
 * one-line reason (K6.3). POST_NOTIFICATIONS is asked the first time an alert or the monitor is enabled (§7).
 * With Market off the settings stay visible and a note says price alerts need Market on (K4).
 */
@Composable
internal fun AlertsScreen(
    content: ContentRepository,
    fmt: MarketFmt,
    marketOn: Boolean,
    s: AlertSettings,
    rules: Map<String, CoinRule>,
    coins: List<Coin>,
    vm: AlertsNewsViewModel,
    onOpen: (String) -> Unit,
) {
    val u = ui()
    val ctx = LocalContext.current
    val c = LocalAndarzColors.current
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun needNotif() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val ruleList = remember(rules) { rules.values.toList() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!marketOn) {
            item(key = "off") {
                SectionCard(null) { Text(content.ui("mkt.link.needMarket"), style = MaterialTheme.typography.bodyMedium, color = c.caution) }
            }
        }
        item(key = "main") {
            SectionCard(null) {
                SwitchRow(content.ui("mkt.alerts.enable"), s.enabled, { v ->
                    if (v) needNotif()
                    vm.update { it.copy(enabled = v) }
                }, hint = content.ui("mkt.alerts.enable.hint"))
            }
        }
        item(key = "ring") {
            SectionCard(content.ui("mkt.alerts.ring")) {
                val overlayOk = remember(resumes) { vm.canOverlay() }
                val clockOk = remember(resumes) { vm.clockAvailable() }
                val fullOk = remember(resumes) { vm.canFullScreen() }
                val exactOk = remember(resumes) { vm.canExact() }
                Text(content.ui("mkt.alerts.ring.hint"), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                Pills { RingMode.entries.forEach { m -> FilterChip(s.ring == m, { vm.update { it.copy(ring = m) } }, label = { Text(content.ui("mkt.ring." + m.name)) }) } }
                if (s.ring != RingMode.OFF) {
                    SwitchRow(content.ui("mkt.alerts.clock"), s.clockApp, { v -> vm.update { it.copy(clockApp = v) } }, hint = content.ui("mkt.alerts.clock.hint"))
                    if (s.clockApp && !clockOk) PermissionRow(content.ui("mkt.alerts.noClock"), content.ui("mkt.alerts.noClock.hint")) {}
                    if (s.clockApp && clockOk && !overlayOk) PermissionRow(content.ui("mkt.alerts.overlay"), content.ui("mkt.alerts.overlay.hint")) { vm.openOverlaySettings() }
                    if (!fullOk) PermissionRow(content.ui("mkt.alerts.fullscreen"), content.ui("mkt.alerts.fullscreen.hint")) { vm.openFullScreenSettings() }
                    if (!exactOk) PermissionRow(content.ui("mkt.alerts.exact"), content.ui("mkt.alerts.exact.hint")) { vm.openExactSettings() }
                    Button(onClick = { vm.testAlarm() }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text(content.ui("mkt.alerts.testAlarm")) }
                }
            }
        }
        item(key = "config") {
            SectionCard(null) {
                var pct by remember(s.thresholdPct) { mutableFloatStateOf(s.thresholdPct) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(content.ui("mkt.alerts.threshold"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(pctText(u, pct.toDouble()), style = MaterialTheme.typography.titleLarge, color = c.caution)
                }
                Slider(
                    value = pct,
                    onValueChange = { pct = (it * 2).roundToInt() / 2f },
                    onValueChangeFinished = { vm.update { it.copy(thresholdPct = pct) } },
                    valueRange = 0.5f..20f,
                )
                FieldLabel(content.ui("mkt.alerts.window"))
                Pills { AlertWindow.entries.forEach { w -> FilterChip(s.window == w, { vm.update { it.copy(window = w) } }, label = { Text(content.ui("mkt.win." + w.name)) }) } }
                FieldLabel(content.ui("mkt.alerts.direction"))
                Pills { AlertDirection.entries.forEach { d -> FilterChip(s.direction == d, { vm.update { it.copy(direction = d) } }, label = { Text(content.ui("mkt.dir." + d.name)) }) } }
                FieldLabel(content.ui("mkt.alerts.scope"))
                Pills { AlertScope.entries.forEach { sc -> FilterChip(s.scope == sc, { vm.update { it.copy(scope = sc) } }, label = { Text(content.ui("mkt.scope." + sc.name)) }) } }
                FieldLabel(content.ui("mkt.alerts.cooldown"))
                Pills {
                    COOLDOWNS.forEach { m ->
                        FilterChip(s.cooldownMin == m, { vm.update { it.copy(cooldownMin = m) } }, label = { Text(content.ui("mkt.alerts.minutes", "n" to u.int(m))) })
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = c.divider)
                SwitchRow(content.ui("mkt.alerts.usdt"), s.includeUsdt, { v -> vm.update { it.copy(includeUsdt = v) } }, hint = content.ui("mkt.alerts.usdt.hint"))
            }
        }
        item(key = "personal") {
            SectionCard(content.ui("mkt.alerts.personal")) {
                var name by remember { mutableStateOf(s.name) }
                OutlinedTextField(
                    value = name,
                    onValueChange = { v ->
                        name = v.take(24)
                        vm.update { it.copy(name = name.trim()) }
                    },
                    singleLine = true,
                    label = { Text(content.ui("mkt.alerts.name")) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                val who = name.trim().let { if (it.isEmpty()) "" else it + content.ui("mkt.sep.comma") }
                val preview = MarketFmt.UP + " " + who + content.ui(
                    "mkt.alert.move.up",
                    mapOf("symbol" to Fmt.isolate("BTC"), "window" to content.ui("mkt.win." + s.window.name), "pct" to Fmt.isolate(u.num(s.thresholdPct.toDouble(), 1, group = false, trim = true))),
                )
                Text(content.ui("mkt.alerts.preview") + preview, style = MaterialTheme.typography.bodySmall, color = c.textMuted, modifier = Modifier.padding(top = 8.dp))
                Button(onClick = {
                    needNotif()
                    vm.test()
                }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text(content.ui("mkt.alerts.test")) }
            }
        }
        item(key = "background") {
            SectionCard(null) {
                SwitchRow(content.ui("mkt.alerts.bg"), s.background, { v ->
                    if (v) needNotif()
                    vm.setMonitor(v)
                }, hint = content.ui("mkt.alerts.bg.hint"))
                Text(
                    content.ui(if (s.scope == AlertScope.ALL) "mkt.alerts.bg.all" else "mkt.alerts.bg.watch"),
                    style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                )
                TextButton(onClick = {
                    runCatching { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text(content.ui("mkt.alerts.battery")) }
            }
        }
        item(key = "rules-title") {
            Text(content.ui("mkt.alerts.rules"), style = MaterialTheme.typography.titleSmall, color = c.textMuted, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        }
        if (ruleList.isEmpty()) {
            item(key = "rules-empty") {
                SectionCard(null) { Text(content.ui("mkt.alerts.rules.empty"), style = MaterialTheme.typography.bodyMedium, color = c.textMuted) }
            }
        }
        items(ruleList, key = { it.id }, contentType = { "rule" }) { r ->
            RuleRow(content, fmt, u, r, coins.firstOrNull { it.id == r.id }, onOpen = { onOpen(r.id) }, onRemove = { vm.removeRule(r.id) })
        }
    }
}

@Composable
private fun PermissionRow(title: String, sub: String, onClick: () -> Unit) {
    val c = LocalAndarzColors.current
    Surface(Modifier.fillMaxWidth().padding(top = 8.dp).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick), shape = RoundedCornerShape(12.dp), color = c.surfaceAlt) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            // K6.7: the warning emoji of March becomes a core icon next to the text.
            Icon(Icons.Filled.Warning, contentDescription = null, tint = c.caution, modifier = Modifier.padding(end = 8.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = c.caution)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
        }
    }
}

@Composable
private fun FieldLabel(t: String) {
    Text(t, style = MaterialTheme.typography.labelLarge, color = LocalAndarzColors.current.textMuted, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun Pills(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** One rule line (March's `RuleRow`): "threshold 3% · above $70,000 · below $60,000". */
internal fun ruleSummary(content: ContentRepository, fmt: MarketFmt, u: Ui, r: CoinRule): String {
    val toman = r.id == USDT_IRT_ID
    val unit: (Double) -> String = { v -> if (toman) fmt.toman(v) else fmt.usd(v) }
    return buildList {
        r.pct?.let { add(content.ui("mkt.rule.pct", "pct" to Fmt.isolate(u.num(it.toDouble(), 1, group = false, trim = true)))) }
        r.above?.let { add(content.ui("mkt.word.above") + unit(it)) }
        r.below?.let { add(content.ui("mkt.word.below") + unit(it)) }
    }.joinToString(SEP)
}

@Composable
private fun RuleRow(content: ContentRepository, fmt: MarketFmt, u: Ui, r: CoinRule, coin: Coin?, onOpen: () -> Unit, onRemove: () -> Unit) {
    val toman = r.id == USDT_IRT_ID
    Surface(Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onOpen), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (toman) content.ui("mkt.coins.usdtPair") else Fmt.isolate(coin?.symbol ?: r.id), style = MaterialTheme.typography.titleSmall)
                Text(ruleSummary(content, fmt, u, r), style = MaterialTheme.typography.bodySmall, color = LocalAndarzColors.current.textMuted)
            }
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = content.ui("common.delete"), tint = LocalAndarzColors.current.redline) }
        }
    }
}
