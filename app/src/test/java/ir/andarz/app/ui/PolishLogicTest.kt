package ir.andarz.app.ui

import ir.andarz.app.ui.analysis.decimate
import ir.andarz.app.ui.platform.LearnLogic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** M7 pure logic: the learn expectancy calculator (5.13) and chart decimation (6.5). */
class PolishLogicTest {
    @Test
    fun expectancyMatchesTheWorkedExampleOfTheLearnPage() {
        // learn.additions.expectancy.example: 0.4 x 2 - 0.6 = 0.2
        assertEquals(0.2, LearnLogic.expectancy(0.4, 2.0)!!, 1e-12)
        assertEquals(0.0, LearnLogic.expectancy(0.5, 1.0)!!, 1e-12)
        assertEquals(-1.0, LearnLogic.expectancy(0.0, 3.0)!!, 1e-12)
        assertEquals(0.4, LearnLogic.fromPercent("40")!!, 1e-12)
        assertEquals(0.4, LearnLogic.fromPercent("\u06F4\u06F0")!!, 1e-12)
        assertNull(LearnLogic.expectancy(1.2, 2.0))
        assertNull(LearnLogic.expectancy(0.4, 0.0))
        assertNull(LearnLogic.expectancy(null, 2.0))
        assertNull(LearnLogic.expectancy(0.4, Double.NaN))
    }

    @Test
    fun breakEvenFollowsTheNote() {
        // learn.additions.expectancy.breakEvenNote: R = 2 -> about 33%, R = 1 -> 50%
        assertEquals(1.0 / 3, LearnLogic.breakEven(2.0)!!, 1e-12)
        assertEquals(0.5, LearnLogic.breakEven(1.0)!!, 1e-12)
        assertNull(LearnLogic.breakEven(-1.0))
        // at break-even the expectancy is zero
        assertEquals(0.0, LearnLogic.expectancy(LearnLogic.breakEven(2.5), 2.5)!!, 1e-12)
    }

    @Test
    fun decimationKeepsEndsAndExtremesWithinTheCap() {
        val short = listOf(1.0, 2.0, 3.0)
        assertEquals(short, decimate(short))
        val n = 5_000
        val pts = (0 until n).map { kotlin.math.sin(it / 50.0) * 100 + if (it == 1234) 1_000.0 else 0.0 }
        val d = decimate(pts)
        assertTrue(d.size <= 600, "size ${d.size}")
        assertEquals(pts.first(), d.first())
        assertEquals(pts.last(), d.last())
        assertEquals(pts.max(), d.max(), "the spike survives")
        assertEquals(pts.min(), d.min())
    }
}
