package ir.andarz.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.theme.Tone
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * Surfaces of the design canvas. Depth is made of tone steps (bg, sf1, alt, high) and of a one pixel edge, never
 * of shadows: a divider-coloured ring all around and a faint highlight along the top edge ("inset 0 1px 0 edge,
 * inset 0 0 0 1px divider" on the boards). Only the hero card also glows, in the colour of the state of the day.
 */

/** The shapes of the canvas. Arches ("taagh") have a large top radius over a small bottom radius. */
object ArchShapes {
    /** Cards and panels. */
    val card = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 12.dp, bottomStart = 12.dp)

    /** Verdict, lock and warning banners. */
    val banner = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 14.dp, bottomStart = 14.dp)

    /** A panel inside a card, an input, a tonal button. */
    val tile = RoundedCornerShape(16.dp)

    /** The big capital card of the Today screen. */
    val hero = RoundedCornerShape(28.dp)

    /** A text input: arch, slightly taller on top. */
    val field = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 12.dp, bottomStart = 12.dp)

    val pill = RoundedCornerShape(percent = 50)

    /** An icon tile: half its width on top, 7 to 12 dp below. */
    fun arch(top: Dp, bottom: Dp): Shape = RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
}

/**
 * Fills [shape] with [color] (or [brush]), then draws the one pixel [ring] just inside its edge and a highlight
 * that fades out down the sides.
 *
 * Nothing is clipped. A layer clipped to a rounded rectangle with unequal corners is hit-tested through
 * `Path.op`, which Robolectric's legacy graphics does not implement, so every click inside such a layer would
 * silently miss in the unit tests (found the hard way with the eye button of the Today card). Drawing the arch
 * is just as exact and cheaper. Content that must not overflow the corners is kept inside by padding, or
 * clipped on its own with a uniform shape; [clipContent] does that for a uniform [shape] only.
 */
fun Modifier.archSurface(
    shape: Shape,
    color: Color,
    ring: Color,
    shine: Color = Color.Transparent,
    brush: ((Size) -> Brush)? = null,
    clipContent: Boolean = false,
): Modifier = this
    .then(if (clipContent) Modifier.clip(shape) else Modifier)
    .drawWithCache {
        val w = 1.dp.toPx()
        val fill = shape.createOutline(size, layoutDirection, this)
        val ringOutline = shape.createOutline(Size(size.width - w, size.height - w), layoutDirection, this)
        val fillBrush = brush?.invoke(size)
        val shineBrush = Brush.verticalGradient(
            colorStops = arrayOf(0f to shine, 0.35f to Color.Transparent, 1f to Color.Transparent),
            startY = 0f, endY = size.height,
        )
        onDrawWithContent {
            if (fillBrush != null) drawOutline(fill, fillBrush) else drawOutline(fill, color)
            drawContent()
            translate(w / 2f, w / 2f) {
                drawOutline(ringOutline, ring, style = Stroke(w))
                if (shine != Color.Transparent) drawOutline(ringOutline, shineBrush, style = Stroke(w))
            }
        }
    }

/** A card: sf1 surface, divider ring, top highlight. The default container of every group of content. */
@Composable
fun ArchCard(
    modifier: Modifier = Modifier,
    shape: Shape = ArchShapes.card,
    color: Color = LocalAndarzColors.current.sf1,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalAndarzColors.current
    Column(modifier.archSurface(shape, color, c.divider, c.edge).padding(contentPadding), content = content)
}

/**
 * The hero card of the Today screen: a 165 degree gradient from h1 to h2, a soft glow in the colour of the
 * state of the day behind the lamp, and the khatam lattice at 6% over everything.
 */
@Composable
fun HeroSurface(
    tone: Tone,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 10.dp),
    content: @Composable () -> Unit,
) {
    val c = LocalAndarzColors.current
    val glow = c.glow(tone)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        modifier
            .archSurface(
                shape = ArchShapes.hero, color = c.h2, ring = c.divider, shine = c.edge,
                brush = { s -> diagonalGradient(s, c.h1, c.h2) },
                clipContent = true,
            )
            .drawWithCache {
                // the glow sits behind the lamp, which is at the end edge of the card
                val cx = if (rtl) 54.dp.toPx() else size.width - 54.dp.toPx()
                val brush = Brush.radialGradient(
                    colors = listOf(glow, Color.Transparent), center = Offset(cx, 62.dp.toPx()), radius = 110.dp.toPx(),
                )
                onDrawBehind { drawRect(brush) }
            },
    ) {
        KhatamPattern(Modifier.matchParentSize())
        Box(Modifier.padding(contentPadding)) { content() }
    }
}

/** CSS `linear-gradient(165deg, a 0%, b 78%)` over a box of [size]. */
private fun diagonalGradient(size: Size, a: Color, b: Color): Brush {
    val angle = Math.toRadians(165.0)
    val dx = sin(angle).toFloat()
    val dy = (-cos(angle)).toFloat()
    val len = abs(size.width * dx) + abs(size.height * dy)
    val cx = size.width / 2f
    val cy = size.height / 2f
    return Brush.linearGradient(
        colorStops = arrayOf(0f to a, 0.78f to b, 1f to b),
        start = Offset(cx - dx * len / 2f, cy - dy * len / 2f),
        end = Offset(cx + dx * len / 2f, cy + dy * len / 2f),
    )
}

/**
 * The arch tile of a verdict, lock or tag: a small arch (half its width over 8 dp) in the soft colour of a
 * tone with a glyph centred in it.
 */
@Composable
fun ArchTile(
    background: Color,
    modifier: Modifier = Modifier,
    width: Dp = 32.dp,
    height: Dp = 38.dp,
    bottom: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.size(width, height).background(background, ArchShapes.arch(width / 2, bottom)),
        contentAlignment = Alignment.Center,
    ) { content() }
}
