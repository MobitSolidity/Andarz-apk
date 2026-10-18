package ir.andarz.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import ir.andarz.app.content.ContentRepository
import ir.andarz.app.ui.nav.AndarzNav
import ir.andarz.app.ui.nav.AndarzNavHost
import ir.andarz.app.ui.theme.AndarzTheme
import ir.andarz.app.ui.theme.ThemeChoice
import javax.inject.Inject

/**
 * The one activity (§2.3). Compose only, edge to edge, RTL, dark by default (law 9).
 *
 * The app lock of §8.2 will wrap the whole content, Market included (K6.11); it arrives with M6. The
 * route extra is how a notification deep-links in: ids only, so it survives process death (§5.0).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var content: ContentRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val startRoute = intent?.getStringExtra(EXTRA_ROUTE) ?: AndarzNav.TODAY

        setContent {
            // The app is Persian and forces RTL, and must still work if a device reports LTR (§6.3).
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                AndarzTheme(choice = ThemeChoice.DARK) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AndarzNavHost(content = content, startRoute = startRoute)
                    }
                }
            }
        }
    }

    companion object {
        /** The route a notification wants opened, e.g. `market/coin/bitcoin` (K6.11). */
        const val EXTRA_ROUTE = "ir.andarz.app.extra.ROUTE"
    }
}
