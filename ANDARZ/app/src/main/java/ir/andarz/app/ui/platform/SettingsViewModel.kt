package ir.andarz.app.ui.platform

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.andarz.app.BuildConfig
import ir.andarz.app.content.ContentRepository
import ir.andarz.app.platform.AppLock
import ir.andarz.app.platform.ScheduleReconciler
import ir.andarz.data.backup.BackupDocument
import ir.andarz.data.backup.BackupError
import ir.andarz.data.backup.BackupService
import ir.andarz.data.backup.BackupSummary
import ir.andarz.data.backup.InvalidBackup
import ir.andarz.data.csv.CsvHeaderException
import ir.andarz.data.csv.CsvImportPreview
import ir.andarz.data.csv.CsvRepository
import ir.andarz.data.csv.CsvVocab
import ir.andarz.data.db.AndarzDatabase
import ir.andarz.data.settings.CalendarMode
import ir.andarz.data.settings.Digits
import ir.andarz.data.settings.LockMethod
import ir.andarz.data.settings.PinLock
import ir.andarz.data.settings.Settings
import ir.andarz.data.settings.SettingsRepository
import ir.andarz.data.settings.ThemeMode
import ir.andarz.market.MarketModule
import ir.andarz.market.model.AlertSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.io.File
import java.time.Instant
import javax.inject.Inject

/** Settings document for backup/restore (8.3): everything except PIN, lock state and permission state. */
object SettingsDoc {
    fun of(s: Settings): JsonObject = JsonObject(
        mapOf(
            "theme" to JsonPrimitive(s.theme.name), "digits" to JsonPrimitive(s.digits.name), "calendar" to JsonPrimitive(s.calendar.name),
            "zoneId" to JsonPrimitive(s.zoneId), "reduceMotion" to JsonPrimitive(s.reduceMotion), "haptics" to JsonPrimitive(s.haptics),
            "notifCooldown" to JsonPrimitive(s.notifCooldown), "notifTimeStop" to JsonPrimitive(s.notifTimeStop),
            "notifReviews" to JsonPrimitive(s.notifReviews), "notifOpen" to JsonPrimitive(s.notifOpen),
            "notifBackup" to JsonPrimitive(s.notifBackup), "notifAdvice" to JsonPrimitive(s.notifAdvice),
            "quietStart" to JsonPrimitive(s.quietStart), "quietEnd" to JsonPrimitive(s.quietEnd),
            "backupRemindDays" to JsonPrimitive(s.backupRemindDays), "liqVolMin" to JsonPrimitive(s.liqVolMin),
            "relVolLo" to JsonPrimitive(s.relVolLo), "relVolHi" to JsonPrimitive(s.relVolHi),
            "lastSecurityReviewAt" to JsonPrimitive(s.lastSecurityReviewAt), "adviceEnabled" to JsonPrimitive(s.adviceEnabled),
            "onboardingDone" to JsonPrimitive(s.onboardingDone),
        ),
    )

    suspend fun apply(o: JsonObject, repo: SettingsRepository) {
        fun str(k: String) = o[k]?.jsonPrimitive?.content
        fun bool(k: String) = o[k]?.jsonPrimitive?.booleanOrNull
        fun int(k: String) = o[k]?.jsonPrimitive?.intOrNull
        fun dbl(k: String) = o[k]?.jsonPrimitive?.doubleOrNull
        val cur = repo.current()
        str("theme")?.let { v -> ThemeMode.entries.firstOrNull { it.name == v }?.let { repo.setTheme(it) } }
        str("digits")?.let { v -> Digits.entries.firstOrNull { it.name == v }?.let { repo.setDigits(it) } }
        str("calendar")?.let { v -> CalendarMode.entries.firstOrNull { it.name == v }?.let { repo.setCalendar(it) } }
        str("zoneId")?.let { runCatching { repo.setZone(it) } }
        bool("reduceMotion")?.let { repo.setReduceMotion(it) }
        bool("haptics")?.let { repo.setHaptics(it) }
        repo.setNotifications(
            bool("notifCooldown") ?: cur.notifCooldown, bool("notifTimeStop") ?: cur.notifTimeStop, bool("notifReviews") ?: cur.notifReviews,
            bool("notifOpen") ?: cur.notifOpen, bool("notifBackup") ?: cur.notifBackup, bool("notifAdvice") ?: cur.notifAdvice,
        )
        repo.setQuietHours(int("quietStart") ?: cur.quietStart, int("quietEnd") ?: cur.quietEnd)
        repo.setLiquidity(dbl("liqVolMin") ?: cur.liqVolMin, dbl("relVolLo") ?: cur.relVolLo, dbl("relVolHi") ?: cur.relVolHi)
        o["lastSecurityReviewAt"]?.jsonPrimitive?.content?.toLongOrNull()?.let { repo.setLastSecurityReviewAt(it) }
        bool("adviceEnabled")?.let { repo.setAdviceEnabled(it) }
        repo.setOnboardingDone(true)
    }
}

/** Typed outcomes shown by the settings screen; keys are content keys. */
data class Pending(val doc: BackupDocument, val summary: BackupSummary)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: SettingsRepository,
    private val lock: AppLock,
    private val backup: BackupService,
    private val csv: CsvRepository,
    private val content: ContentRepository,
    private val reconciler: ScheduleReconciler,
    private val db: AndarzDatabase,
    private val demo: ir.andarz.data.DemoData,
) : ViewModel() {
    val settings: StateFlow<Settings?> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val marketGate = MarketModule.gate.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ir.andarz.market.MarketGate())
    val marketAlerts: StateFlow<AlertSettings> = MarketModule.alerts.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertSettings())
    val marketToman: StateFlow<Boolean> = MarketModule.repository.toman.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val pendingRestore = MutableStateFlow<Pending?>(null)
    val csvPreview = MutableStateFlow<CsvImportPreview?>(null)

    private val vocab: CsvVocab by lazy { CsvVocab.fromContent(content.bundle.base) }

    val versionName: String = BuildConfig.VERSION_NAME
    val versionCode: Int = BuildConfig.VERSION_CODE

    fun <T> set(block: suspend SettingsRepository.() -> T) = viewModelScope.launch { repo.block(); reconciler.reconcile(ctx) }

    // ---- lock (8.2)
    fun enableLock(pin: String, biometric: Boolean, timeout: Int, hide: Boolean) = viewModelScope.launch {
        if (!PinLock.valid(pin)) { message.value = "set.lock.pin"; return@launch }
        lock.enable(pin, if (biometric) LockMethod.BIOMETRIC else LockMethod.PIN, timeout, hide)
        message.value = "common.saved"
    }

    fun updateLock(biometric: Boolean, timeout: Int, hide: Boolean) = viewModelScope.launch {
        repo.setLock(true, if (biometric) LockMethod.BIOMETRIC else LockMethod.PIN, timeout, hide)
    }

    fun disableLock() = viewModelScope.launch { lock.disable() }

    // ---- backup (8.3)
    /** Market settings as `settings.market` of the payload (K6.12); consent and switch excluded. */
    private suspend fun marketDoc(): kotlinx.serialization.json.JsonElement? =
        runCatching { kotlinx.serialization.json.Json.parseToJsonElement(MarketModule.exportJson()) }.getOrNull()

    fun exportBackup(uri: Uri, password: String) = viewModelScope.launch {
        busy.value = true
        try {
            val s = repo.current()
            val bytes = withContext(Dispatchers.Default) {
                backup.export(password, versionName, s.zone, SettingsDoc.of(s), marketDoc(), emptyMap())
            }
            withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri, "w")!!.use { it.write(bytes) } }
            repo.setLastBackupAt(System.currentTimeMillis())
            message.value = "set.backup.done"
        } catch (e: Exception) { message.value = "err.storage" } finally { busy.value = false }
    }

    fun openBackup(uri: Uri, password: String) = viewModelScope.launch {
        busy.value = true
        try {
            val bytes = withContext(Dispatchers.IO) {
                ctx.contentResolver.openInputStream(uri)!!.use { s ->
                    val b = s.readBytes()
                    if (b.size > ir.andarz.data.backup.BackupCrypto.MAX_FILE_BYTES) throw BackupError.Corrupt("size")
                    b
                }
            }
            val doc = withContext(Dispatchers.Default) { backup.open(bytes, password) }
            pendingRestore.value = Pending(doc, backup.summary(doc))
        } catch (e: BackupError.NotAndarz) { message.value = "err.backup.notAndarz" }
        catch (e: BackupError.Version) { message.value = "err.backup.version" }
        catch (e: BackupError.WrongPassword) { message.value = "err.backup.wrongPassword" }
        catch (e: BackupError) { message.value = "err.backup.wrongPassword" }
        catch (e: InvalidBackup) { message.value = "err.backup.notAndarz" }
        catch (e: Exception) { message.value = "err.generic" }
        finally { busy.value = false }
    }

    fun confirmRestore() = viewModelScope.launch {
        val p = pendingRestore.value ?: return@launch
        busy.value = true
        try {
            backup.restore(p.doc)
            SettingsDoc.apply(p.doc.settings, repo)
            p.doc.market?.let { m -> runCatching { MarketModule.importJson(m.toString()) } }
            runCatching { MarketModule.onRestored(ctx) }
            reconciler.reconcile(ctx)
            pendingRestore.value = null
            message.value = "set.backup.restore.done"
        } catch (e: Exception) { message.value = "err.generic" } finally { busy.value = false }
    }

    // ---- data (5.15, 9)
    private fun outFile(name: String): File = File(ctx.cacheDir, "out").apply { mkdirs() }.let { File(it, name) }

    /** Writes [text] to cacheDir/out and returns its FileProvider uri for sharing (8.4). */
    private suspend fun share(name: String, text: String): Uri = withContext(Dispatchers.IO) {
        val f = outFile(name)
        f.writeText(text, Charsets.UTF_8)
        androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    }

    val shareUri = MutableStateFlow<Pair<Uri, String>?>(null)

    fun exportCsv(full: Boolean) = viewModelScope.launch {
        val s = repo.current()
        val text = if (full) csv.exportFull(vocab, s.zone) else csv.exportExcel(vocab, s.zone)
        val stamp = ir.andarz.data.backup.BackupService.fileName(Instant.now(), s.zone).removePrefix("andarz-backup-").removeSuffix(".andz")
        shareUri.value = share("andarz-" + (if (full) "full" else "excel") + "-" + stamp + ".csv", text) to "text/csv"
        message.value = "journal.export.done"
    }

    fun exportJson() = viewModelScope.launch {
        val s = repo.current()
        val doc = backup.snapshot(versionName, s.zone, SettingsDoc.of(s), marketDoc())
        val stamp = ir.andarz.data.backup.BackupService.fileName(Instant.now(), s.zone).removePrefix("andarz-backup-").removeSuffix(".andz")
        shareUri.value = share("andarz-$stamp.json", backup.encode(doc, pretty = true)) to "application/json"
    }

    fun previewCsv(uri: Uri) = viewModelScope.launch {
        try {
            val text = withContext(Dispatchers.IO) {
                val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                val dec = Charsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                runCatching { dec.decode(java.nio.ByteBuffer.wrap(bytes)).toString() }.getOrElse { throw java.nio.charset.CharacterCodingException() }
            }
            csvPreview.value = csv.preview(text, vocab, repo.current().zone)
        } catch (e: java.nio.charset.CharacterCodingException) { message.value = "err.csv.encoding" }
        catch (e: CsvHeaderException) { message.value = "err.csv.header" }
        catch (e: Exception) { message.value = "err.generic" }
    }

    /** [moveBaseline]: the 9.2 step 7 choice, the new baseline amount when the user confirmed it. */
    fun commitCsv(moveBaselineAmount: Double?) = viewModelScope.launch {
        val p = csvPreview.value ?: return@launch
        val s = repo.current()
        val move = if (moveBaselineAmount != null && p.earliest != null) p.earliest!!.atStartOfDay(s.zone).toInstant() to moveBaselineAmount else null
        csv.commit(p, move)
        csvPreview.value = null
        message.value = "journal.import.done"
    }

    /** Suggested baseline when importing older history (9.2 step 7): equityNow minus the imported nets. */
    suspend fun suggestedBaseline(p: CsvImportPreview): Double {
        val base = db.ledger().baselines().firstOrNull()?.amount ?: 0.0
        return base - p.trades.sumOf { it.net ?: 0.0 }
    }

    fun wipe() = viewModelScope.launch {
        backup.wipe()
        repo.setOnboardingDone(false)
        repo.setDemoLoaded(false)
        lock.disable()
        reconciler.reconcile(ctx)
        message.value = "common.deleted"
    }

    // ---- demo data (5.14, D-68)
    fun loadDemo() = viewModelScope.launch {
        demo.load(); repo.setDemoLoaded(true); message.value = "common.done"
    }

    fun removeDemo() = viewModelScope.launch {
        demo.remove(); repo.setDemoLoaded(false); message.value = "common.deleted"
    }

    // ---- market (K6.9)
    fun setMarketEnabled(on: Boolean) = viewModelScope.launch { MarketModule.setEnabled(on) }
    /** Settings -> Internet switch, first time on: records the consent and turns the connection on (D-76). */
    fun acceptMarketConsent() = viewModelScope.launch { MarketModule.acceptConsent(System.currentTimeMillis()) }
    fun setMarketToman(on: Boolean) = viewModelScope.launch { MarketModule.repository.setToman(on) }
    fun setMarketMonitor(on: Boolean) = viewModelScope.launch { MarketModule.setMonitor(on) }
    fun setMarketQuietMute(on: Boolean) = viewModelScope.launch { MarketModule.alerts.update { it.copy(muteInQuiet = on) } }
}
