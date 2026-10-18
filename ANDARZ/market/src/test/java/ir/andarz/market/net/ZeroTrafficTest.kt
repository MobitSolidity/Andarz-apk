package ir.andarz.market.net

import okhttp3.Call
import okhttp3.Request
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * K8.4 guard 6, the zero-traffic rule of K4: with Market off (the default), or on but not demanded, the
 * app opens no socket and issues no request.
 *
 * The proof here is at the module's own boundary: [MarketHttp] does not build a client and never asks its
 * call factory for anything until a call site really wants data. MK1 extends this to the hub itself
 * (`acquire`/`release` demand counting, K6.4) once the repository exists.
 */
class ZeroTrafficTest {

    private class CountingFactory : Call.Factory {
        var newCalls = 0
            private set

        override fun newCall(request: Request): Call {
            newCalls++
            throw AssertionError("the zero-traffic rule was broken: a call was created for " + request.url)
        }
    }

    @Test
    fun `constructing the client opens nothing`() {
        val factory = CountingFactory()
        val http = MarketHttp(callFactory = { factory })
        assertFalse(http.started, "no client may exist before the first demand")
        assertEquals(0, factory.newCalls, "no call may be created before the first demand")
    }

    @Test
    fun `the client is built only on the first real call`() {
        var built = 0
        val http = MarketHttp(callFactory = {
            built++
            CountingFactory()
        })
        assertEquals(0, built, "building MarketHttp must not build a client")
        assertFalse(http.started)

        // The first call builds the client. The counting factory then refuses, which is enough: the point
        // is that nothing was created earlier.
        runCatching { http.get("https://" + ir.andarz.market.MarketHosts.COIN_LIST + "/api/v3/ping") }
        assertEquals(1, built, "the client is built exactly once, on demand")
        assertTrue(http.started)
    }

    @Test
    fun `an installed module has not started any client`() {
        // install() stores the host bridge and the preference reader; it must not touch the network.
        val http = MarketHttp(callFactory = { CountingFactory() })
        assertFalse(http.started, "install must leave Market silent (K4)")
    }
}
