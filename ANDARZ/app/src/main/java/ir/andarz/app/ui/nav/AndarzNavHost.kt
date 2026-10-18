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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ir.andarz.app.content.ContentRepository
import ir.andarz.app.ui.market.MarketScreen

/**
 * The RTL shell: a bottom bar built from [AndarzNav.bottomBar] and one destination per route.
 *
 * M0 ships the bar, the Market destination (off state plus consent sheet) and a placeholder per other
 * route. The journal screens arrive with M4, so the placeholders name themselves from the content JSON
 * rather than from a literal (law 3).
 */
@Composable
fun AndarzNavHost(
    content: ContentRepository,
    startRoute: String = AndarzNav.TODAY,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
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
                                destination.icon != null ->
                                    Icon(destination.icon, contentDescription = null)
                                destination.iconRes != null ->
                                    Icon(painterResource(destination.iconRes), contentDescription = null)
                            }
                        },
                        label = { Text(content.ui(destination.labelKey)) },
                    )
                }
            }
        },
    ) { insets ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.padding(insets),
        ) {
            composable(AndarzNav.TODAY) { Placeholder(content.ui("nav.today")) }
            composable(AndarzNav.JOURNAL) { Placeholder(content.ui("nav.journal")) }
            composable(AndarzNav.MARKET) { MarketScreen(content = content) }
            composable(AndarzNav.CALCULATOR) { Placeholder(content.ui("nav.calculator")) }
            composable(AndarzNav.MORE) { Placeholder(content.ui("nav.more")) }
            // Insights keeps its own route and is reached from More and from Journal (D-M07).
            composable(AndarzNav.INSIGHTS) { Placeholder(content.ui("nav.insights")) }
        }
    }
}

@Composable
private fun Placeholder(label: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}
