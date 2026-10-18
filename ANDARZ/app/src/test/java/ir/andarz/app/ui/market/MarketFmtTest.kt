package ir.andarz.app.ui.market

import ir.andarz.engine.Fmt
import ir.andarz.market.model.Coin
import ir.andarz.market.model.USDT_IRT_ID
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** MK1: March's formatting tiers rebuilt on Fmt (K7), with Latin and Persian digits. */
class MarketFmtTest {

    // A fixed offset, so the test does not depend on the host JDK's tzdata (some still carry Iran's
    // abolished DST). On the device the zone comes from the settings.
    private val tehran = ZoneId.of("+03:30")
    private val en = MarketFmt(fa = false, tomanSuffix = " T", zone = tehran)
    private val fa = MarketFmt(fa = true, tomanSuffix = " T", zone = tehran)

    private fun bare(s: String) = s.replace("\u2066", "").replace("\u2069", "")

    @Test
    fun `price tiers match march`() {
        assertEquals("64,251", en.priceNumber(64250.5), ">= 1000: no decimals, grouped")
        assertEquals("1,000", en.priceNumber(1000.0))
        assertEquals("999.99", en.priceNumber(999.99), ">= 1: two decimals")
        assertEquals("1.00", en.priceNumber(1.0))
        assertEquals("0.5200", en.priceNumber(0.52), ">= 0.01: four decimals")
        assertEquals("0.0100", en.priceNumber(0.01))
        // March's own formula, 3 - floor(log10 p) decimals, kept exactly (K5.2): about three significant digits.
        assertEquals("0.001234", en.priceNumber(0.001234))
        assertEquals("0.00001234", en.priceNumber(0.00001234))
        assertEquals("0.0000000000", en.priceNumber(1.234e-12), "at most ten decimals")
        assertEquals("0", en.priceNumber(0.0))
        assertEquals("0", en.priceNumber(-3.0))
    }

    @Test
    fun `persian digits and separators follow the digit setting`() {
        assertEquals("\u06F6\u06F4\u066C\u06F2\u06F5\u06F1", fa.priceNumber(64250.5))
        assertEquals("\u06F0\u066B\u06F5\u06F2\u06F0\u06F0", fa.priceNumber(0.52))
        assertEquals("\u06F0", fa.priceNumber(0.0))
    }

    @Test
    fun `prices are isolated and carry their unit`() {
        val usd = en.usd(64250.5)
        assertTrue(usd.startsWith("\u2066") && usd.endsWith("\u2069"), "an LTR isolate (trap 9)")
        assertEquals("$64,251", bare(usd))
        assertEquals("60,450 T", bare(en.toman(60450.0)))
    }

    @Test
    fun `coin price follows the usd toman toggle and usdt is always toman`() {
        val btc = Coin("bitcoin", 1, "BTC", "Bitcoin", 64000.0, 1.0, 1e12)
        val usdt = Coin(USDT_IRT_ID, 0, "USDT", "", 60450.0, 0.75, 0.0, isToman = true)
        assertEquals("$64,000", bare(en.coinPrice(btc, toman = false, rate = 60000.0)))
        assertEquals("3,840,000,000 T", bare(en.coinPrice(btc, toman = true, rate = 60000.0)))
        assertEquals("$64,000", bare(en.coinPrice(btc, toman = true, rate = null)), "no rate: stay in USD")
        assertEquals("60,450 T", bare(en.coinPrice(usdt, toman = false, rate = null)))
        assertNull(en.altPrice(usdt, false, 60000.0))
        assertEquals("$64,000", bare(en.altPrice(btc, true, 60000.0)!!))
        assertEquals("\u2248 3,840,000,000 T", bare(en.altPrice(btc, false, 60000.0)!!))
        assertNull(en.altPrice(btc, false, null))
    }

    @Test
    fun `market cap uses T B M`() {
        assertEquals("$2.45T", bare(en.cap(2.45e12)))
        assertEquals("$31.0B", bare(en.cap(3.1e10)))
        assertEquals("$12.3M", bare(en.cap(1.234e7)))
        assertEquals("$999,999", bare(en.cap(999_999.0)))
    }

    @Test
    fun `percent has an arrow and two decimals`() {
        assertEquals("\u25B2 1.23%", bare(en.pct(1.234)))
        assertEquals("\u25BC 0.40%", bare(en.pct(-0.4)))
        assertEquals("\u25B2 0.00%", bare(en.pct(0.0)), "zero reads as up, as in March")
        assertEquals("\u25B2 \u06F1\u066B\u06F2\u06F3" + Fmt.FA_PERCENT, bare(fa.pct(1.234)))
        assertEquals("51.6%", bare(en.share(51.62)))
    }

    @Test
    fun `the clock is in the app zone with the digit setting`() {
        // 2024-06-12T00:00:00Z is 03:30:00 in Tehran.
        assertEquals("03:30:00", bare(en.clock(1_718_150_400_000L)))
        assertEquals("\u06F0\u06F3:\u06F3\u06F0:\u06F0\u06F0", bare(fa.clock(1_718_150_400_000L)))
        val utc = MarketFmt(false, " T", ZoneId.of("UTC"))
        assertEquals("00:00:00", bare(utc.clock(1_718_150_400_000L)))
    }

    @Test
    fun `the decimals table`() {
        assertEquals(0, MarketFmt.priceDecimals(1000.0))
        assertEquals(2, MarketFmt.priceDecimals(1.0))
        assertEquals(4, MarketFmt.priceDecimals(0.01))
        assertEquals(6, MarketFmt.priceDecimals(0.00999))
        assertEquals(10, MarketFmt.priceDecimals(1e-15))
    }
}
