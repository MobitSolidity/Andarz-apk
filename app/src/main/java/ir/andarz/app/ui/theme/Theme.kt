package ir.andarz.app.ui.theme

import ir.andarz.app.R

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The theme the user chose (`DARK` is the default — law 9). */
enum class ThemeChoice { DARK, LIGHT, SYSTEM }

/**
 * The semantic colours that Material 3 has no role for (§6.2). Colour is never the only signal: every
 * state also carries an icon and a text label, and every signed number its own sign (§6.1).
 */
@Immutable
data class AndarzColors(
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
)

val LocalAndarzColors: ProvidableCompositionLocal<AndarzColors> =
    staticCompositionLocalOf { error("AndarzColors was read outside AndarzTheme") }

/**
 * Vazirmatn (SIL OFL), weights 300/400/500/700 (§6.3).
 *
 * The TTFs ship in `res/font` (D-M34). They are referenced as `R.font.*`, not looked up by name, so R8's
 * resource shrinker sees them and keeps them (D-80, r8-analyzer: no reflection-only references).
 * Light (300) is used only for the large display figures (equity, prices, countdowns) of the v2 look (D-UI2).
 */
private val persianFamily = FontFamily(
    Font(R.font.vazirmatn_light, FontWeight.Light),
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

@Composable
private fun rememberPersianFamily(): FontFamily = persianFamily

/**
 * Line height 1.6 for Persian text (§6.3, `typography.lineHeightFactorFa`). Tabular figures everywhere, so
 * money, R values and prices line up in columns (tokens `typography.numerals`).
 */
private fun fa(family: FontFamily, size: Int, weight: FontWeight, lineFactor: Float = 1.6f) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * lineFactor).sp,
    fontFeatureSettings = "tnum",
)

/**
 * Every Material 3 role is set, so no text ever falls back to the platform font (the v1 scale defined six
 * roles and the other nine — titleLarge, titleSmall, labelLarge, bodySmall… — rendered in the system font).
 * Display roles are light and tighter: they only ever carry figures.
 */
private fun typographyOf(family: FontFamily) = Typography(
    displayLarge = fa(family, 44, FontWeight.Light, 1.2f),
    displayMedium = fa(family, 36, FontWeight.Light, 1.25f),
    displaySmall = fa(family, 32, FontWeight.Light, 1.3f),
    headlineLarge = fa(family, 28, FontWeight.Bold),
    headlineMedium = fa(family, 24, FontWeight.Bold),
    headlineSmall = fa(family, 22, FontWeight.Bold),
    titleLarge = fa(family, 20, FontWeight.Bold),
    titleMedium = fa(family, 17, FontWeight.Medium),
    titleSmall = fa(family, 15, FontWeight.Medium),
    bodyLarge = fa(family, 16, FontWeight.Normal),
    bodyMedium = fa(family, 15, FontWeight.Normal),
    bodySmall = fa(family, 13, FontWeight.Normal),
    labelLarge = fa(family, 14, FontWeight.Medium),
    labelMedium = fa(family, 12, FontWeight.Medium),
    labelSmall = fa(family, 11, FontWeight.Medium),
)

private val andarzShapes = Shapes(
    extraSmall = RoundedCornerShape(ShapeTokens.SMALL.dp),
    small = RoundedCornerShape(ShapeTokens.SMALL.dp),
    medium = RoundedCornerShape(ShapeTokens.MEDIUM.dp),
    large = RoundedCornerShape(ShapeTokens.LARGE.dp),
    extraLarge = RoundedCornerShape(ShapeTokens.SHEET.dp),
)

// The role mapping of §6.2, one row per Material 3 role.
private val darkScheme = darkColorScheme(
    background = DarkTokens.bg,
    onBackground = DarkTokens.text,
    surface = DarkTokens.surface,
    onSurface = DarkTokens.text,
    surfaceVariant = DarkTokens.surfaceAlt,
    onSurfaceVariant = DarkTokens.textMuted,
    surfaceContainerLowest = DarkTokens.bg,
    surfaceContainerLow = DarkTokens.surface,
    surfaceContainer = DarkTokens.surfaceAlt,
    surfaceContainerHigh = DarkTokens.surfaceHigh,
    surfaceContainerHighest = DarkTokens.surfaceHigh,
    outline = DarkTokens.outline,
    outlineVariant = DarkTokens.divider,
    primary = DarkTokens.firouzeh,
    onPrimary = DarkTokens.onFirouzeh,
    primaryContainer = DarkTokens.firouzehContainer,
    onPrimaryContainer = DarkTokens.firouzeh,
    secondary = DarkTokens.lapis,
    secondaryContainer = DarkTokens.lapisContainer,
    onSecondaryContainer = DarkTokens.lapis,
    tertiary = DarkTokens.saffron,
    onTertiary = DarkTokens.onSaffron,
    tertiaryContainer = DarkTokens.saffronContainer,
    onTertiaryContainer = DarkTokens.saffron,
    error = DarkTokens.anar,
    errorContainer = DarkTokens.anarContainer,
    onErrorContainer = DarkTokens.anar,
    onError = DarkTokens.bg,
    onSecondary = DarkTokens.bg,
    inverseSurface = DarkTokens.text,
    inverseOnSurface = DarkTokens.bg,
    inversePrimary = DarkTokens.firouzehContainer,
    surfaceDim = DarkTokens.bg,
    surfaceBright = DarkTokens.surfaceHigh,
    // Tonal elevation must not tint dialogs and sheets green: depth is the surface steps (tokens `elevation`).
    surfaceTint = DarkTokens.surfaceHigh,
    scrim = Color(0x99000000),
)

private val lightScheme = lightColorScheme(
    background = LightTokens.bg,
    onBackground = LightTokens.text,
    surface = LightTokens.surface,
    onSurface = LightTokens.text,
    surfaceVariant = LightTokens.surfaceAlt,
    onSurfaceVariant = LightTokens.textMuted,
    surfaceContainerLowest = LightTokens.bg,
    surfaceContainerLow = LightTokens.surface,
    surfaceContainer = LightTokens.surfaceAlt,
    surfaceContainerHigh = LightTokens.surfaceHigh,
    surfaceContainerHighest = LightTokens.surfaceHigh,
    outline = LightTokens.outline,
    outlineVariant = LightTokens.divider,
    primary = LightTokens.firouzeh,
    onPrimary = LightTokens.onFirouzeh,
    primaryContainer = LightTokens.firouzehContainer,
    onPrimaryContainer = LightTokens.firouzeh,
    secondary = LightTokens.lapis,
    secondaryContainer = LightTokens.lapisContainer,
    onSecondaryContainer = LightTokens.lapis,
    // Light fills use saffronFill; saffron itself stays the text colour (D-20).
    tertiary = LightTokens.saffronFill,
    onTertiary = LightTokens.onSaffron,
    tertiaryContainer = LightTokens.saffronContainer,
    onTertiaryContainer = LightTokens.saffron,
    error = LightTokens.anar,
    errorContainer = LightTokens.anarContainer,
    onErrorContainer = LightTokens.anar,
    onError = LightTokens.onAnarFill,
    onSecondary = LightTokens.bg,
    inverseSurface = LightTokens.text,
    inverseOnSurface = LightTokens.bg,
    inversePrimary = LightTokens.firouzehContainer,
    surfaceDim = LightTokens.bg,
    surfaceBright = LightTokens.surfaceHigh,
    // Tonal elevation must not tint dialogs and sheets green: depth is the surface steps (tokens `elevation`).
    surfaceTint = LightTokens.surfaceHigh,
    scrim = Color(0x99000000),
)

private val darkExtras = AndarzColors(
    allowed = DarkTokens.firouzeh,
    caution = DarkTokens.saffron,
    redline = DarkTokens.anar,
    redlineFill = DarkTokens.anarFill,
    onRedlineFill = DarkTokens.onAnarFill,
    info = DarkTokens.lapis,
    violet = DarkTokens.violet,
    textMuted = DarkTokens.textMuted,
    divider = DarkTokens.divider,
    surfaceAlt = DarkTokens.surfaceAlt,
    surfaceHigh = DarkTokens.surfaceHigh,
    chartSeries = DarkTokens.chartSeries,
    isDark = true,
)

private val lightExtras = AndarzColors(
    allowed = LightTokens.firouzeh,
    caution = LightTokens.saffron,
    redline = LightTokens.anar,
    redlineFill = LightTokens.anarFill,
    onRedlineFill = LightTokens.onAnarFill,
    info = LightTokens.lapis,
    violet = LightTokens.violet,
    textMuted = LightTokens.textMuted,
    divider = LightTokens.divider,
    surfaceAlt = LightTokens.surfaceAlt,
    surfaceHigh = LightTokens.surfaceHigh,
    chartSeries = LightTokens.chartSeries,
    isDark = false,
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
    val family = rememberPersianFamily()
    CompositionLocalProvider(LocalAndarzColors provides if (dark) darkExtras else lightExtras) {
        MaterialTheme(
            colorScheme = if (dark) darkScheme else lightScheme,
            typography = typographyOf(family),
            shapes = andarzShapes,
            content = content,
        )
    }
}
