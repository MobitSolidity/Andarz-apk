package ir.andarz.app.ui.icons

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import java.util.concurrent.ConcurrentHashMap

/*
 * The icon set of the design canvas: line icons on a 24 unit grid with round caps and joins, drawn with a
 * stroke of 1.75 units by default (1.9 to 2.4 for emphasis, 3.2 for the dot icons). The shapes were taken from
 * the boards as they are, so the app and the canvas show the same glyphs. Each glyph is a few SVG path strings
 * turned into a vector once and cached per stroke width; tint and size come from the call site, like any Icon.
 * Glyphs that point sideways mirror themselves in a right-to-left layout.
 *
 * Material icons-extended is deliberately not a dependency (it adds several MB); this table replaces it.
 */

/** One drawn element of a glyph: SVG path data, whether it is filled and/or stroked, an optional own width. */
internal class GlyphPart(
    val d: String,
    val fill: Boolean,
    val stroke: Boolean,
    val width: Float?,
    val alpha: Float,
)

private fun p(d: String, fill: Boolean = false, stroke: Boolean = true, width: Float? = null, alpha: Float = 1f) =
    GlyphPart(d, fill, stroke, width, alpha)

/** The glyphs of the canvas. The name says what it draws; screens pick by meaning, not by look. */
enum class Glyph(internal val parts: List<GlyphPart>, internal val mirror: Boolean = false) {
    BELL(listOf(p("M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 1.5h-15zM10 20.5h4"))),
    TRI_UP(listOf(p("M12 5l8 13H4z", fill = true, stroke = false))),
    BELL_OFF(listOf(p("M9.5 5.2A6 6 0 0 1 18 11v5.5l1.5 1.5H8M6 16.5V11c0-.6.1-1.2.3-1.7M10 20.5h4M4 4l16 16"))),
    ALARM_CLOCK(listOf(p("M5 13a7 7 0 1 0 14 0a7 7 0 1 0 -14 0z"), p("M12 9.5V13l2.3 1.6M4.5 5.5L7 3.5M19.5 5.5L17 3.5"))),
    ARROW_LEFT(listOf(p("M19 12H5M11 6l-6 6 6 6")), mirror = true),
    CHECK(listOf(p("M5 12.5l4.5 4.5L19 7.5"))),
    ALERT_CIRCLE(listOf(p("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0z"), p("M12 7.5v5.5M12 16.5h.01"))),
    X(listOf(p("M6 6l12 12M18 6L6 18"))),
    CALENDAR_CHECK(listOf(p("M7 5h10a3 3 0 0 1 3 3v9a3 3 0 0 1 -3 3h-10a3 3 0 0 1 -3 -3v-9a3 3 0 0 1 3 -3z"), p("M4 10h16M9 3v4M15 3v4M9.5 15l2 2 3.5-3.5"))),
    REFRESH(listOf(p("M20 12a8 8 0 1 1-2.6-5.9M20 4v5h-5"))),
    TRI_DOWN(listOf(p("M12 19L4 6h16z", fill = true, stroke = false))),
    CARD_DOT(listOf(p("M6 6h12a3 3 0 0 1 3 3v7a3 3 0 0 1 -3 3h-12a3 3 0 0 1 -3 -3v-7a3 3 0 0 1 3 -3z"), p("M3 10h18M16.5 14.5h.01"))),
    DIAMOND(listOf(p("M12 4.2L19.8 12L12 19.8L4.2 12z", fill = true, stroke = false))),
    MINUS(listOf(p("M5 12h14"))),
    PLUS(listOf(p("M12 5v14M5 12h14"))),
    BOOK(listOf(p("M6 4h10a3 3 0 0 1 3 3v13H9a3 3 0 0 1-3-3z"), p("M6 17a3 3 0 0 1 3-3h10"))),
    BARS(listOf(p("M5 12h2a1 1 0 0 1 1 1v6a1 1 0 0 1 -1 1h-2a1 1 0 0 1 -1 -1v-6a1 1 0 0 1 1 -1z"), p("M11 5h2a1 1 0 0 1 1 1v13a1 1 0 0 1 -1 1h-2a1 1 0 0 1 -1 -1v-13a1 1 0 0 1 1 -1z"), p("M17 9h2a1 1 0 0 1 1 1v9a1 1 0 0 1 -1 1h-2a1 1 0 0 1 -1 -1v-9a1 1 0 0 1 1 -1z"))),
    CALC(listOf(p("M8 3h8a3 3 0 0 1 3 3v12a3 3 0 0 1 -3 3h-8a3 3 0 0 1 -3 -3v-12a3 3 0 0 1 3 -3z"), p("M9 6h6a1 1 0 0 1 1 1v2a1 1 0 0 1 -1 1h-6a1 1 0 0 1 -1 -1v-2a1 1 0 0 1 1 -1z"), p("M9 14h.01M12 14h.01M15 14h.01M9 17.5h.01M12 17.5h.01M15 17.5h.01", width = 2.4f))),
    DOTS(listOf(p("M6 12h.01M12 12h.01M18 12h.01", width = 3.2f))),
    STAR_FILL(listOf(p("M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1 5.9L12 17l-5.2 2.7 1-5.9L3.5 9.7l5.9-.8z", fill = true, width = 1.6f))),
    CLOCK_ROUND(listOf(p("M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0z"), p("M12 7.5v5l3 2"))),
    REFRESH_CCW(listOf(p("M20 11a8 8 0 1 0-2.3 5.7M20 5v6h-6"))),
    ARROW_RIGHT(listOf(p("M5 12h14M13 6l6 6-6 6")), mirror = true),
    MENU(listOf(p("M4 6h16M4 12h16M4 18h16"))),
    TRIANGLE(listOf(p("M12 4l9 16H3z"))),
    DOTTED_CIRCLE(listOf(p("M20 12h0.01M17.657 17.657h0.01M12 20h0.01M6.343 17.657h0.01M4 12h0.01M6.343 6.343h0.01M12 4h0.01M17.657 6.343h0.01", width = 2.2f))),
    LINE_CHART(listOf(p("M4 19V5M4 19h16M8 15l3-4 3 2 5-7"))),
    SEARCH(listOf(p("M4.5 11a6.5 6.5 0 1 0 13 0a6.5 6.5 0 1 0 -13 0z"), p("M16 16l4.5 4.5"))),
    FILTER(listOf(p("M4 6h16M7 12h10M10 18h4"))),
    DOT_FILL(listOf(p("M4 12a8 8 0 1 0 16 0a8 8 0 1 0 -16 0z", fill = true, stroke = false))),
    NOTE_CHECK(listOf(p("M8 4h8a3 3 0 0 1 3 3v11a3 3 0 0 1 -3 3h-8a3 3 0 0 1 -3 -3v-11a3 3 0 0 1 3 -3z"), p("M9 12.5l2 2 4-4"))),
    X_SMALL(listOf(p("M7 7l10 10M17 7L7 17"))),
    SWAP(listOf(p("M5 9h13l-3.5-3.5M19 15H6l3.5 3.5")), mirror = true),
    STAR(listOf(p("M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1 5.9L12 17l-5.2 2.7 1-5.9L3.5 9.7l5.9-.8z"))),
    INFO_I(listOf(p("M12 11v6M12 7h.01"))),
    GLOBE(listOf(p("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0z"), p("M3 12h18M12 3c3 3 3 15 0 18c-3-3-3-15 0-18"))),
    PHONE(listOf(p("M9.5 3h5a2.5 2.5 0 0 1 2.5 2.5v13a2.5 2.5 0 0 1 -2.5 2.5h-5a2.5 2.5 0 0 1 -2.5 -2.5v-13a2.5 2.5 0 0 1 2.5 -2.5z"), p("M11 17.5h2"))),
    LOCK(listOf(p("M7.5 11h9a2.5 2.5 0 0 1 2.5 2.5v4a2.5 2.5 0 0 1 -2.5 2.5h-9a2.5 2.5 0 0 1 -2.5 -2.5v-4a2.5 2.5 0 0 1 2.5 -2.5z"), p("M8.5 11V8a3.5 3.5 0 0 1 7 0v3"))),
    POWER(listOf(p("M12 3v8M7.5 6.6a7 7 0 1 0 9 0"))),
    ALERT_TRIANGLE(listOf(p("M12 4l9 16H3z"), p("M12 10v4M12 17h.01"))),
    BAN(listOf(p("M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0z"), p("M6 6l12 12"))),
    HOURGLASS(listOf(p("M7 4h10M7 20h10M8 4v3a4 4 0 0 0 1.6 3.2L12 12l-2.4 1.8A4 4 0 0 0 8 17v3M16 4v3a4 4 0 0 1-1.6 3.2L12 12l2.4 1.8A4 4 0 0 1 16 17v3"))),
    SERVER(listOf(p("M6 4h12a2 2 0 0 1 2 2v2a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-2a2 2 0 0 1 2 -2z"), p("M6 14h12a2 2 0 0 1 2 2v2a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-2a2 2 0 0 1 2 -2z"), p("M8 7h.01M8 17h.01"))),
    CLOUD_DOWN(listOf(p("M7 18.5a4.5 4.5 0 0 1-.7-8.95 6 6 0 0 1 11.5 1.45A4 4 0 0 1 17.5 18.5M12 11v7m0 0l-2.5-2.5M12 18l2.5-2.5"))),
    POWER_OFF(listOf(p("M12 2v10"), p("M18.4 6.6a9 9 0 1 1-12.77.04"))),
    CHEVRON_LEFT(listOf(p("M14.5 6l-6 6 6 6")), mirror = true),
    CALENDAR(listOf(p("M7 5h10a3 3 0 0 1 3 3v9a3 3 0 0 1 -3 3h-10a3 3 0 0 1 -3 -3v-9a3 3 0 0 1 3 -3z"), p("M4 10h16M9 3v4M15 3v4"))),
    SHIELD_CHECK(listOf(p("M12 3l7.5 3v5.5c0 4.3-3 7.8-7.5 9.5-4.5-1.7-7.5-5.2-7.5-9.5V6z"), p("M8.8 12l2.2 2.2 4.2-4.4"))),
    CLIPBOARD_CHECK(listOf(p("M8 5.5h8a3 3 0 0 1 3 3v9.5a3 3 0 0 1 -3 3h-8a3 3 0 0 1 -3 -3v-9.5a3 3 0 0 1 3 -3z"), p("M9 5.5V4.5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v1M9.2 13.8l2 2 3.6-3.8"))),
    CHART_AXIS(listOf(p("M4 4v16h16"), p("M8 15l3.2-4.2 3 2.4L19 7.5"))),
    SLIDERS(listOf(p("M4 8h9M17 8h3M4 16h3M11 16h9"), p("M13 8a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z"), p("M7 16a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z"))),
    LAYERS(listOf(p("M12 3.5l8.5 4.5-8.5 4.5L3.5 8z"), p("M3.5 12.5L12 17l8.5-4.5M3.5 16.5L12 21l8.5-4.5"))),
    EYE(listOf(p("M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"), p("M9.2 12a2.8 2.8 0 1 0 5.6 0a2.8 2.8 0 1 0 -5.6 0z"))),
    WALLET(listOf(p("M5 7.5A2.5 2.5 0 0 1 7.5 5H17v3"), p("M7 8h10a3 3 0 0 1 3 3v6a3 3 0 0 1 -3 3h-10a3 3 0 0 1 -3 -3v-6a3 3 0 0 1 3 -3z"), p("M15.5 14h2", width = 2.4f))),
    BULB(listOf(p("M9.5 18h5M10 21h4"), p("M12 3a6 6 0 0 0-3.4 10.9c.6.5 1 1.2 1 1.9V16h4.8v-.2c0-.7.4-1.4 1-1.9A6 6 0 0 0 12 3z"))),
    PIE(listOf(p("M12 3v9h9"), p("M12 3a9 9 0 1 0 9 9"))),
    GEAR(listOf(p("M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"), p("M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z"))),
    INFO(listOf(p("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0z"), p("M12 11v5.5M12 7.5h.01"))),
    BELL_FILL(listOf(p("M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 1.5h-15zM10 20.5h4", fill = true, width = 2f))),
    CHECK_CIRCLE(listOf(p("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0z"), p("M8.5 12.3l2.6 2.6 4.6-5"))),
    LAYERS_2(listOf(p("M12 3.5l8.5 4.5-8.5 4.5L3.5 8z"), p("M3.5 12.5L12 17l8.5-4.5"))),
    CALENDAR_MINUS(listOf(p("M7 5h10a3 3 0 0 1 3 3v9a3 3 0 0 1 -3 3h-10a3 3 0 0 1 -3 -3v-9a3 3 0 0 1 3 -3z"), p("M4 10h16M9 3v4M15 3v4M9.5 15h5"))),
    CHEVRON_DOWN(listOf(p("M6 9l6 6 6-6"))),
    BAR_FILL(listOf(p("M5.5 10.5h13a1.5 1.5 0 0 1 1.5 1.5v0a1.5 1.5 0 0 1 -1.5 1.5h-13a1.5 1.5 0 0 1 -1.5 -1.5v0a1.5 1.5 0 0 1 1.5 -1.5z", fill = true, stroke = false))),
    CLOCK(listOf(p("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0z"), p("M12 7v5l3 2"))),
    DOWNLOAD(listOf(p("M12 5v10M8 11.5l4 4 4-4M6 19.5h12"))),
    HOURGLASS_2(listOf(p("M6 4h12M6 20h12M8 4v3c0 2.5 4 3.5 4 5s-4 2.5-4 5v3M16 4v3c0 2.5-4 3.5-4 5s4 2.5 4 5v3"))),
    CARD(listOf(p("M6 6h12a3 3 0 0 1 3 3v7a3 3 0 0 1 -3 3h-12a3 3 0 0 1 -3 -3v-7a3 3 0 0 1 3 -3z"), p("M3 10h18"))),
    EYE_OFF(listOf(p("M3 3l18 18M8.5 8.5A5 5 0 0 0 7 12c0 1.4.6 2.7 1.5 3.6M16 16.5A5 5 0 0 0 17 12a5 5 0 0 0-5-5"))),
    CROSSHAIR(listOf(p("M12 4v4M12 16v4M4 12h4M16 12h4"))),
    MOON(listOf(p("M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5z"))),
    DOTS_V(listOf(p("M12 5h.01M12 12h.01M12 19h.01", width = 3.2f))),
    NOTES(listOf(p("M5 5h11v14H7a2 2 0 0 1-2-2zM16 8h3v9a2 2 0 0 1-2 2M8 9h5M8 12h5M8 15h3"))),
    SMILE(listOf(p("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0z"), p("M8.5 14.5c1 1.3 2.2 2 3.5 2s2.5-.7 3.5-2M9 9.5h.01M15 9.5h.01"))),
    PENCIL(listOf(p("M5 19l1-4L17 4a2.1 2.1 0 0 1 3 3L9 18z"))),
    SHIELD(listOf(p("M12 3l7 3v5c0 5-3 8.5-7 10-4-1.5-7-5-7-10V6z"))),
    LINES(listOf(p("M4 12h16"), p("M4 7h16M4 17h16", alpha = 0.45f))),
    ;

    /** The glyph as a vector drawn with [strokeWidth] units (of 24) for every element without an own width. */
    fun vector(strokeWidth: Float = DEFAULT_STROKE): ImageVector {
        val key = (ordinal.toLong() shl 32) or strokeWidth.toRawBits().toLong().and(0xFFFFFFFFL)
        return cache.getOrPut(key) { build(this, strokeWidth) }
    }

    companion object {
        const val DEFAULT_STROKE = 1.75f
        private val cache = ConcurrentHashMap<Long, ImageVector>()

        private fun build(g: Glyph, strokeWidth: Float): ImageVector =
            ImageVector.Builder(
                name = g.name, defaultWidth = 24.dp, defaultHeight = 24.dp,
                viewportWidth = 24f, viewportHeight = 24f, autoMirror = g.mirror,
            ).apply {
                for (part in g.parts) {
                    addPath(
                        pathData = PathParser().parsePathString(part.d).toNodes(),
                        fill = if (part.fill) SolidColor(Color.Black) else null,
                        fillAlpha = part.alpha,
                        stroke = if (part.stroke) SolidColor(Color.Black) else null,
                        strokeAlpha = part.alpha,
                        strokeLineWidth = part.width ?: strokeWidth,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }.build()
    }
}

/**
 * Draws a [Glyph]. [contentDescription] is null for a decorative icon (the label next to it says it) and
 * a word for an icon that stands alone; colour is never the only signal (tokens `semantics.rule`).
 */
@Composable
fun GlyphIcon(
    glyph: Glyph,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
    strokeWidth: Float = Glyph.DEFAULT_STROKE,
) {
    val vector = remember(glyph, strokeWidth) { glyph.vector(strokeWidth) }
    Icon(vector, contentDescription, modifier.size(size), tint)
}
