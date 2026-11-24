package ir.andarz.app.ui.market

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The pure parts of the coin sheet of the canvas: the chart's price levels and labels, the stepper, the rule it saves. */
class CoinSheetLogicTest {

    private val en = MarketFmt(fa = false, tomanSuffix = " T", zone = ZoneId.of("+03:30"))

    private fun bare(s: String) = s.replace("⁦", "").replace("⁩", "")

    @Test
    fun `a nice step is one two or five times a power of ten`() {
        assertEquals(2000.0, niceStep(1800.0))
        assertEquals(500.0, niceStep(420.0))
        assertEquals(0.02, niceStep(0.013), 1e-12)
        assertEquals(10.0, niceStep(7.0))
        assertEquals(1.0, niceStep(0.0), 1e-12, "no range, any step")
        assertEquals(1.0, niceStep(Double.NaN), 1e-12)
    }

    @Test
    fun `the chart gets up to three round levels inside the data`() {
        val s = chartScale(listOf(103_800.0, 105_000.0, 109_200.0, 108_450.0), en)
        assertEquals(listOf(104_000.0, 106_000.0, 108_000.0), s.levels)
        assertEquals(listOf("104K", "106K", "108K"), s.labels.map { bare(it) })
        assertEquals(0f, s.frac(109_200.0), "the highest value is at the top")
        assertEquals(1f, s.frac(103_800.0), "the lowest value is at the bottom")
    }

    @Test
    fun `a flat series still gets a scale`() {
        val s = chartScale(listOf(5.0, 5.0, 5.0), en)
        assertTrue(s.hi > s.lo)
        assertTrue(s.levels.size in 1..3)
        assertTrue(s.levels.all { it > s.lo && it < s.hi })
        assertEquals(s.levels.size, s.labels.size)
    }

    @Test
    fun `an empty series has no levels`() {
        val s = chartScale(emptyList(), en)
        assertTrue(s.levels.isEmpty())
        assertTrue(s.labels.isEmpty())
    }

    @Test
    fun `axis labels tell neighbours apart at every size of number`() {
        assertEquals(listOf("1.2", "1.4", "1.6"), en.axisLabels(listOf(1.2, 1.4, 1.6), 0.2).map { bare(it) })
        assertEquals(listOf("3,400", "3,500", "3,600"), en.axisLabels(listOf(3400.0, 3500.0, 3600.0), 100.0).map { bare(it) })
        assertEquals(listOf("0.0123", "0.0125"), en.axisLabels(listOf(0.0123, 0.0125), 0.0002).map { bare(it) })
        assertEquals(listOf("1.2M", "1.4M"), en.axisLabels(listOf(1.2e6, 1.4e6), 2e5).map { bare(it) })
        assertEquals(listOf("98.2K", "98.4K"), en.axisLabels(listOf(98_200.0, 98_400.0), 200.0).map { bare(it) })
        assertTrue(en.axisLabels(emptyList(), 1.0).isEmpty())
    }

    @Test
    fun `the stepper moves by half a point inside its range`() {
        assertEquals(3.5f, stepPct(3.0f, true))
        assertEquals(2.5f, stepPct(3.0f, false))
        assertEquals(0.5f, stepPct(0.5f, false), "not below the least")
        assertEquals(20f, stepPct(20f, true), "not above the most")
        assertEquals(3.0f, stepPct(stepPct(3.0f, true), false), "up and down comes back")
    }

    @Test
    fun `a rule keeps a custom threshold only when asked and drops targets that are not positive numbers`() {
        val plain = ruleOf("bitcoin", false, 3.5f, "", "abc")
        assertNull(plain.pct)
        assertNull(plain.above)
        assertNull(plain.below)
        val full = ruleOf("bitcoin", true, 3.5f, "65000", "0")
        assertEquals(3.5f, full.pct)
        assertEquals(65_000.0, full.above)
        assertNull(full.below, "zero is no target")
    }
}
