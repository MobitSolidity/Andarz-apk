package ir.andarz.app.ui.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.andarz.market.MarketGate
import ir.andarz.market.MarketHosts
import ir.andarz.market.MarketModule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The two gates in front of the network, and nothing else yet (M0). The price hub, the repository and the
 * alerts controller arrive with MK1/MK2.
 *
 * Reading the gate opens no socket: it is a preference flow (K4).
 */
@HiltViewModel
class MarketViewModel @Inject constructor() : ViewModel() {

    val gate: StateFlow<MarketGate> = MarketModule.gate.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MarketGate(),
    )

    /**
     * The hosts the consent sheet lists. They come from the same constants the egress interceptor
     * enforces, so the text cannot drift from the code (K4).
     */
    val hosts: List<String> = MarketHosts.all

    /** "Turn Market on" in the consent sheet: records the consent and opens the master switch. */
    fun acceptConsent() {
        viewModelScope.launch { MarketModule.acceptConsent(System.currentTimeMillis()) }
    }

    /** Settings -> Market, and the off state's action once consent exists. */
    fun setEnabled(on: Boolean) {
        viewModelScope.launch { MarketModule.setEnabled(on) }
    }
}
