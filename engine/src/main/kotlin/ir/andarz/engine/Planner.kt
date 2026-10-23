package ir.andarz.engine

import kotlin.math.abs

/**
 * Plan, gate, size verdict, fill, compliance and the leverage advisor (3.3 – 3.7). A literal port of the
 * functions of the same names in `andarz_ref.py`, guards included: where the reference returns `None`
 * this returns `null`, and a check whose input is `null` adds no reason.
 */
object Planner {

    private fun nz(x: Double): Boolean = x != 0.0

    fun plan(side: Side, equity: Double, entry: Double, sl: Double, tp: Double, lev: Double, p: Params, riskPct: Double? = null): Plan {
        val rp = riskPct ?: p.riskPct
        val sld = if (nz(entry)) abs(entry - sl) / entry else null
        val rr = if (entry != sl) abs(tp - entry) / abs(entry - sl) else null
        val riskUsd = equity * rp
        val notionalMax = if (sld != null && nz(sld)) riskUsd / sld else null
        val margin = if (notionalMax != null && nz(lev)) notionalMax / lev else null
        val marginPct = if (margin != null && nz(equity)) margin / equity else null
        val m = p.maintRate
        val liq = if (nz(lev)) {
            if (side == Side.LONG) entry * (1 - 1 / lev + m) else entry * (1 + 1 / lev - m)
        } else null
        val liqDist = if (liq != null && nz(entry)) abs(entry - liq) / entry else null
        val liqX = if (liqDist != null && sld != null && nz(sld)) liqDist / sld else null
        return Plan(sld, rr, riskUsd, notionalMax, margin, marginPct, liq, liqDist, liqX)
    }

    /** Ordered gate reasons; empty = allowed. Ratios are compared after `ROUND(x, 4)` (3.4). */
    fun gate(side: Side, entry: Double, sl: Double, tp: Double, lev: Double, chk: Int, plan: Plan, p: Params): List<GateReason> {
        val r = ArrayList<GateReason>(6)
        val okDir = if (side == Side.LONG) sl < entry && tp > entry else sl > entry && tp < entry
        if (!okDir) r += GateReason.DIRECTION
        if (chk < p.chkN) r += GateReason.CHECKLIST
        if (lev > p.levMax) r += GateReason.LEVERAGE
        plan.rr?.let { if (XlMath.round(it, 4) < p.rrMin) r += GateReason.RR }
        plan.marginPct?.let { if (XlMath.round(it, 4) > p.marginMax) r += GateReason.MARGIN }
        plan.liqX?.let { if (XlMath.round(it, 4) < p.liqMult) r += GateReason.LIQUIDATION }
        return r
    }

    /** 3.5: YES within `cap × 1.05`; up to `Risk_Max` GRADE_A_ONLY or OVER_CAP; above it RED_LINE. */
    fun sizeVerdict(rriskp: Double, p: Params, riskCap: Double? = null, gradeAAllowed: Boolean = true): SizeVerdict {
        val cap = riskCap ?: p.riskPct
        val r5 = XlMath.round(rriskp, 5)
        return when {
            r5 <= cap * 1.05 -> SizeVerdict.YES
            r5 <= p.riskMax -> if (gradeAAllowed) SizeVerdict.GRADE_A_ONLY else SizeVerdict.OVER_CAP
            else -> SizeVerdict.RED_LINE
        }
    }

    /** 3.6. `legs` fractions must sum to 1 within 1e-9, as the reference asserts. */
    fun fill(side: Side, entry: Double, sl: Double, equity: Double, notional: Double, legs: List<Leg>, fee: Double): Fill {
        require(abs(legs.sumOf { it.fraction } - 1.0) < 1e-9) { "leg fractions must sum to 1" }
        val sld = abs(entry - sl) / entry
        val rrisk = notional * sld
        val rriskp = if (nz(equity)) rrisk / equity else null
        if (legs.isEmpty()) return Fill(rrisk, rriskp, null, null, null, null, null)
        val sign = if (side == Side.LONG) 1 else -1
        // Summed left to right like Python's sum(), so the floating-point result is identical.
        var gross = 0.0
        for (l in legs) gross += sign * (l.price - entry) / entry * notional * l.fraction
        val net = gross - fee
        val r = if (nz(rrisk)) net / rrisk else null
        val result = when {
            net > 0 -> TradeResult.WIN
            net < 0 -> TradeResult.LOSS
            else -> TradeResult.EVEN
        }
        return Fill(rrisk, rriskp, gross, net, r, result, equity + net)
    }

    fun compliant(violations: List<String>, gateAllowed: Boolean, verdict: SizeVerdict): Boolean =
        violations.isEmpty() && gateAllowed && (verdict == SizeVerdict.YES || verdict == SizeVerdict.GRADE_A_ONLY)

    /** 3.7: the integer leverages that pass both the margin and the liquidation check, found with the gate. */
    fun leverageAdvisor(side: Side, equity: Double, entry: Double, sl: Double, tp: Double, chk: Int, p: Params, riskPct: Double? = null): Advisor {
        val sld = abs(entry - sl) / entry
        val rp = riskPct ?: p.riskPct
        val feasible = ArrayList<Pair<Int, Double?>>()
        for (l in 1..p.levMax.toInt()) {
            val c = plan(side, equity, entry, sl, tp, l.toDouble(), p, rp)
            val reasons = gate(side, entry, sl, tp, l.toDouble(), chk, c, p)
            if (GateReason.MARGIN !in reasons && GateReason.LIQUIDATION !in reasons) feasible += l to c.marginPct
        }
        val lMinReal = (rp / sld) / p.marginMax
        val lMaxLiqReal = 1 / (p.liqMult * sld + p.maintRate)
        // Ties on the distance go to the lower leverage; feasible is already in increasing order.
        val best = feasible.minWithOrNull(
            compareBy<Pair<Int, Double?>> { abs((it.second ?: Double.MAX_VALUE) - p.marginTarget) }.thenBy { it.first },
        )
        return Advisor(lMinReal, lMaxLiqReal, feasible.firstOrNull()?.first, feasible.lastOrNull()?.first, best?.first)
    }

    /** Fee impact on R:R (5.7, D-36): display only, never part of the gate. */
    fun rrAfterFees(notional: Double, entry: Double, sl: Double, tp: Double, feePct: Double): Double? {
        if (entry == 0.0) return null
        val reward = notional * abs(tp - entry) / entry
        val risk = notional * abs(entry - sl) / entry
        val fees = notional * feePct
        val den = risk + fees
        return if (den != 0.0) (reward - fees) / den else null
    }
}
