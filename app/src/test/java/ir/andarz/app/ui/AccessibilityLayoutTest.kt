package ir.andarz.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import ir.andarz.app.content.ContentBundle
import ir.andarz.app.ui.components.ChecklistRow
import ir.andarz.app.ui.components.LimitGauge
import ir.andarz.app.ui.components.Verdict
import ir.andarz.app.ui.components.VerdictBanner
import ir.andarz.app.ui.journal.JournalRow
import ir.andarz.app.ui.theme.AndarzTheme
import ir.andarz.app.ui.theme.ThemeChoice
import ir.andarz.app.ui.today.TodayScreen
import ir.andarz.data.db.PlanSnapshot
import ir.andarz.data.db.TradeEntity
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
import kotlin.test.assertTrue

/**
 * F9 and F10 of 10.6, run on the JVM under Robolectric (D-71) because the sandbox has no emulator:
 * - F9: light theme at 200% font on a 360 x 800 dp window — every text node of Today, a journal row and the
 *   wizard checklist stays inside the window horizontally (no clipped text; vertical overflow scrolls).
 * - F10: the verdict banner, the gauges and the checklist rows expose a label and a state word.
 * The same flows remain on the device list in MANUAL_TEST.md (TalkBack itself cannot run here).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi", application = android.app.Application::class)
class AccessibilityLayoutTest {
    /*
     * The Compose test activity comes from the debug-only ui-test-manifest; the release unit-test variant runs
     * the same sources without it. The assumption must run before the compose rule launches its activity, hence
     * an outer rule chain (D-71).
     */
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

    private val bundle: ContentBundle by lazy {
        fun f(n: String) = listOf(File("src/main/assets/$n"), File("app/src/main/assets/$n")).first { it.isFile }.readText()
        ContentBundle.parse(f("content_fa.json"), f("content_market_fa.json"), f("glossary.json"), f("content_app_fa.json"))
    }
    private val ui by lazy { Ui(bundle, Settings()) }

    private fun host(fontScale: Float, theme: ThemeChoice, body: @Composable () -> Unit) {
        // Today ticks once a second; a manual clock keeps the idle check from chasing it.
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale), LocalLayoutDirection provides LayoutDirection.Rtl, LocalUi provides ui) {
                AndarzTheme(theme) { body() }
            }
        }
        rule.mainClock.advanceTimeBy(500)
    }

    /** Every node with text lies inside the root horizontally (F9). */
    private fun assertNoHorizontalClipping() {
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val texts = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(texts.isNotEmpty(), "no text rendered")
        texts.forEach { n ->
            val b = n.boundsInRoot
            val label = n.config.getOrNull(SemanticsProperties.Text)?.joinToString()
            assertTrue(b.left >= root.left - 1f && b.right <= root.right + 1f, "text outside the window: $label $b vs $root")
        }
    }

    private fun snapshot() = Governor.snapshot(
        Instant.parse("2026-10-14T06:00:00Z"), ZoneId.of("Asia/Tehran"), Params.DEFAULTS,
        listOf(LedgerEntry(Instant.parse("2026-10-01T00:00:00Z"), LedgerType.BASELINE, 2000.0)), emptyList(), emptyList(), emptyList(),
    )

    private fun trade() = TradeEntity(
        id = "t1", account = "DEMO", status = "CLOSED", symbol = "BTCUSDT", side = "LONG", kind = "INTRADAY", setupId = null,
        timeframe = "15m", htfTrend = "UP", counterTrendReason = null, emotion = "CALM", checklistMask = 0xFF, chk = 8, equity = 2000.0,
        plannedEntry = 100.0, slInitial = 96.0, tp = 108.0, lev = 5.0, riskPctChosen = 0.01, riskPctCap = 0.01, gradeA = true,
        entryOrder = "LIMIT", isolated = true, slTpOnExchange = true, canMonitor = true,
        plan = PlanSnapshot(0.04, 2.0, 20.0, 500.0, 100.0, 0.05, 80.5, 0.195, 4.875), gateReasons = "", gatePlanOk = true,
        entryAllowed = true, gateAllowed = true, entry = 100.0, notional = 500.0, entryTime = 1L, net = 39.4, r = 1.97, ok = true,
        imported = true, loggedLate = true, createdAt = 1L, updatedAt = 1L,
    )

    @Test
    fun f9TodayLightAt200PercentDoesNotClip() {
        host(2f, ThemeChoice.LIGHT) {
            TodayScreen(snapshot(), emptyList(), pledgeSigned = true, adviceEnabled = true, onNewTrade = {}, onOpenTrade = {}, onNeed = { _, _ -> }, onCloseDay = {}, onMonitor = {})
        }
        assertNoHorizontalClipping()
    }

    @Test
    fun f9JournalRowAndChecklistLightAt200PercentDoNotClip() {
        host(2f, ThemeChoice.LIGHT) {
            androidx.compose.foundation.layout.Column {
                JournalRow(trade(), "retest", {})
                val item = ui.content.arr("checklist")!!.first() as kotlinx.serialization.json.JsonObject
                ChecklistRow(
                    title = item["short"].toString().trim('"'), detail = item["full"].toString().trim('"'), checked = true, enabled = true,
                    tag = ui.t("common.details"), onToggle = {},
                )
            }
        }
        assertNoHorizontalClipping()
    }

    @Test
    fun f10VerdictGaugesAndChecklistExposeLabelAndState() {
        host(1f, ThemeChoice.DARK) {
            androidx.compose.foundation.layout.Column {
                VerdictBanner(Verdict.DENIED, "verdict-title", "numbers")
                LimitGauge("gauge-label", "1 / 3", 0.9, red = false)
                ChecklistRow("check-title", null, checked = false, enabled = true, onToggle = {})
            }
        }
        fun stateOf(text: String): String? = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))
            .fetchSemanticsNodes().firstOrNull { n ->
                val all = (n.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()) +
                    (n.config.getOrNull(SemanticsProperties.ContentDescription) ?: emptyList())
                all.any { it.contains(text) }
            }?.config?.getOrNull(SemanticsProperties.StateDescription)
        assertEquals(ui.t("a11y.redState"), stateOf("verdict-title"))
        assertEquals(ui.t("a11y.cautionState"), stateOf("gauge-label"))
        assertEquals(ui.t("a11y.toggleOff"), stateOf("check-title"))
    }
}
