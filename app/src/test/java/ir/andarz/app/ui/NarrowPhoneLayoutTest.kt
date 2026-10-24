package ir.andarz.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import ir.andarz.app.content.ContentBundle
import ir.andarz.app.ui.components.FitText
import ir.andarz.app.ui.nav.AndarzNav
import ir.andarz.app.ui.theme.AndarzTheme
import ir.andarz.app.ui.theme.ThemeChoice
import ir.andarz.app.ui.today.TodayScreen
import ir.andarz.data.settings.Digits
import ir.andarz.data.settings.Language
import ir.andarz.data.settings.Settings
import ir.andarz.engine.Governor
import ir.andarz.engine.LedgerEntry
import ir.andarz.engine.LedgerType
import ir.andarz.engine.Params
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * D-93 / D-94: the narrowest common phone (320 x 640 dp) at 100% and 200% font, in both languages and both
 * directions. The bottom bar labels are never cut, Today never clips, the eye toggles the mask, and the glossary
 * renders. Runs on the JVM under Robolectric like AccessibilityLayoutTest (D-71).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi", application = android.app.Application::class)
class NarrowPhoneLayoutTest {
    private val compose = createComposeRule()
    val rule get() = compose

    @get:Rule val chain: org.junit.rules.RuleChain = org.junit.rules.RuleChain
        .outerRule(object : org.junit.rules.TestRule {
            override fun apply(base: org.junit.runners.model.Statement, d: org.junit.runner.Description) = object : org.junit.runners.model.Statement() {
                override fun evaluate() {
                    org.junit.Assume.assumeTrue("Compose UI tests run in the debug variant", ir.andarz.app.BuildConfig.DEBUG)
                    base.evaluate()
                }
            }
        })
        .around(compose)

    private fun f(n: String) = listOf(File("src/main/assets/$n"), File("app/src/main/assets/$n")).first { it.isFile }.readText()
    private val fa by lazy { ContentBundle.parse(f("content_fa.json"), f("content_market_fa.json"), f("glossary.json"), f("content_app_fa.json")) }
    private val en by lazy { ContentBundle.parse(f("content_en.json"), f("content_market_en.json"), f("glossary.json"), f("content_app_en.json")) }

    private fun host(ui: Ui, fontScale: Float, body: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(d.density, fontScale),
                LocalLayoutDirection provides if (ui.content.isLtr) LayoutDirection.Ltr else LayoutDirection.Rtl,
                LocalUi provides ui,
            ) { AndarzTheme(ThemeChoice.DARK) { body() } }
        }
        rule.mainClock.advanceTimeBy(500)
    }

    private fun texts() = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()

    private fun assertNoHorizontalClipping() {
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val all = texts()
        assertTrue(all.isNotEmpty())
        all.forEach { n ->
            val b = n.boundsInRoot
            assertTrue(b.left >= root.left - 1f && b.right <= root.right + 1f, "text outside the window: ${n.config.getOrNull(SemanticsProperties.Text)} $b vs $root")
        }
    }

    private fun snapshot() = Governor.snapshot(
        Instant.parse("2026-10-14T06:00:00Z"), ZoneId.of("Asia/Tehran"), Params.DEFAULTS,
        listOf(LedgerEntry(Instant.parse("2026-10-01T00:00:00Z"), LedgerType.BASELINE, 123456.78)), emptyList(), emptyList(), emptyList(),
    )

    /** The five bar labels share the row like NavigationBar items do; each label is drawn whole (D-93). */
    private fun barRow(ui: Ui) {
        host(ui, 2f) {
            Row(Modifier.fillMaxWidth()) {
                AndarzNav.bottomBar.forEach { d -> FitText(ui.t(d.labelKey), MaterialTheme.typography.labelMedium, Modifier.weight(1f)) }
            }
        }
        rule.mainClock.advanceTimeBy(1000)
        val labels = AndarzNav.bottomBar.map { ui.t(it.labelKey) }
        val shown = texts().flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
        assertTrue(shown.containsAll(labels), "every label is present: $shown")
    }

    @Test
    fun `Persian bottom bar labels fit at 320 dp and 200 percent font`() {
        barRow(Ui(fa, Settings()))
        assertNoHorizontalClipping()
    }

    @Test
    fun `English bottom bar labels fit at 320 dp and 200 percent font`() {
        barRow(Ui(en, Settings(language = Language.EN, digits = Digits.EN)))
        assertNoHorizontalClipping()
    }

    @Test
    fun `Today in English, left to right, at 200 percent does not clip`() {
        host(Ui(en, Settings(language = Language.EN, digits = Digits.EN)), 2f) {
            TodayScreen(snapshot(), emptyList(), pledgeSigned = true, adviceEnabled = true, onNewTrade = {}, onOpenTrade = {}, onNeed = { _, _ -> }, onCloseDay = {}, onMonitor = {})
        }
        assertNoHorizontalClipping()
    }

    @Test
    fun `Today in Persian at 320 dp and 200 percent with a six-figure equity does not clip`() {
        host(Ui(fa, Settings()), 2f) {
            TodayScreen(snapshot(), emptyList(), pledgeSigned = true, adviceEnabled = true, onNewTrade = {}, onOpenTrade = {}, onNeed = { _, _ -> }, onCloseDay = {}, onMonitor = {})
        }
        assertNoHorizontalClipping()
    }

    @Test
    fun `the eye button asks to hide, and a hidden Today shows no dollar amount`() {
        var asked: Boolean? = null
        val shownUi = Ui(en, Settings(language = Language.EN, digits = Digits.EN))
        host(shownUi, 1f) {
            TodayScreen(snapshot(), emptyList(), pledgeSigned = true, adviceEnabled = true, onNewTrade = {}, onOpenTrade = {}, onNeed = { _, _ -> }, onCloseDay = {}, onMonitor = {}, onToggleAmounts = { asked = it })
        }
        val all = texts().flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
        assertTrue(all.any { it.contains("123,456.78") }, "equity is visible: $all")
        rule.onNodeWithContentDescription(shownUi.t("app.amounts.hide")).performClick()
        assertEquals(true, asked)
    }

    @Test
    fun `masked Ui hides money but the unmasked copy and percentages stay`() {
        val u = Ui(en, Settings(digits = Digits.EN, hideAmounts = true))
        assertEquals(Ui.MASKED, u.money(1234.5))
        assertEquals(Ui.MASKED, u.signedMoney(-20.0))
        assertFalse(u.unmasked().money(1234.5).contains("\u2022"))
        assertTrue(u.unmasked().money(1234.5).contains("1,234.50"))
        assertTrue(u.money(1234.5, mask = false).contains("1,234.50"))
        assertTrue(u.pct(0.05).contains("5"))
        assertEquals(u.t("common.dash"), u.money(null))
        val shown = Ui(en, Settings(digits = Digits.EN))
        assertTrue(shown.money(1234.5).contains("1,234.50"))
        assertTrue(shown.signedMoney(-20.0).contains("\u2212\$20.00"))
    }
}
