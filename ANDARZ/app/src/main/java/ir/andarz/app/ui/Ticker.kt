package ir.andarz.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/**
 * Wall-clock milliseconds, refreshed every [periodMs] while the screen is at least STARTED (P-01).
 * When [aligned] the tick lands on the period boundary, so a seconds countdown never skips a digit.
 * In the background nothing wakes up, which keeps the app off the battery-usage list.
 */
@Composable
fun rememberNowMillis(periodMs: Long, aligned: Boolean = false): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, periodMs) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val t = System.currentTimeMillis()
                now.longValue = t
                delay(if (aligned) periodMs - t % periodMs else periodMs)
            }
        }
    }
    return now
}

/** Runs [block] every [periodMs] while the screen is STARTED; failures go to [UiErrors] (P-01, A-01). */
@Composable
fun WhileStarted(periodMs: Long, block: suspend () -> Unit) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, periodMs) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { UiErrors.report(UiErrors.keyFor(e)) }
                delay(periodMs)
            }
        }
    }
}
