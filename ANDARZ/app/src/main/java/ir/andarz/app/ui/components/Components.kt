package ir.andarz.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.andarz.app.ui.ui
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.engine.Fmt

enum class Verdict { ALLOWED, DENIED, LOCKED, CAUTION }

@Composable
fun verdictColor(v: Verdict): Color {
    val c = LocalAndarzColors.current
    return when (v) {
        Verdict.ALLOWED -> c.allowed
        Verdict.CAUTION -> c.caution
        Verdict.DENIED, Verdict.LOCKED -> c.redline
    }
}

private fun verdictIcon(v: Verdict): ImageVector = when (v) {
    Verdict.ALLOWED -> Icons.Filled.CheckCircle
    Verdict.CAUTION -> Icons.Filled.Warning
    Verdict.DENIED -> Icons.Filled.Warning
    Verdict.LOCKED -> Icons.Filled.Lock
}

/** 6.4.1 VerdictBanner: tinted container, icon, title, numbers line, expandable why; a polite live region. */
@Composable
fun VerdictBanner(state: Verdict, title: String, numbers: String?, why: List<String> = emptyList(), modifier: Modifier = Modifier) {
    val color = verdictColor(state)
    var open by remember { mutableStateOf(state == Verdict.DENIED) }
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.animateContentSize(),
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(verdictIcon(state), contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = color, modifier = Modifier.weight(1f))
                if (why.isNotEmpty()) TextButton(onClick = { open = !open }) { Text(ui().t(if (open) "common.less" else "common.details")) }
            }
            if (numbers != null) Text(numbers, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            if (open) why.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}

/** 6.4.2 LimitGauge: label, value-of-cap, a 6 dp track, one TalkBack node with a state description. */
@Composable
fun LimitGauge(label: String, valueText: String, ratio: Double, red: Boolean, segments: Int? = null, modifier: Modifier = Modifier) {
    val c = LocalAndarzColors.current
    val u = ui()
    val r = ratio.coerceIn(0.0, 1.0)
    val color = when {
        red -> c.redline
        r >= 2.0 / 3.0 -> c.caution
        else -> c.allowed
    }
    val state = u.t(if (red) "a11y.redState" else if (r >= 2.0 / 3.0) "a11y.cautionState" else "a11y.allowedState")
    Column(
        modifier.heightIn(min = 48.dp).padding(4.dp).semantics(mergeDescendants = true) {
            contentDescription = "$label, $valueText"
            stateDescription = state
        },
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.textMuted)
        Text(valueText, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            val radius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
            drawRoundRect(c.divider, cornerRadius = radius)
            if (segments != null && segments > 0) {
                val gap = 2.dp.toPx()
                val w = (size.width - gap * (segments - 1)) / segments
                val filled = kotlin.math.round(r * segments).toInt()
                // segments fill from the start edge; Canvas runs LTR, so mirror for RTL reading
                for (i in 0 until filled) {
                    val x = size.width - (i + 1) * w - i * gap
                    drawRoundRect(color, topLeft = Offset(x, 0f), size = Size(w, size.height), cornerRadius = radius)
                }
            } else if (r > 0) {
                val w = (size.width * r).toFloat()
                drawRoundRect(color, topLeft = Offset(size.width - w, 0f), size = Size(w, size.height), cornerRadius = radius)
            }
        }
    }
}

/** 6.4.6 LockBanner: anar container, start-edge stripe, lock icon, countdown, one button per need. */
@Composable
fun LockBanner(title: String, body: String, until: String?, actions: List<Pair<String, () -> Unit>>, modifier: Modifier = Modifier) {
    val c = LocalAndarzColors.current
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row {
            Box(Modifier.width(4.dp).heightIn(min = 72.dp).background(c.redline))
            Column(Modifier.padding(16.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = ui().t("a11y.lockedState"), tint = c.redline)
                    Spacer(Modifier.width(8.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium, color = c.redline)
                }
                Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                if (until != null) Text(until, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                actions.forEach { (label, go) -> OutlinedButton(onClick = go, modifier = Modifier.padding(top = 8.dp)) { Text(label) } }
            }
        }
    }
}

/** 6.4.5 RChip: signed R, container by sign, and an arrow so sign is never only color. */
@Composable
fun RChip(r: Double?) {
    val c = LocalAndarzColors.current
    val u = ui()
    val rounded = r?.let { ir.andarz.engine.XlMath.round(it, 2) }
    val color = when {
        rounded == null || rounded == 0.0 -> c.textMuted
        rounded > 0 -> c.allowed
        else -> c.redline
    }
    val arrow = when {
        rounded == null || rounded == 0.0 -> "\u2192"
        rounded > 0 -> "\u25B2"
        else -> "\u25BC"
    }
    Surface(shape = RoundedCornerShape(14.dp), color = color.copy(alpha = 0.14f)) {
        Text(
            "$arrow ${u.rChip(r)}", color = color, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                .semantics { contentDescription = (if ((rounded ?: 0.0) >= 0) u.t("a11y.positive") else u.t("a11y.negative")) + " " + u.rChip(r) },
        )
    }
}

/** A small neutral chip with text. */
@Composable
fun TagChip(text: String, color: Color = LocalAndarzColors.current.textMuted) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.14f)) {
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

/** 6.4.8 SectionCard. */
@Composable
fun SectionCard(title: String?, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null, body: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null || action != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    action?.invoke()
                }
                Spacer(Modifier.height(8.dp))
            }
            body()
        }
    }
}

/** A label/value row; the value sits at the end edge. */
@Composable
fun KeyValue(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = LocalAndarzColors.current.textMuted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

/** 6.4.8 StatTile: value, status chip, target. */
@Composable
fun StatTile(label: String, value: String, status: String?, statusOk: Boolean?, target: String?, modifier: Modifier = Modifier) {
    val c = LocalAndarzColors.current
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = c.textMuted)
            Text(value, style = MaterialTheme.typography.titleLarge)
            if (status != null) TagChip(status, if (statusOk == true) c.allowed else if (statusOk == false) c.caution else c.textMuted)
            if (target != null) Text(target, style = MaterialTheme.typography.labelMedium, color = c.textMuted)
        }
    }
}

/** 6.4.8 EmptyState: icon, one sentence, optional action. */
@Composable
fun EmptyState(text: String, action: Pair<String, () -> Unit>? = null) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = LocalAndarzColors.current.textMuted)
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = LocalAndarzColors.current.textMuted)
        if (action != null) TextButton(onClick = action.second) { Text(action.first) }
    }
}

/**
 * 6.3 NumberField: accepts Persian/Arabic digits and separators while typing; the raw text is kept, the
 * parsed value is shown below when it differs. Parsing is `Fmt.normalizeInput`.
 */
@Composable
fun NumberField(label: String, text: String, onText: (String) -> Unit, modifier: Modifier = Modifier, error: String? = null, suffix: String? = null, hint: String? = null) {
    val u = ui()
    val parsed = Fmt.normalizeInput(text)
    Column(modifier) {
        OutlinedTextField(
            value = text, onValueChange = onText, label = { Text(label) }, singleLine = true, isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            suffix = suffix?.let { { Text(it) } },
            supportingText = when {
                error != null -> { { Text(error) } }
                parsed != null && Fmt.toLatinDigits(text.trim()) != parsed.toString().removeSuffix(".0") -> { { Text(u.num(parsed, 8, trim = true)) } }
                hint != null -> { { Text(hint) } }
                else -> null
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A labelled switch row (48 dp target), exposing on/off to TalkBack. */
@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true, hint: String? = null) {
    val u = ui()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = enabled) { onChange(!checked) }
            .semantics(mergeDescendants = true) { stateDescription = u.t(if (checked) "a11y.toggleOn" else "a11y.toggleOff") },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) Text(hint, style = MaterialTheme.typography.labelMedium, color = LocalAndarzColors.current.textMuted)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** 6.4.8 ReasonField: multiline with the live counter. */
@Composable
fun ReasonField(label: String, text: String, onText: (String) -> Unit, min: Int, counter: (Int) -> String, hint: String? = null) {
    val n = text.trim().length
    OutlinedTextField(
        value = text, onValueChange = onText, label = { Text(label) }, minLines = 3,
        isError = n in 1 until min, placeholder = hint?.let { { Text(it) } },
        supportingText = { Text(counter(n)) }, modifier = Modifier.fillMaxWidth(),
    )
}

/** 6.4.9 KhatamPattern: eight-pointed stars on a 48 dp tile, 1 dp strokes at 5% alpha, static. */
@Composable
fun KhatamPattern(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    Canvas(modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))) {
        val tile = 48.dp.toPx()
        val half = tile * 0.28f
        val stroke = Stroke(width = 1.dp.toPx())
        var y = tile / 2
        while (y < size.height + tile) {
            var x = tile / 2
            while (x < size.width + tile) {
                val sq = Path().apply {
                    moveTo(x - half, y - half); lineTo(x + half, y - half); lineTo(x + half, y + half); lineTo(x - half, y + half); close()
                }
                drawPath(sq, color, style = stroke)
                rotate(45f, Offset(x, y)) { drawPath(sq, color, style = stroke) }
                x += tile
            }
            y += tile
        }
    }
}

