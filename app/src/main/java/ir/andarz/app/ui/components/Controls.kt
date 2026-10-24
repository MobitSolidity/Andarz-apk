package ir.andarz.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui

/*
 * The v2 "Ledger" controls (D-UI2). Same names and the same parameters as the Material 3 functions they
 * wrap, so a screen opts in by importing `ir.andarz.app.ui.components.Button` instead of
 * `androidx.compose.material3.Button`; behaviour, enabled rules and callbacks are untouched.
 *
 * - Button: the one primary action of a view. Solid firouzeh, 52 dp tall, 16 dp corners. Disabled is a
 *   readable muted label on surfaceHigh (M3's default 12% alpha read as invisible on graphite).
 * - OutlinedButton: secondary. A tonal tile (surfaceHigh) with a hairline, like the quick actions of the
 *   reference; never competes with the primary.
 * - TextButton: tertiary, firouzeh label, 48 dp target.
 * - FilterChip: segmented choice. Selected carries a check icon as well as the tint, because colour is
 *   never the only signal (tokens `semantics.rule`).
 */

private val ControlShape = RoundedCornerShape(16.dp)
private val ChipShape = RoundedCornerShape(10.dp)

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val c = LocalAndarzColors.current
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = ControlShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = c.surfaceHigh,
            disabledContentColor = c.textMuted,
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        content = content,
    )
}

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
            containerColor = c.surfaceHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = c.surfaceAlt,
            disabledContentColor = c.textMuted,
        ),
        border = BorderStroke(1.dp, c.divider),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        content = content,
    )
}

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContentColor = LocalAndarzColors.current.textMuted,
        ),
        content = content,
    )
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
        label = label,
        modifier = modifier,
        enabled = enabled,
        shape = ChipShape,
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = c.surfaceAlt,
            labelColor = c.textMuted,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.primary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = c.divider,
            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
            borderWidth = 1.dp,
            selectedBorderWidth = 1.dp,
        ),
    )
}

/**
 * The header of every pushed screen: a back arrow at the start edge (mirrored for RTL, 48 dp, announced
 * with `common.back`), then the screen title as a TalkBack heading, then optional actions at the end edge.
 * It replaces the v1 row of "title + text link back" so all screens share one header (D-UI2).
 */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val u = ui()
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = u.t("common.back"))
            }
            Spacer(Modifier.width(4.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f).padding(vertical = 4.dp).semantics { heading() },
        )
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
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
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
