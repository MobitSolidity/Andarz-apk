package ir.andarz.app.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.andarz.app.R
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.components.ArchCard
import ir.andarz.app.ui.components.ArchShapes
import ir.andarz.app.ui.components.ArchTile
import ir.andarz.app.ui.components.Button
import ir.andarz.app.ui.components.FitText
import ir.andarz.app.ui.components.NumberField
import ir.andarz.app.ui.components.OutlinedButton
import ir.andarz.app.ui.components.Text
import ir.andarz.app.ui.components.TextButton
import ir.andarz.app.ui.icons.Glyph
import ir.andarz.app.ui.icons.GlyphIcon
import ir.andarz.app.ui.safeLaunch
import ir.andarz.app.ui.theme.AndarzFonts
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.theme.Tone
import ir.andarz.app.ui.ui
import ir.andarz.data.LedgerRepository
import ir.andarz.data.PledgeRepository
import ir.andarz.data.settings.Settings
import ir.andarz.data.settings.SettingsRepository
import ir.andarz.engine.Fmt
import ir.andarz.engine.Params
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val ledger: LedgerRepository,
    private val settings: SettingsRepository,
    private val pledges: PledgeRepository,
) : ViewModel() {
    /** Writes BASELINE + NEUTRAL Start_Equity/Risk_Pct (5.1, D-18), the pledge when signed, then finishes. */
    fun finish(equity: Double, riskPct: Double, zone: String, pledgeText: String?, name: String, onDone: () -> Unit) = safeLaunch {
        settings.setZone(zone)
        ledger.onboard(equity, riskPct, ZoneId.of(zone))
        if (pledgeText != null) pledges.sign(1, name, equity, riskPct, pledgeText)
        settings.setOnboardingDone(true)
        onDone()
    }
}

private const val PAGES = 7

/** The mark and the glow of the welcome window are painted on the dark picture in both themes. */
private val MarkTeal = Color(0xFF35D6C4)
private val MarkSaffron = Color(0xFFF3B63F)

/** Seven pages (5.1): welcome, privacy, how, numbers, pledge, lock, done. Never asks for notifications. */
@Composable
fun OnboardingScreen(onDone: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val u = ui().unmasked()
    val c = LocalAndarzColors.current
    var page by rememberSaveable { mutableStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var equity by rememberSaveable { mutableStateOf("") }
    var risk by rememberSaveable { mutableStateOf(Fmt.number(Params.DEFAULTS.riskPct * 100, 2, false, false, true)) }
    var zone by rememberSaveable { mutableStateOf(Settings.DEFAULT_ZONE) }
    var signed by rememberSaveable { mutableStateOf(false) }
    val eq = Fmt.normalizeInput(equity)?.takeIf { it > 0 }
    val riskBounds = u.content.arr("params")?.map { it.jsonObject }?.firstOrNull { it["key"]?.jsonPrimitive?.content == "Risk_Pct" }
    val rMin = riskBounds?.get("min")?.jsonPrimitive?.content?.toDouble() ?: 0.001
    val rMax = riskBounds?.get("max")?.jsonPrimitive?.content?.toDouble() ?: 0.02
    val rp = Fmt.normalizeInput(risk)?.div(100)?.takeIf { it in rMin..rMax }
    val zoneOk = runCatching { ZoneId.of(zone) }.isSuccess

    // Outside the nav Scaffold: keep clear of the system bars and the keyboard (edge-to-edge, D-79).
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 16.dp)) {
        StepDashes(page, PAGES, u.t("app.welcome.step", "n" to u.int(page + 1), "total" to u.int(PAGES)))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (page) {
                0 -> WelcomePage(u)
                1 -> {
                    PageTitle(Glyph.SHIELD_CHECK, u.t("onb.privacy.title"))
                    ArchCard(Modifier.fillMaxWidth()) { Text(u.t("onb.privacy.body"), style = MaterialTheme.typography.bodyLarge) }
                }
                2 -> {
                    PageTitle(Glyph.CLIPBOARD_CHECK, u.t("onb.how.title"))
                    listOf(1, 2, 3).forEach { n -> HowRow(u, n, u.t("onb.how.$n")) }
                }
                3 -> {
                    PageTitle(Glyph.WALLET, u.t("onb.setup.title"))
                    OutlinedTextField(name, { name = it }, label = { Text(u.t("onb.setup.name")) }, placeholder = { Text(u.t("onb.setup.name.hint")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    NumberField(u.t("onb.setup.equity"), equity, { equity = it }, hint = u.t("onb.setup.equity.hint"), error = if (equity.isNotBlank() && eq == null) u.t("err.positive") else null)
                    NumberField(u.t("onb.setup.risk"), risk, { risk = it }, suffix = "%", hint = u.t("onb.setup.risk.hint", "default" to u.pct(Params.DEFAULTS.riskPct, 0)), error = if (rp == null) u.t("err.above") else null)
                    OutlinedTextField(zone, { zone = it }, label = { Text(u.t("onb.setup.zone")) }, singleLine = true, isError = !zoneOk, modifier = Modifier.fillMaxWidth())
                }
                4 -> {
                    PageTitle(Glyph.NOTE_CHECK, u.t("onb.pledge.title"))
                    Text(u.t("onb.pledge.body"), style = MaterialTheme.typography.bodyMedium, color = c.muted)
                    ArchCard(Modifier.fillMaxWidth()) { Text(pledgeText(u, name, eq, rp), style = MaterialTheme.typography.bodyLarge) }
                    if (!signed) {
                        Button(onClick = { signed = true }, enabled = eq != null && rp != null, modifier = Modifier.fillMaxWidth()) { Text(u.t("onb.pledge.sign")) }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlyphIcon(Glyph.CHECK_CIRCLE, null, tint = c.accent(Tone.ALLOWED), size = 22.dp)
                            Text(u.t("common.saved"), color = c.accent(Tone.ALLOWED))
                        }
                    }
                    TextButton(onClick = { page++ }) { Text(u.t("onb.pledge.later")) }
                }
                5 -> {
                    PageTitle(Glyph.LOCK, u.t("onb.lock.title"))
                    ArchCard(Modifier.fillMaxWidth()) { Text(u.t("onb.lock.body"), style = MaterialTheme.typography.bodyLarge) }
                }
                else -> {
                    PageTitle(Glyph.CHECK_CIRCLE, u.t("onb.done.title"))
                    ArchCard(Modifier.fillMaxWidth()) { Text(u.t("onb.done.body"), style = MaterialTheme.typography.bodyLarge) }
                }
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (page > 0) OutlinedButton(onClick = { page-- }, modifier = Modifier.weight(1f)) { Text(u.t("common.back")) }
            val canNext = page != 3 || (eq != null && rp != null && zoneOk)
            Button(
                onClick = {
                    if (page < PAGES - 1) page++ else vm.finish(eq!!, rp!!, zone, if (signed) pledgeText(u, name, eq, rp) else null, name, onDone)
                },
                enabled = canNext && (page < PAGES - 1 || (eq != null && rp != null)),
                modifier = Modifier.weight(1f),
            ) {
                Text(u.t(if (page == 0) "onb.welcome.start" else if (page == PAGES - 1) "onb.done.cta" else "common.next"))
                if (page == 0) {
                    Spacer(Modifier.width(10.dp))
                    // ARROW_RIGHT turns itself around in a right-to-left layout, so it always points forward
                    GlyphIcon(Glyph.ARROW_RIGHT, null, size = 20.dp, strokeWidth = 2.2f)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Seven segments, filled up to the current page; one TalkBack node that says "step n of 7". */
@Composable
private fun StepDashes(page: Int, total: Int, description: String) {
    val c = LocalAndarzColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(4.dp).clearAndSetSemantics { contentDescription = description; role = Role.Image },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(total) { i ->
            Box(Modifier.weight(1f).fillMaxHeight().background(if (i <= page) c.fzG else c.track, RoundedCornerShape(2.dp)))
        }
    }
}

/** The first page: the arch window onto the dawn, the mark, the name, the tagline and the three promises. */
@Composable
private fun WelcomePage(u: Ui) {
    val c = LocalAndarzColors.current
    val windowHeight = (LocalConfiguration.current.screenHeightDp * 0.42f).dp.coerceIn(200.dp, 392.dp)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // 8 dp inset: the ring around the window is drawn 7.5 dp outside it and the scroll area clips at its edge
        DawnWindow(u.t("app.welcome.logo"), Modifier.padding(horizontal = 8.dp).fillMaxWidth().height(windowHeight))
        Spacer(Modifier.height(14.dp))
        FitText(
            u.content.appName,
            style = TextStyle(fontFamily = AndarzFonts.peyda, fontWeight = FontWeight.W800, fontSize = 52.sp, lineHeight = 65.sp),
            color = c.text, modifier = Modifier.fillMaxWidth(),
        )
        Text(
            u.content.tagline,
            style = TextStyle(fontFamily = AndarzFonts.peyda, fontWeight = FontWeight.W500, fontSize = 18.sp, lineHeight = 29.sp),
            color = c.hmuted,
        )
        Column(Modifier.fillMaxWidth().padding(start = 8.dp, top = 22.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Promise(Glyph.SHIELD, u.t("app.welcome.promise1"))
            Promise(Glyph.INFO, u.t("app.welcome.promise2"))
            Promise(Glyph.LINES, u.t("app.welcome.promise3"))
        }
    }
}

@Composable
private fun Promise(glyph: Glyph, text: String) {
    val c = LocalAndarzColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ArchTile(c.fzC, width = 34.dp, height = 38.dp, bottom = 10.dp) { GlyphIcon(glyph, null, tint = c.fz, size = 20.dp, strokeWidth = 1.8f) }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/**
 * An arch window (a half circle over 28 dp corners) onto the picture of the dawn in the mountains, a ring in the
 * colour of "allowed" 6 dp outside it, a soft glow in the sky and the mark of Andarz standing in it. The picture is
 * not interactive, so clipping it to the arch is safe (see `archSurface` for the case that is not).
 */
@Composable
private fun DawnWindow(logoDescription: String, modifier: Modifier = Modifier) {
    val c = LocalAndarzColors.current
    val ring = c.ring(Tone.ALLOWED)
    BoxWithConstraints(modifier) {
        val top: Dp = maxWidth / 2
        val window = ArchShapes.arch(top, 28.dp)
        Box(
            Modifier.fillMaxSize()
                .drawBehind {
                    // 1.5 dp ring, 6 dp clear of the window (a 6.75 dp shift puts the middle of the stroke there)
                    val gap = 6.75.dp.toPx()
                    val outline = ArchShapes.arch(top + 6.75.dp, 34.75.dp)
                        .createOutline(Size(size.width + 2 * gap, size.height + 2 * gap), layoutDirection, this)
                    translate(-gap, -gap) { drawOutline(outline, ring, style = Stroke(1.5.dp.toPx())) }
                },
        ) {
            Box(
                Modifier.fillMaxSize().clip(window).background(Color(0xFF070D18)).drawWithContent {
                    drawContent()
                    drawOutline(window.createOutline(size, layoutDirection, this), Color.White.copy(alpha = 0.10f), style = Stroke(1.dp.toPx()))
                },
            ) {
                Image(
                    painterResource(R.drawable.art_dawn), contentDescription = null,
                    contentScale = ContentScale.Crop, alignment = Alignment.BottomCenter, modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier.align(Alignment.TopCenter).padding(top = 28.dp).size(160.dp)
                        .background(Brush.radialGradient(listOf(MarkTeal.copy(alpha = 0.30f), Color.Transparent))),
                )
                AndarzMark(logoDescription, Modifier.align(Alignment.TopCenter).padding(top = 58.dp))
            }
        }
    }
}

/** The mark: a tall alef (a leaning stem) with a saffron bar under it, drawn on the 28 by 74 grid of the board. */
@Composable
private fun AndarzMark(description: String, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 30.dp, height = 80.dp).semantics { contentDescription = description; role = Role.Image }) {
        val s = size.width / 28f
        val dy = (size.height - 74f * s) / 2f
        fun x(v: Float) = (v - 40f) * s
        fun y(v: Float) = (v - 18f) * s + dy
        val stem = Path().apply {
            moveTo(x(50.5f), y(24f))
            lineTo(x(57.5f), y(24f))
            lineTo(x(55.5f), y(76f))
            lineTo(x(52.5f), y(76f))
            close()
        }
        drawPath(stem, MarkTeal)
        drawPath(stem, MarkTeal, style = Stroke(width = 3f * s, join = StrokeJoin.Round))
        drawRoundRect(MarkSaffron, topLeft = Offset(x(46f), y(82f)), size = Size(16f * s, 4f * s), cornerRadius = CornerRadius(2f * s))
    }
}

/** The title of a page after the first: a small arch tile with the glyph of the page, then the heading. */
@Composable
private fun PageTitle(glyph: Glyph, text: String) {
    val c = LocalAndarzColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ArchTile(c.fzC, width = 40.dp, height = 46.dp, bottom = 10.dp) { GlyphIcon(glyph, null, tint = c.fz, size = 22.dp) }
        Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
    }
}

/** One step of "how Andarz works": a numbered arch tile and the sentence. */
@Composable
private fun HowRow(u: Ui, n: Int, text: String) {
    val c = LocalAndarzColors.current
    ArchCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ArchTile(c.fzC, width = 30.dp, height = 36.dp, bottom = 8.dp) { Text(u.int(n), style = MaterialTheme.typography.labelLarge, color = c.fz) }
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}

private fun pledgeText(u: Ui, name: String, equity: Double?, riskPct: Double?): String {
    val tpl = u.content.obj("constitution", "pledge")?.get("template")?.jsonPrimitive?.content ?: return ""
    return tpl.replace("{name}", name.ifBlank { u.t("common.dash") })
        .replace("{date}", u.date(java.time.LocalDate.now(u.zone), withWeekday = false))
        .replace("{startEquity}", u.money(equity))
        .replace("{riskPct}", u.pct(riskPct))
}
