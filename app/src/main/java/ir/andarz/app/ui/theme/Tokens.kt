package ir.andarz.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The colour tokens of `spec/andarz_design_tokens.json`, transcribed once.
 *
 * `ThemeTokensTest` parses the JSON and fails the build if any constant here drifts from it (§6.2), so
 * this file is a mirror, never a second opinion. Dynamic colour (Material You) is deliberately not used:
 * the palette is part of the identity (§6.2).
 *
 * v2 "Ledger" palette (D-UI2): graphite surfaces stepped in tone (no shadows in dark), an emerald
 * firouzeh for allowed/profit, a gold saffron for caution and a coral anar for redline/loss. The token
 * NAMES are unchanged, so every semantic role keeps its meaning; only the hues moved.
 */
internal object DarkTokens {
    val bg = Color(0xFF0D0E10)
    val surface = Color(0xFF15171B)
    val surfaceAlt = Color(0xFF1C1E24)
    val surfaceHigh = Color(0xFF24272E)
    val divider = Color(0xFF2C2F37)
    val outline = Color(0xFF737A86)
    val text = Color(0xFFF5F5F4)
    val textMuted = Color(0xFFA3A79C)
    val firouzeh = Color(0xFF4AE390)
    val onFirouzeh = Color(0xFF05190D)
    val firouzehContainer = Color(0xFF123623)
    val saffron = Color(0xFFE0B667)
    val onSaffron = Color(0xFF231600)
    val saffronContainer = Color(0xFF352A12)
    val anar = Color(0xFFFF7166)
    val anarFill = Color(0xFFC9342A)
    val onAnarFill = Color(0xFFFFFFFF)
    val anarContainer = Color(0xFF3E1714)
    val lapis = Color(0xFF8FA8FF)
    val lapisContainer = Color(0xFF18213F)
    val violet = Color(0xFFB79CFF)

    /** Series order for charts (§6.5). */
    val chartSeries = listOf(
        Color(0xFF4AE390),
        Color(0xFF8FA8FF),
        Color(0xFFE0B667),
        Color(0xFFFF7166),
        Color(0xFFB79CFF),
    )
}

internal object LightTokens {
    val bg = Color(0xFFF3F3F0)
    val surface = Color(0xFFFFFFFF)
    val surfaceAlt = Color(0xFFEAEAE5)
    val surfaceHigh = Color(0xFFE0E0DA)
    val divider = Color(0xFFD3D3CB)
    val outline = Color(0xFF74776D)
    val text = Color(0xFF0D0E10)
    val textMuted = Color(0xFF4E5249)
    val firouzeh = Color(0xFF0A7342)
    val onFirouzeh = Color(0xFFFFFFFF)
    val firouzehContainer = Color(0xFFD3F2E0)

    /** Text saffron: 5.9:1 on surface. The chart amber below is graphics-only (D-20). */
    val saffron = Color(0xFF835A00)
    val onSaffron = Color(0xFF231600)
    val saffronFill = Color(0xFFE0B667)
    val saffronContainer = Color(0xFFF6EAD0)
    val anar = Color(0xFFB3261E)
    val anarFill = Color(0xFFB3261E)
    val onAnarFill = Color(0xFFFFFFFF)
    val anarContainer = Color(0xFFFADCD8)
    val lapis = Color(0xFF2F4AC4)
    val lapisContainer = Color(0xFFDDE3FB)
    val violet = Color(0xFF6B4FD1)

    /** Series 3 (#B87300) is a chart-only amber at 3.82:1 — never used for text (D-20). */
    val chartSeries = listOf(
        Color(0xFF0A7342),
        Color(0xFF2F4AC4),
        Color(0xFFB87300),
        Color(0xFFB3261E),
        Color(0xFF6B4FD1),
    )
}

/** Shape radii in dp (`shape` of the tokens). */
internal object ShapeTokens {
    const val SMALL = 10
    const val MEDIUM = 16
    const val LARGE = 20
    const val SHEET = 28
}

/** The 4 dp spacing grid (`spacing` of the tokens). */
internal object SpacingTokens {
    const val SCREEN_HORIZONTAL = 16
    const val CARD = 16
    const val MIN_TOUCH_TARGET = 48
}

/** Motion durations in ms (`motion.durationsMs`). No bouncy or celebratory animation anywhere (law 8). */
internal object MotionTokens {
    const val FAST = 150
    const val STANDARD = 250
    const val EMPHASIZED = 400
}
