package ir.andarz.market.alarm

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.andarz.market.MarketFonts
import ir.andarz.market.MarketModule
import ir.andarz.market.MarketTarget
import ir.andarz.market.MarketTexts
import kotlinx.coroutines.delay

/**
 * Full-screen alarm shown over the lock screen while a price alert is ringing (March's `AlarmActivity` and
 * `AlarmScreen`), drawn as the "price alarm" board of the canvas: a header with the tag and the clock, arches around
 * a bell, the coin, its price as the big figure, what happened, the auto-stop bar and the three buttons. It lives
 * outside the app lock because it shows market data only (K6.11) and it does not use :app's theme (K5): the colours
 * are the dark tokens of the canvas, the fonts come from the app through [ir.andarz.market.MarketHost.fonts].
 * "Open coin" goes through [ir.andarz.market.MarketHost.openIntent], so the app lock is shown before the coin.
 */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        wakeScreen()
        val texts = MarketModule.textsOrNull()
        val fonts = MarketModule.fontsOrNull()
        setContent {
            val info by AlarmRinger.state.collectAsStateWithLifecycle()
            LaunchedEffect(info) { if (info == null) finish() }
            BackHandler { stopAndClose() }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                val a = info
                if (a != null && texts != null && fonts != null) {
                    AlarmScreen(
                        info = a,
                        texts = texts,
                        fonts = fonts,
                        onStop = { stopAndClose() },
                        onSnooze = { snoozeAndClose() },
                        onOpen = { openCoin(a.coinId) },
                    )
                }
            }
        }
    }

    private fun wakeScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun stopAndClose() {
        AlarmRinger.stop(this)
        finish()
    }

    private fun snoozeAndClose() {
        AlarmRinger.snooze(this)
        finish()
    }

    private fun openCoin(id: String?) {
        AlarmRinger.stop(this)
        val target = if (id != null) MarketTarget.Coin(id) else MarketTarget.Home
        MarketModule.openIntentOrNull(this, target)?.let { i ->
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
            startActivity(i)
        }
        finish()
    }
}

// The dark tokens of the canvas (the alarm board), kept here because :market cannot reach :app's theme.
private val Bg = Color(0xFF070D13)
private val Ink = Color(0xFFEAF0F5)
private val Muted = Color(0xFFA3B1C0)
private val HMuted = Color(0xFFB9C5D1)
private val Divider = Color(0xFF233245)
private val Outline = Color(0xFF61748A)
private val Track = Color(0xFF243448)
private val Teal = Color(0xFF35D6C4)
private val TealC = Color(0xFF0E3A3A)
private val Lapis = Color(0xFF8CA6FF)
private val LapisC = Color(0xFF18264A)
private val LapisLine = Color(0x738CA6FF)
private val Coral = Color(0xFFFF8A80)

// Glyph paths of the canvas on its 24 unit grid.
private const val BELL = "M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 1.5h-15zM10 20.5h4"
private const val BELL_OFF = "M9.5 5.2A6 6 0 0 1 18 11v5.5l1.5 1.5H8M6 16.5V11c0-.6.1-1.2.3-1.7M10 20.5h4M4 4l16 16"
private const val ALARM_CLOCK = "M5 13a7 7 0 1 0 14 0a7 7 0 1 0 -14 0zM12 9.5V13l2.3 1.6M4.5 5.5L7 3.5M19.5 5.5L17 3.5"
private const val ARROW = "M19 12H5M11 6l-6 6 6 6"
private const val TRI_UP = "M12 5l8 13H4z"
private const val TRI_DOWN = "M12 19L4 6h16z"

private val StopShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
private val SideShape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomEnd = 16.dp, bottomStart = 16.dp)

/** One of the concentric arches of the picture: radius, height of the spring line and opacity, in the board's 390 x 300 units. */
private class Ring(val radius: Float, val spring: Float, val alpha: Float)

private val Rings = listOf(
    Ring(64f, 184f, 0.60f),
    Ring(102f, 180f, 0.42f),
    Ring(140f, 176f, 0.28f),
    Ring(178f, 172f, 0.17f),
    Ring(216f, 168f, 0.09f),
)

@Composable
private fun AlarmScreen(
    info: AlarmRinger.Info,
    texts: MarketTexts,
    fonts: MarketFonts,
    onStop: () -> Unit,
    onSnooze: () -> Unit,
    onOpen: () -> Unit,
) {
    val now = remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now.value = System.currentTimeMillis()
            delay(1000 - now.value % 1000)
        }
    }
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        val screen = maxHeight
        // The arches take what the rest of the screen does not need, from 90 dp to the 300 dp of the board. The rest is
        // the header, the message, the auto-stop row and the buttons, with the text part growing with the font size.
        val others = 56.dp + 36.dp + 154.dp + 171.dp * fontScale
        val arches = (screen - others).coerceIn(90.dp, 300.dp)
        // With a very large font or a very short screen the column grows past the screen and scrolls, so the buttons stay reachable.
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().heightIn(min = screen)) {
                Header(texts, fonts, now)
                Arches(texts.alarmDecor(), Modifier.fillMaxWidth().height(arches))
                Message(info, fonts)
                Spacer(Modifier.weight(1f))
                AutoStop(info, texts, fonts, now)
                Actions(info, texts, fonts, onStop, onSnooze, onOpen)
            }
        }
    }
}

/** The tag at the start and the clock at the end. */
@Composable
private fun Header(texts: MarketTexts, fonts: MarketFonts, now: State<Long>) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(width = 24.dp, height = 28.dp)
                    .background(TealC, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 7.dp, bottomStart = 7.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon24(BELL, 14.dp, Teal, 2.2f)
            }
            AText(texts.alarmTag(), fonts, 13.sp, HMuted, weight = FontWeight.W700)
        }
        AText(texts.clock(now.value), fonts, 22.sp, Ink, weight = FontWeight.W600, figures = true)
    }
}

/**
 * The picture of the board: five arches around a bell, fading outwards, on a baseline. One image for a screen reader.
 * It scales with its height, so a short screen gets a smaller picture instead of a cut one.
 */
@Composable
private fun Arches(description: String, modifier: Modifier) {
    val bell = remember { PathParser().parsePathString(BELL).toPath() }
    Canvas(modifier.clearAndSetSemantics { role = Role.Image; contentDescription = description }) {
        val k = size.height / 300f
        val cx = size.width / 2f
        val base = 292f * k
        val line = 1.5.dp.toPx()
        for (r in Rings) {
            val rad = r.radius * k
            val spring = r.spring * k
            val p = Path().apply {
                moveTo(cx - rad, base)
                lineTo(cx - rad, spring)
                arcTo(Rect(cx - rad, spring - rad, cx + rad, spring + rad), 180f, 180f, false)
                lineTo(cx + rad, base)
            }
            drawPath(p, Teal.copy(alpha = r.alpha), style = Stroke(width = line, join = StrokeJoin.Round))
        }
        drawLine(Divider, Offset(0f, base), Offset(size.width, base), strokeWidth = 1.dp.toPx())
        val rad = 48f * k
        val spring = 224f * k
        val arch = Path().apply {
            moveTo(cx - rad, base)
            lineTo(cx - rad, spring)
            arcTo(Rect(cx - rad, spring - rad, cx + rad, spring + rad), 180f, 180f, false)
            lineTo(cx + rad, base)
            close()
        }
        drawPath(arch, TealC)
        drawPath(arch, Teal, style = Stroke(width = line, join = StrokeJoin.Round))
        translate(left = cx - 24f * k, top = 226f * k) {
            scale(2f * k, 2f * k, pivot = Offset.Zero) {
                drawPath(bell, Teal, style = Stroke(width = 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** The coin and its price, what happened and the 24 h line; a test alarm, which has no coin, shows its title and body. */
@Composable
private fun Message(info: AlarmRinger.Info, fonts: MarketFonts) {
    val face = info.face
    Column(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (face == null) {
            AText(info.title, fonts, 26.sp, Ink, weight = FontWeight.W800, align = TextAlign.Center)
            AText(info.body, fonts, 16.sp, Muted, modifier = Modifier.padding(top = 8.dp), align = TextAlign.Center)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                AText(
                    face.name, fonts, 26.sp, Ink,
                    modifier = Modifier.weight(1f, fill = false).alignByBaseline(), weight = FontWeight.W800, align = TextAlign.Center,
                )
                AText(face.symbol, fonts, 14.sp, Muted, modifier = Modifier.alignByBaseline(), figures = true)
            }
            FitPrice(face.price, fonts, Ink)
            val up = face.up
            val tone = when (up) {
                true -> Teal
                false -> Coral
                null -> HMuted
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                if (up != null) Icon24(if (up) TRI_UP else TRI_DOWN, 12.dp, tone, 0f, fill = true)
                AText(
                    face.status, fonts, 16.sp, tone,
                    modifier = Modifier.weight(1f, fill = false), weight = FontWeight.W700, align = TextAlign.Center,
                )
            }
            if (face.note.isNotEmpty()) {
                AText(face.note, fonts, 13.sp, Muted, modifier = Modifier.padding(top = 6.dp), align = TextAlign.Center)
            }
        }
    }
}

/** The price as the big figure: 58 sp as in the board, smaller only as far as one line needs to fit the width. */
@Composable
private fun FitPrice(text: String, fonts: MarketFonts, color: Color) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val room = constraints.maxWidth
        val pick = remember(text, room, fonts) {
            var s = 58
            while (s > 26 && measurer.measure(text = text, style = priceStyle(fonts, s), softWrap = false, maxLines = 1).size.width > room) s -= 2
            s
        }
        Text(text = text, color = color, style = priceStyle(fonts, pick), maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
    }
}

private fun priceStyle(fonts: MarketFonts, size: Int) = TextStyle(
    fontFamily = fonts.figures,
    fontWeight = FontWeight.W600,
    fontSize = size.sp,
    lineHeight = (size * 1.1f).sp,
    fontFeatureSettings = "tnum",
)

/** "Auto stop in 2:40" and the bar that empties over the three minutes. */
@Composable
private fun AutoStop(info: AlarmRinger.Info, texts: MarketTexts, fonts: MarketFonts, now: State<Long>) {
    val left = alarmLeftMs(info.at, now.value)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).heightIn(min = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AText(texts.alarmLeft(left), fonts, 12.sp, Muted)
        Box(
            Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Track)
                .clearAndSetSemantics { role = Role.Image; contentDescription = texts.alarmLeftDesc(left, AlarmRinger.AUTO_STOP_MS) },
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(left.toFloat() / AlarmRinger.AUTO_STOP_MS).background(HMuted))
        }
    }
}

/** Stop, the big one, then snooze and open the coin side by side. */
@Composable
private fun Actions(
    info: AlarmRinger.Info,
    texts: MarketTexts,
    fonts: MarketFonts,
    onStop: () -> Unit,
    onSnooze: () -> Unit,
    onOpen: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(StopShape).background(Ink)
                .clickable(role = Role.Button, onClick = onStop).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            Icon24(BELL_OFF, 22.dp, Bg, 2f)
            AText(texts.alarmStop(), fonts, 18.sp, Bg, weight = FontWeight.W800)
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.weight(1f).heightIn(min = 52.dp).clip(SideShape).border(1.dp, Outline, SideShape)
                    .clickable(role = Role.Button, onClick = onSnooze).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                Icon24(ALARM_CLOCK, 18.dp, Ink, 2f)
                AText(texts.alarmSnooze(), fonts, 15.sp, Ink, modifier = Modifier.weight(1f, fill = false), weight = FontWeight.W700, align = TextAlign.Center)
            }
            if (info.coinId != null) {
                val name = info.face?.name.orEmpty()
                Row(
                    Modifier.weight(1.2f).heightIn(min = 52.dp).clip(SideShape).background(LapisC).border(1.dp, LapisLine, SideShape)
                        .clickable(role = Role.Button, onClick = onOpen).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    AText(
                        if (name.isEmpty()) texts.alarmOpenCoin() else texts.alarmOpen(name), fonts, 15.sp, Lapis,
                        modifier = Modifier.weight(1f, fill = false), weight = FontWeight.W700, align = TextAlign.Center,
                    )
                    Icon24(ARROW, 18.dp, Lapis, 2.2f)
                }
            }
        }
    }
}

/**
 * Text in the families of the app: words in Peyda and, inside them, every number and Latin run in Plex with tabular
 * figures, as the canvas does (class `n`). [figures] draws the whole string in Plex.
 */
@Composable
private fun AText(
    text: String,
    fonts: MarketFonts,
    size: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.W400,
    figures: Boolean = false,
    align: TextAlign? = null,
) {
    val shaped = remember(text, figures, fonts) { if (figures) AnnotatedString(text) else withFigures(text, fonts.figures) }
    Text(
        text = shaped,
        modifier = modifier,
        color = color,
        textAlign = align,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            fontFamily = if (figures) fonts.figures else fonts.text,
            fontWeight = weight,
            fontSize = size,
            lineHeight = size * 1.5f,
            fontFeatureSettings = if (figures) "tnum" else null,
        ),
    )
}

private fun withFigures(text: String, figures: FontFamily): AnnotatedString {
    val runs = figureRanges(text)
    if (runs.isEmpty()) return AnnotatedString(text)
    val style = SpanStyle(fontFamily = figures, fontFeatureSettings = "tnum")
    return AnnotatedString.Builder(text.length).apply {
        append(text)
        runs.forEach { addStyle(style, it.first, it.last + 1) }
    }.toAnnotatedString()
}

/** A glyph of the canvas: [path] on a 24 unit grid, stroked in [stroke] units (or filled), scaled to [box]. */
@Composable
private fun Icon24(path: String, box: Dp, color: Color, stroke: Float, modifier: Modifier = Modifier, fill: Boolean = false) {
    val shape = remember(path) { PathParser().parsePathString(path).toPath() }
    Canvas(modifier.size(box)) {
        val k = this.size.minDimension / 24f
        scale(k, k, pivot = Offset.Zero) {
            if (fill) {
                drawPath(shape, color)
            } else {
                drawPath(shape, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
