package ir.andarz.app.ui

import android.database.sqlite.SQLiteFullException
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.andarz.app.BuildConfig
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * One channel for failures of user actions (AUDIT A-01). The app shell shows each key as a snackbar.
 * Keys are content keys (`err.*`), never exception text, so nothing internal reaches the screen.
 */
object UiErrors {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun report(key: String) { _events.tryEmit(key) }

    /** Storage problems get their own sentence (`err.storage`); everything else is `err.generic`. */
    fun keyFor(e: Throwable): String = when (e) {
        is SQLiteFullException, is IOException -> "err.storage"
        else -> "err.generic"
    }
}

/**
 * `viewModelScope.launch` with a handler (AUDIT A-01). Repositories reject invalid state by throwing
 * (`require`, `TradeStateException`): for example a trade closed on another screen while "I checked" is tapped,
 * or a plan that expired in the background before "entry done". Without a handler such an exception reaches
 * the thread's default handler and kills the process. Cancellation is not an error and never reaches here.
 */
fun ViewModel.safeLaunch(block: suspend CoroutineScope.() -> Unit): Job =
    viewModelScope.launch(
        CoroutineExceptionHandler { _, e ->
            if (BuildConfig.DEBUG) Log.w("Andarz", "action failed", e)
            UiErrors.report(UiErrors.keyFor(e))
        },
        block = block,
    )

/**
 * Shows every [UiErrors] event as a snackbar. Placed once per shell (nav scaffold, onboarding) so a failed
 * action is always visible instead of silently doing nothing (A-01).
 */
@androidx.compose.runtime.Composable
fun UiErrorSnackbarHost(
    content: ir.andarz.app.content.ContentRepository,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    val state = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }
    androidx.compose.runtime.LaunchedEffect(state) {
        UiErrors.events.collect { key ->
            state.currentSnackbarData?.dismiss()
            state.showSnackbar(content.ui(key))
        }
    }
    androidx.compose.material3.SnackbarHost(state, modifier)
}
