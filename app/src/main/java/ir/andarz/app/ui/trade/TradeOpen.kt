package ir.andarz.app.ui.trade

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.components.BottomBar
import ir.andarz.app.ui.components.DottedRow
import ir.andarz.app.ui.components.FitText
import ir.andarz.app.ui.components.NoticeBar
import ir.andarz.app.ui.components.NoticeTone
import ir.andarz.app.ui.components.NumberField
import ir.andarz.app.ui.components.OutlinedButton
import ir.andarz.app.ui.components.Text
import ir.andarz.app.ui.components.archRing
import ir.andarz.app.ui.components.archSurface
import ir.andarz.app.ui.components.figure
import ir.andarz.app.ui.icons.Glyph
import ir.andarz.app.ui.icons.GlyphIcon
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.data.db.TradeEntity
import ir.andarz.data.db.TradeEventEntity
import ir.andarz.engine.Fmt
import ir.andarz.engine.Leg
import ir.andarz.engine.Management
import ir.andarz.engine.Params
import ir.andarz.engine.Side
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/*
 * The page of an open trade (board "TradeDetail"): the price ladder beside the floating result, the time stop
 * with its check, the numbers of the plan, the events, and a bar with Manage and Close. The current price is typed
 * by the user (a copy aid from the Market module may fill it); nothing about it is stored (D-M06).
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OpenPage(
    u: Ui,
    vm: TradeViewModel,
    t: TradeEntity,
    events: List<TradeEventEntity>,
    p: Params,
    setupName: String?,
    failed: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onDelete: () -> Unit,
) {
    val k = LocalAndarzColors.current
    val ctx = LocalContext.current
    val side = Side.valueOf(t.side)
    val entry = t.entry ?: return
    val notional = t.notional ?: return
    val entryTime = t.entryTime ?: return
    var price by rememberSaveable { mutableStateOf("") }
    var managing by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf<String?>(null) }
    val now by produceState(Instant.now()) { while (true) { delay(30_000); value = Instant.now() } }

    val px = Fmt.normalizeInput(price)?.takeIf { it > 0 }
    val legs = remember(t.legs) { t.legs.map { Leg(it.price, it.fraction) } }
    val sign = if (side == Side.LONG) 1.0 else -1.0
    val slDist = abs(entry - t.slInitial)
    val rr = if (slDist > 0) abs(t.tp - entry) / slDist else (t.plan.rr ?: 1.0)
    val rNow = if (px != null && slDist > 0) sign * (px - entry) / slDist else null
    val slNowR = if (slDist > 0 && t.slCurrent != null) sign * (t.slCurrent!! - entry) / slDist else -1.0
    val floating = px?.let { Management.floating(side, entry, t.slInitial, notional, legs, it) }

    val subtitle = listOfNotNull(
        if (t.account == "DEMO") u.t("trade.account.DEMO") else null,
        if (t.imported) u.t("trade.imported") else null,
        setupName,
        u.t("app.trade.entryAt", "t" to u.time(Instant.ofEpochMilli(entryTime))),
        ago(u, Duration.between(Instant.ofEpochMilli(entryTime), now).toMinutes()),
    ).joinToString(DOT)

    Column(Modifier.fillMaxSize()) {
        TradeHeader(
            u, onBack = onBack, subtitle = subtitle,
            menu = listOf(
                MenuEntry(u.t("trade.shareNote")) { shareNote(ctx, u, t, setupName) },
                MenuEntry(u.t("common.delete"), danger = true, onClick = onDelete),
            ),
        ) {
            Text(u.symbol(t.symbol), style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), maxLines = 1)
            SidePill(u, t.side)
            StatusPill(u, t.status)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (failed) NoticeBar(u.t("err.generic"), NoticeTone.DANGER)
            NumberField(u.t("trade.currentPrice"), price, { price = it }, hint = u.t("trade.currentPrice.hint"))
            ir.andarz.app.ui.market.LivePriceChip(t.symbol, { price = it })
            val r = floating?.second
            if (r != null && r >= p.partialRLo && t.legs.isEmpty()) NoticeBar(u.t("trade.partial.due", "r" to u.num(r)), NoticeTone.CAUTION)
            if (r != null && r >= p.trailLockR) NoticeBar(u.t("trade.greed"), NoticeTone.CAUTION)

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val wide = maxWidth >= 360.dp && LocalDensity.current.fontScale <= 1.15f
                if (wide) {
                    Row(Modifier.fillMaxWidth().height(204.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LadderCard(u, t, rr, rNow, slNowR, px, Modifier.width(156.dp).fillMaxHeight())
                        FloatingCard(u, t, floating, rNow, rr, Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LadderCard(u, t, rr, rNow, slNowR, px, Modifier.fillMaxWidth().height(204.dp))
                        FloatingCard(u, t, floating, rNow, rr, Modifier.fillMaxWidth())
                    }
                }
            }

            TimeStopCard(u, t, p, now, side, onCheck = { vm.monitor() })
            PlanGrid(u, t)
            ir.andarz.app.ui.market.TradeLinkCard(t, p)
            EventsCard(u, t, events, onAdd = { adding = it })
        }
        BottomBar {
            OutlinedButton(onClick = { managing = true }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                GlyphIcon(Glyph.SLIDERS, null, size = 20.dp, strokeWidth = 2f)
                Spacer(Modifier.width(8.dp))
                Text(u.t("trade.section.management"))
            }
            InvertedButton(onClick = onClose, modifier = Modifier.weight(1.4f)) { Text(u.t("trade.close")) }
        }
    }

    if (managing) ModalBottomSheet(onDismissRequest = { managing = false }) { ManageSheet(u, vm, t, p, price) }
    adding?.let { kind -> ModalBottomSheet(onDismissRequest = { adding = null }) { AddEventSheet(u, vm, kind) { adding = null } } }
}

// ---------------------------------------------------------------------------------------------------------------
// The ladder
// ---------------------------------------------------------------------------------------------------------------

private const val LIQ_NONE = 0
private const val LIQ_NEAR = 1
private const val LIQ_FAR = 2

/** Where the lines sit on the 40 by 200 grid of the board: reward above the entry, the risk R below it, 39 units per R at most. */
private class Ladder(
    val tpY: Float, val entryY: Float, val slY: Float, val slNowY: Float, val curY: Float?,
    val liqMode: Int, val liqY: Float, val liqInside: Boolean,
)

private fun ladder(rr: Double, rNow: Double?, slNowR: Double, liqX: Double?): Ladder {
    val r = rr.coerceIn(0.3, 7.0).toFloat()
    val scale = minOf(39.35f, 118f / (r + 1f))
    val tpY = 22f
    val entryY = tpY + r * scale
    val slY = entryY + scale
    val slNowY = (entryY - slNowR.toFloat() * scale).coerceIn(tpY, slY)
    val curY = rNow?.let { (entryY - it.toFloat() * scale).coerceIn(tpY, slY) }
    if (liqX == null) return Ladder(tpY, entryY, slY, slNowY, curY, LIQ_NONE, 0f, false)
    val gap = maxOf((liqX.toFloat() - 1f) * scale, 36f)
    return if (gap <= 50f) Ladder(tpY, entryY, slY, slNowY, curY, LIQ_NEAR, slY + gap, liqX < 1.0)
    else Ladder(tpY, entryY, slY, slNowY, curY, LIQ_FAR, maxOf(190f, slY + 50f), false)
}

private class Lab(val y: Float, val height: Float, val color: Color, val first: String, val second: String?, val strong: Boolean)

@Composable
private fun LadderCard(u: Ui, t: TradeEntity, rr: Double, rNow: Double?, slNowR: Double, px: Double?, modifier: Modifier) {
    val k = LocalAndarzColors.current
    val entry = t.entry ?: return
    val g = remember(rr, rNow, slNowR, t.plan.liqX) { ladder(rr, rNow, slNowR, t.plan.liqX) }
    val slMoved = abs(g.slNowY - g.slY) > 0.5f
    val liq = t.plan.liq
    val sl = t.slCurrent ?: t.slInitial
    val now = if (px != null && rNow != null) u.t("app.trade.ladder.nowDesc", "price" to u.price(px), "r" to u.rChip(rNow)) else ""
    val description = u.t(
        "app.trade.ladder.desc",
        "tp" to u.price(t.tp), "tpR" to u.rChip(rr), "entry" to u.price(entry), "sl" to u.price(sl), "slR" to u.rChip(slNowR),
        "liq" to u.price(liq), "now" to now,
    )
    val labels = buildList<Lab> {
        add(Lab(g.tpY, 34f, k.fz, u.t("app.wizard.tp.short") + " " + u.price(t.tp), u.rChip(rr), true))
        if (px != null && g.curY != null) add(Lab(g.curY, 17f, k.text, u.t("app.trade.ladder.now") + " " + u.price(px), null, true))
        add(Lab(g.entryY, 17f, k.muted, u.t("app.wizard.ladder.entry") + " " + u.price(entry), null, false))
        add(Lab(g.slNowY, 34f, k.an, u.t("app.wizard.sl.short") + " " + u.price(sl), u.rChip(if (slMoved) slNowR else -1.0), true))
        if (g.liqMode != LIQ_NONE && liq != null) add(Lab(g.liqY, 17f, k.an, u.t("app.trade.liq") + " " + u.price(liq), null, true))
    }.sortedBy { it.y - it.height / 2f }
    val density = LocalDensity.current
    val fixed = remember(density.density) { Density(density.density, 1f) }
    Box(
        modifier.panel().clearAndSetSemantics { contentDescription = description; role = Role.Image },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(Modifier.size(width = 156.dp, height = 204.dp)) {
            LadderCanvas(g, rNow, Modifier.padding(start = 6.dp, top = 2.dp).size(width = 40.dp, height = 200.dp))
            // A diagram with fixed geometry keeps a fixed text size; every figure in it is also in the floating card,
            // in the grid below and in the description of the whole ladder.
            CompositionLocalProvider(LocalDensity provides fixed) {
                var floor = 0f
                labels.forEach { l ->
                    val top = maxOf(l.y - l.height / 2f + 2f, floor)
                    floor = top + l.height + 1f
                    Column(Modifier.offset(x = 52.dp, y = top.dp).width(98.dp)) {
                        FitText(
                            l.first,
                            MaterialTheme.typography.labelMedium.copy(fontWeight = if (l.strong) FontWeight.W700 else FontWeight.W400, lineHeight = 17.sp),
                            color = l.color, textAlign = TextAlign.Start,
                        )
                        if (l.second != null) {
                            FitText(l.second, MaterialTheme.typography.labelMedium.copy(lineHeight = 17.sp), color = k.muted, textAlign = TextAlign.Start)
                        }
                    }
                }
            }
        }
    }
}

/** The arches of the reward and the risk, the lines, the floating fill and the dot of the current price. */
@Composable
private fun LadderCanvas(g: Ladder, rNow: Double?, modifier: Modifier) {
    val k = LocalAndarzColors.current
    Canvas(modifier) {
        val s = size.width / 40f
        val zoneStroke = Stroke(width = 1.5f * s, join = StrokeJoin.Round)
        val dash = PathEffect.dashPathEffect(floatArrayOf(2f * s, 4f * s), 0f)
        fun line(color: Color, x1: Float, y1: Float, x2: Float, y2: Float, width: Float, effect: PathEffect? = null) {
            drawLine(color, Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), strokeWidth = width * s, cap = StrokeCap.Round, pathEffect = effect)
        }

        val reward = Path().apply {
            moveTo(10f * s, g.entryY * s)
            lineTo(10f * s, (g.tpY + 10f) * s)
            arcTo(Rect(10f * s, g.tpY * s, 30f * s, (g.tpY + 20f) * s), 180f, 180f, false)
            lineTo(30f * s, g.entryY * s)
            close()
        }
        drawPath(reward, k.fzC)
        drawPath(reward, k.fzG, style = zoneStroke)

        val risk = Path().apply {
            moveTo(10f * s, g.entryY * s)
            lineTo(30f * s, g.entryY * s)
            lineTo(30f * s, (g.slY - 10f) * s)
            arcTo(Rect(10f * s, (g.slY - 20f) * s, 30f * s, g.slY * s), 0f, 180f, false)
            close()
        }
        drawPath(risk, k.anC)
        drawPath(risk, k.anG, style = zoneStroke)

        // the floating result: a tint between the entry and the current price
        val cur = g.curY
        if (cur != null && rNow != null && abs(cur - g.entryY) > 0.5f) {
            val up = cur < g.entryY
            val top = minOf(cur, g.entryY)
            val bottom = maxOf(cur, g.entryY)
            drawRect(
                (if (up) k.fzG else k.anG).copy(alpha = 0.42f),
                topLeft = Offset(10.8f * s, top * s), size = androidx.compose.ui.geometry.Size(18.4f * s, (bottom - top) * s),
            )
        }

        line(k.fzG, 5f, g.tpY, 35f, g.tpY, 1.5f)
        line(k.muted, 5f, g.entryY, 35f, g.entryY, 2f)
        line(k.anG, 5f, g.slNowY, 35f, g.slNowY, 1.5f)
        if (cur != null) {
            drawCircle(k.sf1, 6.5f * s, Offset(20f * s, cur * s))
            drawCircle(k.text, 3.5f * s, Offset(20f * s, cur * s))
        }

        fun diamond(cy: Float) {
            val shape = Path().apply {
                moveTo(20f * s, (cy - 6f) * s)
                lineTo(25.5f * s, cy * s)
                lineTo(20f * s, (cy + 6f) * s)
                lineTo(14.5f * s, cy * s)
                close()
            }
            drawPath(shape, if (g.liqInside) k.an else k.sf1)
            drawPath(shape, k.anG, style = Stroke(width = 2f * s, join = StrokeJoin.Round))
        }
        when (g.liqMode) {
            LIQ_NEAR -> {
                line(k.anG, 20f, g.slY + 2.2f, 20f, g.liqY - 8f, 2f, dash)
                diamond(g.liqY)
            }
            LIQ_FAR -> {
                val mid = (g.slY + 3f + g.liqY - 8f) / 2f - 0.75f
                line(k.anG, 20f, g.slY + 3f, 20f, mid - 7f, 2f, dash)
                line(k.muted, 13f, mid, 27f, mid - 4.5f, 1.5f)
                line(k.muted, 13f, mid + 6f, 27f, mid + 1.5f, 1.5f)
                line(k.anG, 20f, mid + 9f, 20f, g.liqY - 9f, 2f, dash)
                diamond(g.liqY)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// The floating result
// ---------------------------------------------------------------------------------------------------------------

@Composable
private fun FloatingCard(u: Ui, t: TradeEntity, floating: Pair<Double, Double?>?, rNow: Double?, rr: Double, modifier: Modifier) {
    val k = LocalAndarzColors.current
    val pnl = floating?.first
    val r = floating?.second
    val tone = when {
        r == null || abs(r) < 0.005 -> k.text
        r > 0 -> k.fz
        else -> k.an
    }
    Column(modifier.panel().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(u.t("app.trade.floating"), style = MaterialTheme.typography.labelMedium, color = k.muted)
        if (r == null || pnl == null) {
            Spacer(Modifier.height(6.dp))
            Text(u.t("app.trade.floating.empty"), style = MaterialTheme.typography.bodySmall, color = k.muted)
        } else {
            val said = u.t(if (r >= 0) "a11y.positive" else "a11y.negative") + " " + u.rChip(r) + " " + u.signedMoney(pnl)
            Column(Modifier.semantics(mergeDescendants = true) { contentDescription = said }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlyphIcon(if (r >= 0) Glyph.TRI_UP else Glyph.TRI_DOWN, null, tint = tone, size = 14.dp)
                    FitText(u.rChip(r), figure(36, FontWeight.W600), color = tone, minScale = 0.55f, textAlign = TextAlign.Start, modifier = Modifier.weight(1f))
                }
                FitText(u.signedMoney(pnl), figure(17), color = tone, minScale = 0.7f, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(8.dp))
        Spacer(Modifier.weight(1f))
        DottedRow(
            u.t("app.trade.toTp"), if (rNow != null) u.rChip(rr - rNow) else u.t("common.dash"),
            valueStyle = figure(12), valueColor = k.fz, height = 26.dp,
        )
        DottedRow(
            u.t("app.trade.toSl"), if (rNow != null) u.rChip(-1.0 - rNow) else u.t("common.dash"),
            valueStyle = figure(12), valueColor = k.an, height = 26.dp,
        )
        DottedRow(u.t("app.trade.risk0"), u.money(t.rrisk), valueStyle = figure(12), height = 26.dp)
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Time stop and monitoring
// ---------------------------------------------------------------------------------------------------------------

@Composable
private fun TimeStopCard(u: Ui, t: TradeEntity, p: Params, now: Instant, side: Side, onCheck: () -> Unit) {
    val k = LocalAndarzColors.current
    val entry = t.entry ?: return
    val entryTime = t.entryTime ?: return
    val ts = Management.timeStop(side, entry, t.slCurrent, Instant.ofEpochMilli(entryTime), t.timeframe, t.legs.isNotEmpty(), now, p)
    val total = p.timeStopCandles.toInt().coerceIn(1, 24)
    val left = ts.left.toInt().coerceIn(0, total)
    val last = t.monitorChecks.maxOrNull()
    val bar = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomEnd = 4.dp, bottomStart = 4.dp)
    Column(Modifier.fillMaxWidth().panel().padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(u.t("trade.timeStop"), style = MaterialTheme.typography.labelMedium, color = k.muted, modifier = Modifier.weight(1f))
            Text(
                if (ts.due) u.t("app.trade.candlesDue") else u.t("app.trade.candlesLeft", "n" to u.int(left)),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700),
                color = if (ts.due) k.sf else k.text,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().height(16.dp).clearAndSetSemantics {
                contentDescription = u.t("app.trade.timeStop.desc", "left" to u.int(left), "total" to u.int(total)); role = Role.Image
            },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (i in 0 until total) {
                val spent = i < total - left
                Box(
                    Modifier.weight(1f).fillMaxHeight().then(
                        if (spent) Modifier.archRing(bar, k.outline, 1.5.dp) else Modifier.background(k.lapis, bar),
                    ),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().drawBehind { drawLine(k.divider, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (last != null) u.t("app.trade.monitor.last", "t" to u.time(Instant.ofEpochMilli(last))) else u.t("app.trade.monitor.none"),
                style = MaterialTheme.typography.labelMedium, color = k.muted, modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            )
            Row(
                Modifier.heightIn(min = 48.dp)
                    .archSurface(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 12.dp, bottomStart = 12.dp), k.fzC, k.ringAllowed)
                    .clickable(role = Role.Button, onClickLabel = u.t("trade.monitor.check"), onClick = onCheck)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlyphIcon(Glyph.CHECK, null, tint = k.fz, size = 16.dp, strokeWidth = 2.6f)
                Text(u.t("trade.monitor.check"), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700), color = k.fz)
            }
        }
        if (ts.due) {
            Spacer(Modifier.height(4.dp))
            Text(
                u.t("trade.timeStop.due", "n" to u.int(p.timeStopCandles.toInt())),
                style = MaterialTheme.typography.labelMedium, color = k.sf, modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// The numbers of the plan, in two columns of dotted rows (one column at large font sizes)
// ---------------------------------------------------------------------------------------------------------------

@Composable
internal fun PlanGrid(u: Ui, t: TradeEntity, modifier: Modifier = Modifier) {
    val plan = t.plan
    val cells = listOf(
        u.t("size.actual") to u.pct(t.rriskp),
        u.t("app.trade.cell.notional") to u.money(t.notional, 0),
        u.t("wiz.leverage") to (u.mult(t.lev) + DOT + u.t(if (t.isolated) "app.trade.isolated" else "app.trade.cross")),
        u.t("app.trade.cell.margin") to (u.money(plan.margin, 0) + " (" + u.pct(plan.marginPct, 1) + ")"),
        u.t("wiz.calc.rr") to u.num(plan.rr),
        u.t("wiz.calc.sld") to u.pct(plan.sld),
        u.t("app.trade.cell.grade") to u.t(if (t.gradeA) "setups.grade.A" else "setups.grade.B"),
        u.t("wiz.calc.liqX") to u.mult(plan.liqX),
    )
    val two = LocalDensity.current.fontScale <= 1.15f
    Column(modifier.fillMaxWidth().panel().padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (two) {
            cells.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    pair.forEach { (label, value) -> DottedRow(label, value, Modifier.weight(1f), valueStyle = figure(12)) }
                }
            }
        } else {
            cells.forEach { (label, value) -> DottedRow(label, value, valueStyle = figure(13)) }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Events: the gate, the entry, the checks and what the user logged, oldest first
// ---------------------------------------------------------------------------------------------------------------

private enum class Mark { GATE_OK, GATE_NO, ENTRY, MOVE, WARN, PLAIN }

private class TimelineRow(val time: Long, val mark: Mark, val text: String)

private fun payloadText(e: TradeEventEntity, key: String): String? =
    runCatching { Json.parseToJsonElement(e.payload).jsonObject[key]?.jsonPrimitive?.contentOrNull }.getOrNull()

private fun payloadNumber(e: TradeEventEntity, key: String): Double? =
    runCatching { Json.parseToJsonElement(e.payload).jsonObject[key]?.jsonPrimitive?.doubleOrNull }.getOrNull()

private fun eventRow(u: Ui, e: TradeEventEntity): TimelineRow {
    val label = u.t("trade.event.${e.type}")
    return when (e.type) {
        "SL_MOVE" -> TimelineRow(
            e.time, Mark.MOVE,
            u.t("trade.moveSl.log", "from" to u.price(payloadNumber(e, "from")), "to" to u.price(payloadNumber(e, "to"))),
        )
        "PARTIAL_EXIT" -> TimelineRow(
            e.time, Mark.MOVE, label + DOT + u.price(payloadNumber(e, "price")) + DOT + u.pct(payloadNumber(e, "fraction"), 0),
        )
        "NOTE", "NEWS" -> TimelineRow(e.time, Mark.PLAIN, payloadText(e, "text")?.let { "$label: $it" } ?: label)
        "EMOTION" -> TimelineRow(e.time, Mark.PLAIN, payloadText(e, "emotion")?.let { label + ": " + u.enumLabel("emotion", it) } ?: label)
        "ADD_REPORTED", "PRAYER" -> TimelineRow(e.time, Mark.WARN, label)
        else -> TimelineRow(e.time, Mark.PLAIN, label)
    }
}

@Composable
internal fun EventsCard(u: Ui, t: TradeEntity, events: List<TradeEventEntity>, onAdd: ((String) -> Unit)?) {
    val k = LocalAndarzColors.current
    val rows = buildList {
        add(
            TimelineRow(
                t.createdAt, if (t.gateAllowed) Mark.GATE_OK else Mark.GATE_NO,
                if (t.gateAllowed) u.t("app.trade.tl.gate", "risk" to u.pct(t.riskPctChosen, 2)) else u.t("app.trade.tl.gateNo"),
            ),
        )
        t.entryTime?.let { at ->
            add(
                TimelineRow(
                    at, Mark.ENTRY,
                    u.t(
                        "app.trade.tl.entry",
                        "price" to u.price(t.entry), "n" to u.int(t.chk), "total" to u.int(8),
                        "grade" to u.t(if (t.gradeA) "setups.grade.A" else "setups.grade.B"),
                    ),
                ),
            )
        }
        t.monitorChecks.forEach { add(TimelineRow(it, Mark.PLAIN, u.t("app.trade.tl.monitor"))) }
        events.forEach { add(eventRow(u, it)) }
    }.sortedBy { it.time }
    Column(Modifier.fillMaxWidth().panel().padding(start = 12.dp, end = 12.dp, bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                u.t("app.trade.events"),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.W800, fontSize = 14.sp),
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (onAdd != null) {
                EventButton(Glyph.GLOBE, u.t("trade.news.add")) { onAdd("NEWS") }
                EventButton(Glyph.SMILE, u.t("trade.emotionNow")) { onAdd("EMOTION") }
                EventButton(Glyph.PENCIL, u.t("trade.note")) { onAdd("NOTE") }
            }
        }
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 4.dp).semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MarkTile(row.mark)
                Text(u.time(Instant.ofEpochMilli(row.time)), style = figure(13, FontWeight.W600), modifier = Modifier.width(44.dp))
                Text(row.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EventButton(glyph: Glyph, label: String, onClick: () -> Unit) {
    val k = LocalAndarzColors.current
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        GlyphIcon(glyph, label, tint = k.hmuted, size = 22.dp, strokeWidth = 2f)
    }
}

/** The 18 by 22 arch that marks a row of the timeline: a check on turquoise for the gate, solid lapis for the entry. */
@Composable
private fun MarkTile(mark: Mark) {
    val k = LocalAndarzColors.current
    val shape = RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp, bottomEnd = 5.dp, bottomStart = 5.dp)
    val base = Modifier.size(width = 18.dp, height = 22.dp)
    when (mark) {
        Mark.GATE_OK -> Box(base.background(k.fzC, shape), contentAlignment = Alignment.Center) { GlyphIcon(Glyph.CHECK, null, tint = k.fz, size = 12.dp, strokeWidth = 2.8f) }
        Mark.GATE_NO -> Box(base.background(k.anC, shape), contentAlignment = Alignment.Center) { GlyphIcon(Glyph.X, null, tint = k.an, size = 12.dp, strokeWidth = 2.8f) }
        Mark.ENTRY, Mark.MOVE -> Box(base.background(k.lapis, shape))
        Mark.WARN -> Box(base.background(k.sfC, shape).archRing(shape, k.sfLine, 1.dp))
        Mark.PLAIN -> Box(base.archRing(shape, k.outline, 1.5.dp))
    }
}
