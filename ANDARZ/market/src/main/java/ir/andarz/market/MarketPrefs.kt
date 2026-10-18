package ir.andarz.market

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Market's own preference store, separate from the journal's settings (D-M17). March's theme and language
 * preferences are dropped: Andarz owns both.
 *
 * The watch list, alert settings and per-coin rules join this file in MK1/MK2; M0 needs only the two
 * values that gate the network: the master switch and the consent stamp. Both default to "off/never", so
 * a fresh install opens no socket (K4).
 */
private val Context.marketStore by preferencesDataStore(name = "market")

private val MARKET_ENABLED = booleanPreferencesKey("market_enabled")
private val CONSENT_AT = longPreferencesKey("consent_at")

/** The state of the two gates in front of every request. */
data class MarketGate(
    /** The master switch of Settings -> Market (`mkt.set.enable`). Default off (D-M08). */
    val enabled: Boolean = false,
    /** When the user accepted the consent sheet, or null while they never did (K4). */
    val consentAt: Long? = null,
) {
    /** The hub may run only when both gates are open (K4, K6.4). */
    val mayUseNetwork: Boolean get() = enabled && consentAt != null
}

internal class MarketPrefs(private val context: Context) {

    val gate: Flow<MarketGate> = context.marketStore.data.map { p ->
        MarketGate(
            enabled = p[MARKET_ENABLED] ?: false,
            consentAt = p[CONSENT_AT]?.takeIf { it > 0L },
        )
    }

    /** Turning the switch off stops the hub, the service and every price alert (K4). */
    suspend fun setEnabled(on: Boolean) {
        context.marketStore.edit { it[MARKET_ENABLED] = on }
    }

    /** Records the consent and turns Market on, in one write, as the sheet's accept button does. */
    suspend fun acceptConsent(at: Long) {
        context.marketStore.edit {
            it[CONSENT_AT] = at
            it[MARKET_ENABLED] = true
        }
    }
}
