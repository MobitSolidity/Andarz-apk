package ir.andarz.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.components.ArchCard
import ir.andarz.app.ui.components.EmptyState
import ir.andarz.app.ui.components.LimitGauge
import ir.andarz.app.ui.components.LoadingState
import ir.andarz.app.ui.components.LockBanner
import ir.andarz.app.ui.components.LockCountdown
import ir.andarz.app.ui.icons.Glyph
import ir.andarz.app.ui.journal.LockTexts
import ir.andarz.app.ui.market.shortLeft
import ir.andarz.app.ui.rememberNowMillis
import ir.andarz.app.ui.theme.AndarzColors
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.theme.Tone
import ir.andarz.app.ui.ui
import ir.andarz.data.db.TradeEntity
import ir.andarz.engine.DashStatus
import ir.andarz.engine.LockNeed
import ir.andarz.engine.Management
import ir.andarz.engine.Params
import ir.andarz.engine.Side
import ir.andarz.engine.Snapshot
import ir.andarz.engine.XlMath
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.time.Instant

/** One of the six dials: its label, the figure and its cap as text, how full it is, what the engine says about it. */
private class GaugeSpec(
    val label: String,
    val value: String,
    val cap: String,
    val ratio: Double,
    val status: DashStatus,
    val segments: Int? = null,
)

private fun gaugeSpecs(u: Ui, snapshot: Snapshot): List<GaugeSpec> {
    val p = snapshot.params
    val d = snapshot.dashboard
    val st = d.status
    val dailyLoss = maxOf(0.0, -d.dailyPct)
    val weeklyLoss = maxOf(0.0, -d.weeklyPct)
    return listOf(
        GaugeSpec(u.t("today.tradesToday"), u.int(d.tradesToday), u.int(p.maxTradesDay.toInt()), d.tradesToday / p.maxTradesDay, st.trades, p.maxTradesDay.toInt()),
        GaugeSpec(u.t("today.dailyLoss"), u.pct(dailyLoss), u.pct(p.dailyLossCap, 0), dailyLoss / p.dailyLossCap, st.daily),
        GaugeSpec(u.t("today.weeklyLoss"), u.pct(weeklyLoss), u.pct(p.weeklyLossCap, 0), weeklyLoss / p.weeklyLossCap, st.weekly),
        GaugeSpec(u.t("today.drawdown"), u.pct(d.drawdown), u.pct(p.ddCap, 0), d.drawdown / p.ddCap, st.drawdown),
        GaugeSpec(u.t("today.positions"), u.int(d.openPositions), u.int(p.maxPositions.toInt()), d.openPositions / p.maxPositions, st.positions, p.maxPositions.toInt()),
        GaugeSpec(u.t("today.openRisk"), u.pct(d.openRiskPct), u.pct(p.openRiskMax, 0), d.openRiskPct / p.openRiskMax, st.openRisk),
    )
}

/** The cell of a dial: the dial, its word under it, and the long wording of a red status for TalkBack. */
private fun gaugeCell(u: Ui, p: Params, s: GaugeSpec): @Composable () -> Unit = {
    val word = when (gaugeWord(s.status, s.ratio)) {
        GaugeWord.FREE -> u.t("app.today.gauge.free")
        GaugeWord.NEAR -> u.t("app.today.gauge.near")
        GaugeWord.FULL -> u.t("app.today.gauge.full")
    }
    val detail = when (s.status) {
        DashStatus.OK -> null
        DashStatus.DD_RED -> u.t("today.status.DD_RED", "days" to u.int(p.ddLockDays.toInt()))
        else -> u.t("today.status.${s.status.name}")
    }
    LimitGauge(
        label = s.label,
        valueText = s.value + " / " + u.t("app.today.capOf", "v" to s.cap),
        ratio = s.ratio,
        red = isRed(s.status),
        segments = s.segments,
        statusText = word,
        detail = detail,
    )
}

/** The sign of an amount as the card shows it: rounded to cents first, so $0.004 is flat, not up. */
private fun trendOf(x: Double): Trend {
    val cents = Math.round(x * 100.0)
    return if (cents > 0) Trend.UP else if (cents < 0) Trend.DOWN else Trend.FLAT
}

/** A moment of today as a clock time, a moment of another day with its date. */
private fun momentText(u: Ui, t: Instant, now: Instant): String = if (u.localDate(t) == u.localDate(now)) u.time(t) else u.dateTime(t)

/** The verdict at the foot of the board of dials: its glyph, its line, an optional second line and the state as a word. */
private class StripSpec(val glyph: Glyph, val title: String, val detail: String?, val state: String)

private fun stripOf(u: Ui, tone: Tone, locked: Boolean, anyNear: Boolean, snapshot: Snapshot, now: Instant): StripSpec {
    val g = snapshot.governor
    return when (tone) {
        Tone.ALLOWED -> StripSpec(Glyph.CHECK, u.t("app.today.verdict.ok"), null, u.t("a11y.allowedState"))
        Tone.CAUTION -> {
            val parts = ArrayList<String>(2)
            if (g.reductions.isNotEmpty()) parts += u.t("app.today.verdict.risk", "risk" to u.pct(g.riskCap, 2))
            g.bigWinHoldUntil?.let { parts += u.t("app.today.verdict.holdA", "until" to momentText(u, it, now)) }
            StripSpec(
                Glyph.ALERT_CIRCLE,
                u.t(if (anyNear) "app.today.verdict.near" else "app.today.verdict.limited"),
                parts.joinToString(" \u00B7 ").ifEmpty { null },
                u.t("a11y.cautionState"),
            )
        }
        Tone.LOCKED -> if (locked) {
            StripSpec(Glyph.LOCK, u.t("app.today.verdict.lock"), null, u.t("a11y.lockedState"))
        } else {
            StripSpec(Glyph.X, u.t("app.today.verdict.no"), null, u.t("a11y.redState"))
        }
    }
}

/** The tiles of an open position: entry, stop, target and the time stop. */
private fun positionTiles(u: Ui, t: TradeEntity, p: Params, now: Instant, c: AndarzColors): List<PositionTile> {
    val ts = Management.timeStop(
        Side.valueOf(t.side), t.entry ?: t.plannedEntry, t.slCurrent, Instant.ofEpochMilli(t.entryTime ?: 0),
        t.timeframe, t.legs.isNotEmpty(), now, p,
    )
    return listOf(
        PositionTile(u.t("app.today.pos.entry"), u.price(t.entry), labelGlyph = Glyph.DIAMOND, labelTint = c.muted),
        PositionTile(u.t("app.today.pos.sl"), u.price(t.slCurrent), labelGlyph = Glyph.TRI_DOWN, labelTint = c.an),
        PositionTile(u.t("app.today.pos.tp"), u.price(t.tp), labelGlyph = Glyph.TRI_UP, labelTint = c.fz),
        PositionTile(
            u.t("app.today.pos.timeStop"),
            if (ts.due) u.t("app.today.pos.due") else u.t("app.today.pos.candles", "n" to u.int(ts.left.toInt())),
            valueGlyph = Glyph.CLOCK,
            valueTint = if (ts.due) c.caution else Color.Unspecified,
        ),
    )
}

/**
 * Today (5.2), in the design of the final canvas: the capital card with its curve and the lamp of the day, the lock
 * banners with their countdown, the restrictions in force, the board of six dials with the verdict of the day, the open
 * positions, the advice of the day and the close of the day. The tone of the day (open, cautious, closed) is decided once
 * (TodayModel) and shared by the lamp, the glow, the heading marks and the verdict strip.
 */
@Composable
fun TodayScreen(
    snapshot: Snapshot?,
    openTrades: List<TradeEntity>,
    pledgeSigned: Boolean,
    adviceEnabled: Boolean,
    onNewTrade: () -> Unit,
    onOpenTrade: (String) -> Unit,
    onNeed: (LockNeed, String?) -> Unit,
    onCloseDay: () -> Unit,
    onMonitor: (String) -> Unit,
    onToggleAmounts: (Boolean) -> Unit = {},
) {
    val u = ui()
    val c = LocalAndarzColors.current
    val nowMs by rememberNowMillis(1_000, aligned = true)
    val now: Instant = Instant.ofEpochMilli(nowMs)

    if (snapshot == null) { LoadingState(); return }
    val p = snapshot.params
    val g = snapshot.governor
    val d = snapshot.dashboard
    val mayEnter = snapshot.mayEnter

    val specs = gaugeSpecs(u, snapshot)
    val anyNear = specs.any { isNear(it.status, it.ratio) }
    val locked = g.locks.isNotEmpty()
    val restricted = g.reductions.isNotEmpty() || g.bigWinHoldUntil != null
    val tone = todayTone(mayEnter, locked, near = anyNear || restricted)

    // the curve of the account only changes when the snapshot does, not with the clock
    val curve = remember(snapshot) { equityCurve(snapshot.trades, snapshot.now, snapshot.equity.now) }
    val curveText = if (curve.size >= 2) {
        u.t("app.today.chart.desc", "from" to u.money(curve.first()), "to" to u.money(curve.last()))
    } else {
        ""
    }

    // the three figures under the curve; the weekly amount follows from its percentage and the equity at the start of the week
    val weekNet = d.weeklyPct * snapshot.equity.startOfWeek
    val belowPeak = XlMath.round(d.drawdown * 100, 2) > 0
    val peakEquity = if (d.drawdown > 0.0 && d.drawdown < 1.0) snapshot.equity.now / (1 - d.drawdown) else snapshot.equity.now
    val stats = listOf(
        HeroStat(u.t("app.today.col.day"), u.signedMoney(g.dailyNet), u.pct(d.dailyPct, 2, signed = true), trendOf(g.dailyNet)),
        HeroStat(u.t("app.today.col.week"), u.signedMoney(weekNet), u.pct(d.weeklyPct, 2, signed = true), trendOf(weekNet)),
        HeroStat(
            u.t("today.drawdown"),
            if (belowPeak) u.pct(-d.drawdown, 2, signed = true) else u.pct(0.0, 0),
            if (belowPeak) u.t("app.today.fromPeak", "v" to u.money(peakEquity)) else u.t("app.today.atPeak"),
            if (belowPeak) Trend.DOWN else Trend.FLAT,
        ),
    )
    val lamp = when (tone) {
        Tone.ALLOWED -> "open"
        Tone.CAUTION -> "caution"
        Tone.LOCKED -> "closed"
    }

    val strip = stripOf(u, tone, locked, anyNear, snapshot, now)

    Column(Modifier.fillMaxSize()) {
        TodayHeader(date = u.date(u.localDate(now)), amountsHidden = u.hidden, onToggleAmounts = { onToggleAmounts(!u.hidden) })
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeroCard(
                    tone = tone,
                    eyebrow = u.t("today.equity"),
                    equity = u.money(snapshot.equity.now),
                    curve = curve,
                    curveDescription = curveText,
                    lampCaption = u.t("app.today.lamp.$lamp"),
                    lampDescription = u.t("app.today.lamp.$lamp.desc"),
                    stats = stats,
                )

                if (!pledgeSigned) RestrictionCard(Glyph.ALERT_CIRCLE, u.t("today.pledgeMissing"), emptyList())

                // locks: every banner shows its body and its needs as buttons; a cool-down also shows its countdown ring
                g.locks.forEach { l ->
                    val total = LockTexts.total(l, p)
                    val until = l.until
                    val countdown = if (total != null && until != null && now.isBefore(until)) {
                        val left = Duration.between(now, until)
                        LockCountdown(
                            remaining = (left.toMillis().toFloat() / total.toMillis().toFloat()).coerceIn(0f, 1f),
                            label = shortLeft(u, left.toMillis()),
                            caption = u.t("app.today.left"),
                            description = LockTexts.until(u, l, now) ?: "",
                        )
                    } else {
                        null
                    }
                    LockBanner(
                        title = LockTexts.title(u, l),
                        body = LockTexts.body(u, l, p, g.tradesToday) + l.needs.joinToString("") { "\n• " + LockTexts.needText(u, it, p) },
                        until = LockTexts.until(u, l, now),
                        actions = l.needs.map { n -> LockTexts.needAction(u, n) to { onNeed(n, l.tradeId) } },
                        countdown = countdown,
                    )
                }

                // restrictions in force
                if (g.reductions.isNotEmpty()) {
                    RestrictionCard(
                        Glyph.DOWNLOAD,
                        u.t("app.today.cap.title", "risk" to u.pct(g.riskCap, 2)),
                        g.reductions.map { LockTexts.reduction(u, it) },
                    )
                }
                g.bigWinHoldUntil?.let { hold ->
                    RestrictionCard(
                        Glyph.HOURGLASS,
                        u.t("app.today.hold.title"),
                        listOf(u.t("bigwin.hold", "until" to momentText(u, hold, now))),
                    )
                }

                LimitsCard(u.t("today.limits"), tone, specs.map { gaugeCell(u, p, it) }) {
                    VerdictStrip(tone, strip.glyph, strip.title, strip.detail, strip.state)
                }

                // open positions
                SectionHeading(u.t("today.openTitle"))
                val open = openTrades.sortedBy { if (it.account == "REAL") 0 else 1 }
                if (open.isEmpty()) {
                    ArchCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), contentPadding = PaddingValues(0.dp)) {
                        EmptyState(u.t("today.noOpen"))
                    }
                }
                open.forEach { t ->
                    val last = t.monitorChecks.lastOrNull()
                    PositionCard(
                        symbol = u.symbol(t.symbol),
                        side = u.enumLabel("side", t.side),
                        short = t.side == Side.SHORT.name,
                        demo = if (t.account == "DEMO") u.t("common.demo") else null,
                        tiles = positionTiles(u, t, p, now, c),
                        monitorLabel = if (last != null) u.t("app.today.pos.checked", "t" to u.time(Instant.ofEpochMilli(last))) else u.t("trade.monitor.check"),
                        onMonitor = { onMonitor(t.id) },
                        manageLabel = u.t("app.today.pos.manage"),
                        onManage = { onOpenTrade(t.id) },
                    )
                }

                // advice of the day (D-32)
                if (adviceEnabled) {
                    val pool = u.content.arr("adviceOfDay")
                    if (pool != null && pool.size > 0) {
                        val a = pool[(u.localDate(now).toEpochDay().mod(pool.size))].jsonObject
                        AdviceCard(u.t("today.advice"), a["text"]!!.jsonPrimitive.content)
                    }
                }

                CloseDayButton(u.t("app.today.closeDay"), onCloseDay)
            }

            NewTradeFab(
                label = u.t(if (mayEnter) "today.newTrade" else "today.newTrade.blocked"),
                enabled = mayEnter,
                onClick = onNewTrade,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}
