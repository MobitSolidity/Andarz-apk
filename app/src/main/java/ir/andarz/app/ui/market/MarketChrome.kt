package ir.andarz.app.ui.market

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ir.andarz.app.ui.Ui
import ir.andarz.app.ui.components.ArchShapes
import ir.andarz.app.ui.components.FilterChip
import ir.andarz.app.ui.components.SegmentedTabs
import ir.andarz.app.ui.components.Text
import ir.andarz.app.ui.components.archSurface
import ir.andarz.app.ui.icons.Glyph
import ir.andarz.app.ui.icons.GlyphIcon
import ir.andarz.app.ui.nav.MarketSection
import ir.andarz.app.ui.theme.LocalAndarzColors
import ir.andarz.app.ui.ui
import ir.andarz.market.model.LiveMode
import ir.andarz.market.model.MarketState

/*
 * The chrome of the Market destination on the canvas: the title with its one status line, the unit toggle and the
 * refresh button at the end edge, and the four sections as one segmented control under them.
 */

/** What the dot of the status line says: live data, steady polling, connecting, old data, or Market switched off. */
internal enum class Pulse { LIVE, STEADY, CONNECTING, STALE, OFF }

/** The status line under the title: a [lead] word in the tone of the [pulse], then a muted [rest]. */
internal class StatusLine(val pulse: Pulse, val lead: String, val rest: String = "")

/**
 * The status of Market in one line (the four headers of the canvas): off, connecting, live or polling with the
 * time of the last data, and old data after an error. Times are clock times, so the line never needs a timer.
 */
internal fun marketStatus(u: Ui, fmt: MarketFmt, state: MarketState, on: Boolean): StatusLine {
    if (!on) return StatusLine(Pulse.OFF, u.t("app.mkt.status.off"))
    val has = state.updatedAt > 0
    val updated = if (has) u.t("app.mkt.status.updated", "t" to fmt.clock(state.updatedAt)) else ""
    return when {
        state.error != null && has -> StatusLine(Pulse.STALE, u.t("app.mkt.stale"), u.t("app.mkt.stale.at", "t" to fmt.clock(state.updatedAt)))
        state.error != null -> StatusLine(Pulse.STALE, u.t("app.mkt.status.noprice"))
        state.live == LiveMode.BINANCE || state.live == LiveMode.NOBITEX -> StatusLine(Pulse.LIVE, u.t("app.mkt.live"), updated)
        state.live == LiveMode.POLLING && has -> StatusLine(Pulse.STEADY, u.t("mkt.live.POLLING"), updated)
        else -> StatusLine(Pulse.CONNECTING, u.t("app.mkt.status.connecting"), if (has) updated else u.t("app.mkt.status.noprice"))
    }
}

/**
 * The header of Market: the title and its status line at the start edge, [controls] at the end edge. On a narrow
 * screen or at a large font the controls move to a row of their own, so nothing is cut.
 */
@Composable
internal fun MarketHeader(title: String, status: StatusLine, modifier: Modifier = Modifier, controls: (@Composable () -> Unit)? = null) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wide = controls == null || (maxWidth >= 360.dp && LocalDensity.current.fontScale <= 1.15f)
        if (wide) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { TitleAndStatus(title, status) }
                if (controls != null) controls()
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp)) {
                TitleAndStatus(title, status)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (controls != null) controls()
                }
            }
        }
    }
}

@Composable
private fun TitleAndStatus(title: String, status: StatusLine) {
    val k = LocalAndarzColors.current
    val ink = when (status.pulse) {
        Pulse.LIVE -> k.fz
        Pulse.STALE -> k.sf
        Pulse.CONNECTING -> k.hmuted
        else -> k.muted
    }
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Row(
            Modifier.semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PulseMark(status.pulse)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = ink, fontWeight = if (status.pulse == Pulse.OFF) null else FontWeight.W700)) { append(status.lead) }
                    if (status.rest.isNotEmpty()) append(SEP + status.rest)
                },
                style = MaterialTheme.typography.labelMedium,
                color = k.muted,
            )
        }
    }
}

/** The dot before the status: a filled dot with a halo when live, a ring when connecting or off, the alert glyph when old. */
@Composable
private fun PulseMark(pulse: Pulse) {
    val k = LocalAndarzColors.current
    if (pulse == Pulse.STALE) {
        GlyphIcon(Glyph.ALERT_CIRCLE, null, tint = k.sf, size = 13.dp, strokeWidth = 2.4f)
        return
    }
    Canvas(Modifier.size(7.dp)) {
        val r = size.minDimension / 2f
        val ring = 1.5.dp.toPx()
        when (pulse) {
            Pulse.LIVE -> {
                drawCircle(k.fzC, r + 3.dp.toPx(), center)
                drawCircle(k.fzG, r, center)
            }
            Pulse.STEADY -> drawCircle(k.muted, r, center)
            Pulse.CONNECTING -> drawCircle(k.hmuted, r - ring / 2f, center, style = Stroke(ring))
            else -> drawCircle(k.outline, r - ring / 2f, center, style = Stroke(ring))
        }
    }
}

/** The unit toggle and the refresh button of the Prices and My coins sections. */
@Composable
internal fun HeaderControls(toman: Boolean, onToman: (Boolean) -> Unit, loading: Boolean, onRefresh: () -> Unit) {
    val u = ui()
    val k = LocalAndarzColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        UnitToggle(toman, onToman)
        IconButton(onClick = onRefresh, enabled = !loading, modifier = Modifier.size(48.dp)) {
            GlyphIcon(
                Glyph.REFRESH, u.t(if (loading) "app.mkt.refreshing" else "mkt.prices.refresh"),
                tint = if (loading) k.muted else k.text, size = 22.dp, strokeWidth = 1.9f,
            )
        }
    }
}

/** Dollar or Toman: two radio options in one arch, the chosen one raised to `high`. */
@Composable
private fun UnitToggle(toman: Boolean, onToman: (Boolean) -> Unit) {
    val u = ui()
    val k = LocalAndarzColors.current
    Row(
        Modifier.semantics { contentDescription = u.t("app.mkt.unit.desc") }
            .selectableGroup()
            .archSurface(ArchShapes.arch(17.dp, 12.dp), k.alt, k.divider)
            .padding(3.dp),
    ) {
        UnitOption(!toman, u.t("mkt.unit.usd")) { onToman(false) }
        UnitOption(toman, u.t("mkt.unit.toman")) { onToman(true) }
    }
}

@Composable
private fun UnitOption(selected: Boolean, label: String, onClick: () -> Unit) {
    val k = LocalAndarzColors.current
    Box(
        Modifier.heightIn(min = 48.dp)
            .background(if (selected) k.high else Color.Transparent, ArchShapes.arch(14.dp, 9.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.W700 else FontWeight.W400),
            color = if (selected) k.text else k.muted,
            maxLines = 1,
        )
    }
}

/**
 * The four sections as one segmented control. At a font scale above 130% the labels would not fit four to a row, so
 * the control becomes a scrolling row of chips and every label stays whole.
 */
@Composable
internal fun SectionTabs(selected: MarketSection, onSelect: (MarketSection) -> Unit, modifier: Modifier = Modifier) {
    val u = ui()
    val labels = MarketSection.entries.map { u.t(it.labelKey) }
    if (LocalDensity.current.fontScale <= 1.3f) {
        SegmentedTabs(labels, selected.ordinal, { onSelect(MarketSection.entries[it]) }, modifier.fillMaxWidth())
    } else {
        Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MarketSection.entries.forEachIndexed { i, s ->
                FilterChip(selected = s == selected, onClick = { onSelect(s) }, label = { Text(labels[i]) })
            }
        }
    }
}
