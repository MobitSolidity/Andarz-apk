package ir.andarz.market.data

import ir.andarz.market.model.Coin
import ir.andarz.market.model.IranSource
import ir.andarz.market.model.LiveQuote
import ir.andarz.market.model.MarketOverview
import ir.andarz.market.model.Source
import ir.andarz.market.model.UsdtRate
import org.json.JSONArray
import org.json.JSONObject

/**
 * The response parsers of March's `CoinRepository`, `IranMarket` and `BinanceStream`, lifted out as pure
 * `String -> model` functions so the JVM tests can feed them fixtures (MERGE_MAP, D-M28). The logic is
 * March's line for line; only the clock became a parameter.
 *
 * `org.json` is the platform copy on the device and a test dependency on the JVM (D-M28).
 */
internal object Parsers {

    /** Reads a number that may arrive as a JSON number or a numeric string (March's `num()`). */
    fun JSONObject.num(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val v = opt(key)
        return when (v) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull()
            else -> null
        }?.takeIf { !it.isNaN() && !it.isInfinite() }
    }

    // ---------------------------------------------------------------- CoinGecko

    fun markets(json: String, now: Long): List<Coin> {
        val arr = JSONArray(json)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val sparkArr = o.optJSONObject("sparkline_in_7d")?.optJSONArray("price")
            val spark = if (sparkArr == null) {
                emptyList()
            } else {
                List(sparkArr.length()) { k -> sparkArr.optDouble(k) }.filter { !it.isNaN() }
            }
            Coin(
                id = o.getString("id"),
                rank = o.optInt("market_cap_rank", i + 1).takeIf { it > 0 } ?: (i + 1),
                symbol = o.getString("symbol").uppercase(java.util.Locale.ROOT),
                name = o.getString("name"),
                price = o.num("current_price") ?: 0.0,
                change24h = o.num("price_change_percentage_24h") ?: 0.0,
                marketCap = o.num("market_cap") ?: 0.0,
                volume24h = o.num("total_volume") ?: 0.0,
                high24h = o.num("high_24h") ?: 0.0,
                low24h = o.num("low_24h") ?: 0.0,
                change7d = o.num("price_change_percentage_7d_in_currency") ?: 0.0,
                ath = o.num("ath") ?: 0.0,
                athChange = o.num("ath_change_percentage") ?: 0.0,
                spark = spark,
                source = Source.COINGECKO,
                tickAt = now,
            )
        }
    }

    fun global(json: String): MarketOverview {
        val d = JSONObject(json).getJSONObject("data")
        return MarketOverview(
            totalCap = d.optJSONObject("total_market_cap")?.num("usd") ?: 0.0,
            capChange24h = d.num("market_cap_change_percentage_24h_usd") ?: 0.0,
            btcDominance = d.optJSONObject("market_cap_percentage")?.num("btc") ?: 0.0,
        )
    }

    /** Value plus the raw English classification; the UI maps it to a key. */
    fun fearGreed(json: String): Pair<Int, String>? {
        val o = JSONObject(json).getJSONArray("data").getJSONObject(0)
        val v = o.optString("value").toIntOrNull() ?: return null
        return v to o.optString("value_classification")
    }

    // ---------------------------------------------------------------- Iranian exchanges

    /** Nobitex quotes IRT markets in Rial, so every value is divided by 10. */
    fun nobitexUsdt(json: String, now: Long): UsdtRate? {
        val stats = JSONObject(json).optJSONObject("stats") ?: return null
        val o = stats.optJSONObject("usdt-rls") ?: return null
        val last = o.num("latest")?.takeIf { it > 0 } ?: return null
        return UsdtRate(
            toman = last / 10,
            change24h = o.num("dayChange") ?: 0.0,
            high = (o.num("dayHigh") ?: 0.0) / 10,
            low = (o.num("dayLow") ?: 0.0) / 10,
            bestBuy = (o.num("bestBuy") ?: 0.0) / 10,
            bestSell = (o.num("bestSell") ?: 0.0) / 10,
            source = IranSource.NOBITEX,
            at = now,
        )
    }

    fun nobitexQuotes(json: String, now: Long): Map<String, LiveQuote> {
        val stats = JSONObject(json).optJSONObject("stats") ?: return emptyMap()
        val out = HashMap<String, LiveQuote>()
        for (key in stats.keys()) {
            if (!key.endsWith("-usdt")) continue
            val o = stats.optJSONObject(key) ?: continue
            if (o.optBoolean("isClosed", false)) continue
            val p = o.num("latest")?.takeIf { it > 0 } ?: continue
            out[key.removeSuffix("-usdt").uppercase(java.util.Locale.ROOT)] = LiveQuote(p, o.num("dayChange") ?: 0.0, now)
        }
        return out
    }

    fun wallexUsdt(json: String, now: Long): UsdtRate? {
        val st = JSONObject(json).optJSONObject("result")?.optJSONObject("symbols")
            ?.optJSONObject("USDTTMN")?.optJSONObject("stats") ?: return null
        val last = st.num("lastPrice")?.takeIf { it > 0 } ?: return null
        return UsdtRate(
            toman = last,
            change24h = st.num("24h_ch") ?: 0.0,
            high = st.num("24h_highPrice") ?: 0.0,
            low = st.num("24h_lowPrice") ?: 0.0,
            bestBuy = st.num("bidPrice") ?: 0.0,
            bestSell = st.num("askPrice") ?: 0.0,
            source = IranSource.WALLEX,
            at = now,
        )
    }

    // ---------------------------------------------------------------- Binance miniTicker frames

    /** A frame is one ticker object or an array of them; keyed by base symbol (e.g. "BTC"), priced in USDT. */
    fun miniTicker(text: String, now: Long): Map<String, LiveQuote> {
        val t = text.trimStart()
        val out = HashMap<String, LiveQuote>()
        if (t.startsWith("[")) {
            val arr = JSONArray(t)
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { addTicker(it, now, out) }
        } else {
            addTicker(JSONObject(t), now, out)
        }
        return out
    }

    private fun addTicker(o: JSONObject, now: Long, out: MutableMap<String, LiveQuote>) {
        if (o.optString("e") != "24hrMiniTicker") return
        val s = o.optString("s")
        if (!s.endsWith("USDT")) return
        val close = o.optString("c").toDoubleOrNull() ?: return
        val open = o.optString("o").toDoubleOrNull() ?: 0.0
        val change = if (open > 0) (close - open) / open * 100 else 0.0
        out[s.removeSuffix("USDT")] = LiveQuote(close, change, now)
    }
}
