package ir.andarz.engine

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 10.4 items 3, 4, 5 and the close-reason guess of 5.5. */
class ManagementTest {
    private val p = Params.DEFAULTS
    private val t0 = Instant.parse("2026-10-14T06:00:00Z")

    @Test
    fun timeStopForEveryTimeframe() {
        for ((tf, m) in Timeframes.MINUTES) {
            val before = t0.plusSeconds(60L * m * 10 - 1)
            val at = t0.plusSeconds(60L * m * 10)
            assertFalse(Management.timeStop(Side.LONG, 100.0, 96.0, t0, tf, false, before, p).due, tf)
            assertTrue(Management.timeStop(Side.LONG, 100.0, 96.0, t0, tf, false, at, p).due, tf)
            assertFalse(Management.timeStop(Side.LONG, 100.0, 96.0, t0, tf, true, at, p).due, "$tf partial exit")
            assertFalse(Management.timeStop(Side.LONG, 100.0, 100.0, t0, tf, false, at, p).due, "$tf stop at entry")
            assertFalse(Management.timeStop(Side.SHORT, 100.0, 99.0, t0, tf, false, at, p).due, "$tf short stop beyond entry")
            assertEquals(0, Management.timeStop(Side.LONG, 100.0, 96.0, t0, tf, false, at, p).left)
        }
    }

    @Test
    fun stopMovesAndBreakEven() {
        assertFalse(Management.isAwayMove(Side.LONG, 96.0, 98.0)); assertTrue(Management.isAwayMove(Side.LONG, 96.0, 95.0))
        assertFalse(Management.isAwayMove(Side.SHORT, 104.0, 102.0)); assertTrue(Management.isAwayMove(Side.SHORT, 104.0, 105.0))
        assertTrue(Management.isAwayMove(Side.LONG, 96.0, null))
        assertEquals(100.1, Management.breakEven(Side.LONG, 100.0), 1e-12)
        assertEquals(99.9, Management.breakEven(Side.SHORT, 100.0), 1e-12)
    }

    @Test
    fun floatingRAndPriceAtR() {
        val (pnl, r) = Management.floating(Side.LONG, 100.0, 96.0, 500.0, emptyList(), 104.2)
        assertEquals(21.0, pnl, 1e-9); assertEquals(1.05, r!!, 1e-9)
        assertEquals(104.0, Management.priceAtR(Side.LONG, 100.0, 96.0, 1.0), 1e-12)
        assertEquals(96.0, Management.priceAtR(Side.SHORT, 100.0, 104.0, 1.0), 1e-12)
    }

    @Test
    fun exitReasonGuess() {
        assertEquals("STOP_LOSS", Management.guessExitReason(Side.LONG, 100.0, 96.0, 108.0, 96.0))
        assertEquals("BREAKEVEN", Management.guessExitReason(Side.LONG, 100.0, 100.0, 108.0, 100.0))
        assertEquals("TRAILING_STOP", Management.guessExitReason(Side.LONG, 100.0, 103.0, 108.0, 103.0))
        assertEquals("TAKE_PROFIT", Management.guessExitReason(Side.SHORT, 100.0, 104.0, 92.0, 92.0))
        assertEquals("MANUAL", Management.guessExitReason(Side.LONG, 100.0, 96.0, 108.0, 101.0))
    }

    @Test
    fun suggestedViolations() {
        assertEquals(listOf("V07"), Management.suggestedViolations("REVENGE", false, false, 10, "15m", p))
        assertEquals(listOf("V09"), Management.suggestedViolations("CALM", false, true, 451, "15m", p))
        assertTrue(Management.suggestedViolations("CALM", false, true, 450, "15m", p).isEmpty())
    }
}
