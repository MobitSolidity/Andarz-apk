package ir.andarz.app.ui.components

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import ir.andarz.app.ui.theme.AndarzFonts

/*
 * The one Text of the app. It has the signature of `androidx.compose.material3.Text`, so a screen opts in by
 * importing `ir.andarz.app.ui.components.Text`, and it does one extra thing: every run of numerals and every
 * run of Latin letters (prices, R values, tickers) is drawn in IBM Plex Sans Arabic with tabular figures,
 * while the Persian text around it stays in Peyda. That is the numeral rule of the design canvas
 * (class `.n`), applied once here instead of at every call site.
 */

private const val DIGIT = "0-9\\u0660-\\u0669\\u06F0-\\u06F9"

/**
 * A number with its sign or currency in front, its separators inside (decimal, thousands, colon, slash,
 * dot, comma) and its unit behind (percent, multiplication sign), or a run of Latin letters.
 */
private val numeralRun = Regex(
    "[+\\-\\u2212\\$]?[$DIGIT]+(?:[\\u066B\\u066C.,:/][$DIGIT]+)*[%\\u066A\\u00D7]?|[A-Za-z]+",
)

/** The character ranges of [text] that belong to the numeral family. Exposed for the unit test. */
internal fun numeralRanges(text: String): List<IntRange> {
    if (text.none { it in '0'..'9' || it in '٠'..'٩' || it in '۰'..'۹' || it in 'A'..'Z' || it in 'a'..'z' }) return emptyList()
    return numeralRun.findAll(text).map { it.range }.toList()
}

private val numeralStyle = SpanStyle(fontFamily = AndarzFonts.plex, fontFeatureSettings = "tnum")

/** [text] with its numeral runs drawn in Plex. Plain text without digits or Latin letters is returned as is. */
internal fun withNumerals(text: String): AnnotatedString {
    val ranges = numeralRanges(text)
    if (ranges.isEmpty()) return AnnotatedString(text)
    return AnnotatedString.Builder(text.length).apply {
        append(text)
        ranges.forEach { addStyle(numeralStyle, it.first, it.last + 1) }
    }.toAnnotatedString()
}

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    // Naskh carries the advice quote only; its own figures stay as they are.
    val effective = fontFamily ?: style.fontFamily
    val shaped = if (effective === AndarzFonts.naskh) null else remember(text) { withNumerals(text) }
    if (shaped == null) {
        androidx.compose.material3.Text(
            text = text, modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
            fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
            lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
            onTextLayout = onTextLayout, style = style,
        )
    } else {
        androidx.compose.material3.Text(
            text = shaped, modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
            fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
            lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
            onTextLayout = onTextLayout ?: {}, style = style,
        )
    }
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) {
    androidx.compose.material3.Text(
        text = text, modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
        fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
        lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
        inlineContent = inlineContent, onTextLayout = onTextLayout, style = style,
    )
}
