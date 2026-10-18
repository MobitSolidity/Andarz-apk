package ir.andarz.market.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** MK1: the session samples behind the 5/15/60-minute windows. */
class PriceHistoryTest {

    @Test
    fun `range needs two samples inside the window`() {
        val h = PriceHistory()
        h.add("btc", 0, 100.0)
        assertNull(h.range("btc", 0), "one sample is not a range")
        h.add("btc", 5_000, 110.0)
        h.add("btc", 10_000, 90.0)
        assertEquals(90.0 to 110.0, h.range("btc", 0))
        assertEquals(90.0 to 110.0, h.range("btc", 5_000))
        assertNull(h.range("btc", 6_000), "only one sample since 6 s")
        assertNull(h.range("eth", 0), "unknown coin")
    }

    @Test
    fun `non-positive prices are ignored`() {
        val h = PriceHistory()
        h.add("x", 0, 0.0)
        h.add("x", 1, -5.0)
        h.add("x", 2, 3.0)
        assertEquals(listOf(3.0), h.series("x"))
    }

    @Test
    fun `samples older than the keep window are dropped`() {
        val h = PriceHistory(keepMs = 60_000)
        h.add("x", 0, 1.0)
        h.add("x", 30_000, 2.0)
        h.add("x", 61_000, 3.0)
        assertEquals(listOf(2.0, 3.0), h.series("x"))
    }

    @Test
    fun `the default keeps seventy minutes`() {
        val h = PriceHistory()
        h.add("x", 0, 1.0)
        h.add("x", 70 * 60_000L, 2.0)
        assertEquals(2, h.series("x").size)
        h.add("x", 70 * 60_000L + 1, 3.0)
        assertEquals(listOf(2.0, 3.0), h.series("x"))
    }
}
