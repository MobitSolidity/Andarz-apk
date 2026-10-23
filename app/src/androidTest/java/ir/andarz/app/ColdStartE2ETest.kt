package ir.andarz.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.andarz.app.content.ContentBundle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end smoke (T-E2E-1, TESTING.md): a fresh install launches the real activity with Hilt, Room and
 * DataStore, passes the splash and shows the first onboarding page in Persian, and the first button works.
 * Needs a device or emulator: `./gradlew :app:connectedDebugAndroidTest` (clear app data first).
 */
@RunWith(AndroidJUnit4::class)
class ColdStartE2ETest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val bundle: ContentBundle by lazy {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        fun read(n: String) = assets.open(n).bufferedReader().use { it.readText() }
        ContentBundle.parse(read("content_fa.json"), read("content_market_fa.json"))
    }

    @Test
    fun freshInstallShowsOnboardingAndAdvances() {
        rule.waitUntil(10_000) { rule.onAllNodes(androidx.compose.ui.test.hasText(bundle.ui("onb.welcome.title"))).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(bundle.ui("onb.welcome.title")).assertIsDisplayed()
        rule.onNodeWithText(bundle.ui("onb.welcome.start")).performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(androidx.compose.ui.test.hasText(bundle.ui("onb.privacy.title"))).fetchSemanticsNodes().isNotEmpty() }
    }
}
