package ir.andarz.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.andarz.app.ui.icons.Glyph
import ir.andarz.app.ui.icons.GlyphIcon
import ir.andarz.app.ui.theme.AndarzFonts
import ir.andarz.app.ui.theme.LocalAndarzColors

/*
 * Small pieces that several screens of the canvas share: the figure style, the dotted leader between a label and
 * its number, the message bar, the bottom bar and the one pixel ring of a tile.
 */

/** Plex tabular figures at [size] sp: the numbers of the canvas (the Peyda family has no figures of its own there). */
@Composable
fun figure(size: Int, weight: FontWeight = FontWeight.W700): TextStyle = MaterialTheme.typography.bodyMedium.copy(
    fontFamily = AndarzFonts.plex, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.4f).sp, fontFeatureSettings = "tnum",
)

/** The dotted line between a label and its figure, sitting on the text baseline. */
@Composable
fun Leader(modifier: Modifier = Modifier, color: Color = LocalAndarzColors.current.outline) {
    Canvas(modifier.widthIn(min = 6.dp).height(2.dp).offset(y = 4.dp)) {
        val w = 1.5.dp.toPx()
        drawLine(
            color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = w, cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, 3.dp.toPx()), 0f),
        )
    }
}

/** A label, a dotted leader and a figure on one line; TalkBack reads the pair as one node. */
@Composable
fun DottedRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = LocalAndarzColors.current.text,
    valueStyle: TextStyle = figure(13),
    height: Dp = 28.dp,
) {
    val k = LocalAndarzColors.current
    Row(
        modifier.fillMaxWidth().heightIn(min = height).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium, color = k.muted)
        Leader(Modifier.weight(1f))
        Text(value, maxLines = 1, softWrap = false, style = valueStyle, color = valueColor)
    }
}

enum class NoticeTone { CAUTION, DANGER }

/** A message with a glyph and a word, never a colour alone: an arch tile, then the text, on the soft tone. */
@Composable
fun NoticeBar(text: String, tone: NoticeTone, modifier: Modifier = Modifier) {
    val k = LocalAndarzColors.current
    val danger = tone == NoticeTone.DANGER
    val container = if (danger) k.anC else k.sfC
    val ink = if (danger) k.an else k.sf
    val tile = if (danger) k.anSoft else k.sfSoft
    val ring = if (danger) k.anLine else k.sfLine
    Row(
        modifier.fillMaxWidth().archSurface(RoundedCornerShape(16.dp), container, ring).padding(start = 10.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArchTile(tile, width = 24.dp, height = 28.dp, bottom = 7.dp) {
            GlyphIcon(if (danger) Glyph.X else Glyph.ALERT_CIRCLE, null, tint = ink, size = 14.dp, strokeWidth = 2.4f)
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = ink, modifier = Modifier.weight(1f))
    }
}

/** The bar at the foot of a screen: the `sf1` step with a hairline on top, holding one to three actions. */
@Composable
fun BottomBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val k = LocalAndarzColors.current
    Row(
        modifier.fillMaxWidth().background(k.sf1)
            .drawBehind { drawLine(k.divider, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A ring of [width] drawn just inside [shape]; nothing is clipped (see [archSurface]). */
fun Modifier.archRing(shape: Shape, color: Color, width: Dp = 1.5.dp): Modifier = this.drawWithCache {
    val w = width.toPx()
    val outline = shape.createOutline(Size(size.width - w, size.height - w), layoutDirection, this)
    onDrawWithContent {
        drawContent()
        translate(w / 2f, w / 2f) { drawOutline(outline, color, style = Stroke(w)) }
    }
}

/** A 48 dp arch tile with one glyph: the actions at the end edge of a tab header (search, filter, reset). */
@Composable
fun HeaderTile(
    glyph: Glyph, description: String, tint: Color, container: Color, onClick: () -> Unit,
    enabled: Boolean = true, selected: Boolean = false,
) {
    Box(
        Modifier.size(48.dp)
            .background(container, ArchShapes.arch(24.dp, 12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph, description, tint = tint, size = 22.dp, strokeWidth = 2f) }
}

/** The primary action of a page: turquoise fill with `onFz` text, the grey of `high` while it cannot be used yet. */
@Composable
fun FilledAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 52.dp,
    radius: Dp = 16.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val k = LocalAndarzColors.current
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = height),
        shape = RoundedCornerShape(radius),
        colors = ButtonDefaults.buttonColors(
            containerColor = k.fzFill, contentColor = k.onFz, disabledContainerColor = k.high, disabledContentColor = k.muted,
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) { ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W700)) { content() } }
}

/** A small pill: text with an optional glyph, on a container colour (status words, sides, tags). */
@Composable
fun Pill(text: String, container: Color, ink: Color, glyph: Glyph? = null, height: Dp = 24.dp, modifier: Modifier = Modifier) {
    Row(
        modifier.heightIn(min = height).background(container, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (glyph != null) GlyphIcon(glyph, null, tint = ink, size = 10.dp)
        Text(text, color = ink, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W700), maxLines = 1)
    }
}
