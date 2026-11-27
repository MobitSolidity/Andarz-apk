package ir.andarz.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ir.andarz.app.ui.theme.LocalAndarzColors

/**
 * A text input in the arch field of the canvas, the same anatomy as [NumberField]: the label above in muted
 * type, the text below. Focus draws a 2 dp lapis ring 3 dp outside; an [error] turns the ring red and adds the
 * message under the field. The label and the text are one TalkBack node.
 */
@Composable
fun AndarzTextField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val k = LocalAndarzColors.current
    val message = error
    var focused by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth()
                .onFocusChanged { focused = it.hasFocus }
                .drawBehind {
                    if (focused) {
                        val g = 3.dp.toPx()
                        drawRoundRect(k.lapis, Offset(-g, -g), Size(size.width + 2 * g, size.height + 2 * g), CornerRadius(22.dp.toPx()), style = Stroke(2.dp.toPx()))
                    }
                }
                .archSurface(ArchShapes.field, k.alt, if (message != null) k.an else k.outline)
                .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 8.dp)
                .semantics(mergeDescendants = true) { if (message != null) error(message) },
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = k.muted)
            Box(Modifier.fillMaxWidth().heightIn(min = 30.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && placeholder != null) {
                    Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = k.hmuted)
                }
                BasicTextField(
                    value = value, onValueChange = onValue, singleLine = singleLine, minLines = minLines,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = k.text),
                    keyboardOptions = keyboardOptions,
                    cursorBrush = SolidColor(k.lapis),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (message != null) {
            Text(message, style = MaterialTheme.typography.labelMedium, color = k.an, modifier = Modifier.padding(start = 14.dp, top = 4.dp))
        }
    }
}
