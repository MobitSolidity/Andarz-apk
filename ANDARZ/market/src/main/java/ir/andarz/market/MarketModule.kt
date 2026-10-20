package ir.andarz.market

import android.app.Application
import android.content.Context
import android.content.Intent
import ir.andarz.market.alarm.Channels
import ir.andarz.market.alarm.ClockRing
import ir.andarz.market.alerts.AlertDispatcher
import ir.andarz.market.alerts.AlertEvaluator
import ir.andarz.market.data.MarketSources
import ir.andarz.market.data.NetSources
import ir.andarz.market.data.PriceHub
import ir.andarz.market.net.MarketHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ir.andarz.market.monitor.LiveService

/**
 * The whole public surface of :market (K4). Everything else in the module is `internal`, so :app can only
 * reach what is listed here — and :market can reach nothing of :engine or :data at all (guard 3).
 *
 * Installed once from `Application.onCreate`:
 *
 *     MarketModule.install(this, MarketHostImpl(content, formatter))
 *
 * M0 shipped the gate (master switch and consent) and the HTTP layer with its egress policy; MK1 adds the
 * price hub behind [repository]; MK2 adds [alerts], [calendar], the alarm, the monitor and the reminders.
 */
object MarketModule {

    @Volatile
    private var state: Installed? = null

    private class Installed(
        val app: Application?,
        val host: MarketHost,
        val prefs: MarketPrefs,
        val http: MarketHttp,
        sources: MarketSources = NetSources(http, Dispatchers.IO, System::currentTimeMillis),
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        now: () -> Long = System::currentTimeMillis,
    ) {
        // Building the hub starts nothing: it waits for both an acquire() and an open gate (K6.4).
        val hub = PriceHub(
            sources = sources,
            scope = scope,
            now = now,
            gate = prefs.gate.map { it.mayUseNetwork },
            watchFlow = prefs.watch,
            rulesFlow = prefs.rules,
            settingsFlow = prefs.alertSettings,
        )
        val repository = MarketRepository(hub, prefs)
        val scope = scope
        val evaluator = AlertEvaluator()
        val dispatcher: AlertDispatcher? = app?.let { a ->
            AlertDispatcher(a, host) { mute && host.inQuietHours(System.currentTimeMillis()) }
        }
        @Volatile var mute: Boolean = false
        val alerts: AlertsController? = app?.let { a -> dispatcher?.let { AlertsController(a, prefs, it) } }
        val calendar: CalendarController? = app?.let { CalendarController(it, prefs) }
        val visible = MutableStateFlow(false)

        init {
            // Alerts run where March ran them: after each published snapshot. The hub only publishes while
            // the gate is open, so a switched-off Market fires no price alert (K4).
            hub.onPublish = { r ->
                val round = evaluator.evaluate(r.state, r.history, r.settings, r.rules, r.watch, r.now)
                dispatcher?.dispatch(round, r.settings, r.state.usdt?.toman)
                if (round.clearTargets.isNotEmpty()) {
                    scope.launch { for ((id, above) in round.clearTargets) prefs.clearTarget(id, above) }
                }
            }
        }
    }

    /** Installs the host bridge. Safe to call twice; the second call is ignored. */
    fun install(app: Application, host: MarketHost) {
        if (state != null) return
        val installed: Installed
        synchronized(this) {
            if (state != null) return
            // No client is built here: MarketHttp creates its OkHttpClient on the first real call, so an
            // installed-but-never-demanded Market opens no socket (K4, ZeroTrafficTest).
            installed = Installed(app, host, MarketPrefs(app), MarketHttp())
            state = installed
        }
        start(app, installed)
    }

    /**
     * App-start work (MainActivity/MainViewModel behaviours of March, MERGE_MAP): channels created, saved
     * reminders re-armed, and two observers that open nothing by themselves:
     * - K6.4 reason (b): while the app is visible and sharp-move alerts are on or a target is armed, hold the
     *   hub with watch-list and rule coins only;
     * - the monitor follows its switch and the gate: off, or Market off, stops the service (K4).
     */
    private fun start(app: Application, i: Installed) {
        runCatching { Channels.ensure(app, i.host.texts) }
        i.scope.launch { runCatching { i.calendar?.restore(System.currentTimeMillis()) } }
        i.scope.launch {
            var holding = false
            combine(i.visible, i.prefs.alertSettings, i.prefs.rules) { v, s, r ->
                v && (s.enabled || r.values.any { it.above != null || it.below != null })
            }.distinctUntilChanged().collect { want ->
                if (want && !holding) i.hub.acquire(ui = false)
                if (!want && holding) i.hub.release(ui = false)
                holding = want
            }
        }
        i.scope.launch { i.prefs.alertSettings.collect { i.mute = it.muteInQuiet } }
        i.scope.launch {
            combine(i.prefs.gate, i.prefs.alertSettings) { g, s -> g.mayUseNetwork && s.background }
                .distinctUntilChanged()
                .collect { on -> if (on) LiveService.start(app) else LiveService.stop(app) }
        }
    }

    /** :app's lifecycle observer: MainActivity started/stopped (ClockRing.appVisible, demand reason b). */
    fun setAppVisible(visible: Boolean) {
        ClockRing.appVisible = visible
        state?.visible?.value = visible
    }

    /** Alert settings, rules, tests and permission rows (MK2). */
    val alerts: AlertsController get() = require().alerts ?: error("Market installed without an Application")

    /** The release calendar and its reminders (MK2). */
    val calendar: CalendarController get() = require().calendar ?: error("Market installed without an Application")

    /** The monitor switch of the Alerts section and Settings (D-M08: default off). */
    suspend fun setMonitor(on: Boolean) = require().prefs.updateSettings { it.copy(background = on) }

    /** Reboot and app update: reminders come back; the monitor only when enabled and Market is on (K5.2). */
    suspend fun restoreAfterBoot(context: Context) {
        val i = state ?: return
        runCatching { Channels.ensure(context, i.host.texts) }
        i.calendar?.restore(System.currentTimeMillis())
        val on = i.prefs.gate.first().mayUseNetwork && i.prefs.alertSettings.first().background
        if (on) LiveService.start(context)
    }

    /** Market settings for the backup (K6.12); never consent or the master switch. */
    suspend fun exportJson(): String = require().prefs.exportJson()

    /** Applies restored market settings; malformed input throws and changes nothing. */
    suspend fun importJson(json: String) = require().prefs.importJson(json)

    /**
     * After a restore (K9.1 a): release reminders are rescheduled; the monitor restarts only when enabled,
     * and only if the gates are open, which a restore never opens.
     */
    suspend fun onRestored(context: Context) = restoreAfterBoot(context)

    /** Wording, or null before install (a receiver in a cold process of a not-yet-installed app). */
    internal fun textsOrNull(): MarketTexts? = state?.host?.texts

    /** The explicit deep link into the app (K6.11). */
    internal fun openIntent(context: Context, target: MarketTarget): Intent = require().host.openIntent(context, target)

    internal fun openIntentOrNull(context: Context, target: MarketTarget): Intent? = state?.host?.openIntent(context, target)

    /** True once [install] has run. */
    val isInstalled: Boolean get() = state != null

    private fun require(): Installed =
        state ?: error("MarketModule.install() must run in Application.onCreate before Market is used")

    /** Wording and deep links, implemented by :app. */
    val texts: MarketTexts get() = require().host.texts

    /** Prices, stars and the demand counter (MK1). */
    val repository: MarketRepository get() = require().repository

    /** The hosts the consent sheet and the About network page list (K4, K6.9). */
    val hosts: List<String> get() = MarketHosts.all

    // ---------------------------------------------------------------- the two gates in front of the network

    /** The master switch and the consent stamp. Both default to off (D-M08). */
    val gate: Flow<MarketGate> get() = require().prefs.gate

    /** Accepts the consent sheet and turns Market on (K4). */
    suspend fun acceptConsent(now: Long) = require().prefs.acceptConsent(now)

    /** Settings -> Market. Off stops the hub, the service and every price alert (K4). */
    suspend fun setEnabled(on: Boolean) = require().prefs.setEnabled(on)

    // ---------------------------------------------------------------- test and guard hooks

    /**
     * True once something actually asked for data. The zero-traffic test asserts it stays false after
     * install and with no demand (K8.4 guard 6).
     */
    internal val httpStarted: Boolean get() = state?.http?.started ?: false

    /** Installs a module state for tests without an Application. */
    internal fun installForTest(context: Context, host: MarketHost, http: MarketHttp) {
        state = Installed(context.applicationContext as Application, host, MarketPrefs(context), http)
    }

    /** True while the price hub's loops run. The zero-traffic test asserts it stays false without demand. */
    internal val hubRunning: Boolean get() = state?.hub?.running ?: false

    /** Clears the installed state between tests. */
    internal fun resetForTest() {
        state = null
    }
}
