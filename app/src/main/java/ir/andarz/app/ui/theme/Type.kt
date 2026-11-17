package ir.andarz.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ir.andarz.app.R

/**
 * The three families of the design.
 *
 * - **Peyda** for the interface, text and headings. It ships two weights only: Regular for every weight from
 *   100 to 700 and Black for 800 and 900. No weight is ever synthesised, so a request for "bold" draws Regular
 *   and a request for 800 draws Black, exactly as the canvas does.
 * - **IBM Plex Sans Arabic** (subset: Latin, digits and punctuation) for every numeral and every Latin symbol
 *   (prices, R values, tickers). [ir.andarz.app.ui.components.Text] routes those runs to it.
 * - **Noto Naskh Arabic** for the daily advice quote only.
 *
 * All files ship in `res/font` and are referenced as `R.font.*`, never looked up by name, so R8's resource
 * shrinker sees and keeps them (D-80).
 */
object AndarzFonts {
    val peyda: FontFamily = FontFamily(
        Font(R.font.peyda_regular, FontWeight.W400),
        Font(R.font.peyda_regular, FontWeight.W500),
        Font(R.font.peyda_regular, FontWeight.W600),
        Font(R.font.peyda_regular, FontWeight.W700),
        Font(R.font.peyda_black, FontWeight.W800),
        Font(R.font.peyda_black, FontWeight.W900),
    )

    val plex: FontFamily = FontFamily(
        Font(R.font.plex_arabic_regular, FontWeight.W400),
        Font(R.font.plex_arabic_medium, FontWeight.W500),
        Font(R.font.plex_arabic_semibold, FontWeight.W600),
        Font(R.font.plex_arabic_bold, FontWeight.W700),
    )

    val naskh: FontFamily = FontFamily(
        Font(R.font.naskh_regular, FontWeight.W400),
    )
}

/** Peyda Black, the only heavy weight of the family. */
val PeydaBlack: FontWeight = FontWeight.W800

/**
 * Line height 1.6 for Persian body text, 1.4 for headings, 1.15 for display figures. Tabular figures on every
 * Plex run so money, R values and prices line up in columns.
 */
private fun peyda(size: Int, weight: FontWeight, lineFactor: Float = 1.6f) = TextStyle(
    fontFamily = AndarzFonts.peyda,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * lineFactor).sp,
)

private fun plex(size: Int, weight: FontWeight, lineFactor: Float = 1.15f) = TextStyle(
    fontFamily = AndarzFonts.plex,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * lineFactor).sp,
    fontFeatureSettings = "tnum",
)

/**
 * The type scale of the canvas, mapped onto every Material 3 role so no text ever falls back to the platform
 * font: display 42 Plex 600 (figures only), headline 24 Peyda 800, title 18 Peyda 800, body 15 Peyda 400,
 * label 12 Peyda 400.
 */
internal fun andarzTypography() = Typography(
    displayLarge = plex(42, FontWeight.W600),
    displayMedium = plex(36, FontWeight.W600),
    displaySmall = plex(32, FontWeight.W600),
    headlineLarge = peyda(28, PeydaBlack, 1.4f),
    headlineMedium = peyda(24, PeydaBlack, 1.4f),
    headlineSmall = peyda(22, PeydaBlack, 1.4f),
    titleLarge = peyda(20, PeydaBlack, 1.4f),
    titleMedium = peyda(17, PeydaBlack, 1.45f),
    titleSmall = peyda(15, PeydaBlack, 1.5f),
    bodyLarge = peyda(16, FontWeight.W400),
    bodyMedium = peyda(15, FontWeight.W400),
    bodySmall = peyda(13, FontWeight.W400),
    labelLarge = peyda(14, FontWeight.W400, 1.5f),
    labelMedium = peyda(12, FontWeight.W400, 1.5f),
    labelSmall = peyda(11, FontWeight.W400, 1.5f),
)
