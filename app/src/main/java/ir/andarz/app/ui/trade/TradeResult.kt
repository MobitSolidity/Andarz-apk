package ir.andarz.app.ui.trade

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.components.ArchTile
import ir.andarz.app.ui.components.FitText
import ir.andarz.app.ui.components.KeyValue
import ir.andarz.app.ui.components.OutlinedButton
import ir.andarz.app.ui.components.SectionCard
import ir.andarz.app.ui.components.Text
import ir.andarz.app.ui.components.TextButton
import ir.andarz.app.ui.components.archSurface
import ir.andarz.app.ui.components.figure
import ir.andarz.app.ui.icons.Glyph
import ir.andarz.app.ui.icons.GlyphIcon
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.data.db.TradeEntity
import ir.andarz.data.db.TradeEventEntity
import ir.andarz.engine.Side
import kotlin.math.abs

/*
 * The page of a closed trade (board "Result"). The same neutral layout for a win and a loss (law 8): a ruler that
 * shows where the exit fell between the stop and the target, the receipt of the net result, and one line on
 * whether the rules were kept. No animation, no confetti, no streak.
 */

/** The weighted average price of the exit legs, or null when none is recorded. */
internal fun avgExit(t: TradeEntity): Double? {
    val f = t.legs.sumOf { it.fraction }
    return if (f > 0) t.legs.sumOf { it.price * it.fraction } / f else null
}

@Composable
internal fun ResultPage(
    u: Ui,
    t: TradeEntity,
    events: List<TradeEventEntity>,
    setupName: String?,
    onBack: () -> Unit,
    onReview: () -> Unit,
    onDelete: () -> Unit,
) {
    val k = LocalAndarzColors.current
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        TradeHeader(
            u, onBack = onBack,
            menu = listOf(
                MenuEntry(u.t("trade.shareNote")) { shareNote(ctx, u, t, setupName) },
                MenuEntry(u.t("common.delete"), danger = true, onClick = onDelete),
            ),
        ) {
            Text(u.t("app.trade.result.title"), style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), maxLines = 1)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Identity(u, t)
            Ruler(u, t)
            Receipt(u, t)
            Compliance(u, t)
            Text(u.t("result.rVsInitial"), style = MaterialTheme.typography.labelMedium, color = k.muted)
            if (t.reviewedAt != null) {
                SectionCard(u.t("trade.section.review")) {
                    KeyValue(u.t("review.mistake"), u.enumLabel("mistake", t.mistake))
                    t.lesson?.takeIf { it.isNotBlank() }?.let { KeyValue(u.t("review.lesson"), it) }
                    if (t.violations.isNotEmpty()) {
                        KeyValue(u.t("review.violations"), t.violations.joinToString("\u060C ") { u.enumLabel("violation", it) })
                    }
                }
            }
            PlanGrid(u, t)
            EventsCard(u, t, events, onAdd = null)
        }
        Column(
            Modifier.fillMaxWidth().background(k.sf1)
                .drawBehind { drawLine(k.divider, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (t.reviewedAt == null) {
                FilledAction(onReview, Modifier.fillMaxWidth(), height = 56.dp, radius = 18.dp) { Text(u.t("app.trade.review.cta")) }
                TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(u.t("review.skip")) }
            } else {
                OutlinedButton(onClick = onReview, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(u.t("app.trade.review.edit")) }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------

@Composable
private fun Identity(u: Ui, t: TradeEntity) {
    val k = LocalAndarzColors.current
    val exit = avgExit(t)
    val line = listOfNotNull(
        t.entry?.let { u.t("app.trade.result.entry", "price" to u.price(it)) },
        exit?.let { u.t("app.trade.result.exit", "price" to u.price(it)) },
        t.exitReason?.let { u.t("close.reason.$it") },
    ).joinToString(DOT)
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(u.symbol(t.symbol), style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp), maxLines = 1)
            SidePill(u, t.side)
            Pill(u.t("app.trade.closed"), k.high, k.muted)
        }
        if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.labelMedium, color = k.muted)
    }
}

/**
 * Where the exit fell: the stop zone on the left, the target zone on the right (always in this order, whatever the
 * side), the entry as a tick, the exit as a dot. The scale is R, so the zones show the planned reward to risk.
 */
@Composable
private fun Ruler(u: Ui, t: TradeEntity) {
    val k = LocalAndarzColors.current
    val entry = t.entry ?: return
    val exit = avgExit(t) ?: return
    val slDist = abs(entry - t.slInitial)
    val tpDist = abs(t.tp - entry)
    if (slDist <= 0.0 || tpDist <= 0.0) return
    val sign = if (Side.valueOf(t.side) == Side.LONG) 1.0 else -1.0
    val rr = tpDist / slDist
    val rExit = sign * (exit - entry) / slDist
    val slFrac = (1.0 / (rr + 1.0)).toFloat()
    val exitFrac = ((rExit + 1.0) / (rr + 1.0)).coerceIn(0.0, 1.0).toFloat()
    val description = u.t(
        "app.trade.ruler.desc",
        "sl" to u.price(t.slInitial), "entry" to u.price(entry), "tp" to u.price(t.tp), "exit" to u.price(exit),
    )
    // the ruler reads left to right in every language: stop, entry, target
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(78.dp).clearAndSetSemantics { contentDescription = description; role = Role.Image }) {
            val w = maxWidth
            val pad = 16.dp
            val barW = w - pad * 2
            val entryX = pad + barW * slFrac
            val exitX = pad + barW * exitFrac
            Canvas(Modifier.fillMaxSize()) {
                val left = pad.toPx()
                val width = barW.toPx()
                val top = 30.dp.toPx()
                val h = 12.dp.toPx()
                val r = 6.dp.toPx()
                val ex = left + width * slFrac
                val risk = Path().apply {
                    addRoundRect(RoundRect(left, top, ex, top + h, CornerRadius(r), CornerRadius(0f), CornerRadius(0f), CornerRadius(r)))
                }
                val reward = Path().apply {
                    addRoundRect(RoundRect(ex, top, left + width, top + h, CornerRadius(0f), CornerRadius(r), CornerRadius(r), CornerRadius(0f)))
                }
                drawPath(risk, k.anC)
                drawPath(risk, k.anG, style = Stroke(1.5.dp.toPx()))
                drawPath(reward, k.fzC)
                drawPath(reward, k.fzG, style = Stroke(1.5.dp.toPx()))
                // the entry tick
                drawLine(k.text, Offset(ex, top - 5.dp.toPx()), Offset(ex, top + h + 5.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                // the exit dot with its two rings
                val cx = left + width * exitFrac
                val cy = top + h / 2f
                drawCircle(k.outline, 15.5.dp.toPx(), Offset(cx, cy))
                drawCircle(k.bg, 14.dp.toPx(), Offset(cx, cy))
                drawCircle(k.text, 10.dp.toPx(), Offset(cx, cy))
            }
            val labelW = 66.dp
            RulerLabel(u.t("app.trade.ruler.exit"), null, exitX - labelW / 2, 0.dp, labelW, k.text, strongTitle = true)
            RulerLabel(u.t("app.wizard.sl"), u.price(t.slInitial), 4.dp, 52.dp, labelW, k.an, align = Alignment.Start)
            // the entry label keeps clear of the stop label and the target label at either end
            val lo = 4.dp + labelW + 4.dp + labelW / 2
            val hi = maxOf(lo, w - 4.dp - labelW - 4.dp - labelW / 2)
            RulerLabel(u.t("app.wizard.ladder.entry"), u.price(entry), maxOf(lo, minOf(hi, entryX)) - labelW / 2, 52.dp, labelW, k.text)
            RulerLabel(u.t("app.wizard.tp"), u.price(t.tp), w - 4.dp - labelW, 52.dp, labelW, k.fz, align = Alignment.End)
        }
    }
}

@Composable
private fun RulerLabel(
    title: String,
    price: String?,
    x: Dp,
    y: Dp,
    width: Dp,
    color: Color,
    align: Alignment.Horizontal = Alignment.CenterHorizontally,
    strongTitle: Boolean = false,
) {
    val k = LocalAndarzColors.current
    val textAlign = when (align) {
        Alignment.Start -> TextAlign.Start
        Alignment.End -> TextAlign.End
        else -> TextAlign.Center
    }
    Column(Modifier.absoluteOffset(x = x, y = y).width(width), horizontalAlignment = align) {
        Text(
            title, style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (strongTitle) FontWeight.W700 else FontWeight.W400),
            color = if (strongTitle) color else k.muted, maxLines = 1, textAlign = textAlign, modifier = Modifier.fillMaxWidth(),
        )
        if (price != null) FitText(price, figure(13), color = color, minScale = 0.7f, textAlign = textAlign, modifier = Modifier.fillMaxWidth())
    }
}

/** The receipt of the net result: scalloped top edge, the net with its R, a perforation with notches, the rows. */
@Composable
private fun Receipt(u: Ui, t: TradeEntity) {
    val k = LocalAndarzColors.current
    val shape = RoundedCornerShape(24.dp)
    val net = t.net
    val gross = t.gross
    Column(Modifier.fillMaxWidth().archSurface(shape, k.alt, Color.Transparent, k.edge, clipContent = true)) {
        // a row of arches along the top edge: 28 dp pitch, 18 dp high
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            val pitch = 28.dp.toPx()
            val stroke = Stroke(1.5.dp.toPx())
            var x0 = 0f
            while (x0 < size.width) {
                val arch = Path().apply {
                    moveTo(x0 + 3.dp.toPx(), 18.dp.toPx())
                    lineTo(x0 + 3.dp.toPx(), 10.dp.toPx())
                    arcTo(androidx.compose.ui.geometry.Rect(x0 + 3.dp.toPx(), 1.dp.toPx(), x0 + 25.dp.toPx(), 19.dp.toPx()), 180f, 180f, false)
                    lineTo(x0 + 25.dp.toPx(), 18.dp.toPx())
                }
                drawPath(arch, k.outline.copy(alpha = 0.45f), style = stroke)
                x0 += pitch
            }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(u.t("app.trade.receipt.net"), style = MaterialTheme.typography.labelMedium, color = k.muted)
            Row(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FitText(u.signedMoney(net), figure(38, FontWeight.W600), color = k.text, minScale = 0.6f, textAlign = TextAlign.Start, modifier = Modifier.weight(1f))
                val r = t.r
                Row(
                    Modifier.heightIn(min = 28.dp).background(k.high, RoundedCornerShape(50)).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (r != null) GlyphIcon(if (r >= 0) Glyph.TRI_UP else Glyph.TRI_DOWN, null, tint = k.muted, size = 11.dp)
                    Text(u.rChip(r), style = figure(14), color = k.text)
                }
            }
        }
        // the perforation: a dashed line with a notch in each side edge
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            val y = size.height / 2f
            drawLine(
                k.outline, Offset(14.dp.toPx(), y), Offset(size.width - 14.dp.toPx(), y), strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()), 0f),
            )
            drawCircle(k.bg, 9.dp.toPx(), Offset(0f, y))
            drawCircle(k.bg, 9.dp.toPx(), Offset(size.width, y))
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)) {
            if (gross != null) ReceiptRow(u.t("app.trade.receipt.gross"), u.signedMoney(gross))
            if (gross != null && net != null) ReceiptRow(u.t("app.trade.receipt.fees"), u.signedMoney(net - gross))
            ReceiptRow(u.t("app.trade.receipt.r"), u.rChip(t.r))
            ReceiptRow(u.t("app.trade.receipt.equity"), u.money(t.equityAfter))
        }
    }
}

@Composable
private fun ReceiptRow(label: String, value: String) {
    val k = LocalAndarzColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = k.muted, modifier = Modifier.weight(1f))
        Text(value, style = figure(14), color = k.text, maxLines = 1)
    }
}

/** Were the rules kept: one verdict with its three checks as chips, a glyph and a word each. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Compliance(u: Ui, t: TradeEntity) {
    val k = LocalAndarzColors.current
    val ok = t.ok == true
    val sizeOk = t.verdict != "OVER_CAP" && t.verdict != "RED_LINE"
    val violationFree = t.violations.isEmpty()
    Row(
        Modifier.fillMaxWidth().panel().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArchTile(if (ok) k.fzC else k.anC, width = 40.dp, height = 48.dp, bottom = 9.dp) {
            GlyphIcon(if (ok) Glyph.CHECK else Glyph.X, null, tint = if (ok) k.fz else k.an, size = 22.dp, strokeWidth = 2.4f)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                u.t(if (ok) "app.trade.rules.ok" else "app.trade.rules.no"),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, fontWeight = FontWeight.W700), color = if (ok) k.fz else k.an,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Check(u.t(if (t.gateAllowed) "app.trade.chip.gate.ok" else "app.trade.chip.gate.no"), t.gateAllowed)
                Check(u.t(if (sizeOk) "app.trade.chip.size.ok" else "app.trade.chip.size.no"), sizeOk)
                Check(u.t(if (violationFree) "app.trade.chip.viol.ok" else "app.trade.chip.viol.no"), violationFree)
            }
            if (ok && (t.net ?: 0.0) < 0) Text(u.t("result.goodLoss"), style = MaterialTheme.typography.bodySmall, color = k.muted)
            if (!ok && (t.net ?: 0.0) > 0) Text(u.t("result.badWin"), style = MaterialTheme.typography.bodySmall, color = k.muted)
        }
    }
}

@Composable
private fun Check(text: String, ok: Boolean) {
    val k = LocalAndarzColors.current
    Pill(text, if (ok) k.fzC else k.anC, if (ok) k.fz else k.an, if (ok) Glyph.CHECK else Glyph.X)
}
