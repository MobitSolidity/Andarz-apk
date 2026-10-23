package ir.andarz.market

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ir.andarz.market.model.AlertDirection
import ir.andarz.market.model.AlertScope
import ir.andarz.market.model.AlertSettings
import ir.andarz.market.model.AlertWindow
import ir.andarz.market.model.CoinRule
import ir.andarz.market.model.RingMode
import ir.andarz.market.model.USDT_IRT_ID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/**
 * Market's own preference store, separate from the journal's settings (D-M17). Port of March's
 * `data/Prefs.kt`: the keys are March's (`watch`, `alarms`, `toman`, `alert_*`, `user_name`,
 * `coin_rules`); `theme` and `lang` are dropped because Andarz owns both; `market_enabled` and `consent_at`
 * are new and default to "off/never", so a fresh install opens no socket (K4).
 *
 * Defaults of the alert keys follow D-M08 (off), not March's (on).
 */
private val Context.marketStore by preferencesDataStore(name = "market")

private val MARKET_ENABLED = booleanPreferencesKey("market_enabled")
private val CONSENT_AT = longPreferencesKey("consent_at")
private val WATCH = stringSetPreferencesKey("watch")
private val ALARMS = stringSetPreferencesKey("alarms")
private val TOMAN = booleanPreferencesKey("toman")
private val A_ENABLED = booleanPreferencesKey("alert_enabled")
private val A_PCT = floatPreferencesKey("alert_pct")
private val A_WINDOW = stringPreferencesKey("alert_window")
private val A_SCOPE = stringPreferencesKey("alert_scope")
private val A_DIR = stringPreferencesKey("alert_dir")
private val A_COOLDOWN = intPreferencesKey("alert_cooldown")
private val A_BG = booleanPreferencesKey("alert_bg")
private val A_USDT = booleanPreferencesKey("alert_usdt")
private val A_RING = stringPreferencesKey("alert_ring")
private val A_CLOCK = booleanPreferencesKey("alert_clock_app")
private val A_QUIET = booleanPreferencesKey("alert_quiet_mute")
private val NAME = stringPreferencesKey("user_name")
private val RULES = stringPreferencesKey("coin_rules")

/** March's default stars (K5.2). */
val DEFAULT_WATCH: Set<String> = setOf("bitcoin", "ethereum", "solana", "ripple", USDT_IRT_ID)

/** The state of the two gates in front of every request. */
data class MarketGate(
    /** The master switch of Settings -> Market (`mkt.set.enable`). Default off (D-M08). */
    val enabled: Boolean = false,
    /** When the user accepted the consent sheet, or null while they never did (K4). */
    val consentAt: Long? = null,
) {
    /** The hub may run only when both gates are open (K4, K6.4). */
    val mayUseNetwork: Boolean get() = enabled && consentAt != null
}

private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: fallback

private val DEFAULTS = AlertSettings()

internal fun readSettings(p: Preferences) = AlertSettings(
    enabled = p[A_ENABLED] ?: DEFAULTS.enabled,
    thresholdPct = p[A_PCT] ?: DEFAULTS.thresholdPct,
    window = enumOr(p[A_WINDOW], DEFAULTS.window),
    scope = enumOr(p[A_SCOPE], DEFAULTS.scope),
    direction = enumOr(p[A_DIR], DEFAULTS.direction),
    cooldownMin = p[A_COOLDOWN] ?: DEFAULTS.cooldownMin,
    background = p[A_BG] ?: DEFAULTS.background,
    includeUsdt = p[A_USDT] ?: DEFAULTS.includeUsdt,
    name = p[NAME] ?: DEFAULTS.name,
    ring = enumOr(p[A_RING], DEFAULTS.ring),
    clockApp = p[A_CLOCK] ?: DEFAULTS.clockApp,
    muteInQuiet = p[A_QUIET] ?: DEFAULTS.muteInQuiet,
)

private fun MutablePreferences.write(s: AlertSettings) {
    this[A_ENABLED] = s.enabled
    this[A_PCT] = s.thresholdPct
    this[A_WINDOW] = s.window.name
    this[A_SCOPE] = s.scope.name
    this[A_DIR] = s.direction.name
    this[A_COOLDOWN] = s.cooldownMin
    this[A_BG] = s.background
    this[A_USDT] = s.includeUsdt
    this[NAME] = s.name
    this[A_RING] = s.ring.name
    this[A_CLOCK] = s.clockApp
    this[A_QUIET] = s.muteInQuiet
}

/**
 * The settings document of the backup (K6.12, K9.1 a): stars, reminded releases, unit, alert settings and
 * rules. The consent stamp and the master switch are never exported, so a restore leaves Market off.
 */
internal fun exportDoc(p: Preferences): JSONObject = JSONObject().apply {
    put("version", 1)
    put("watch", org.json.JSONArray((p[WATCH] ?: DEFAULT_WATCH).sorted()))
    put("alarms", org.json.JSONArray((p[ALARMS] ?: emptySet()).sorted()))
    put("toman", p[TOMAN] ?: false)
    val s = readSettings(p)
    put("alerts", JSONObject().apply {
        put("enabled", s.enabled); put("pct", s.thresholdPct.toDouble()); put("window", s.window.name); put("scope", s.scope.name)
        put("direction", s.direction.name); put("cooldown", s.cooldownMin); put("background", s.background)
        put("usdt", s.includeUsdt); put("name", s.name); put("ring", s.ring.name); put("clockApp", s.clockApp); put("muteInQuiet", s.muteInQuiet)
    })
    put("rules", JSONObject(encodeRules(decodeRules(p[RULES]))))
}

/** Applies a document; unknown fields are ignored, invalid values fall back to the current ones. */
internal fun importDoc(o: JSONObject, p: MutablePreferences) {
    fun strings(k: String): Set<String>? = o.optJSONArray(k)?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() && s.length <= 64 } }.toSet() }
    strings("watch")?.let { p[WATCH] = it }
    strings("alarms")?.let { p[ALARMS] = it }
    if (o.has("toman")) p[TOMAN] = o.optBoolean("toman")
    o.optJSONObject("alerts")?.let { a ->
        val cur = readSettings(p)
        val next = cur.copy(
            enabled = a.optBoolean("enabled", cur.enabled),
            thresholdPct = a.optDouble("pct", cur.thresholdPct.toDouble()).takeIf { it.isFinite() && it > 0 && it <= 100 }?.toFloat() ?: cur.thresholdPct,
            window = enumOr(a.optString("window"), cur.window),
            scope = enumOr(a.optString("scope"), cur.scope),
            direction = enumOr(a.optString("direction"), cur.direction),
            cooldownMin = a.optInt("cooldown", cur.cooldownMin).takeIf { it in 1..1440 } ?: cur.cooldownMin,
            background = a.optBoolean("background", cur.background),
            includeUsdt = a.optBoolean("usdt", cur.includeUsdt),
            name = a.optString("name", cur.name).take(40),
            ring = enumOr(a.optString("ring"), cur.ring),
            clockApp = a.optBoolean("clockApp", cur.clockApp),
            muteInQuiet = a.optBoolean("muteInQuiet", cur.muteInQuiet),
        )
        p.write(next)
    }
    o.optJSONObject("rules")?.let { p[RULES] = encodeRules(decodeRules(it.toString())) }
}

private fun JSONObject.optNum(k: String): Double? =
    if (has(k) && !isNull(k)) optDouble(k).takeIf { !it.isNaN() } else null

internal fun decodeRules(raw: String?): Map<String, CoinRule> {
    if (raw.isNullOrBlank()) return emptyMap()
    return try {
        val o = JSONObject(raw)
        val out = LinkedHashMap<String, CoinRule>()
        for (id in o.keys()) {
            val r = o.optJSONObject(id) ?: continue
            val rule = CoinRule(id, r.optNum("pct")?.toFloat(), r.optNum("above"), r.optNum("below"))
            if (!rule.isEmpty) out[id] = rule
        }
        out
    } catch (_: Exception) {
        emptyMap()
    }
}

internal fun encodeRules(m: Map<String, CoinRule>): String {
    val o = JSONObject()
    for (r in m.values) {
        val j = JSONObject()
        r.pct?.let { j.put("pct", it.toDouble()) }
        r.above?.let { j.put("above", it) }
        r.below?.let { j.put("below", it) }
        o.put(r.id, j)
    }
    return o.toString()
}

internal class MarketPrefs(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.marketStore)

    val gate: Flow<MarketGate> = store.data.map { p ->
        MarketGate(
            enabled = p[MARKET_ENABLED] ?: false,
            consentAt = p[CONSENT_AT]?.takeIf { it > 0L },
        )
    }.distinctUntilChanged()

    val watch: Flow<Set<String>> = store.data.map { it[WATCH] ?: DEFAULT_WATCH }.distinctUntilChanged()
    val alarms: Flow<Set<String>> = store.data.map { it[ALARMS] ?: emptySet() }.distinctUntilChanged()
    val toman: Flow<Boolean> = store.data.map { it[TOMAN] ?: false }.distinctUntilChanged()
    val alertSettings: Flow<AlertSettings> = store.data.map { readSettings(it) }.distinctUntilChanged()
    val rules: Flow<Map<String, CoinRule>> = store.data.map { decodeRules(it[RULES]) }.distinctUntilChanged()

    /** Turning the switch off stops the hub, the service and every price alert (K4). */
    suspend fun setEnabled(on: Boolean) {
        store.edit { it[MARKET_ENABLED] = on }
    }

    /** Records the consent and turns Market on, in one write, as the sheet's accept button does. */
    suspend fun acceptConsent(at: Long) {
        store.edit {
            it[CONSENT_AT] = at
            it[MARKET_ENABLED] = true
        }
    }

    suspend fun toggleWatch(id: String) {
        store.edit {
            val cur = it[WATCH] ?: DEFAULT_WATCH
            it[WATCH] = if (id in cur) cur - id else cur + id
        }
    }

    suspend fun toggleAlarm(id: String): Boolean {
        var on = false
        store.edit {
            val cur = it[ALARMS] ?: emptySet()
            on = id !in cur
            it[ALARMS] = if (on) cur + id else cur - id
        }
        return on
    }

    suspend fun setToman(on: Boolean) {
        store.edit { it[TOMAN] = on }
    }

    suspend fun updateSettings(change: (AlertSettings) -> AlertSettings) {
        store.edit { p -> p.write(change(readSettings(p))) }
    }

    suspend fun setRule(rule: CoinRule) {
        store.edit { p ->
            val m = decodeRules(p[RULES]).toMutableMap()
            if (rule.isEmpty) m.remove(rule.id) else m[rule.id] = rule
            p[RULES] = encodeRules(m)
        }
    }

    suspend fun exportJson(): String = exportDoc(store.data.first()).toString()

    /** Throws on malformed JSON; never touches consent or the master switch (K6.12). */
    suspend fun importJson(json: String) {
        val o = JSONObject(json)
        store.edit { importDoc(o, it) }
    }

    suspend fun clearTarget(id: String, above: Boolean) {
        store.edit { p ->
            val m = decodeRules(p[RULES]).toMutableMap()
            val r = m[id] ?: return@edit
            val next = if (above) r.copy(above = null) else r.copy(below = null)
            if (next.isEmpty) m.remove(id) else m[id] = next
            p[RULES] = encodeRules(m)
        }
    }
}
