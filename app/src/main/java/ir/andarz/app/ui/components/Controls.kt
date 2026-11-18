package ir.andarz.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.andarz.app.ui.icons.Glyph
import ir.andarz.app.ui.icons.GlyphIcon
import ir.andarz.app.ui.theme.AndarzFonts
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.theme.MotionTokens
import ir.andarz.app.ui.ui

/*
 * The controls of the design canvas. Same names and the same parameters as the Material 3 functions they wrap, so
 * a screen opts in by importing `ir.andarz.app.ui.components.Button` instead of `androidx.compose.material3.Button`;
 * behaviour, enabled rules and callbacks are untouched.
 *
 * - Button: the one primary action of a view. A turquoise pill, 56 dp tall, label 17 sp on dark teal. Disabled is
 *   a readable muted label on `high` (the 12% alpha of Material read as invisible on the night surfaces).
 * - TonalButton: secondary, a `high` tile with a 16 dp corner. OutlinedButton: secondary, a 1 dp `outline` ring.
 *   DestructiveButton: the red-line fill with white text, only for an action that cannot be undone.
 * - TextButton: tertiary, lapis label, 48 dp target.
 * - FilterChip: a 48 dp pill; the selected one carries a check mark as well as the tint, because colour is never
 *   the only signal (tokens `semantics.rule`).
 *
 * Every shape here is uniform (equal corners) on purpose: see `archSurface` for why unequal corners are drawn and
 * never used as the clip of a layer that holds a clickable.
 */

private val PrimaryShape = RoundedCornerShape(28.dp)
private val ControlShape = RoundedCornerShape(16.dp)
private val ChipShape = RoundedCornerShape(24.dp)

/** Peyda has Regular and Black only: 700 resolves to Regular, so these labels are Regular at their size. */
private fun primaryLabel() = TextStyle(fontFamily = AndarzFonts.peyda, fontWeight = FontWeight.W700, fontSize = 17.sp, lineHeight = 25.sp)
private fun controlLabel() = TextStyle(fontFamily = AndarzFonts.peyda, fontWeight = FontWeight.W700, fontSize = 15.sp, lineHeight = 22.sp)

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val c = LocalAndarzColors.current
    // The soft drop shadow of the primary action exists on the dark theme only; on the light one it reads heavy.
    val lift = if (enabled && c.isDark) {
        Modifier.shadow(8.dp, PrimaryShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
    } else {
        Modifier
    }
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp).then(lift),
        enabled = enabled,
        shape = PrimaryShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = c.fzFill,
            contentColor = c.onFz,
            disabledContainerColor = c.high,
            disabledContentColor = c.muted,
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) { ProvideTextStyle(primaryLabel()) { content() } }
}

/** Secondary action drawn as a tonal tile. */
@Composable
fun TonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val c = LocalAndarzColors.current
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = ControlShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = c.high,
            contentColor = c.text,
            disabledContainerColor = c.alt,
            disabledContentColor = c.muted,
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    ) { ProvideTextStyle(controlLabel()) { content() } }
}

/** Secondary action drawn as a ring. */
@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val c = LocalAndarzColors.current
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = ControlShape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = c.text,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = c.muted,
        ),
        border = BorderStroke(1.dp, if (enabled) c.outline else c.divider),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    ) { ProvideTextStyle(controlLabel()) { content() } }
}

/** The action that cannot be undone (delete all data, close the day for good): red-line fill, white text. */
@Composable
fun DestructiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val c = LocalAndarzColors.current
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = ControlShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = c.anFill,
            contentColor = c.onRedlineFill,
            disabledContainerColor = c.high,
            disabledContentColor = c.muted,
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    ) { ProvideTextStyle(controlLabel()) { content() } }
}

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val c = LocalAndarzColors.current
    androidx.compose.material3.TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = c.lapis, disabledContentColor = c.muted),
    ) { ProvideTextStyle(controlLabel()) { content() } }
}

@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = LocalAndarzColors.current
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = { ProvideTextStyle(controlLabel()) { label() } },
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = ChipShape,
        leadingIcon = if (selected) {
            { GlyphIcon(Glyph.CHECK, contentDescription = null, size = FilterChipDefaults.IconSize, strokeWidth = 2.6f) }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = c.text,
            selectedContainerColor = c.fzC,
            selectedLabelColor = c.fz,
            selectedLeadingIconColor = c.fz,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = c.outline,
            selectedBorderColor = c.fz,
            borderWidth = 1.dp,
            selectedBorderWidth = 1.dp,
        ),
    )
}

/**
 * The switch of the canvas: a 52 by 30 dp pill track in a 48 dp tall touch area. On is a turquoise track with a
 * dark thumb at the end edge; off is a `track` pill with an `outline` ring and a grey thumb at the start edge.
 * Colour is not the only signal: the thumb moves, and the semantics carry the Switch role.
 */
@Composable
fun AndarzSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = LocalAndarzColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val reduce = ui().settings.reduceMotion
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(if (reduce) 0 else MotionTokens.FAST),
        label = "switch",
    )
    val dim = if (enabled) 1f else 0.45f
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
    } else {
        Modifier
    }
    Box(
        modifier
            .then(toggle)
            .size(width = 52.dp, height = 48.dp)
            .drawBehind {
                val trackW = 52.dp.toPx()
                val trackH = 30.dp.toPx()
                val top = (size.height - trackH) / 2f
                val r = CornerRadius(trackH / 2f)
                drawRoundRect(if (checked) c.fzFill.copy(alpha = dim) else c.track.copy(alpha = dim), Offset(0f, top), Size(trackW, trackH), r)
                if (!checked) {
                    val w = 1.5.dp.toPx()
                    drawRoundRect(c.outline.copy(alpha = dim), Offset(w / 2f, top + w / 2f), Size(trackW - w, trackH - w), CornerRadius((trackH - w) / 2f), style = Stroke(w))
                }
                val d = 22.dp.toPx()
                val inset = 4.dp.toPx()
                val travel = trackW - d - 2 * inset
                val fromStart = inset + travel * progress
                val x = if (rtl) trackW - fromStart - d else fromStart
                drawRoundRect(
                    color = (if (checked) c.onFz else c.hmuted).copy(alpha = dim),
                    topLeft = Offset(x, top + (trackH - d) / 2f),
                    size = Size(d, d),
                    cornerRadius = CornerRadius(d / 2f),
                )
            },
    )
}

/**
 * A segmented control: one tab per label, the selected one raised to `high` with a top highlight and a short
 * turquoise bar under its label. [selectedIndex] is the single source of truth.
 */
@Composable
fun SegmentedTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAndarzColors.current
    Row(
        modifier
            .archSurface(ArchShapes.arch(20.dp, 14.dp), c.sf1, c.divider)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            val source = remember { MutableInteractionSource() }
            val pressed by source.collectIsPressedAsState()
            val shape = ArchShapes.arch(17.dp, 11.dp)
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .background(
                        when {
                            selected -> c.high
                            pressed -> c.alt
                            else -> Color.Transparent
                        },
                        shape,
                    )
                    .selectable(selected = selected, interactionSource = source, indication = null, role = Role.Tab, onClick = { onSelect(i) })
                    .drawWithContent {
                        drawContent()
                        if (selected) {
                            val barW = 14.dp.toPx()
                            val barH = 3.dp.toPx()
                            drawRoundRect(
                                c.fzG, Offset((size.width - barW) / 2f, size.height - barH - 4.dp.toPx()),
                                Size(barW, barH), CornerRadius(barH / 2f),
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700),
                    color = if (selected) c.text else c.muted,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/**
 * The header of every pushed screen: a back arrow at the start edge (mirrored for RTL, 48 dp, announced with
 * `common.back`), then the screen title as a TalkBack heading with an optional line under it, then optional
 * actions at the end edge. All screens share this one header (D-UI2).
 */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val u = ui()
    val c = LocalAndarzColors.current
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                GlyphIcon(Glyph.ARROW_LEFT, u.t("common.back"), tint = c.text, strokeWidth = 2f)
            }
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = c.muted)
            }
        }
        actions()
    }
}

/**
 * One line of text that shrinks its font (down to [minScale] of the style) until it fits, instead of clipping
 * the last letters. Used by the bottom bar, where five labels share a 320 dp screen at up to 200% font
 * (the clipped final letter of the calculator label, D-93). Below [minScale] it ellipsizes rather than cut a word.
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    minScale: Float = 0.7f,
) {
    var scale by androidx.compose.runtime.remember(text, style) { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var settled by androidx.compose.runtime.remember(text, style) { androidx.compose.runtime.mutableStateOf(false) }
    Text(
        text = text,
        style = style.copy(fontSize = style.fontSize * scale),
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = if (settled) androidx.compose.ui.text.style.TextOverflow.Ellipsis else androidx.compose.ui.text.style.TextOverflow.Clip,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier.drawWithContentIfSettled(settled),
        onTextLayout = { r ->
            if (r.hasVisualOverflow && scale > minScale) scale = (scale - 0.05f).coerceAtLeast(minScale)
            else settled = true
        },
    )
}

/** Skips drawing the frames in which [FitText] is still shrinking, so the clipped label never flashes. */
private fun Modifier.drawWithContentIfSettled(settled: Boolean): Modifier =
    this.drawWithContent { if (settled) drawContent() }
