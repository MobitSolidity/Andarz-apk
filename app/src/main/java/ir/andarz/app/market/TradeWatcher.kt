package ir.andarz.app.market

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.andarz.app.content.ContentRepository
import ir.andarz.app.platform.DisciplineChannels
import ir.andarz.app.platform.Reminder
import ir.andarz.app.platform.ReminderKind
import ir.andarz.data.EngineFacade
import ir.andarz.data.db.AndarzDatabase
import ir.andarz.data.db.TradeAlertEntity
import ir.andarz.engine.Fmt
import ir.andarz.engine.XlMath
import ir.andarz.market.MarketModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The trade-level watcher of K9.1 (b) and K6.4 (d), in `:app` so journal data never enters `:market` (D-M21).
 * While an alert is armed on an OPEN trade it asks the hub for those symbols and holds a background demand;
 * each published state is checked, and a reached level fires once on the discipline channel (private on the
 * lock screen). Alerts are information only: nothing here touches the gate, locks or the journal (D-M20).
 */
@Singleton
class TradeWatcher @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: AndarzDatabase,
    private val content: ContentRepository,
    private val facade: EngineFacade,
    private val settings: ir.andarz.data.settings.SettingsRepository,
) {
    @Volatile private var holding = false

    fun start(scope: CoroutineScope) {
        if (!MarketModule.isInstalled) return
        val repo = MarketModule.repository
        // Demand: symbols for the stream plus one background hold while anything is armed.
        scope.launch {
            combine(db.trades().observeEngineRows(), db.tradeAlerts().observeAll()) { t, a -> TradeLink.symbols(t, a) }
                .distinctUntilChanged()
                .collect { syms ->
                    repo.extraSymbols = syms
                    if (syms.isNotEmpty() && !holding) { repo.acquire(ui = false); holding = true }
                    if (syms.isEmpty() && holding) { repo.release(ui = false); holding = false }
                }
        }
        // Evaluation on every published market state.
        scope.launch {
            repo.state.map { it.coins }.distinctUntilChanged().collect { coins ->
                val alerts = db.tradeAlerts().all().filter { it.firedAt == null }
                if (alerts.isEmpty()) return@collect
                val now = System.currentTimeMillis()
                for (f in TradeLink.evaluate(db.trades().engineRows(), alerts, coins, now)) {
                    // One-shot: only the call that flips firedAt posts (K9.1 b).
                    if (db.tradeAlerts().markFired(f.trade.id, f.level.name, now) == 1) {
                        val fa = settings.current().digits == ir.andarz.data.settings.Digits.FA
                        post(f, facade.paramsAt(Instant.ofEpochMilli(now)).partialRLo, fa)
                    }
                }
            }
        }
    }

    private fun post(f: TradeLink.Fire, partialR: Double, fa: Boolean) {
        val sym = Fmt.isolate(f.trade.symbol)
        val (title, body) = when (f.level) {
            LinkLevel.TP -> content.ui("mkt.link.notif.tp.title", "symbol" to sym) to content.ui("mkt.link.notif.body")
            LinkLevel.STOP -> content.ui("mkt.link.notif.stop.title", "symbol" to sym) to content.ui("mkt.link.notif.body")
            LinkLevel.PARTIAL -> {
                val r = Fmt.number(XlMath.round(partialR, 2), 2, fa = fa, group = false, trim = true)
                content.ui("notif.partial.title", "symbol" to sym, "r" to r) to content.ui("notif.partial.body")
            }
        }
        val r = Reminder("trade-link-${f.trade.id}-${f.level.name}", ReminderKind.OPEN, Instant.now(), "trade/${f.trade.id}", f.trade.id)
        DisciplineChannels.post(ctx, r, title, body)
    }

    /** Arms or disarms one level of an OPEN trade at the price of [TradeLink.levelPrice]. */
    suspend fun setArmed(tradeId: String, level: LinkLevel, on: Boolean) {
        if (!on) { db.tradeAlerts().remove(tradeId, level.name); return }
        val t = db.trades().get(tradeId) ?: return
        if (t.status != "OPEN") return
        val p = facade.paramsAt(Instant.now())
        db.tradeAlerts().put(TradeAlertEntity(tradeId, level.name, TradeLink.levelPrice(t, level, p), System.currentTimeMillis()))
    }
}
