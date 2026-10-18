package ir.andarz.app.ui.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.andarz.app.content.ContentRepository
import ir.andarz.app.ui.AppViewModel
import ir.andarz.app.ui.journal.JournalScreen
import ir.andarz.app.ui.market.MarketScreen
import ir.andarz.app.ui.today.TodayScreen
import ir.andarz.app.ui.trade.TradeScreen
import ir.andarz.app.ui.wizard.WizardScreen
import ir.andarz.data.JournalRepository
import ir.andarz.data.db.AndarzDatabase
import ir.andarz.data.db.TradeEntity
import ir.andarz.engine.LockNeed
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TodayViewModel @Inject constructor(db: AndarzDatabase, private val journal: JournalRepository) : ViewModel() {
    val open: StateFlow<List<TradeEntity>> =
        db.trades().observeAll().map { all -> all.filter { it.status == "OPEN" } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init { viewModelScope.launch { journal.expirePlans() } }

    fun monitor(id: String) = viewModelScope.launch { journal.monitorCheck(id) }
}

/**
 * The RTL shell: a bottom bar built from [AndarzNav.bottomBar] and one destination per route. Routes carry
 * ids only, so the back stack survives process death (5.0).
 */
@Composable
fun AndarzNavHost(
    content: ContentRepository,
    startRoute: String = AndarzNav.TODAY,
    navController: NavHostController = rememberNavController(),
    app: AppViewModel = hiltViewModel(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val snapshot by app.snapshot.collectAsState()
    val pledge by app.pledge.collectAsState()
    val settings by app.settings.collectAsState()
    val showBar = AndarzNav.bottomBar.any { currentRoute?.startsWith(it.route) == true }

    Scaffold(
        bottomBar = {
            if (showBar) NavigationBar {
                for (destination in AndarzNav.bottomBar.filter { it.visible }) {
                    NavigationBarItem(
                        selected = currentRoute?.startsWith(destination.route) == true,
                        onClick = {
                            if (currentRoute != destination.route) {
                                navController.navigate(destination.route) {
                                    popUpTo(AndarzNav.TODAY) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            when {
                                destination.icon != null -> Icon(destination.icon, contentDescription = null)
                                destination.iconRes != null -> Icon(painterResource(destination.iconRes), contentDescription = null)
                            }
                        },
                        label = { Text(content.ui(destination.labelKey)) },
                    )
                }
            }
        },
    ) { insets ->
        NavHost(navController = navController, startDestination = startRoute, modifier = Modifier.padding(insets)) {
            composable(AndarzNav.TODAY) {
                val vm: TodayViewModel = hiltViewModel()
                val open by vm.open.collectAsState()
                TodayScreen(
                    snapshot = snapshot, openTrades = open, pledgeSigned = pledge != null,
                    adviceEnabled = settings?.adviceEnabled ?: true,
                    onNewTrade = { navController.navigate(AndarzNav.WIZARD) },
                    onOpenTrade = { navController.navigate(AndarzNav.trade(it)) },
                    onNeed = { need, tradeId ->
                        when (need) {
                            LockNeed.REPORT, LockNeed.CLOSE_POSITION -> tradeId?.let { navController.navigate(AndarzNav.trade(it)) }
                            // Reviews arrive with M5; until then the need is shown and the button does nothing harmful.
                            LockNeed.REVIEW, LockNeed.WEEKLY_REVIEW -> navController.navigate(AndarzNav.REVIEWS)
                        }
                    },
                    onCloseDay = { navController.navigate(AndarzNav.REVIEWS) },
                    onMonitor = { vm.monitor(it) },
                )
            }
            composable(AndarzNav.JOURNAL) {
                JournalScreen(onOpen = { navController.navigate(AndarzNav.trade(it)) }, onInsights = { navController.navigate(AndarzNav.INSIGHTS) })
            }
            composable(AndarzNav.MARKET) { MarketScreen(content = content) }
            composable(AndarzNav.CALCULATOR) { Placeholder(content.ui("nav.calculator")) }
            composable(AndarzNav.MORE) { Placeholder(content.ui("nav.more")) }
            composable(AndarzNav.INSIGHTS) { Placeholder(content.ui("nav.insights")) }
            composable(AndarzNav.REVIEWS) { Placeholder(content.ui("rev.title")) }
            composable(AndarzNav.WIZARD) {
                WizardScreen(
                    pledgeSigned = pledge != null,
                    onDone = { id -> navController.navigate(AndarzNav.trade(id)) { popUpTo(AndarzNav.WIZARD) { inclusive = true } } },
                    onCancel = { navController.popBackStack() },
                )
            }
            composable(AndarzNav.TRADE) { TradeScreen(onBack = { navController.popBackStack() }) }
        }
    }
}

@Composable
private fun Placeholder(label: String) {
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}
