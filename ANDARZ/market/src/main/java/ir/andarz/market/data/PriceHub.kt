package ir.andarz.market.data

import ir.andarz.market.MarketError
import ir.andarz.market.MarketException
import ir.andarz.market.MarketService
import ir.andarz.market.model.AlertScope
import ir.andarz.market.model.AlertSettings
import ir.andarz.market.model.Coin
import ir.andarz.market.model.CoinRule
import ir.andarz.market.model.LiveMode
import ir.andarz.market.model.LiveQuote
import ir.andarz.market.model.MarketOverview
import ir.andarz.market.model.MarketState
import ir.andarz.market.model.Source
import ir.andarz.market.model.USDT_IRT_ID
import ir.andarz.market.model.UsdtRate
import ir.andarz.market.net.toMarketError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * Single source of truth for prices, shared by the UI and (MK2) the background alert service. Port of
 * March's `PriceHub` object (MERGE_MAP row `data/PriceHub.kt`, ADAPT):
 *
 * - the singleton became a class with its sources, scope and clock injected, so the tests drive it with
 *   virtual time and a fake [MarketSources];
 * - every loop and every cadence of K5.2 is March's: CoinGecko 60 s on screen / 5 min in background /
 *   2 min after a 429; Iran every 10 s; overview every 5 min; ticks with a 15 s liveness window; samples
 *   every 5 s; a quote older than 10 min is unused;
 * - new: the hub runs only while the consent-and-master-switch [gate] is open **and** someone holds it
 *   (K4, K6.4). When either falls, every loop is cancelled and the socket closes;
 * - new: [extraSymbols] for the MK3 trade-level watcher;
 * - dropped: the language handling (Andarz owns it) and the alert evaluation, which MK2 hooks in through
 *   [onPublish].
 */
/** Everything one alert round needs, handed over after a snapshot is published. */
internal class PublishRound(
    val state: MarketState,
    val history: PriceHistory,
    val settings: AlertSettings,
    val rules: Map<String, CoinRule>,
    val watch: Set<String>,
    val now: Long,
)

internal class PriceHub(
    private val sources: MarketSources,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val gate: Flow<Boolean>,
    private val watchFlow: Flow<Set<String>>,
    private val rulesFlow: Flow<Map<String, CoinRule>>,
    private val settingsFlow: Flow<AlertSettings>,
) {
    private val _state = MutableStateFlow(MarketState())
    val state: StateFlow<MarketState> = _state.asStateFlow()
    val history = PriceHistory()

    /** Called after each published snapshot with the inputs of the alert evaluator (MK2). */
    @Volatile
    var onPublish: ((PublishRound) -> Unit)? = null

    private val kick = Channel<Unit>(Channel.CONFLATED)
    private val lock = Any()
    private val users = MutableStateFlow(0)
    @Volatile private var uiUsers = 0
    private var controller: Job? = null

    /** True while the loops run, i.e. while the network may be used. Read by the zero-traffic tests. */
    @Volatile
    var running: Boolean = false
        private set

    // Guarded by `lock`.
    private var base: List<Coin> = emptyList()
    private val binance = HashMap<String, LiveQuote>()
    private var iran: Map<String, LiveQuote> = emptyMap()
    private var iranAt = 0L
    private var usdt: UsdtRate? = null
    private var overview: MarketOverview? = null
    private var error: MarketError? = null
    private var geckoAt = 0L
    @Volatile private var lastWsAt = 0L
    private var dirty = true

    @Volatile private var settings = AlertSettings()
    @Volatile private var rules: Map<String, CoinRule> = emptyMap()
    @Volatile private var watch: Set<String> = emptySet()

    /** Base symbols the MK3 trade watcher wants streamed even in background-only runs (K6.4 d). */
    private val _extra = MutableStateFlow<Set<String>>(emptySet())
    var extraSymbols: Set<String>
        get() = _extra.value
        set(value) {
            _extra.value = value.map { it.uppercase(java.util.Locale.ROOT) }.toSet()
        }

    // ---------------------------------------------------------------- demand (K6.4)

    fun acquire(ui: Boolean) {
        val first: Boolean
        synchronized(lock) {
            if (ui) uiUsers++
            if (controller == null) controller = scope.launch { control() }
            first = users.value == 0
        }
        users.update { it + 1 }
        // March: the first holder starts the hub; a later UI holder kicks a fresh list (it may want the
        // sparkline a background run skipped). Kicking on the first acquire would make the loop fetch twice.
        if (!first && ui) kick.trySend(Unit)
    }

    fun release(ui: Boolean) {
        synchronized(lock) {
            if (users.value == 0) return
            if (ui && uiUsers > 0) uiUsers--
        }
        users.update { (it - 1).coerceAtLeast(0) }
    }

    fun refreshNow() {
        kick.trySend(Unit)
    }

    /** Starts the loops when the gate is open and someone holds the hub; stops them otherwise. */
    private suspend fun control() {
        combine(gate, users) { open, n -> open && n > 0 }
            .distinctUntilChanged()
            .collectLatest { run ->
                if (run) {
                    running = true
                    try {
                        coroutineScope { runLoops(this) }
                    } finally {
                        running = false
                    }
                }
            }
    }

    private fun runLoops(s: CoroutineScope) {
        s.launch { settingsFlow.collect { settings = it } }
        s.launch { rulesFlow.collect { rules = it } }
        s.launch { watchFlow.collect { watch = it } }
        s.launch { geckoLoop() }
        s.launch { overviewLoop() }
        s.launch { iranLoop() }
        s.launch { binanceLoop() }
        s.launch { publishLoop() }
    }

    private val wsLive: Boolean get() = now() - lastWsAt < WS_LIVE_MS

    private suspend fun geckoLoop() {
        while (true) {
            val ui = uiUsers > 0
            var wait = if (ui) GECKO_UI else GECKO_BG
            _state.update { it.copy(loading = true) }
            try {
                // Background polls skip the sparkline: about six times smaller (March).
                val fresh = sources.top(sparkline = ui)
                synchronized(lock) {
                    base = if (ui) {
                        fresh
                    } else {
                        val oldSpark: Map<String, List<Double>> = base.associate { it.id to it.spark }
                        fresh.map { c -> c.copy(spark = oldSpark[c.id] ?: emptyList()) }
                    }
                    geckoAt = now()
                    error = null
                    dirty = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val typed = (e as? MarketException)?.error ?: toMarketError(e, MarketService.COIN_LIST)
                synchronized(lock) {
                    error = typed
                    dirty = true
                }
                if (typed is MarketError.RateLimited) wait = GECKO_429
            }
            _state.update { it.copy(loading = false) }
            withTimeoutOrNull(wait) { kick.receive() }
        }
    }

    private suspend fun overviewLoop() {
        while (true) {
            val ov = sources.overview()
            if (ov != null) {
                synchronized(lock) {
                    overview = ov
                    dirty = true
                }
            }
            delay(OVERVIEW_EVERY)
        }
    }

    private suspend fun iranLoop() {
        while (true) {
            try {
                val snap = sources.iran(withCoins = !wsLive)
                synchronized(lock) {
                    if (snap.usdt != null) usdt = snap.usdt
                    if (snap.quotes.isNotEmpty()) {
                        iran = snap.quotes
                        iranAt = now()
                    }
                    dirty = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keep the last known rate; the UI shows its age (March).
            }
            delay(IRAN_EVERY)
        }
    }

    private class Resubscribe : Exception()

    /** Symbols to stream: everything while the UI is open, otherwise only what alerts and MK3 need. */
    fun wantedSymbols(): Set<String> = synchronized(lock) {
        val coins = if (uiUsers > 0 || settings.scope == AlertScope.ALL) {
            base
        } else {
            val ids = watch + rules.keys
            base.filter { it.id in ids }
        }
        val out = HashSet<String>()
        coins.mapTo(out) { it.symbol }
        out.addAll(_extra.value)
        out.filterTo(HashSet()) { it != "USDT" && SYMBOL_OK.matches(it) }
    }

    private suspend fun binanceLoop() {
        var backoff = 2_000L
        while (true) {
            val wanted = wantedSymbols()
            if (wanted.isEmpty()) {
                delay(2_000)
                continue
            }
            try {
                coroutineScope {
                    launch {
                        while (true) {
                            delay(10_000)
                            if (wantedSymbols() != wanted) throw Resubscribe()
                        }
                    }
                    sources.ticks(wanted).collect { batch ->
                        backoff = 2_000L
                        synchronized(lock) {
                            binance.putAll(batch)
                            lastWsAt = now()
                            dirty = true
                        }
                    }
                    throw java.io.IOException("stream closed")
                }
            } catch (e: Resubscribe) {
                continue
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Blocked or offline: Nobitex takes over as the live source until we reconnect (March).
            }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(60_000L)
        }
    }

    private suspend fun publishLoop() {
        var lastSample = 0L
        while (true) {
            delay(1_000)
            val snap = synchronized(lock) {
                if (!dirty) {
                    null
                } else {
                    dirty = false
                    compose()
                }
            } ?: continue
            _state.value = snap
            val t = now()
            if (t - lastSample >= SAMPLE_EVERY) {
                lastSample = t
                for (c in snap.coins) history.add(c.id, t, c.price)
            }
            onPublish?.invoke(PublishRound(snap, history, settings, rules, watch, t))
        }
    }

    /** Merges CoinGecko's list with the freshest live quote per symbol. Call while holding the lock. */
    internal fun compose(): MarketState {
        val t = now()
        val ws = t - lastWsAt < WS_LIVE_MS
        val iranFresh = t - iranAt < 3 * IRAN_EVERY
        val seen = HashSet<String>()
        val coins = ArrayList<Coin>(base.size + 1)
        usdt?.let { coins += usdtCoin(it) }
        for (c in base) {
            // Several CoinGecko coins can share a ticker: only the highest-ranked one gets live prices.
            if (!seen.add(c.symbol)) {
                coins += c
                continue
            }
            val b = if (ws) binance[c.symbol]?.takeIf { t - it.at < WS_QUOTE_MAX_AGE } else null
            val n = if (iranFresh) iran[c.symbol] else null
            val q = b ?: n
            // Sanity check against CoinGecko, so a same-ticker different token cannot hijack the price.
            if (q != null && c.price > 0 && abs(q.price / c.price - 1) < 0.3) {
                coins += c.copy(
                    price = q.price,
                    change24h = q.change24h,
                    source = if (q === b) Source.BINANCE else Source.NOBITEX,
                    tickAt = q.at,
                )
            } else {
                coins += c
            }
        }
        val mode = when {
            ws -> LiveMode.BINANCE
            iranFresh && iran.isNotEmpty() -> LiveMode.NOBITEX
            base.isNotEmpty() -> LiveMode.POLLING
            else -> LiveMode.CONNECTING
        }
        return MarketState(
            coins = coins,
            usdt = usdt,
            overview = overview,
            loading = _state.value.loading,
            error = error,
            updatedAt = if (mode == LiveMode.BINANCE || mode == LiveMode.NOBITEX) t else geckoAt,
            live = mode,
        )
    }

    private fun usdtCoin(u: UsdtRate) = Coin(
        id = USDT_IRT_ID,
        rank = 0,
        symbol = "USDT",
        name = "",
        price = u.toman,
        change24h = u.change24h,
        marketCap = 0.0,
        high24h = u.high,
        low24h = u.low,
        spark = history.series(USDT_IRT_ID),
        isToman = true,
        source = Source.NOBITEX,
        tickAt = u.at,
        iranSource = u.source,
    )

    // ---------------------------------------------------------------- test hooks

    /** Seeds the merge inputs directly, for the compose() table tests. */
    internal fun seedForTest(
        base: List<Coin> = emptyList(),
        binance: Map<String, LiveQuote> = emptyMap(),
        lastWsAt: Long = 0L,
        iran: Map<String, LiveQuote> = emptyMap(),
        iranAt: Long = 0L,
        usdt: UsdtRate? = null,
        geckoAt: Long = 0L,
    ) = synchronized(lock) {
        this.base = base
        this.binance.clear()
        this.binance.putAll(binance)
        this.lastWsAt = lastWsAt
        this.iran = iran
        this.iranAt = iranAt
        this.usdt = usdt
        this.geckoAt = geckoAt
    }

    internal companion object {
        const val GECKO_UI = 60_000L
        const val GECKO_BG = 5 * 60_000L
        const val GECKO_429 = 2 * 60_000L
        const val IRAN_EVERY = 10_000L
        const val OVERVIEW_EVERY = 5 * 60_000L
        const val SAMPLE_EVERY = 5_000L
        const val WS_LIVE_MS = 15_000L
        const val WS_QUOTE_MAX_AGE = 10 * 60_000L

        private val SYMBOL_OK = Regex("^[A-Z0-9]{2,15}$")
    }
}
