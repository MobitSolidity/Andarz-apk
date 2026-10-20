package ir.andarz.market.model

import ir.andarz.market.MarketError

/**
 * Market models, ported from March's `data/Models.kt` (MERGE_MAP row `data/Models.kt`).
 *
 * Every `tr()` and `label` of March is gone: :market holds no wording (law 3). The UI maps each enum value
 * to a content key. Defaults follow D-M08 (alerts, monitor, ring and Clock-app ring all off).
 */

/** Synthetic coin id for USDT priced in Toman on Iranian exchanges. */
const val USDT_IRT_ID: String = "usdt-irt"

/** Where a coin's current price came from. */
enum class Source { COINGECKO, NOBITEX, BINANCE }

/** The Iranian exchange a USDT/Toman rate came from. March stored the localized name; this is the typed value. */
enum class IranSource { NOBITEX, WALLEX }

data class Coin(
    val id: String,
    val rank: Int,
    val symbol: String,
    /** The catalog name (Latin). Empty for the synthetic USDT/Toman coin: the UI names it from content. */
    val name: String,
    /** USD, except when [isToman] (USDT/IRT) where it is Toman. */
    val price: Double,
    val change24h: Double,
    val marketCap: Double,
    val volume24h: Double = 0.0,
    val high24h: Double = 0.0,
    val low24h: Double = 0.0,
    val change7d: Double = 0.0,
    val ath: Double = 0.0,
    val athChange: Double = 0.0,
    /** 7 days, hourly (CoinGecko). For USDT/IRT: samples collected this session. */
    val spark: List<Double> = emptyList(),
    val isToman: Boolean = false,
    val source: Source = Source.COINGECKO,
    val tickAt: Long = 0L,
    /** Set on the synthetic USDT/Toman coin only. */
    val iranSource: IranSource? = null,
)

data class LiveQuote(val price: Double, val change24h: Double, val at: Long)

data class UsdtRate(
    val toman: Double,
    val change24h: Double,
    val high: Double,
    val low: Double,
    val bestBuy: Double,
    val bestSell: Double,
    val source: IranSource,
    val at: Long,
)

data class MarketOverview(
    val totalCap: Double,
    val capChange24h: Double,
    val btcDominance: Double,
    val fearGreed: Int? = null,
    /** Raw alternative.me classification ("Extreme Fear" ... "Extreme Greed"); the UI maps it to a key. */
    val fearLabel: String = "",
)

enum class LiveMode { CONNECTING, BINANCE, NOBITEX, POLLING }

data class MarketState(
    val coins: List<Coin> = emptyList(),
    val usdt: UsdtRate? = null,
    val overview: MarketOverview? = null,
    val loading: Boolean = true,
    /** The last failure of the coin list, typed (K4). The UI turns it into `mkt.err.*`. */
    val error: MarketError? = null,
    val updatedAt: Long = 0L,
    val live: LiveMode = LiveMode.CONNECTING,
)

// ---- Price alerts (the evaluator arrives in MK2; the settings live here because the hub reads them) ----

enum class AlertWindow(val minutes: Int) { M5(5), M15(15), H1(60), H24(1440) }

enum class AlertScope { WATCHLIST, ALL }

enum class AlertDirection { BOTH, UP, DOWN }

/** How hard an alert tries to get your attention. */
enum class RingMode { OFF, TARGETS, ALL }

/**
 * March's alert settings. Defaults per D-M08: alerts off, background monitor off, ring "notification only",
 * Clock-app ring off. The rest are March's values, which apply once the user turns alerts on.
 */
data class AlertSettings(
    val enabled: Boolean = false,
    val thresholdPct: Float = 5f,
    val window: AlertWindow = AlertWindow.M15,
    val scope: AlertScope = AlertScope.WATCHLIST,
    val direction: AlertDirection = AlertDirection.BOTH,
    val cooldownMin: Int = 30,
    /** Keep a foreground service alive so alerts fire while the app is closed. */
    val background: Boolean = false,
    val includeUsdt: Boolean = true,
    /** Used to personalise notification text. Never sent anywhere (K4). */
    val name: String = "",
    val ring: RingMode = RingMode.OFF,
    /** Ring through the phone's Clock app when Android allows it; the own alarm otherwise. */
    val clockApp: Boolean = false,
    /** P1 `mkt.set.quiet` (K6.6): inside the journal's quiet hours, alerts are posted silently. Default off. */
    val muteInQuiet: Boolean = false,
)

/** Per-coin overrides: own % threshold and one-shot target prices (USD, or Toman for USDT/IRT). */
data class CoinRule(
    val id: String,
    val pct: Float? = null,
    val above: Double? = null,
    val below: Double? = null,
) {
    val isEmpty: Boolean get() = pct == null && above == null && below == null
}
