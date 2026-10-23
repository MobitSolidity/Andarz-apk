package ir.andarz.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.andarz.data.EngineFacade
import ir.andarz.data.db.AndarzDatabase
import ir.andarz.data.db.PledgeEntity
import ir.andarz.data.settings.Settings
import ir.andarz.data.settings.SettingsRepository
import ir.andarz.engine.Snapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Settings, the live snapshot and the pledge, shared by every journal screen. */
@HiltViewModel
class AppViewModel @Inject constructor(
    settings: SettingsRepository,
    facade: EngineFacade,
    db: AndarzDatabase,
) : ViewModel() {
    val settings: StateFlow<Settings?> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val snapshot: StateFlow<Snapshot?> = facade.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val pledge: StateFlow<PledgeEntity?> = db.pledges().observeLatest().stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
