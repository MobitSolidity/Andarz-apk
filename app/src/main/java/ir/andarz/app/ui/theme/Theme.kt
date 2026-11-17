package ir.andarz.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The theme the user chose (`DARK` is the default — law 9). */
enum class ThemeChoice { DARK, LIGHT, SYSTEM }

/** The three states of the day that colour the hero, the lamp and the gauges. */
enum class Tone { ALLOWED, CAUTION, LOCKED }

/**
 * The semantic colours that Material 3 has no role for. Colour is never the only signal: every state also
 * carries an icon and a text label, and every signed number its own sign (§6.1).
 *
 * The first block keeps the names every screen already uses. The second block carries the tokens of the
 * final design canvas under the names of its CSS variables (`bg`, `sf1`, `fz`, `sf`, `an` ...), so a board
 * and a composable read the same.
 */
@Immutable
data class AndarzColors(
    // ---- semantic roles used across the app
    val allowed: Color,
    val caution: Color,
    val redline: Color,
    val redlineFill: Color,
    val onRedlineFill: Color,
    val info: Color,
    val violet: Color,
    val textMuted: Color,
    val divider: Color,
    val surfaceAlt: Color,
    val surfaceHigh: Color,
    val chartSeries: List<Color>,
    val isDark: Boolean,
    // ---- the design canvas
    val bg: Color,
    val bgDeep: Color,
    val sf1: Color,
    val alt: Color,
    val high: Color,
    val text: Color,
    val muted: Color,
    val hmuted: Color,
    val outline: Color,
    val track: Color,
    val fz: Color,
    val fzG: Color,
    val fzC: Color,
    val fzFill: Color,
    val onFz: Color,
    val fzSoft: Color,
    val sf: Color,
    val sfG: Color,
    val sfC: Color,
    val sfSoft: Color,
    val sfLine: Color,
    val an: Color,
    val anG: Color,
    val anC: Color,
    val anFill: Color,
    val anSoft: Color,
    val anLine: Color,
    val lapis: Color,
    val lapisC: Color,
    val lapisSoft: Color,
    val lapisLine: Color,
    val edge: Color,
    val h1: Color,
    val h2: Color,
    val glowAllowed: Color,
    val glowCaution: Color,
    val glowLocked: Color,
    val ringAllowed: Color,
    val ringCaution: Color,
    val ringLocked: Color,
) {
    /** The glow behind the hero card and the lamp for a state of the day. */
    fun glow(tone: Tone): Color = when (tone) {
        Tone.ALLOWED -> glowAllowed
        Tone.CAUTION -> glowCaution
        Tone.LOCKED -> glowLocked
    }

    /** The 1 dp ring around the lamp and the verdict tile for a state of the day. */
    fun ring(tone: Tone): Color = when (tone) {
        Tone.ALLOWED -> ringAllowed
        Tone.CAUTION -> ringCaution
        Tone.LOCKED -> ringLocked
    }

    /** The text-level accent of a state of the day. */
    fun accent(tone: Tone): Color = when (tone) {
        Tone.ALLOWED -> fz
        Tone.CAUTION -> sf
        Tone.LOCKED -> an
    }

    /** The graphic-level accent (lines, arcs, dots) of a state of the day. */
    fun accentG(tone: Tone): Color = when (tone) {
        Tone.ALLOWED -> fzG
        Tone.CAUTION -> sfG
        Tone.LOCKED -> anG
    }

    /** The soft container of a state of the day. */
    fun container(tone: Tone): Color = when (tone) {
        Tone.ALLOWED -> fzC
        Tone.CAUTION -> sfC
        Tone.LOCKED -> anC
    }
}

val LocalAndarzColors: ProvidableCompositionLocal<AndarzColors> =
    staticCompositionLocalOf { error("AndarzColors was read outside AndarzTheme") }

private val andarzTypo = andarzTypography()

private val andarzShapes = Shapes(
    extraSmall = RoundedCornerShape(ShapeTokens.SMALL.dp),
    small = RoundedCornerShape(ShapeTokens.SMALL.dp),
    medium = RoundedCornerShape(ShapeTokens.MEDIUM.dp),
    // Cards are arches: a large top radius over a small bottom radius.
    large = RoundedCornerShape(
        topStart = ShapeTokens.ARCH_TOP.dp, topEnd = ShapeTokens.ARCH_TOP.dp,
        bottomEnd = ShapeTokens.ARCH_BOTTOM.dp, bottomStart = ShapeTokens.ARCH_BOTTOM.dp,
    ),
    extraLarge = RoundedCornerShape(ShapeTokens.SHEET.dp),
)

// The role mapping, one row per Material 3 role. `primary` is the text-level teal (it tints links, icons and
// selected labels); the app's own Button, FAB and indicator use `fzFill` / `onFz` through AndarzColors.
private val darkScheme = darkColorScheme(
    background = DarkTokens.bg,
    onBackground = DarkTokens.text,
    surface = DarkTokens.sf1,
    onSurface = DarkTokens.text,
    surfaceVariant = DarkTokens.alt,
    onSurfaceVariant = DarkTokens.muted,
    surfaceContainerLowest = DarkTokens.bg,
    surfaceContainerLow = DarkTokens.sf1,
    surfaceContainer = DarkTokens.alt,
    surfaceContainerHigh = DarkTokens.high,
    surfaceContainerHighest = DarkTokens.high,
    outline = DarkTokens.outline,
    outlineVariant = DarkTokens.divider,
    primary = DarkTokens.fz,
    onPrimary = DarkTokens.onFz,
    primaryContainer = DarkTokens.fzC,
    onPrimaryContainer = DarkTokens.fz,
    secondary = DarkTokens.lapis,
    onSecondary = DarkTokens.bg,
    secondaryContainer = DarkTokens.lapisC,
    onSecondaryContainer = DarkTokens.lapis,
    tertiary = DarkTokens.sf,
    onTertiary = Color(0xFF231600),
    tertiaryContainer = DarkTokens.sfC,
    onTertiaryContainer = DarkTokens.sf,
    error = DarkTokens.an,
    onError = DarkTokens.bg,
    errorContainer = DarkTokens.anC,
    onErrorContainer = DarkTokens.an,
    inverseSurface = DarkTokens.text,
    inverseOnSurface = DarkTokens.bg,
    inversePrimary = DarkTokens.fzC,
    surfaceDim = DarkTokens.bg,
    surfaceBright = DarkTokens.high,
    // Tonal elevation must not tint dialogs and sheets: depth is the surface steps.
    surfaceTint = DarkTokens.high,
    scrim = DarkTokens.scrim,
)

private val lightScheme = lightColorScheme(
    background = LightTokens.bg,
    onBackground = LightTokens.text,
    surface = LightTokens.sf1,
    onSurface = LightTokens.text,
    surfaceVariant = LightTokens.alt,
    onSurfaceVariant = LightTokens.muted,
    surfaceContainerLowest = LightTokens.bg,
    surfaceContainerLow = LightTokens.sf1,
    surfaceContainer = LightTokens.alt,
    surfaceContainerHigh = LightTokens.high,
    surfaceContainerHighest = LightTokens.high,
    outline = LightTokens.outline,
    outlineVariant = LightTokens.divider,
    primary = LightTokens.fz,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = LightTokens.fzC,
    onPrimaryContainer = LightTokens.fz,
    secondary = LightTokens.lapis,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = LightTokens.lapisC,
    onSecondaryContainer = LightTokens.lapis,
    // Light fills use the amber graphic colour; the darker saffron stays the text colour.
    tertiary = LightTokens.sfG,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = LightTokens.sfC,
    onTertiaryContainer = LightTokens.sf,
    error = LightTokens.an,
    onError = LightTokens.onAnFill,
    errorContainer = LightTokens.anC,
    onErrorContainer = LightTokens.an,
    inverseSurface = LightTokens.text,
    inverseOnSurface = LightTokens.bg,
    inversePrimary = LightTokens.fzC,
    surfaceDim = LightTokens.bg,
    surfaceBright = LightTokens.high,
    surfaceTint = LightTokens.high,
    scrim = LightTokens.scrim,
)

private val darkExtras = AndarzColors(
    allowed = DarkTokens.fz,
    caution = DarkTokens.sf,
    redline = DarkTokens.an,
    redlineFill = DarkTokens.anFill,
    onRedlineFill = DarkTokens.onAnFill,
    info = DarkTokens.lapis,
    violet = DarkTokens.violet,
    textMuted = DarkTokens.muted,
    divider = DarkTokens.divider,
    surfaceAlt = DarkTokens.alt,
    surfaceHigh = DarkTokens.high,
    chartSeries = DarkTokens.chartSeries,
    isDark = true,
    bg = DarkTokens.bg,
    bgDeep = DarkTokens.bgDeep,
    sf1 = DarkTokens.sf1,
    alt = DarkTokens.alt,
    high = DarkTokens.high,
    text = DarkTokens.text,
    muted = DarkTokens.muted,
    hmuted = DarkTokens.hmuted,
    outline = DarkTokens.outline,
    track = DarkTokens.track,
    fz = DarkTokens.fz,
    fzG = DarkTokens.fzG,
    fzC = DarkTokens.fzC,
    fzFill = DarkTokens.fzFill,
    onFz = DarkTokens.onFz,
    fzSoft = DarkTokens.fzSoft,
    sf = DarkTokens.sf,
    sfG = DarkTokens.sfG,
    sfC = DarkTokens.sfC,
    sfSoft = DarkTokens.sfSoft,
    sfLine = DarkTokens.sfLine,
    an = DarkTokens.an,
    anG = DarkTokens.anG,
    anC = DarkTokens.anC,
    anFill = DarkTokens.anFill,
    anSoft = DarkTokens.anSoft,
    anLine = DarkTokens.anLine,
    lapis = DarkTokens.lapis,
    lapisC = DarkTokens.lapisC,
    lapisSoft = DarkTokens.lapisSoft,
    lapisLine = DarkTokens.lapisLine,
    edge = DarkTokens.edge,
    h1 = DarkTokens.h1,
    h2 = DarkTokens.h2,
    glowAllowed = DarkTokens.glowAllowed,
    glowCaution = DarkTokens.glowCaution,
    glowLocked = DarkTokens.glowLocked,
    ringAllowed = DarkTokens.ringAllowed,
    ringCaution = DarkTokens.ringCaution,
    ringLocked = DarkTokens.ringLocked,
)

private val lightExtras = AndarzColors(
    allowed = LightTokens.fz,
    caution = LightTokens.sf,
    redline = LightTokens.an,
    redlineFill = LightTokens.anFill,
    onRedlineFill = LightTokens.onAnFill,
    info = LightTokens.lapis,
    violet = LightTokens.violet,
    textMuted = LightTokens.muted,
    divider = LightTokens.divider,
    surfaceAlt = LightTokens.alt,
    surfaceHigh = LightTokens.high,
    chartSeries = LightTokens.chartSeries,
    isDark = false,
    bg = LightTokens.bg,
    bgDeep = LightTokens.bgDeep,
    sf1 = LightTokens.sf1,
    alt = LightTokens.alt,
    high = LightTokens.high,
    text = LightTokens.text,
    muted = LightTokens.muted,
    hmuted = LightTokens.hmuted,
    outline = LightTokens.outline,
    track = LightTokens.track,
    fz = LightTokens.fz,
    fzG = LightTokens.fzG,
    fzC = LightTokens.fzC,
    fzFill = LightTokens.fzFill,
    onFz = LightTokens.onFz,
    fzSoft = LightTokens.fzSoft,
    sf = LightTokens.sf,
    sfG = LightTokens.sfG,
    sfC = LightTokens.sfC,
    sfSoft = LightTokens.sfSoft,
    sfLine = LightTokens.sfLine,
    an = LightTokens.an,
    anG = LightTokens.anG,
    anC = LightTokens.anC,
    anFill = LightTokens.anFill,
    anSoft = LightTokens.anSoft,
    anLine = LightTokens.anLine,
    lapis = LightTokens.lapis,
    lapisC = LightTokens.lapisC,
    lapisSoft = LightTokens.lapisSoft,
    lapisLine = LightTokens.lapisLine,
    edge = LightTokens.edge,
    h1 = LightTokens.h1,
    h2 = LightTokens.h2,
    glowAllowed = LightTokens.glowAllowed,
    glowCaution = LightTokens.glowCaution,
    glowLocked = LightTokens.glowLocked,
    ringAllowed = LightTokens.ringAllowed,
    ringCaution = LightTokens.ringCaution,
    ringLocked = LightTokens.ringLocked,
)

/** Dark is the default; every screen must also work in the light theme (law 9). */
@Composable
fun AndarzTheme(
    choice: ThemeChoice = ThemeChoice.DARK,
    content: @Composable () -> Unit,
) {
    val dark = when (choice) {
        ThemeChoice.DARK -> true
        ThemeChoice.LIGHT -> false
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(LocalAndarzColors provides if (dark) darkExtras else lightExtras) {
        MaterialTheme(
            colorScheme = if (dark) darkScheme else lightScheme,
            typography = androidx.compose.material3.Typography(),
            shapes = andarzShapes,
            content = content,
        )
    }
}
