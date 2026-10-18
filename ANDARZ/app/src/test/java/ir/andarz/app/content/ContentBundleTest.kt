package ir.andarz.app.content

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ir.andarz.engine.Clause
import ir.andarz.engine.Params
import ir.andarz.engine.Ref
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The wording rules of K7 and the content-integrity rules of §10.3, checked against the documents that are
 * really shipped. Pure JVM: no Android, no Robolectric.
 *
 * It reads the two assets from the module directory, so it fails loudly if the copy step
 * (`:app:prepareContentAssets`) has not run.
 */
class ContentBundleTest {

    private fun asset(name: String): File {
        val candidates = listOf(
            File("src/main/assets/" + name),
            File("app/src/main/assets/" + name),
            File("../app/src/main/assets/" + name),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error(
                "asset " + name + " not found. Run ./gradlew :app:prepareContentAssets first. Looked in: " +
                    candidates.joinToString { it.absolutePath },
            )
    }

    private val bundle: ContentBundle by lazy {
        ContentBundle.parse(
            asset(ContentRepository.BASE_ASSET).readText(Charsets.UTF_8),
            asset(ContentRepository.MARKET_ASSET).readText(Charsets.UTF_8),
        )
    }

    // ---------------------------------------------------------------- the two documents together

    @Test
    fun `the shipped documents are consistent`() {
        val problems = bundle.validate()
        assertTrue(
            problems.isEmpty(),
            "the content documents break the rules of K7:" + System.lineSeparator() +
                problems.joinToString(System.lineSeparator()) { "  - " + it },
        )
    }

    @Test
    fun `no ui key is defined by both documents`() {
        // The one sanctioned way to change a base string is the overrides block (K7).
        val base = ContentBundle.parse(asset(ContentRepository.BASE_ASSET).readText(Charsets.UTF_8))
        val marketOnly = bundle.keys - base.keys
        assertTrue(marketOnly.isNotEmpty(), "the market document must add keys")
        assertTrue(
            marketOnly.all { it.startsWith(ContentBundle.MARKET_PREFIX) },
            "every key the market document adds starts with " + ContentBundle.MARKET_PREFIX,
        )
    }

    @Test
    fun `every override replaces a base string`() {
        val base = ContentBundle.parse(asset(ContentRepository.BASE_ASSET).readText(Charsets.UTF_8))
        for (key in OVERRIDE_KEYS) {
            assertTrue(base.uiOrNull(key) != null, "override '" + key + "' must have a base string (K7)")
            assertTrue(
                bundle.uiOrNull(key) != base.uiOrNull(key),
                "override '" + key + "' must actually change the base string",
            )
        }
    }

    // ---------------------------------------------------------------- K2: no "the app has no internet"

    @Test
    fun `no shipped string claims the app has no internet`() {
        // Market talks to the network (K2), so a string that denies it is a lie, however reassuring.
        // The claim itself lives in tools/guards/guard_config.json, which is also what the Gradle guard 9
        // reads: one source of truth, and no Persian in a .kt file (law 3).
        val claims = guardConfigClaims()
        assertTrue(claims.isNotEmpty(), "guard_config.json must list at least one claim to look for")
        val offenders = bundle.keys.filter { key ->
            val text = bundle.uiOrNull(key) ?: return@filter false
            claims.any { text.contains(it) }
        }
        assertTrue(
            offenders.isEmpty(),
            "these strings claim the app has no internet: " + offenders.joinToString(),
        )
    }

    private fun guardConfigClaims(): List<String> {
        val candidates = listOf(
            File("../tools/guards/guard_config.json"),
            File("tools/guards/guard_config.json"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("guard_config.json not found; looked in " + candidates.joinToString { it.absolutePath })
        val root = Json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject
        return root["noInternetClaims"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
    }

    // ---------------------------------------------------------------- placeholders

    @Test
    fun `uiArgs matches the placeholders of its string`() {
        for ((key, declared) in bundle.uiArgs) {
            val text = bundle.uiOrNull(key) ?: continue
            assertEquals(
                declared.toSet(),
                ContentBundle.placeholdersOf(text),
                "uiArgs of '" + key + "' must list exactly the placeholders of the string",
            )
        }
    }

    @Test
    fun `every market placeholder is declared in uiArgs`() {
        for (key in bundle.keys.filter { it.startsWith(ContentBundle.MARKET_PREFIX) }) {
            val found = ContentBundle.placeholdersOf(bundle.uiOrNull(key)!!)
            if (found.isEmpty()) continue
            assertEquals(
                found,
                bundle.uiArgs[key]?.toSet() ?: emptySet(),
                "'" + key + "' carries placeholders, so uiArgs must declare them",
            )
        }
    }

    // ---------------------------------------------------------------- lookup behaviour

    @Test
    fun `arguments are filled by name`() {
        val text = bundle.ui("mkt.consent.hosts", "hosts" to "api.coingecko.com")
        assertTrue(text.contains("api.coingecko.com"))
        assertFalse(text.contains("{hosts}"), "the placeholder must be gone")
    }

    @Test
    fun `a missing key is reported instead of crashing`() {
        val fresh = ContentBundle.parse(
            asset(ContentRepository.BASE_ASSET).readText(Charsets.UTF_8),
            asset(ContentRepository.MARKET_ASSET).readText(Charsets.UTF_8),
        )
        assertEquals("[[mkt.nope.nothing]]", fresh.ui("mkt.nope.nothing"))
        assertTrue("mkt.nope.nothing" in fresh.missingKeys(), "a missing key feeds MISSING_COPY.md")
    }

    @Test
    fun `the app name comes from the content document`() {
        assertTrue(bundle.appName.isNotBlank(), "meta.app.name drives the manifest label (trap 8)")
    }

    @Test
    fun `the K13 market keys are all present`() {
        for (key in K13_KEYS) {
            assertTrue(bundle.uiOrNull(key) != null, "K13 key '" + key + "' is missing")
        }
    }

    // ---------------------------------------------------------------- 10.3 content integrity (engine)

    @Test
    fun `every live clause renders to its original text with the default parameters`() {
        assertEquals(10, bundle.numberWords.size, "numberWords must hold one to ten (3.11)")
        val articles = bundle.obj("constitution")!!["articles"]!!.jsonArray
        var rendered = 0
        for (a in articles) {
            for (b in a.jsonObject["blocks"]!!.jsonArray) {
                val o = b.jsonObject
                val refs = o["refs"]?.jsonArray ?: continue
                val text = o["text"]!!.jsonPrimitive.content
                val list = refs.map {
                    val r = it.jsonObject
                    Ref(r["param"]!!.jsonPrimitive.content, r["literal"]!!.jsonPrimitive.content,
                        r["template"]!!.jsonPrimitive.content, r["fmt"]!!.jsonPrimitive.content)
                }
                assertEquals(text, Clause.render(text, list, Params.DEFAULTS, bundle.numberWords), o["id"].toString())
                rendered++
            }
        }
        assertTrue(rendered > 0, "no clause with refs found")
    }

    @Test
    fun `every parameter of the content document exists in Params`() {
        val keys = bundle.arr("params")!!.map { it.jsonObject["key"]!!.jsonPrimitive.content }
        assertEquals(Params.KEYS.toSet(), keys.toSet())
        for (it in bundle.arr("params")!!) {
            val o = it.jsonObject
            val k = o["key"]!!.jsonPrimitive.content
            assertEquals(Params.DEFAULTS[k], o["default"]!!.jsonPrimitive.content.toDouble(), k)
        }
    }

    @Test
    fun `every literal ui key used in Kotlin exists`() {
        val roots = listOf(File("src/main/java"), File("app/src/main/java")).filter { it.isDirectory }
        assertTrue(roots.isNotEmpty(), "source root not found")
        val call = Regex("""(?:\.t|\.ui|uiOrNull)\(\s*"([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+)"""")
        val missing = roots.first().walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            call.findAll(f.readText()).map { it.groupValues[1] }.filter { bundle.uiOrNull(it) == null }.map { f.name + ": " + it }
        }.toList()
        assertTrue(missing.isEmpty(), "ui keys used in code but defined nowhere:\n" + missing.joinToString("\n"))
    }

    @Test
    fun `dynamic ui keys resolve for every enum id`() {
        val enums = bundle.obj("enums")!!
        fun ids(e: String) = enums[e]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }
        val prefixes = mapOf(
            "trade.status." to ids("tradeStatus"), "trade.account." to ids("account"), "trade.event." to ids("tradeEvent"),
            "wiz.gate.reason." to ids("gateReason"), "wiz.gate.detail." to ids("gateReason"), "wiz.gate.fix." to ids("gateReason"),
            "size.short." to ids("sizeVerdict"), "close.reason." to ids("exitReason"), "wiz.entryOrder." to ids("entryOrder"),
            "lock." to ids("lockCode").map { "$it.title" }, "need." to ids("lockNeed"), "reduction." to ids("reductionCode"),
            "wiz.gate.slWhere." to ids("side"), "wiz.gate.tpWhere." to ids("side"),
            "today.status." to ir.andarz.engine.DashStatus.entries.map { it.name },
        )
        val missing = prefixes.flatMap { (pre, list) -> list.map { pre + it }.filter { bundle.uiOrNull(it) == null } }
        assertTrue(missing.isEmpty(), "missing dynamic keys: $missing")
    }

    private companion object {
        val OVERRIDE_KEYS = listOf("about.privacy", "onb.privacy.body")

        /** Every key K13 of the kickoff message specifies. */
        val K13_KEYS = listOf(
            "mkt.nav.market", "mkt.tab.prices", "mkt.tab.coins", "mkt.tab.news", "mkt.tab.alerts",
            "mkt.consent.title", "mkt.consent.body", "mkt.consent.hosts", "mkt.consent.accept",
            "mkt.consent.decline", "mkt.off.title", "mkt.off.body", "mkt.off.action",
            "mkt.set.enable", "mkt.set.enable.hint", "mkt.set.sources", "mkt.set.sources.body",
            "mkt.set.quiet", "mkt.net.title", "mkt.net.body", "mkt.net.sent", "mkt.disclaimer",
            "mkt.about.credit", "mkt.cal.expired.title", "mkt.cal.expired.body", "mkt.cal.validUntil",
            "mkt.link.title", "mkt.link.tp", "mkt.link.partial", "mkt.link.stop", "mkt.link.note",
            "mkt.link.nocoin", "mkt.link.needMarket", "mkt.link.notif.tp.title",
            "mkt.link.notif.stop.title", "mkt.link.notif.body", "mkt.chip.live", "mkt.chip.age",
            "mkt.chip.stale", "mkt.chip.note",
        )
    }
}
