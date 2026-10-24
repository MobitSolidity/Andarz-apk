package ir.andarz.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.ZoneId

enum class ThemeMode { DARK, LIGHT, SYSTEM }
enum class Digits { FA, EN }
enum class CalendarMode { JALALI, GREGORIAN }
enum class LockMethod { NONE, BIOMETRIC, PIN }

/** Display language (D-90). Persian is the default and the source of every translation. */
enum class Language { FA, EN }

/** The DataStore settings of 4.2, with their defaults. PIN material is never exported (8.3). */
data class Settings(
    val theme: ThemeMode = ThemeMode.DARK,
    val digits: Digits = Digits.FA,
    val calendar: CalendarMode = CalendarMode.JALALI,
    val zoneId: String = DEFAULT_ZONE,
    val reduceMotion: Boolean = false,
    val haptics: Boolean = true,
    val lockEnabled: Boolean = false,
    val lockMethod: LockMethod = LockMethod.NONE,
    val lockTimeoutSeconds: Int = 0,
    val hideRecents: Boolean = true,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val pinIterations: Int = 0,
    val pinFailCount: Int = 0,
    val pinLockedUntil: Long = 0,
    val notifCooldown: Boolean = true,
    val notifTimeStop: Boolean = true,
    val notifReviews: Boolean = true,
    val notifOpen: Boolean = true,
    val notifBackup: Boolean = true,
    val notifAdvice: Boolean = false,
    /** Minutes after local midnight; default 23:30–07:00 (D-34). */
    val quietStart: Int = 23 * 60 + 30,
    val quietEnd: Int = 7 * 60,
    val lastBackupAt: Long = 0,
    val backupRemindDays: Int = 30,
    val liqVolMin: Double = 100_000_000.0,
    val relVolLo: Double = 0.7,
    val relVolHi: Double = 3.0,
    val readArticles: Set<String> = emptySet(),
    val lastSecurityReviewAt: Long = 0,
    val onboardingDone: Boolean = false,
    val adviceEnabled: Boolean = true,
    val demoLoaded: Boolean = false,
    val language: Language = Language.FA,
    /** Masks equity and dollar P&L on screen (D-91); the numbers themselves are never changed. */
    val hideAmounts: Boolean = false,
) {
    val zone: ZoneId get() = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.of(DEFAULT_ZONE))

    companion object {
        const val DEFAULT_ZONE = "Asia/Tehran"
    }
}

class SettingsRepository(private val store: DataStore<Preferences>) {

    private object K {
        val theme = stringPreferencesKey("theme")
        val digits = stringPreferencesKey("digits")
        val calendar = stringPreferencesKey("calendar")
        val zone = stringPreferencesKey("zoneId")
        val reduceMotion = booleanPreferencesKey("reduceMotion")
        val haptics = booleanPreferencesKey("haptics")
        val lockEnabled = booleanPreferencesKey("lock.enabled")
        val lockMethod = stringPreferencesKey("lock.method")
        val lockTimeout = intPreferencesKey("lock.timeoutSeconds")
        val hideRecents = booleanPreferencesKey("lock.hideRecents")
        val pinHash = stringPreferencesKey("lock.pinHash")
        val pinSalt = stringPreferencesKey("lock.pinSalt")
        val pinIter = intPreferencesKey("lock.pinIterations")
        val pinFail = intPreferencesKey("lock.pinFailCount")
        val pinLocked = longPreferencesKey("lock.pinLockedUntil")
        val nCooldown = booleanPreferencesKey("notif.cooldown")
        val nTimeStop = booleanPreferencesKey("notif.timeStop")
        val nReviews = booleanPreferencesKey("notif.reviews")
        val nOpen = booleanPreferencesKey("notif.open")
        val nBackup = booleanPreferencesKey("notif.backup")
        val nAdvice = booleanPreferencesKey("notif.advice")
        val quietStart = intPreferencesKey("notif.quietStart")
        val quietEnd = intPreferencesKey("notif.quietEnd")
        val lastBackup = longPreferencesKey("lastBackupAt")
        val backupDays = intPreferencesKey("backupRemindDays")
        val liqVolMin = doublePreferencesKey("Liq_Vol_Min")
        val relVolLo = doublePreferencesKey("Rel_Vol_Lo")
        val relVolHi = doublePreferencesKey("Rel_Vol_Hi")
        val readArticles = stringSetPreferencesKey("readArticles")
        val lastSecurity = longPreferencesKey("lastSecurityReviewAt")
        val onboarding = booleanPreferencesKey("onboardingDone")
        val advice = booleanPreferencesKey("adviceEnabled")
        val demo = booleanPreferencesKey("demoLoaded")
        val language = stringPreferencesKey("language")
        val hideAmounts = booleanPreferencesKey("hideAmounts")
    }

    val settings: Flow<Settings> = store.data.map(::read)

    suspend fun current(): Settings = settings.first()

    private inline fun <reified E : Enum<E>> enumOr(v: String?, d: E): E = v?.let { s -> enumValues<E>().firstOrNull { it.name == s } } ?: d

    private fun read(p: Preferences): Settings {
        val d = Settings()
        return Settings(
            theme = enumOr(p[K.theme], d.theme), digits = enumOr(p[K.digits], d.digits),
            calendar = enumOr(p[K.calendar], d.calendar), zoneId = p[K.zone] ?: d.zoneId,
            reduceMotion = p[K.reduceMotion] ?: d.reduceMotion, haptics = p[K.haptics] ?: d.haptics,
            lockEnabled = p[K.lockEnabled] ?: d.lockEnabled, lockMethod = enumOr(p[K.lockMethod], d.lockMethod),
            lockTimeoutSeconds = p[K.lockTimeout] ?: d.lockTimeoutSeconds, hideRecents = p[K.hideRecents] ?: d.hideRecents,
            pinHash = p[K.pinHash], pinSalt = p[K.pinSalt], pinIterations = p[K.pinIter] ?: 0,
            pinFailCount = p[K.pinFail] ?: 0, pinLockedUntil = p[K.pinLocked] ?: 0,
            notifCooldown = p[K.nCooldown] ?: d.notifCooldown, notifTimeStop = p[K.nTimeStop] ?: d.notifTimeStop,
            notifReviews = p[K.nReviews] ?: d.notifReviews, notifOpen = p[K.nOpen] ?: d.notifOpen,
            notifBackup = p[K.nBackup] ?: d.notifBackup, notifAdvice = p[K.nAdvice] ?: d.notifAdvice,
            quietStart = p[K.quietStart] ?: d.quietStart, quietEnd = p[K.quietEnd] ?: d.quietEnd,
            lastBackupAt = p[K.lastBackup] ?: 0, backupRemindDays = p[K.backupDays] ?: d.backupRemindDays,
            liqVolMin = p[K.liqVolMin] ?: d.liqVolMin, relVolLo = p[K.relVolLo] ?: d.relVolLo, relVolHi = p[K.relVolHi] ?: d.relVolHi,
            readArticles = p[K.readArticles] ?: emptySet(), lastSecurityReviewAt = p[K.lastSecurity] ?: 0,
            onboardingDone = p[K.onboarding] ?: false, adviceEnabled = p[K.advice] ?: d.adviceEnabled, demoLoaded = p[K.demo] ?: false,
            language = enumOr(p[K.language], d.language), hideAmounts = p[K.hideAmounts] ?: d.hideAmounts,
        )
    }

    suspend fun setTheme(v: ThemeMode) = store.edit { it[K.theme] = v.name }
    suspend fun setDigits(v: Digits) = store.edit { it[K.digits] = v.name }
    suspend fun setCalendar(v: CalendarMode) = store.edit { it[K.calendar] = v.name }
    suspend fun setZone(id: String) { ZoneId.of(id); store.edit { it[K.zone] = id } }
    suspend fun setReduceMotion(v: Boolean) = store.edit { it[K.reduceMotion] = v }
    suspend fun setHaptics(v: Boolean) = store.edit { it[K.haptics] = v }
    suspend fun setOnboardingDone(v: Boolean) = store.edit { it[K.onboarding] = v }
    suspend fun setAdviceEnabled(v: Boolean) = store.edit { it[K.advice] = v }
    suspend fun setLastBackupAt(v: Long) = store.edit { it[K.lastBackup] = v }
    suspend fun markArticleRead(n: String) = store.edit { it[K.readArticles] = (it[K.readArticles] ?: emptySet()) + n }
    suspend fun setLastSecurityReviewAt(v: Long) = store.edit { it[K.lastSecurity] = v }
    suspend fun setDemoLoaded(v: Boolean) = store.edit { it[K.demo] = v }
    suspend fun setHideAmounts(v: Boolean) = store.edit { it[K.hideAmounts] = v }

    /**
     * Switches the display language (D-90). Digits and calendar follow the language, because a Persian text with
     * Latin digits or an English text with a Solar Hijri date is rarely what anyone wants; both stay adjustable.
     */
    suspend fun setLanguage(v: Language) = store.edit {
        it[K.language] = v.name
        it[K.digits] = (if (v == Language.FA) Digits.FA else Digits.EN).name
        it[K.calendar] = (if (v == Language.FA) CalendarMode.JALALI else CalendarMode.GREGORIAN).name
    }
    suspend fun setQuietHours(start: Int, end: Int) = store.edit { it[K.quietStart] = start; it[K.quietEnd] = end }

    suspend fun setLiquidity(volMin: Double, relLo: Double, relHi: Double) = store.edit {
        it[K.liqVolMin] = volMin; it[K.relVolLo] = relLo; it[K.relVolHi] = relHi
    }

    suspend fun setNotifications(cooldown: Boolean, timeStop: Boolean, reviews: Boolean, open: Boolean, backup: Boolean, advice: Boolean) =
        store.edit {
            it[K.nCooldown] = cooldown; it[K.nTimeStop] = timeStop; it[K.nReviews] = reviews
            it[K.nOpen] = open; it[K.nBackup] = backup; it[K.nAdvice] = advice
        }

    suspend fun setLock(enabled: Boolean, method: LockMethod, timeoutSeconds: Int, hideRecents: Boolean) = store.edit {
        it[K.lockEnabled] = enabled; it[K.lockMethod] = method.name; it[K.lockTimeout] = timeoutSeconds; it[K.hideRecents] = hideRecents
    }

    suspend fun setPin(hash: String?, salt: String?, iterations: Int) = store.edit {
        if (hash == null) { it.remove(K.pinHash); it.remove(K.pinSalt); it.remove(K.pinIter) }
        else { it[K.pinHash] = hash; it[K.pinSalt] = salt!!; it[K.pinIter] = iterations }
        it[K.pinFail] = 0; it[K.pinLocked] = 0
    }

    suspend fun setPinAttempts(failCount: Int, lockedUntil: Long) = store.edit { it[K.pinFail] = failCount; it[K.pinLocked] = lockedUntil }
}
