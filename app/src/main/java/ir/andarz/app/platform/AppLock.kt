package ir.andarz.app.platform

import ir.andarz.data.settings.LockMethod
import ir.andarz.data.settings.PinLock
import ir.andarz.data.settings.Settings
import ir.andarz.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of one PIN attempt (8.2, D-33). */
sealed interface PinResult {
    data object Ok : PinResult
    data class Wrong(val triesLeft: Int, val waitMs: Long) : PinResult
    data class Wait(val ms: Long) : PinResult
}

/**
 * The app lock of 8.2. Locked on every cold start when enabled, and after `timeoutSeconds` in the
 * background. While [locked] is true MainActivity composes nothing of the journal.
 */
@Singleton
class AppLock @Inject constructor(private val settings: SettingsRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked
    @Volatile private var backgroundAt: Long = 0
    @Volatile private var lastEnabled: Boolean? = null

    /** Called once settings are read: a cold start with the lock off is unlocked. */
    fun bind(s: Settings) {
        if (lastEnabled == null) _locked.value = s.lockEnabled && s.pinHash != null
        if (!s.lockEnabled || s.pinHash == null) _locked.value = false
        lastEnabled = s.lockEnabled
    }

    fun onBackground() { backgroundAt = System.currentTimeMillis() }

    fun onForeground() {
        val at = backgroundAt
        if (at == 0L) return
        scope.launch {
            val s = settings.current()
            if (s.lockEnabled && s.pinHash != null && System.currentTimeMillis() - at >= s.lockTimeoutSeconds * 1000L) _locked.value = true
        }
    }

    fun unlockByBiometric() { _locked.value = false }

    suspend fun tryPin(pin: String): PinResult = withContext(Dispatchers.Default) {
        val s = settings.current()
        val now = System.currentTimeMillis()
        if (s.pinLockedUntil > now) return@withContext PinResult.Wait(s.pinLockedUntil - now)
        val ok = s.pinHash != null && s.pinSalt != null && PinLock.verify(pin, PinLock.Hashed(s.pinHash!!, s.pinSalt!!, s.pinIterations))
        if (ok) {
            settings.setPinAttempts(0, 0)
            _locked.value = false
            PinResult.Ok
        } else {
            val fails = s.pinFailCount + 1
            val wait = PinLock.waitAfter(fails)
            settings.setPinAttempts(fails, if (wait > 0) now + wait else 0)
            PinResult.Wrong((PinLock.FREE_ATTEMPTS - fails).coerceAtLeast(0), wait)
        }
    }

    /** Turns the lock on with a new PIN (hash only is stored). */
    suspend fun enable(pin: String, method: LockMethod, timeoutSeconds: Int, hideRecents: Boolean) = withContext(Dispatchers.Default) {
        val h = PinLock.hash(pin)
        settings.setPin(h.hash, h.salt, h.iterations)
        settings.setLock(true, method, timeoutSeconds, hideRecents)
        _locked.value = false
    }

    suspend fun disable() {
        settings.setLock(false, LockMethod.NONE, 0, true)
        settings.setPin(null, null, 0)
        _locked.value = false
    }
}
