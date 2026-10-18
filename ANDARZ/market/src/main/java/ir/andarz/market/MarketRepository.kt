package ir.andarz.market

import ir.andarz.market.data.PriceHub
import ir.andarz.market.model.CoinRule
import ir.andarz.market.model.MarketState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The read side of Market that :app sees (K4 sketch: "StateFlows, acquire(ui), refreshNow(), extraSymbols").
 *
 * Holding the repository is the only way to make the hub run, and even then it runs only while the
 * consent-and-master-switch gate is open (K6.4). Every [acquire] must be matched by a [release]; the app
 * ties them to the lifecycle of the Market screen (reason a) and, in MK2, to the foreground alert reason
 * (b) and the service (c).
 */
class MarketRepository internal constructor(
    private val hub: PriceHub,
    private val prefs: MarketPrefs,
) {
    /** The merged price state. Empty until the hub has run at least once. */
    val state: StateFlow<MarketState> get() = hub.state

    /** Market's starred coins (D-M19: separate from Andarz's discipline watch-list). */
    val watch: Flow<Set<String>> get() = prefs.watch

    /** USD/Toman display toggle (K6.2). */
    val toman: Flow<Boolean> get() = prefs.toman

    /** Per-coin rules. The editor arrives in MK2; the hub already streams rule coins in the background. */
    val rules: Flow<Map<String, CoinRule>> get() = prefs.rules

    /** Asks for data. [ui] = the full list is on screen (K6.4 reason a). */
    fun acquire(ui: Boolean) = hub.acquire(ui)

    /** Gives back a demand taken with [acquire]. */
    fun release(ui: Boolean) = hub.release(ui)

    /** The refresh button: the coin list is fetched again now (March). */
    fun refreshNow() = hub.refreshNow()

    /** Session samples of one coin, for the coin-sheet chart when there is no 7-day series. */
    fun session(id: String): List<Double> = hub.history.series(id)

    /** MK3: the trade-level watcher's symbols, streamed even when only the background holds the hub. */
    var extraSymbols: Set<String>
        get() = hub.extraSymbols
        set(value) {
            hub.extraSymbols = value
        }

    suspend fun toggleWatch(id: String) = prefs.toggleWatch(id)

    suspend fun setToman(on: Boolean) = prefs.setToman(on)
}
