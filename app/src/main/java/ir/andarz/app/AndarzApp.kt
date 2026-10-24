package ir.andarz.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import ir.andarz.app.content.ContentRepository
import ir.andarz.app.market.MarketHostImpl
import ir.andarz.app.platform.AppLock
import ir.andarz.app.platform.DisciplineChannels
import ir.andarz.app.platform.ScheduleReconciler
import ir.andarz.data.db.AndarzDatabase
import ir.andarz.market.MarketModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Installs the `:market` bridge, creates the discipline channels and runs the reminder reconciler (7).
 * Nothing here touches the network: Market is off by default and its HTTP client is built only on the
 * first real demand (K4, D-M08).
 */
@HiltAndroidApp
class AndarzApp : Application(), Configuration.Provider {

    @Inject lateinit var marketHost: MarketHostImpl
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var reconciler: ScheduleReconciler
    @Inject lateinit var content: ContentRepository
    @Inject lateinit var db: AndarzDatabase
    @Inject lateinit var lock: AppLock
    @Inject lateinit var tradeWatcher: ir.andarz.app.market.TradeWatcher
    @Inject lateinit var settingsRepo: ir.andarz.data.settings.SettingsRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        marketHost.watchSettings(appScope)
        MarketModule.install(this, marketHost)
        tradeWatcher.start(appScope)
        // Cold start (P-02): the 185 KB wording parse, the channel set-up and the purge of outgoing files (8.4)
        // run off the main thread. The splash waits for settings, and a receiver in a cold process still loads
        // the wording synchronously through ContentRepository's lock, so nothing can see a half-built state.
        appScope.launch(Dispatchers.IO) {
            runCatching { File(cacheDir, "out").deleteRecursively() }
            runCatching { content.preload() }
            runCatching { DisciplineChannels.ensure(this@AndarzApp, content) }
        }
        // D-90: notifications and receivers read the wording through ContentRepository, so it follows the
        // language setting even when no screen is open; channel names are renamed in the new language.
        appScope.launch(Dispatchers.IO) {
            settingsRepo.settings.collect { s ->
                if (content.language != s.language) {
                    content.setLanguage(s.language)
                    runCatching { DisciplineChannels.ensure(this@AndarzApp, content) }
                }
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, e ->
                when (e) {
                    Lifecycle.Event.ON_START -> { MarketModule.setAppVisible(true); lock.onForeground() }
                    Lifecycle.Event.ON_STOP -> { MarketModule.setAppVisible(false); lock.onBackground() }
                    else -> Unit
                }
            },
        )
        // The reconciler runs at start and after any write to the rule tables (7), debounced.
        appScope.launch {
            callbackFlow {
                val o = object : androidx.room.InvalidationTracker.Observer(arrayOf("trade", "ledger_entry", "review", "param_change")) {
                    override fun onInvalidated(tables: Set<String>) { trySend(Unit) }
                }
                db.invalidationTracker.addObserver(o)
                awaitClose { db.invalidationTracker.removeObserver(o) }
            }.onStart { emit(Unit) }.debounce(2_000).collect { runCatching { reconciler.reconcile(this@AndarzApp) } }
        }
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
