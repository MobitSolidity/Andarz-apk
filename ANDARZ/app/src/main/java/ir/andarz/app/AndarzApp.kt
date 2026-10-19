package ir.andarz.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import ir.andarz.app.market.MarketHostImpl
import ir.andarz.market.MarketModule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

/**
 * Installs the `:market` bridge and warms the wording cache. Nothing here touches the network: Market is
 * off by default and its HTTP client is built only on the first real demand (K4, D-M08).
 */
@HiltAndroidApp
class AndarzApp : Application() {

    @Inject
    lateinit var marketHost: MarketHostImpl

    override fun onCreate() {
        super.onCreate()
        // The bridge only hands :market a way to ask for words and routes. The two gates in front of the
        // network (master switch and consent) stay closed until the user opens them (K4).
        marketHost.watchSettings(appScope)
        MarketModule.install(this, marketHost)
        // ClockRing.appVisible and demand reason (b) follow the whole app's foreground state (K6.4).
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, e ->
                when (e) {
                    Lifecycle.Event.ON_START -> MarketModule.setAppVisible(true)
                    Lifecycle.Event.ON_STOP -> MarketModule.setAppVisible(false)
                    else -> Unit
                }
            },
        )
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
