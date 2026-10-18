package ir.andarz.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import ir.andarz.app.content.ContentRepository
import ir.andarz.app.ui.AppViewModel
import ir.andarz.app.ui.LocalUi
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.nav.AndarzNav
import ir.andarz.app.ui.nav.AndarzNavHost
import ir.andarz.app.ui.onboarding.OnboardingScreen
import ir.andarz.app.ui.theme.AndarzTheme
import ir.andarz.app.ui.theme.ThemeChoice
import ir.andarz.data.settings.ThemeMode
import javax.inject.Inject

/**
 * The one activity (2.3). Compose only, edge to edge, RTL, dark by default (law 9). The splash stays until
 * settings are read, so onboarding and the theme never flash. The app lock of 8.2 wraps this content in M6.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var content: ContentRepository

    private val app: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { app.settings.value == null }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val startRoute = intent?.getStringExtra(EXTRA_ROUTE) ?: AndarzNav.TODAY

        setContent {
            val settings by app.settings.collectAsState()
            val s = settings ?: return@setContent
            var onboarded by rememberSaveable { mutableStateOf(s.onboardingDone) }
            val choice = when (s.theme) { ThemeMode.DARK -> ThemeChoice.DARK; ThemeMode.LIGHT -> ThemeChoice.LIGHT; ThemeMode.SYSTEM -> ThemeChoice.SYSTEM }
            // The app is Persian and forces RTL, and must still work if a device reports LTR (6.3).
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalUi provides Ui(content.bundle, s),
            ) {
                AndarzTheme(choice = choice) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        if (!onboarded && !s.onboardingDone) OnboardingScreen(onDone = { onboarded = true })
                        else AndarzNavHost(content = content, startRoute = startRoute, app = app)
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
