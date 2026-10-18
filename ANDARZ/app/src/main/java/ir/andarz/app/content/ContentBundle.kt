package ir.andarz.app.content

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * The parsed content of the app, built from one or two JSON documents and nothing else. Pure Kotlin on
 * purpose: no Android type appears here, so every rule about wording can be tested on the JVM
 * (§10.3 content integrity).
 *
 * Two documents (K7, D-M12):
 *  - `content_fa.json`, copied from `spec/andarz_content_fa.json`, is the base. It is never edited.
 *  - `content_market_fa.json` adds the `mkt.*` keys and an `overrides` block whose values *replace*
 *    values of the base document.
 *
 * The rules [validate] enforces:
 *  - a `ui` key may not be defined by both documents (an override is the one sanctioned way to change a
 *    base string),
 *  - every `overrides` key must already exist in the base document,
 *  - every `uiArgs` entry must list exactly the placeholders its `ui` string carries.
 */
class ContentBundle private constructor(
    /** Effective wording: base `ui`, then the market `ui`, then the overrides. */
    private val uiMap: Map<String, String>,
    /** Placeholder names per key, from both documents. */
    val uiArgs: Map<String, List<String>>,
    /** The base document, for the typed accessors that grow with the milestones. */
    val base: JsonObject,
    /** The market document, or null when the app ships without it. */
    val market: JsonObject?,
) {

    /** Every known `ui` key. */
    val keys: Set<String> get() = uiMap.keys

    /** The wording of [key], or null when no document defines it. */
    fun uiOrNull(key: String): String? = uiMap[key]

    /**
     * The wording of [key] with its `{placeholder}` arguments filled. Values arrive already formatted for
     * the user's digit setting: this class never formats a number (§5, general rules).
     *
     * A key no document defines returns the debug marker `[[key]]` and is reported by
     * [missingKeys] so it lands in `MISSING_COPY.md` (§0.3) instead of crashing a screen.
     */
    fun ui(key: String, args: Map<String, String> = emptyMap()): String {
        val raw = uiMap[key] ?: run {
            seenMissing.add(key)
            return "[[" + key + "]]"
        }
        if (args.isEmpty()) return raw
        var out = raw
        for ((name, value) in args) {
            out = out.replace("{" + name + "}", value)
        }
        return out
    }

    /** Convenience form: `ui("mkt.consent.hosts", "hosts" to hostList)`. */
    fun ui(key: String, vararg args: Pair<String, String>): String = ui(key, args.toMap())

    private val seenMissing = linkedSetOf<String>()

    /** Keys that were asked for and do not exist. Feeds `MISSING_COPY.md`. */
    fun missingKeys(): Set<String> = seenMissing.toSet()

    // ---------------------------------------------------------------- typed access to the base document

    /** `meta.app.name` — the app's own name, also the source of the manifest label (trap 8). */
    val appName: String get() = str(base, "meta", "app", "name") ?: ""

    /** `meta.app.tagline`. */
    val tagline: String get() = str(base, "meta", "app", "tagline") ?: ""

    /**
     * The Persian words for one … ten used by the `word` clause format (3.11), from the market document's
     * `numberWords` (D-38). Empty when absent; `Clause.formatRef` then falls back to digits.
     */
    val numberWords: List<String>
        get() = (market?.get("numberWords") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()

    /** A nested object of the base document, e.g. `obj("params")` or `obj("enums")`. */
    fun obj(vararg path: String): JsonObject? = node(base, *path) as? JsonObject

    /** A nested array of the base document, e.g. `arr("params")`. */
    fun arr(vararg path: String): JsonArray? = node(base, *path) as? JsonArray

    // ---------------------------------------------------------------- integrity

    /**
     * Every problem the two documents have together, as readable English lines. An empty list means the
     * wording is consistent. The app does not refuse to start on a problem; the test does
     * (`ContentBundleTest`), which is what §10.3 asks for.
     */
    fun validate(): List<String> {
        val problems = ArrayList<String>()

        val baseUi = (node(base, "ui") as? JsonObject)?.keys ?: emptySet()
        val marketUi = (market?.let { node(it, "ui") } as? JsonObject)?.keys ?: emptySet()

        for (key in marketUi) {
            if (key in baseUi) {
                problems.add(
                    "ui key '" + key + "' is defined by both documents; " +
                        "change a base string through the overrides block, never by redefining it (K7)",
                )
            }
            if (!key.startsWith(MARKET_PREFIX)) {
                problems.add("ui key '" + key + "' lives in the market document but does not start with '" + MARKET_PREFIX + "'")
            }
        }

        val overrides = (market?.let { node(it, "overrides") } as? JsonObject) ?: JsonObject(emptyMap())
        for (key in overrides.keys) {
            if (key !in baseUi) {
                problems.add(
                    "override '" + key + "' has no base string to replace; " +
                        "an override may only restate a key of content_fa.json (K7)",
                )
            }
        }

        for ((key, declared) in uiArgs) {
            val text = uiMap[key]
            if (text == null) {
                problems.add("uiArgs names '" + key + "', which no document defines")
                continue
            }
            val found = placeholdersOf(text)
            if (found != declared.toSet()) {
                problems.add(
                    "uiArgs for '" + key + "' declares " + declared.sorted() +
                        " but the string carries " + found.sorted(),
                )
            }
        }

        return problems
    }

    companion object {
        /** Keys of the market document all carry this prefix (K7). */
        const val MARKET_PREFIX = "mkt."

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = false
        }

        private val PLACEHOLDER = Regex("""\{([A-Za-z0-9_]+)\}""")

        /** The placeholder names a wording string carries. */
        fun placeholdersOf(text: String): Set<String> =
            PLACEHOLDER.findAll(text).map { it.groupValues[1] }.toSet()

        /**
         * Parses the base document and, when present, the market document. Throws only when a document is
         * not JSON at all; everything softer is reported by [validate].
         */
        fun parse(baseJson: String, marketJson: String? = null): ContentBundle {
            val base = json.parseToJsonElement(baseJson).jsonObject
            val market = marketJson?.let { json.parseToJsonElement(it).jsonObject }

            val ui = LinkedHashMap<String, String>()
            (node(base, "ui") as? JsonObject)?.forEach { (k, v) ->
                (v as? JsonPrimitive)?.let { ui[k] = it.content }
            }
            market?.let { m ->
                (node(m, "ui") as? JsonObject)?.forEach { (k, v) ->
                    (v as? JsonPrimitive)?.let { ui[k] = it.content }
                }
                // Overrides come last: they replace base values (D-M12).
                (node(m, "overrides") as? JsonObject)?.forEach { (k, v) ->
                    (v as? JsonPrimitive)?.let { ui[k] = it.content }
                }
            }

            val args = LinkedHashMap<String, List<String>>()
            fun readArgs(doc: JsonObject) {
                (node(doc, "uiArgs") as? JsonObject)?.forEach { (k, v) ->
                    (v as? JsonArray)?.let { list ->
                        args[k] = list.mapNotNull { (it as? JsonPrimitive)?.content }
                    }
                }
            }
            readArgs(base)
            market?.let { readArgs(it) }

            return ContentBundle(ui, args, base, market)
        }

        private fun node(root: JsonObject, vararg path: String): Any? {
            var current: Any? = root
            for (segment in path) {
                current = (current as? JsonObject)?.get(segment) ?: return null
            }
            return current
        }

        private fun str(root: JsonObject, vararg path: String): String? =
            (node(root, *path) as? JsonPrimitive)?.content
    }
}
