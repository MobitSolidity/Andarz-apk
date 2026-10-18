package ir.andarz.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The colour tokens of `spec/andarz_design_tokens.json`, transcribed once.
 *
 * `ThemeTokensTest` parses the JSON and fails the build if any constant here drifts from it (§6.2), so
 * this file is a mirror, never a second opinion. Dynamic colour (Material You) is deliberately not used:
 * the palette is part of the identity (§6.2).
 */
internal object DarkTokens {
    val bg = Color(0xFF0A1016)
    val surface = Color(0xFF111922)
    val surfaceAlt = Color(0xFF17212C)
    val surfaceHigh = Color(0xFF1E2A37)
    val divider = Color(0xFF273445)
    val outline = Color(0xFF62768C)
    val text = Color(0xFFE9EFF5)
    val textMuted = Color(0xFF9DB0C3)
    val firouzeh = Color(0xFF2DD4C0)
    val onFirouzeh = Color(0xFF02201C)
    val firouzehContainer = Color(0xFF0F3A37)
    val saffron = Color(0xFFF5B942)
    val onSaffron = Color(0xFF231600)
    val saffronContainer = Color(0xFF3A2B07)
    val anar = Color(0xFFFF7385)
    val anarFill = Color(0xFFC62A43)
    val onAnarFill = Color(0xFFFFFFFF)
    val anarContainer = Color(0xFF3F1219)
    val lapis = Color(0xFF8AA4FF)
    val lapisContainer = Color(0xFF16214A)
    val violet = Color(0xFFB79CFF)

    /** Series order for charts (§6.5). */
    val chartSeries = listOf(
        Color(0xFF2DD4C0),
        Color(0xFF8AA4FF),
        Color(0xFFF5B942),
        Color(0xFFFF7385),
        Color(0xFFB79CFF),
    )
}

internal object LightTokens {
    val bg = Color(0xFFF6F1E6)
    val surface = Color(0xFFFFFDF8)
    val surfaceAlt = Color(0xFFEFE8D8)
    val surfaceHigh = Color(0xFFE7DFCB)
    val divider = Color(0xFFD8CFBB)
    val outline = Color(0xFF7D7560)
    val text = Color(0xFF14212C)
    val textMuted = Color(0xFF4F6071)
    val firouzeh = Color(0xFF087069)
    val onFirouzeh = Color(0xFFFFFFFF)
    val firouzehContainer = Color(0xFFD3EFEA)

    /** Text saffron: 6.05:1 on surface. The chart amber below is graphics-only (D-20). */
    val saffron = Color(0xFF8A5600)
    val onSaffron = Color(0xFF231600)
    val saffronFill = Color(0xFFF2B134)
    val saffronContainer = Color(0xFFFBEBC4)
    val anar = Color(0xFFB3203A)
    val anarFill = Color(0xFFB3203A)
    val onAnarFill = Color(0xFFFFFFFF)
    val anarContainer = Color(0xFFFBD9DE)
    val lapis = Color(0xFF2F4AC4)
    val lapisContainer = Color(0xFFDDE3FB)
    val violet = Color(0xFF6B4FD1)

    /** Series 3 (#B87300) is a chart-only amber at 3.76:1 — never used for text (D-20). */
    val chartSeries = listOf(
        Color(0xFF087069),
        Color(0xFF2F4AC4),
        Color(0xFFB87300),
        Color(0xFFB3203A),
        Color(0xFF6B4FD1),
    )
}

/** Shape radii in dp (`shape` of the tokens). */
internal object ShapeTokens {
    const val SMALL = 8
    const val MEDIUM = 14
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
