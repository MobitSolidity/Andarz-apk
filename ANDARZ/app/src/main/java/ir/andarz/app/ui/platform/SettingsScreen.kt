package ir.andarz.app.ui.platform

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.components.NumberField
import ir.andarz.app.ui.components.SectionCard
import ir.andarz.app.ui.components.SwitchRow
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui
import ir.andarz.data.backup.BackupService
import ir.andarz.data.settings.CalendarMode
import ir.andarz.data.settings.Digits
import ir.andarz.data.settings.LockMethod
import ir.andarz.data.settings.PinLock
import ir.andarz.data.settings.Settings
import ir.andarz.data.settings.ThemeMode
import ir.andarz.engine.Fmt
import kotlinx.coroutines.launch
import java.time.Instant

/** Settings of 5.15 with the Market section of K6.9. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onAbout: () -> Unit, onMarketAlerts: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val u = ui()
    val s = vm.settings.collectAsState().value ?: return
    val message by vm.message.collectAsState()
    val busy by vm.busy.collectAsState()
    val share by vm.shareUri.collectAsState()
    val ctx = LocalContext.current
    val c = LocalAndarzColors.current

    LaunchedEffect(share) {
        val (uri, mime) = share ?: return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, u.t("journal.export.share")))
        vm.shareUri.value = null
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(u.t("set.title"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text(u.t("common.back")) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(u.t(it), color = if (it.startsWith("err.")) c.redline else c.allowed) }

        Appearance(u, s, vm)
        Security(u, s, vm)
        Notifications(u, s, vm)
        Backup(u, s, vm)
        Data(u, vm)
        MarketSettings(u, vm, onMarketAlerts)
        SectionCard(u.t("set.about")) {
            Text(u.t("set.version", "v" to u.digits(vm.versionName)) + " \u00B7 " + u.t("set.build", "b" to u.int(vm.versionCode)), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAbout) { Text(u.t("about.title")) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Appearance(u: Ui, s: Settings, vm: SettingsViewModel) {
    SectionCard(u.t("set.appearance")) {
        Text(u.t("set.theme"), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            mapOf(ThemeMode.DARK to "set.theme.dark", ThemeMode.LIGHT to "set.theme.light", ThemeMode.SYSTEM to "set.theme.system").forEach { (m, k) ->
                FilterChip(selected = s.theme == m, onClick = { vm.set { setTheme(m) } }, label = { Text(u.t(k)) })
            }
        }
        Text(u.t("set.digits"), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = s.digits == Digits.FA, onClick = { vm.set { setDigits(Digits.FA) } }, label = { Text(u.t("set.digits.fa")) })
            FilterChip(selected = s.digits == Digits.EN, onClick = { vm.set { setDigits(Digits.EN) } }, label = { Text(u.t("set.digits.en")) })
        }
        Text(u.t("set.calendar"), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = s.calendar == CalendarMode.JALALI, onClick = { vm.set { setCalendar(CalendarMode.JALALI) } }, label = { Text(u.t("set.cal.jalali")) })
            FilterChip(selected = s.calendar == CalendarMode.GREGORIAN, onClick = { vm.set { setCalendar(CalendarMode.GREGORIAN) } }, label = { Text(u.t("set.cal.gregorian")) })
        }
        Text(u.t("set.week"), style = MaterialTheme.typography.labelMedium, color = LocalAndarzColors.current.textMuted)
        Text(u.t("set.zone"), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZONES.forEach { z -> FilterChip(selected = s.zoneId == z, onClick = { vm.set { setZone(z) } }, label = { Text(Fmt.isolate(z)) }) }
        }
        SwitchRow(u.t("set.reduceMotion"), s.reduceMotion, { v -> vm.set { setReduceMotion(v) } })
        SwitchRow(u.t("set.haptics"), s.haptics, { v -> vm.set { setHaptics(v) } })
        SwitchRow(u.t("set.advice.show"), s.adviceEnabled, { v -> vm.set { setAdviceEnabled(v) } })
        Text(u.t("set.fontScale") + " \u00B7 " + u.t("set.language") + ": " + u.t("set.language.fa"), style = MaterialTheme.typography.labelMedium, color = LocalAndarzColors.current.textMuted)
    }
}

/** A short list of zones; Tehran first. Ids are tz identifiers, not wording. */
private val ZONES = listOf("Asia/Tehran", "Asia/Dubai", "Europe/Istanbul", "Europe/London", "Europe/Berlin", "America/Toronto", "UTC")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Security(u: Ui, s: Settings, vm: SettingsViewModel) {
    var pin by rememberSaveable { mutableStateOf("") }
    var pin2 by rememberSaveable { mutableStateOf("") }
    var bio by rememberSaveable { mutableStateOf(s.lockMethod != LockMethod.PIN) }
    var setting by rememberSaveable { mutableStateOf(false) }
    SectionCard(u.t("set.security")) {
        SwitchRow(u.t("set.lock"), s.lockEnabled && s.pinHash != null, { on -> if (on) setting = true else vm.disableLock() })
        if (s.lockEnabled && s.pinHash != null) {
            SwitchRow(u.t("set.lock.biometric"), s.lockMethod == LockMethod.BIOMETRIC, { v -> vm.updateLock(v, s.lockTimeoutSeconds, s.hideRecents) })
            Text(u.t("set.lock.timeout"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "set.lock.immediate", 60 to "set.lock.min1", 300 to "set.lock.min5").forEach { (sec, k) ->
                    FilterChip(selected = s.lockTimeoutSeconds == sec, onClick = { vm.updateLock(s.lockMethod == LockMethod.BIOMETRIC, sec, s.hideRecents) }, label = { Text(u.t(k)) })
                }
            }
            SwitchRow(u.t("set.lock.hideRecents"), s.hideRecents, { v -> vm.updateLock(s.lockMethod == LockMethod.BIOMETRIC, s.lockTimeoutSeconds, v) })
        }
    }
    if (setting) AlertDialog(
        onDismissRequest = { setting = false },
        title = { Text(u.t("set.lock.pin.set")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 8.2: there is no PIN recovery; the only way out is wipe and restore from a backup.
                Text(u.t("set.backup.warn"), style = MaterialTheme.typography.bodyMedium)
                val kb = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                OutlinedTextField(pin, { v -> if (v.length <= 6 && v.all(Char::isDigit)) pin = v }, label = { Text(u.t("set.lock.pin")) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = kb, singleLine = true)
                OutlinedTextField(pin2, { v -> if (v.length <= 6 && v.all(Char::isDigit)) pin2 = v }, label = { Text(u.t("set.lock.pin.confirm")) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = kb, singleLine = true)
                SwitchRow(u.t("set.lock.biometric"), bio, { bio = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.enableLock(pin, bio, 0, true); setting = false; pin = ""; pin2 = "" }, enabled = PinLock.valid(pin) && pin == pin2) { Text(u.t("common.save")) }
        },
        dismissButton = { TextButton(onClick = { setting = false }) { Text(u.t("common.cancel")) } },
    )
}

@Composable
private fun Notifications(u: Ui, s: Settings, vm: SettingsViewModel) {
    val ctx = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    // Asked only when the user first turns a reminder on (7), never at start.
    fun on(v: Boolean, apply: () -> Unit) {
        if (v && Build.VERSION.SDK_INT >= 33 && !ir.andarz.app.platform.DisciplineChannels.permitted(ctx)) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        apply()
    }
    SectionCard(u.t("set.notifications")) {
        if (!ir.andarz.app.platform.DisciplineChannels.permitted(ctx)) {
            TextButton(onClick = { if (Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(u.t("set.notif.permission")) }
        }
        fun save(cd: Boolean = s.notifCooldown, ts: Boolean = s.notifTimeStop, rv: Boolean = s.notifReviews, op: Boolean = s.notifOpen, bk: Boolean = s.notifBackup, ad: Boolean = s.notifAdvice) =
            vm.set { setNotifications(cd, ts, rv, op, bk, ad) }
        SwitchRow(u.t("set.notif.cooldown"), s.notifCooldown, { v -> on(v) { save(cd = v) } })
        SwitchRow(u.t("set.notif.timeStop"), s.notifTimeStop, { v -> on(v) { save(ts = v) } })
        SwitchRow(u.t("set.notif.reviews"), s.notifReviews, { v -> on(v) { save(rv = v) } })
        SwitchRow(u.t("set.notif.open"), s.notifOpen, { v -> on(v) { save(op = v) } })
        SwitchRow(u.t("set.notif.backup"), s.notifBackup, { v -> on(v) { save(bk = v) } })
        SwitchRow(u.t("set.notif.advice"), s.notifAdvice, { v -> on(v) { save(ad = v) } })
        Text(u.t("set.notif.quiet"), style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeStepper(u, s.quietStart) { vm.set { setQuietHours(it, s.quietEnd) } }
            Text(u.t("common.to"))
            TimeStepper(u, s.quietEnd) { vm.set { setQuietHours(s.quietStart, it) } }
        }
    }
}

/** Minutes of day in 30-minute steps, shown as 24 h time. */
@Composable
private fun TimeStepper(u: Ui, minutes: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onChange(Math.floorMod(minutes - 30, 1440)) }) { Text(u.digits("\u2212")) }
        Text(Fmt.isolate(u.digits("%02d:%02d".format(java.util.Locale.ROOT, minutes / 60, minutes % 60))))
        TextButton(onClick = { onChange(Math.floorMod(minutes + 30, 1440)) }) { Text("+") }
    }
}

@Composable
private fun Backup(u: Ui, s: Settings, vm: SettingsViewModel) {
    var password by rememberSaveable { mutableStateOf("") }
    var password2 by rememberSaveable { mutableStateOf("") }
    var restorePassword by rememberSaveable { mutableStateOf("") }
    var confirmWord by rememberSaveable { mutableStateOf("") }
    val pending by vm.pendingRestore.collectAsState()
    val c = LocalAndarzColors.current
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) { vm.exportBackup(uri, password); password = ""; password2 = "" }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.openBackup(uri, restorePassword) }
    SectionCard(u.t("set.backup")) {
        Text(
            if (s.lastBackupAt > 0) u.t("set.backup.last", "date" to u.dateTime(Instant.ofEpochMilli(s.lastBackupAt))) else u.t("set.backup.never"),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(u.t("set.backup.warn"), style = MaterialTheme.typography.labelMedium, color = c.caution)
        OutlinedTextField(password, { password = it }, label = { Text(u.t("set.backup.password")) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password2, { password2 = it }, label = { Text(u.t("set.backup.password.confirm")) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        val pwErr = when { password.isEmpty() -> null; password.length < 8 -> "err.passwordShort"; password2.isNotEmpty() && password != password2 -> "err.passwordMismatch"; else -> null }
        pwErr?.let { Text(u.t(it), color = c.redline, style = MaterialTheme.typography.labelMedium) }
        Button(
            onClick = { create.launch(BackupService.fileName(Instant.now(), s.zone)) },
            enabled = password.length >= 8 && password == password2, modifier = Modifier.fillMaxWidth(),
        ) { Text(u.t("set.backup.export")) }
        SwitchRow(u.t("set.backup.autoRemind"), s.notifBackup, { v -> vm.set { setNotifications(s.notifCooldown, s.notifTimeStop, s.notifReviews, s.notifOpen, v, s.notifAdvice) } })

        Text(u.t("set.backup.restore.title"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        Text(u.t("set.backup.restore.warn"), style = MaterialTheme.typography.labelMedium, color = c.caution)
        OutlinedTextField(restorePassword, { restorePassword = it }, label = { Text(u.t("set.backup.password")) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { pick.launch(arrayOf("*/*")) }, enabled = restorePassword.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(u.t("set.backup.import")) }
    }
    pending?.let { p ->
        AlertDialog(
            onDismissRequest = { vm.pendingRestore.value = null },
            title = { Text(u.t("set.backup.restore.title")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(u.t("journal.count", "n" to u.int(p.summary.trades)))
                    val range = listOfNotNull(p.summary.firstEntry, p.summary.lastEntry).map { u.date(u.localDate(it), false) }.distinct().joinToString(" \u2013 ")
                    if (range.isNotEmpty()) Text(range)
                    Text(u.t("set.backup.last", "date" to u.dateTime(p.summary.createdAt)))
                    Text(u.t("set.backup.restore.warn"), color = LocalAndarzColors.current.caution)
                    OutlinedTextField(confirmWord, { confirmWord = it }, label = { Text(u.t("set.wipe.confirm")) }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { vm.confirmRestore(); confirmWord = "" }, enabled = confirmWord.trim() == u.t("set.wipe.word")) { Text(u.t("common.confirm")) } },
            dismissButton = { TextButton(onClick = { vm.pendingRestore.value = null }) { Text(u.t("common.cancel")) } },
        )
    }
}

@Composable
private fun Data(u: Ui, vm: SettingsViewModel) {
    val preview by vm.csvPreview.collectAsState()
    var wipeAsk by rememberSaveable { mutableStateOf(false) }
    var word by rememberSaveable { mutableStateOf("") }
    var jsonAsk by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.previewCsv(uri) }
    SectionCard(u.t("set.data")) {
        Text(u.t("set.csv"), style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { vm.exportCsv(false) }, modifier = Modifier.fillMaxWidth()) { Text(u.t("journal.export.csv.excel")) }
        OutlinedButton(onClick = { vm.exportCsv(true) }, modifier = Modifier.fillMaxWidth()) { Text(u.t("journal.export.csv.full")) }
        OutlinedButton(onClick = { pick.launch(arrayOf("text/*", "application/csv", "*/*")) }, modifier = Modifier.fillMaxWidth()) { Text(u.t("journal.import.csv")) }
        Text(u.t("journal.import.hint"), style = MaterialTheme.typography.labelMedium, color = LocalAndarzColors.current.textMuted)
        OutlinedButton(onClick = { jsonAsk = true }, modifier = Modifier.fillMaxWidth()) { Text(u.t("set.exportAll")) }
        TextButton(onClick = { wipeAsk = true }) { Text(u.t("set.wipe"), color = LocalAndarzColors.current.redline) }
    }
    preview?.let { p ->
        var moveBaseline by rememberSaveable { mutableStateOf(false) }
        var baseText by rememberSaveable { mutableStateOf("") }
        LaunchedEffect(p) { if (p.earliest != null) baseText = ir.andarz.app.ui.analysis.CalcLogic.field(vm.suggestedBaseline(p)) }
        AlertDialog(
            onDismissRequest = { vm.csvPreview.value = null },
            title = { Text(u.t("journal.import.csv")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(u.t("journal.import.preview", "ok" to u.int(p.trades.size), "skip" to u.int(p.errors.size)))
                    if (p.duplicates > 0) Text(u.t("journal.import.dup", "n" to u.int(p.duplicates)))
                    p.errors.take(20).forEach { e -> Text(u.t("journal.import.row.invalid", "row" to u.int(e.row), "why" to Fmt.isolate(e.why)), style = MaterialTheme.typography.labelMedium) }
                    Text(u.t("journal.import.note"), style = MaterialTheme.typography.labelMedium, color = LocalAndarzColors.current.textMuted)
                    if (p.earliest != null) {
                        // 9.2 step 7: offered, never silent.
                        SwitchRow(u.t("ledger.type.BASELINE") + " \u00B7 " + u.date(p.earliest!!, false), moveBaseline, { moveBaseline = it })
                        if (moveBaseline) NumberField(u.t("ledger.amount"), baseText, { baseText = it }, suffix = "$")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.commitCsv(if (moveBaseline) Fmt.normalizeInput(baseText) else null) }, enabled = p.trades.isNotEmpty()) {
                    Text(u.t("journal.import.confirm", "n" to u.int(p.trades.size)))
                }
            },
            dismissButton = { TextButton(onClick = { vm.csvPreview.value = null }) { Text(u.t("common.cancel")) } },
        )
    }
    if (jsonAsk) AlertDialog(
        onDismissRequest = { jsonAsk = false },
        title = { Text(u.t("set.exportAll")) },
        // 9.4: the JSON export is not encrypted; the backup warning says what is at stake.
        text = { Text(u.t("set.backup.warn")) },
        confirmButton = { TextButton(onClick = { vm.exportJson(); jsonAsk = false }) { Text(u.t("common.continue")) } },
        dismissButton = { TextButton(onClick = { jsonAsk = false }) { Text(u.t("common.cancel")) } },
    )
    if (wipeAsk) AlertDialog(
        onDismissRequest = { wipeAsk = false },
        title = { Text(u.t("set.wipe")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(u.t("set.wipe.warn"))
                OutlinedTextField(word, { word = it }, label = { Text(u.t("set.wipe.confirm")) }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { scope.launch { vm.wipe() }; wipeAsk = false; word = "" }, enabled = word.trim() == u.t("set.wipe.word")) { Text(u.t("common.delete")) } },
        dismissButton = { TextButton(onClick = { wipeAsk = false }) { Text(u.t("common.cancel")) } },
    )
}

@Composable
private fun MarketSettings(u: Ui, vm: SettingsViewModel, onMarketAlerts: () -> Unit) {
    val gate by vm.marketGate.collectAsState()
    val alerts by vm.marketAlerts.collectAsState()
    val toman by vm.marketToman.collectAsState()
    SectionCard(u.t("mkt.nav.market")) {
        SwitchRow(u.t("mkt.set.enable"), gate.enabled, { vm.setMarketEnabled(it) }, hint = u.t("mkt.set.enable.hint"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !toman, onClick = { vm.setMarketToman(false) }, label = { Text(u.t("mkt.unit.usd")) })
            FilterChip(selected = toman, onClick = { vm.setMarketToman(true) }, label = { Text(u.t("mkt.unit.toman")) })
        }
        SwitchRow(u.t("mkt.alerts.bg"), alerts.background, { vm.setMarketMonitor(it) }, enabled = gate.mayUseNetwork)
        SwitchRow(u.t("mkt.set.quiet"), alerts.muteInQuiet, { vm.setMarketQuietMute(it) })
        TextButton(onClick = onMarketAlerts) { Text(u.t("mkt.tab.alerts")) }
        Text(u.t("mkt.set.sources"), style = MaterialTheme.typography.labelLarge)
        Text(u.t("mkt.set.sources.body"), style = MaterialTheme.typography.bodyMedium)
        ir.andarz.market.MarketModule.hosts.forEach { Text(Fmt.isolate(it), style = MaterialTheme.typography.labelMedium, color = LocalAndarzColors.current.textMuted) }
    }
}
