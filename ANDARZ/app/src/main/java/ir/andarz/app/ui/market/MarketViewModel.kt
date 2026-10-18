package ir.andarz.app.ui.market

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.andarz.app.ui.nav.MarketSection
import ir.andarz.market.DEFAULT_WATCH
import ir.andarz.market.MarketGate
import ir.andarz.market.MarketHosts
import ir.andarz.market.MarketModule
import ir.andarz.market.MarketRepository
import ir.andarz.market.model.MarketState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Market destination's state: the two gates, the merged prices, the stars and the USD/Toman toggle.
 *
 * Demand (K6.4 reason a) is not taken here: the screen holds the hub only while it is visible, through
 * [onVisible]/[onHidden] driven by the lifecycle, so a Market screen in the back stack opens no socket.
 * Reading the gate or the stars is a preference read and opens nothing (K4).
 *
 * The section and the open coin sheet live in the [SavedStateHandle], so both survive process death
 * (K6.1; route arguments stay ids only).
 */
@HiltViewModel
class MarketViewModel @Inject constructor(
    private val repo: MarketRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {

    val gate: StateFlow<MarketGate> = MarketModule.gate.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MarketGate())
    val state: StateFlow<MarketState> = repo.state
    val watch: StateFlow<Set<String>> = repo.watch.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_WATCH)
    val toman: StateFlow<Boolean> = repo.toman.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The hosts the consent sheet lists: the constants the egress interceptor enforces (K4). */
    val hosts: List<String> = MarketHosts.all

    val section: StateFlow<String> = saved.getStateFlow(KEY_SECTION, MarketSection.PRICES.name)
    val openCoin: StateFlow<String?> = saved.getStateFlow(KEY_COIN, null)

    private var holding = false

    fun setSection(s: MarketSection) {
        saved[KEY_SECTION] = s.name
    }

    fun openCoin(id: String?) {
        saved[KEY_COIN] = id
    }

    /** The Market screen became visible: hold the hub with the full list (K6.4 a). */
    fun onVisible() {
        if (holding) return
        holding = true
        repo.acquire(ui = true)
    }

    /** The screen left the foreground: give the demand back, so no socket stays open for it. */
    fun onHidden() {
        if (!holding) return
        holding = false
        repo.release(ui = true)
    }

    fun refresh() = repo.refreshNow()

    fun toggleStar(id: String) {
        viewModelScope.launch { repo.toggleWatch(id) }
    }

    fun setToman(on: Boolean) {
        viewModelScope.launch { repo.setToman(on) }
    }

    fun session(id: String): List<Double> = repo.session(id)

    /** "Turn Market on" in the consent sheet: records the consent and opens the master switch. */
    fun acceptConsent() {
        viewModelScope.launch { MarketModule.acceptConsent(System.currentTimeMillis()) }
    }

    /** Settings -> Market, and the off state's action once consent exists. */
    fun setEnabled(on: Boolean) {
        viewModelScope.launch { MarketModule.setEnabled(on) }
    }

    override fun onCleared() {
        onHidden()
    }

    private companion object {
        const val KEY_SECTION = "market.section"
        const val KEY_COIN = "market.coin"
    }
}
