package ir.andarz.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The colour tokens of the final design canvas (`spec/andarz_design_tokens.json`, version 3), transcribed once.
 *
 * "Night and turquoise": deep navy surfaces stepped in tone, a turquoise firouzeh for allowed, profit and the
 * main action, a warm saffron for caution, a coral anar for the red line and for loss, lapis for information.
 * The names follow the CSS variables of the canvas (`fz`, `sf`, `an`, `lapis` ...) so a board and a composable
 * read the same. Dynamic colour (Material You) is deliberately not used: the palette is the identity.
 *
 * Text colours (`fz`, `sf`, `an`, `lapis`) and graphic colours (`fzG`, `sfG`, `anG`) are separate because in the
 * light theme the colour that is readable as text is darker than the one that looks right as a fill or a line.
 */
internal object DarkTokens {
    val bg = Color(0xFF0A1118)
    val bgDeep = Color(0xFF070D13)
    val sf1 = Color(0xFF101A25)
    val alt = Color(0xFF162231)
    val high = Color(0xFF1D2B3D)
    val text = Color(0xFFEAF0F5)
    val muted = Color(0xFFA3B1C0)
    val hmuted = Color(0xFFB9C5D1)
    val divider = Color(0xFF233245)
    val outline = Color(0xFF61748A)
    val track = Color(0xFF243448)

    val fz = Color(0xFF35D6C4)
    val fzG = Color(0xFF35D6C4)
    val fzC = Color(0xFF0E3A3A)
    val fzFill = Color(0xFF35D6C4)
    val onFz = Color(0xFF03201C)
    val fzSoft = Color(0x2935D6C4)

    val sf = Color(0xFFF3B63F)
    val sfG = Color(0xFFF3B63F)
    val sfC = Color(0xFF35290B)
    val sfSoft = Color(0x29F3B63F)
    val sfLine = Color(0x66F3B63F)

    val an = Color(0xFFFF8A80)
    val anG = Color(0xFFFF8A80)
    val anC = Color(0xFF3A1619)
    val anFill = Color(0xFFC8372F)
    val onAnFill = Color(0xFFFFFFFF)
    val anSoft = Color(0x33FF8A80)
    val anLine = Color(0x57FF8A80)

    val lapis = Color(0xFF8CA6FF)
    val lapisC = Color(0xFF18264A)
    val lapisSoft = Color(0x428CA6FF)
    val lapisLine = Color(0x738CA6FF)

    val violet = Color(0xFFB79CFF)

    val edge = Color(0x12FFFFFF)
    val h1 = Color(0xFF16242F)
    val h2 = Color(0xFF101A25)

    val glowAllowed = Color(0x2B35D6C4)
    val glowCaution = Color(0x26F3B63F)
    val glowLocked = Color(0x24FF8A80)
    val ringAllowed = Color(0x6B35D6C4)
    val ringCaution = Color(0x6BF3B63F)
    val ringLocked = Color(0x6BFF8A80)

    val scrim = Color(0xAD02060A)

    /** Series order for charts. */
    val chartSeries = listOf(fzG, lapis, sfG, anG, violet)
}

internal object LightTokens {
    val bg = Color(0xFFF3F5F7)
    val bgDeep = Color(0xFFF3F5F7)
    val sf1 = Color(0xFFFFFFFF)
    val alt = Color(0xFFEAEEF2)
    val high = Color(0xFFDEE4EA)
    val text = Color(0xFF0D1620)
    val muted = Color(0xFF45556A)
    val hmuted = Color(0xFF45556A)
    val divider = Color(0xFFD3DAE2)
    val outline = Color(0xFF7B8998)
    val track = Color(0xFFDEE4EA)

    val fz = Color(0xFF00695D)
    val fzG = Color(0xFF0A8576)
    val fzC = Color(0xFFD5F3EF)
    val fzFill = Color(0xFF17C2AF)
    val onFz = Color(0xFF03201C)
    val fzSoft = Color(0x1F00695D)

    /** Text saffron; the amber below it is for graphics only. */
    val sf = Color(0xFF8A5600)
    val sfG = Color(0xFFB87300)
    val sfC = Color(0xFFFBEFD3)
    val sfSoft = Color(0x1F8A5600)
    val sfLine = Color(0x578A5600)

    val an = Color(0xFFA8221B)
    val anG = Color(0xFFB3261E)
    val anC = Color(0xFFFBE3E0)
    val anFill = Color(0xFFB3261E)
    val onAnFill = Color(0xFFFFFFFF)
    val anSoft = Color(0x24B3261E)
    val anLine = Color(0x4DA8221B)

    val lapis = Color(0xFF2F4FB5)
    val lapisC = Color(0xFFE1E8FF)
    val lapisSoft = Color(0x332F4FB5)
    val lapisLine = Color(0x662F4FB5)

    val violet = Color(0xFF6B4FD1)

    val edge = Color(0xE6FFFFFF)
    val h1 = Color(0xFFFFFFFF)
    val h2 = Color(0xFFE8F0F2)

    val glowAllowed = Color(0x3317C2AF)
    val glowCaution = Color(0x21B87300)
    val glowLocked = Color(0x1AB3261E)
    val ringAllowed = Color(0x5900695D)
    val ringCaution = Color(0x618A5600)
    val ringLocked = Color(0x59A8221B)

    val scrim = Color(0x700D1620)

    /** Series 3 is a chart-only amber, never used for text. */
    val chartSeries = listOf(fzG, lapis, sfG, anG, violet)
}

/**
 * Shape radii in dp. The signature shape of the design is the arch ("taagh"): a large top radius and a small
 * bottom radius ([ARCH_TOP] over [ARCH_BOTTOM] for a card; a small icon tile uses half its width on top and
 * 7 to 12 dp below).
 */
internal object ShapeTokens {
    const val SMALL = 8
    const val MEDIUM = 14
    const val LARGE = 20
    const val SHEET = 28
    const val HERO = 28

    /** Card: 26 dp top corners over 14 dp bottom corners. */
    const val ARCH_TOP = 26
    const val ARCH_BOTTOM = 14
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
