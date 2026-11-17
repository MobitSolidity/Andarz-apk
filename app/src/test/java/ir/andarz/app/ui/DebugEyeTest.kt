package ir.andarz.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import ir.andarz.app.content.ContentBundle
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi", application = android.app.Application::class)
class DebugEyeTest {
    @get:Rule val rule = createComposeRule()

    private fun f(n: String) = listOf(File("src/main/assets/$n"), File("app/src/main/assets/$n")).first { it.isFile }.readText()
    private val en by lazy { ContentBundle.parse(f("content_en.json"), f("content_market_en.json"), f("glossary.json"), f("content_app_en.json")) }

    private fun snapshot() = Governor.snapshot(
        Instant.parse("2026-10-14T06:00:00Z"), ZoneId.of("Asia/Tehran"), Params.DEFAULTS,
        listOf(LedgerEntry(Instant.parse("2026-10-01T00:00:00Z"), LedgerType.BASELINE, 123456.78)), emptyList(), emptyList(), emptyList(),
    )

    private val log = ArrayList<String>()

    private fun host(ui: Ui, fontScale: Float, body: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(d.density, fontScale),
                LocalLayoutDirection provides if (ui.content.isLtr) LayoutDirection.Ltr else LayoutDirection.Rtl,
                LocalUi provides ui,
            ) {
                AndarzTheme(ThemeChoice.DARK) {
                    Box(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val e = awaitPointerEvent(PointerEventPass.Initial)
                                    log.add("I:${e.type}@${e.changes.firstOrNull()?.position}")
                                    val e2 = awaitPointerEvent(PointerEventPass.Final)
                                    log.add("F:${e2.type} consumed=${e2.changes.firstOrNull()?.isConsumed}")
                                }
                            }
                        },
                    ) { body() }
                }
            }
        }
        rule.mainClock.advanceTimeBy(500)
    }

    @Test
    fun debugEye() {
        var asked: Boolean? = null
        var newTrade = 0
        val ui = Ui(en, Settings(language = Language.EN, digits = Digits.EN))
        host(ui, 1f) {
            TodayScreen(snapshot(), emptyList(), pledgeSigned = true, adviceEnabled = true, onNewTrade = { newTrade++ }, onOpenTrade = {}, onNeed = { _, _ -> }, onCloseDay = {}, onMonitor = {}, onToggleAmounts = { asked = it })
        }
        val desc = ui.t("app.amounts.hide")
        val node = rule.onNodeWithContentDescription(desc).fetchSemanticsNode()
        val rootB = rule.onRoot().fetchSemanticsNode().boundsInRoot

        rule.onNodeWithContentDescription(desc).performClick()
        val a1 = asked; val l1 = log.toList(); log.clear()

        rule.onRoot().performTouchInput { click(Offset(260f, 142f)) }
        val a2 = asked; val l2 = log.toList(); log.clear()

        rule.onRoot().performTouchInput { click(Offset(270f, 150f)) }
        val a3 = asked; log.clear()

        rule.onRoot().performTouchInput { click(Offset(250f, 570f)) }
        val fab = newTrade; val l4 = log.toList(); log.clear()

        rule.mainClock.autoAdvance = true
        rule.onNodeWithContentDescription(desc).performClick()
        val a5 = asked

        rule.onNodeWithContentDescription(desc).performSemanticsAction(SemanticsActions.OnClick)
        val aSem = asked
        throw AssertionError("DEBUGEYE bounds=${node.boundsInRoot} root=$rootB | touchOnNode=$a1 log=$l1 | clickAt260,142=$a2 log=$l2 | clickAt270,150=$a3 | fabClicks=$fab log=$l4 | autoAdvanceTouch=$a5 | semantics=$aSem")
    }
}
