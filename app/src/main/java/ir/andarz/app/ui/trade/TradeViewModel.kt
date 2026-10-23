package ir.andarz.app.ui.trade

import ir.andarz.app.ui.safeLaunch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.andarz.data.EngineFacade
import ir.andarz.data.ExitLeg
import ir.andarz.data.JournalRepository
import ir.andarz.data.ReviewInput
import ir.andarz.data.db.AndarzDatabase
import ir.andarz.data.db.TradeEntity
import ir.andarz.data.db.TradeEventEntity
import ir.andarz.engine.Params
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/** Detail, management, close and review of one trade (5.4, 5.5). The route carries only the id. */
@HiltViewModel
class TradeViewModel @Inject constructor(
    state: SavedStateHandle,
    db: AndarzDatabase,
    private val journal: JournalRepository,
    private val facade: EngineFacade,
) : ViewModel() {
    val id: String = checkNotNull(state["id"])
    val trade: StateFlow<TradeEntity?> = db.trades().observe(id).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val events: StateFlow<List<TradeEventEntity>> = db.events().observeForTrade(id).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val params = MutableStateFlow(Params.DEFAULTS)
    val setupName: StateFlow<String?> = kotlinx.coroutines.flow.combine(db.trades().observe(id), db.setups().observeAll()) { t, all -> all.firstOrNull { it.id == t?.setupId }?.name }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val error = MutableStateFlow<String?>(null)
    val deleted = MutableStateFlow(false)

    init { safeLaunch { params.value = facade.paramsAt(Instant.now()) } }

    private fun run(block: suspend () -> Unit) = safeLaunch {
        try { block() } catch (e: IllegalArgumentException) { error.value = e.message } catch (e: IllegalStateException) { error.value = e.message }
    }

    fun moveStop(newSl: Double?) = run { journal.moveStop(id, newSl) }
    fun partial(price: Double, fraction: Double, fee: Double) = run { journal.partialExit(id, price, fraction, fee) }
    fun event(type: String, text: String?) = run { journal.addEvent(id, type, text) }
    fun reportAdd() = run { journal.reportAdd(id) }
    fun monitor() = run { journal.monitorCheck(id) }
    fun close(legs: List<ExitLeg>, fee: Double, reason: String, at: Instant) = run { journal.close(id, legs, fee, reason, at) }
    fun review(r: ReviewInput) = run { journal.saveReview(id, r, params.value.reportMinChars.toInt()) }
    fun cancelPlan() = run { journal.cancelPlan(id) }
    fun delete() = run { deleted.value = journal.delete(id); if (!deleted.value) error.value = "locked" }
}
