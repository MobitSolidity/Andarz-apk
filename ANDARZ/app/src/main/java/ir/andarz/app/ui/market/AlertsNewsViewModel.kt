package ir.andarz.app.ui.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.andarz.market.MarketModule
import ir.andarz.market.calendar.CalendarData
import ir.andarz.market.calendar.EconEvent
import ir.andarz.market.model.AlertSettings
import ir.andarz.market.model.CoinRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State of the News and Alerts sections and of the coin sheet's rule editor (MK2). Everything here reads
 * Market's own preferences and the local calendar asset; nothing opens a socket (K4).
 */
@HiltViewModel
class AlertsNewsViewModel @Inject constructor() : ViewModel() {
    private val alerts = MarketModule.alerts
    private val calendar = MarketModule.calendar

    val settings: StateFlow<AlertSettings> =
        alerts.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertSettings())
    val rules: StateFlow<Map<String, CoinRule>> =
        alerts.rules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val reminded: StateFlow<Set<String>> =
        calendar.reminded.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _calendar = MutableStateFlow(CalendarData.NONE)
    val calendarData: StateFlow<CalendarData> = _calendar.asStateFlow()

    init {
        viewModelScope.launch { _calendar.value = calendar.data() }
    }

    fun update(change: (AlertSettings) -> AlertSettings) {
        viewModelScope.launch { alerts.update(change) }
    }

    fun setRule(rule: CoinRule) {
        viewModelScope.launch { alerts.setRule(rule) }
    }

    fun removeRule(id: String) {
        viewModelScope.launch { alerts.removeRule(id) }
    }

    fun test() {
        viewModelScope.launch { alerts.test() }
    }

    fun testAlarm() {
        viewModelScope.launch { alerts.testAlarm() }
    }

    fun setMonitor(on: Boolean) {
        viewModelScope.launch { MarketModule.setMonitor(on) }
    }

    fun toggleReminder(e: EconEvent) {
        viewModelScope.launch { calendar.toggle(e, System.currentTimeMillis()) }
    }

    /** False when no Clock app handled the alarm (March shows a short message then). */
    fun setClockAlarm(e: EconEvent): Boolean = calendar.setClockAlarm(e, System.currentTimeMillis())

    // permission rows
    fun canOverlay() = alerts.canOverlay()
    fun clockAvailable() = alerts.clockAvailable()
    fun canFullScreen() = alerts.canFullScreen()
    fun canExact() = alerts.canExact()
    fun openOverlaySettings() = alerts.openOverlaySettings()
    fun openFullScreenSettings() = alerts.openFullScreenSettings()
    fun openExactSettings() = alerts.openExactSettings()
}
