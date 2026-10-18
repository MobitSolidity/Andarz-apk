package ir.andarz.market.net

import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * K8.4 guard 5: the egress policy of K4 is enforced in code, not by convention. A POST, an `http://` URL,
 * an unknown host and an extra header are all refused before anything reaches the network.
 *
 * The interceptor is driven through a fake [Interceptor.Chain] rather than a MockWebServer, because the
 * policy forbids plaintext and a loopback host: the refusals are exactly what has to be proven, and a fake
 * chain proves them without a TLS fixture (K4 allows either).
 */
class MarketHttpEgressTest {

    private class FakeChain(private val request: Request) : Interceptor.Chain {
        var proceeded = false
            private set

        override fun request(): Request = request

        override fun proceed(request: Request): Response {
            proceeded = true
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }

        override fun connection(): Connection? = null
        override fun call(): Call = throw UnsupportedOperationException("not needed by the policy check")
        override fun connectTimeoutMillis(): Int = 0
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun readTimeoutMillis(): Int = 0
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun writeTimeoutMillis(): Int = 0
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }

    private val interceptor = EgressInterceptor()

    private fun allowed(path: String = "/api/v3/coins/markets"): Request.Builder =
        Request.Builder()
            .url("https://api.coingecko.com" + path)
            .header("Accept", "application/json")
            .header("User-Agent", MarketHttp.USER_AGENT)

    // ---------------------------------------------------------------- the allowed shape passes

    @Test
    fun `a plain GET to an allowed host over https passes`() {
        val chain = FakeChain(allowed().get().build())
        val response = interceptor.intercept(chain)
        assertTrue(chain.proceeded, "the allowed request must reach the network")
        assertEquals(200, response.code)
    }

    @Test
    fun `every allowed host is accepted`() {
        for (host in ir.andarz.market.MarketHosts.all) {
            val chain = FakeChain(
                Request.Builder()
                    .url("https://" + host + "/x")
                    .header("Accept", "application/json")
                    .header("User-Agent", MarketHttp.USER_AGENT)
                    .get()
                    .build(),
            )
            interceptor.intercept(chain)
            assertTrue(chain.proceeded, host + " is in the allowlist and must pass")
        }
    }

    // ---------------------------------------------------------------- refusals

    @Test
    fun `a POST is refused`() {
        val chain = FakeChain(allowed().post("{}".toRequestBody("application/json".toMediaType())).build())
        assertFailsWith<EgressRefused> { interceptor.intercept(chain) }
        assertFalse(chain.proceeded, "a POST must never reach the network")
    }

    @Test
    fun `a plaintext http url is refused`() {
        val chain = FakeChain(
            Request.Builder()
                .url("http://api.coingecko.com/api/v3/ping")
                .header("Accept", "application/json")
                .header("User-Agent", MarketHttp.USER_AGENT)
                .get()
                .build(),
        )
        assertFailsWith<EgressRefused> { interceptor.intercept(chain) }
        assertFalse(chain.proceeded, "cleartext must never reach the network")
    }

    @Test
    fun `an unknown host is refused`() {
        val chain = FakeChain(
            Request.Builder()
                .url("https://example.invalid/prices")
                .header("Accept", "application/json")
                .header("User-Agent", MarketHttp.USER_AGENT)
                .get()
                .build(),
        )
        assertFailsWith<EgressRefused> { interceptor.intercept(chain) }
        assertFalse(chain.proceeded, "a host outside K4 must never be reached")
    }

    @Test
    fun `a loopback host is refused too`() {
        val chain = FakeChain(
            Request.Builder().url("https://127.0.0.1:8080/x").get().build(),
        )
        assertFailsWith<EgressRefused> { interceptor.intercept(chain) }
    }

    @Test
    fun `an authorization header is refused`() {
        val chain = FakeChain(allowed().header("Authorization", "Bearer x").get().build())
        assertFailsWith<EgressRefused> { interceptor.intercept(chain) }
        assertFalse(chain.proceeded)
    }

    @Test
    fun `a cookie header is refused`() {
        val chain = FakeChain(allowed().header("Cookie", "a=b").get().build())
        assertFailsWith<EgressRefused> { interceptor.intercept(chain) }
    }

    @Test
    fun `only Accept and User-Agent may be sent`() {
        assertEquals(setOf("accept", "user-agent"), EgressInterceptor.ALLOWED_HEADERS)
    }

    // ---------------------------------------------------------------- WebSocket client frames

    @Test
    fun `only miniTicker usdt streams may be subscribed`() {
        assertTrue(WsPolicy.isAllowedParam("btcusdt@miniTicker"))
        assertTrue(WsPolicy.isAllowedParam("1000satsusdt@miniTicker"))
        assertFalse(WsPolicy.isAllowedParam("BTCUSDT@miniTicker"), "upper case is not the stream spelling")
        assertFalse(WsPolicy.isAllowedParam("btcusdt@trade"), "only miniTicker is subscribed")
        assertFalse(WsPolicy.isAllowedParam("btcirt@miniTicker"), "only USDT pairs")
        assertFalse(WsPolicy.isAllowedParam("!ticker@arr"), "no all-market stream")
        assertFalse(WsPolicy.isAllowedParam(""))
    }

    @Test
    fun `a subscribe frame carries only allowed streams`() {
        val frame = WsPolicy.subscribeFrame(listOf("btcusdt@miniTicker", "ethusdt@miniTicker"), id = 1)
        assertEquals(
            "{\"method\":\"SUBSCRIBE\",\"params\":[\"btcusdt@miniTicker\",\"ethusdt@miniTicker\"],\"id\":1}",
            frame,
        )
    }

    @Test
    fun `a subscribe frame with a forbidden stream throws`() {
        assertFailsWith<EgressRefused> {
            WsPolicy.subscribeFrame(listOf("btcusdt@miniTicker", "btcusdt@depth"), id = 2)
        }
    }

    @Test
    fun `streamOf builds the stream name of a symbol`() {
        assertEquals("btcusdt@miniTicker", WsPolicy.streamOf("BTC"))
        assertEquals(null, WsPolicy.streamOf("A-VERY-LONG-SYMBOL-NAME"))
    }
}
