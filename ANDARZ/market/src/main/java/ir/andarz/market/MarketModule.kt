package ir.andarz.market

import android.app.Application
import android.content.Context
import ir.andarz.market.net.MarketHttp
import kotlinx.coroutines.flow.Flow

/**
 * The whole public surface of :market (K4). Everything else in the module is `internal`, so :app can only
 * reach what is listed here — and :market can reach nothing of :engine or :data at all (guard 3).
 *
 * Installed once from `Application.onCreate`:
 *
 *     MarketModule.install(this, MarketHostImpl(content, formatter))
 *
 * M0 ships the gate (master switch and consent) and the HTTP layer with its egress policy. The repository
 * (MK1), the alerts controller (MK2) and the calendar controller (MK2) are added by their milestones; see
 * MERGE_MAP.md.
 */
object MarketModule {

    @Volatile
    private var state: Installed? = null

    private class Installed(
        val app: Application,
        val host: MarketHost,
        val prefs: MarketPrefs,
        val http: MarketHttp,
    )

    /** Installs the host bridge. Safe to call twice; the second call is ignored. */
    fun install(app: Application, host: MarketHost) {
        if (state != null) return
        synchronized(this) {
            if (state != null) return
            // No client is built here: MarketHttp creates its OkHttpClient on the first real call, so an
            // installed-but-never-demanded Market opens no socket (K4, ZeroTrafficTest).
            state = Installed(app, host, MarketPrefs(app), MarketHttp())
        }
    }

    /** True once [install] has run. */
    val isInstalled: Boolean get() = state != null

    private fun require(): Installed =
        state ?: error("MarketModule.install() must run in Application.onCreate before Market is used")

    /** Wording and deep links, implemented by :app. */
    val texts: MarketTexts get() = require().host.texts

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

    /** Clears the installed state between tests. */
    internal fun resetForTest() {
        state = null
    }
}
