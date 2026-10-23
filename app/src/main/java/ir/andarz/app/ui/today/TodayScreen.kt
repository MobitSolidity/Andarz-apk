package ir.andarz.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.andarz.app.ui.components.EmptyState
import ir.andarz.app.ui.components.KhatamPattern
import ir.andarz.app.ui.components.LimitGauge
import ir.andarz.app.ui.components.LockBanner
import ir.andarz.app.ui.components.SectionCard
import ir.andarz.app.ui.components.TagChip
import ir.andarz.app.ui.components.Verdict
import ir.andarz.app.ui.components.VerdictBanner
import ir.andarz.app.ui.journal.LockTexts
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui
import ir.andarz.data.db.TradeEntity
import ir.andarz.engine.DashStatus
import ir.andarz.engine.LockNeed
import ir.andarz.engine.Management
import ir.andarz.engine.Side
import ir.andarz.engine.Snapshot
import kotlinx.coroutines.delay
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

/** Today (5.2): hero, lock banners, reductions, six gauges, overall verdict, open positions, advice. */
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
) {
    val u = ui()
    val c = LocalAndarzColors.current
    val nowMs by ir.andarz.app.ui.rememberNowMillis(1_000, aligned = true)
    val now: Instant = Instant.ofEpochMilli(nowMs)

    if (snapshot == null) { ir.andarz.app.ui.components.LoadingState(); return }
    val p = snapshot.params
    val g = snapshot.governor
    val d = snapshot.dashboard
    val mayEnter = snapshot.mayEnter

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row {
                Text(u.t("app.name") + " \u00B7 " + u.date(u.localDate(now)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (g.locks.isNotEmpty()) Icon(Icons.Filled.Lock, contentDescription = u.t("a11y.lockedState"), tint = c.redline)
            }

            // hero
            Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Box {
                    KhatamPattern(Modifier.matchParentSize())
                    Column(Modifier.padding(20.dp).fillMaxWidth()) {
                        Text(u.t("today.equity"), style = MaterialTheme.typography.labelLarge, color = c.textMuted)
                        Text(u.money(snapshot.equity.now), style = MaterialTheme.typography.displaySmall)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(u.t("today.dayPnl") + " " + u.signedMoney(g.dailyNet) + " (" + u.pct(d.dailyPct, 2, signed = true) + ")", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(u.t("today.weekPnl") + " " + u.pct(d.weeklyPct, 2, signed = true) + "   " + u.t("today.drawdown") + " " + u.pct(d.drawdown, 2), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (!pledgeSigned) Text(u.t("today.pledgeMissing"), color = c.caution, style = MaterialTheme.typography.bodyMedium)

            // locks (first one expanded by design: every banner shows its body; needs as buttons)
            g.locks.forEach { l ->
                LockBanner(
                    title = LockTexts.title(u, l),
                    body = LockTexts.body(u, l, p, g.tradesToday) + l.needs.joinToString("") { "\n\u2022 " + LockTexts.needText(u, it, p) },
                    until = LockTexts.until(u, l, now),
                    actions = l.needs.map { n -> LockTexts.needAction(u, n) to { onNeed(n, l.tradeId) } },
                )
            }

            if (g.reductions.isNotEmpty() || g.bigWinHoldUntil != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    g.reductions.forEach { TagChip(LockTexts.reduction(u, it), c.caution) }
                    g.bigWinHoldUntil?.let { TagChip(u.t("bigwin.hold", "until" to u.dateTime(it)), c.caution) }
                }
                if (g.reductions.isNotEmpty()) Text(u.t("reduction.cap", "risk" to u.pct(g.riskCap, 2)), style = MaterialTheme.typography.labelLarge)
            }

            // six gauges, 2 x 3
            SectionCard(u.t("today.limits")) {
                val cap = { v: String -> u.t("today.cap", "v" to v) }
                val rows = listOf(
                    Triple(u.t("today.tradesToday"), "${u.int(d.tradesToday)} / ${cap(u.int(p.maxTradesDay.toInt()))}", Triple(d.tradesToday / p.maxTradesDay, d.status.trades, p.maxTradesDay.toInt())),
                    Triple(u.t("today.dailyLoss"), "${u.pct(maxOf(0.0, -d.dailyPct))} / ${cap(u.pct(p.dailyLossCap, 0))}", Triple(maxOf(0.0, -d.dailyPct) / p.dailyLossCap, d.status.daily, null)),
                    Triple(u.t("today.weeklyLoss"), "${u.pct(maxOf(0.0, -d.weeklyPct))} / ${cap(u.pct(p.weeklyLossCap, 0))}", Triple(maxOf(0.0, -d.weeklyPct) / p.weeklyLossCap, d.status.weekly, null)),
                    Triple(u.t("today.drawdown"), "${u.pct(d.drawdown)} / ${cap(u.pct(p.ddCap, 0))}", Triple(d.drawdown / p.ddCap, d.status.drawdown, null)),
                    Triple(u.t("today.positions"), "${u.int(d.openPositions)} / ${cap(u.int(p.maxPositions.toInt()))}", Triple(d.openPositions / p.maxPositions, d.status.positions, p.maxPositions.toInt())),
                    Triple(u.t("today.openRisk"), "${u.pct(d.openRiskPct)} / ${cap(u.pct(p.openRiskMax, 0))}", Triple(d.openRiskPct / p.openRiskMax, d.status.openRisk, null)),
                )
                rows.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { (label, value, info) ->
                            val (ratio, status, segments) = info
                            Column(Modifier.weight(1f)) {
                                LimitGauge(label, value, ratio, red = status != DashStatus.OK && status != DashStatus.POSITIONS_FULL, segments = segments)
                                if (status != DashStatus.OK) {
                                    val text = if (status == DashStatus.DD_RED) u.t("today.status.DD_RED", "days" to u.int(p.ddLockDays.toInt())) else u.t("today.status.${status.name}")
                                    Text(text, style = MaterialTheme.typography.labelMedium, color = c.redline)
                                }
                            }
                        }
                    }
                }
            }

            VerdictBanner(
                state = if (mayEnter) Verdict.ALLOWED else if (g.locks.isNotEmpty()) Verdict.LOCKED else Verdict.DENIED,
                title = u.t(if (mayEnter) "today.overall.ok" else "today.overall.no"),
                numbers = u.t(if (mayEnter) "today.allowed.yes" else "today.allowed.no"),
            )

            // open positions
            SectionCard(u.t("today.openTitle")) {
                val open = openTrades.sortedBy { if (it.account == "REAL") 0 else 1 }
                if (open.isEmpty()) EmptyState(u.t("today.noOpen"))
                open.forEach { t ->
                    val ts = Management.timeStop(
                        Side.valueOf(t.side), t.entry ?: t.plannedEntry, t.slCurrent, Instant.ofEpochMilli(t.entryTime ?: 0),
                        t.timeframe, t.legs.isNotEmpty(), now, p,
                    )
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(u.symbol(t.symbol), style = MaterialTheme.typography.titleSmall)
                            TagChip(u.enumLabel("side", t.side))
                            if (t.account == "DEMO") TagChip(u.t("common.demo"))
                        }
                        Text(
                            u.t("wiz.entry") + " " + u.price(t.entry) + "   " + u.t("trade.sl.current") + " " + u.price(t.slCurrent) +
                                "   " + u.t("wiz.tp") + " " + u.price(t.tp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            if (ts.due) u.t("trade.timeStop.due", "n" to u.int(p.timeStopCandles.toInt())) else u.t("trade.timeStop.left", "n" to u.int(ts.left.toInt())),
                            style = MaterialTheme.typography.labelMedium, color = if (ts.due) c.caution else c.textMuted,
                        )
                        t.monitorChecks.lastOrNull()?.let {
                            Text(u.t("trade.monitor.last", "t" to u.time(Instant.ofEpochMilli(it))), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onOpenTrade(t.id) }) { Text(u.t("common.open")) }
                            TextButton(onClick = { onMonitor(t.id) }) { Text(u.t("trade.monitor.check")) }
                        }
                    }
                }
            }

            // advice of the day (D-32)
            if (adviceEnabled) {
                val pool = u.content.arr("adviceOfDay")
                if (pool != null && pool.size > 0) {
                    val a = pool[(u.localDate(now).toEpochDay().mod(pool.size))].jsonObject
                    SectionCard(u.t("today.advice")) {
                        Text(a["text"]!!.jsonPrimitive.content, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            OutlinedButton(onClick = onCloseDay, modifier = Modifier.fillMaxWidth()) { Text(u.t("today.closeDay")) }
            Spacer(Modifier.height(72.dp))
        }

        ExtendedFloatingActionButton(
            onClick = onNewTrade,
            icon = { Icon(if (mayEnter) Icons.Filled.Add else Icons.Filled.Lock, contentDescription = null) },
            text = { Text(u.t(if (mayEnter) "today.newTrade" else "today.newTrade.blocked")) },
            containerColor = if (mayEnter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(16.dp),
        )
    }
}

