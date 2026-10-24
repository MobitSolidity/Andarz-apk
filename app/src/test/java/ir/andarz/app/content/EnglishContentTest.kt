package ir.andarz.app.content

import ir.andarz.engine.Clause
import ir.andarz.engine.Params
import ir.andarz.engine.Ref
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * D-90 / D-92: the English wording mirrors the Persian one key for key, keeps every placeholder, renders every live
 * clause, and holds no Persian outside the parts that stay Persian on purpose (the CSV headers, the Persian
 * language name). The glossary has its 165 terms in ten sections in both languages.
 */
class EnglishContentTest {
    private fun asset(name: String): File = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        .firstOrNull { it.isFile } ?: error("asset $name not found; run :app:prepareContentAssets")

    private fun text(n: String) = asset(n).readText(Charsets.UTF_8)

    private val fa by lazy {
        ContentBundle.parse(text(ContentRepository.BASE_ASSET), text(ContentRepository.MARKET_ASSET), text(ContentRepository.GLOSSARY_ASSET), text(ContentRepository.APP_ASSET))
    }
    private val en by lazy {
        ContentBundle.parse(text(ContentRepository.BASE_ASSET_EN), text(ContentRepository.MARKET_ASSET_EN), text(ContentRepository.GLOSSARY_ASSET), text(ContentRepository.APP_ASSET_EN))
    }

    private val persian = Regex("[\\u0600-\\u06FF]")

    @Test
    fun `the English documents are consistent`() {
        val problems = en.validate()
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
        assertTrue(en.isLtr)
        assertFalse(fa.isLtr)
    }

    @Test
    fun `English defines exactly the Persian keys with the same placeholders`() {
        assertEquals(fa.keys, en.keys)
        for (k in fa.keys) {
            assertEquals(
                ContentBundle.placeholdersOf(fa.uiOrNull(k)!!), ContentBundle.placeholdersOf(en.uiOrNull(k)!!),
                "placeholders of $k",
            )
        }
    }

    @Test
    fun `no English ui string holds Persian except the Persian language name`() {
        val allowed = setOf("app.lang.fa", "set.digits.fa")
        val offenders = en.keys.filter { it !in allowed && persian.containsMatchIn(en.uiOrNull(it)!!) }
        assertTrue(offenders.isEmpty(), "Persian left in: $offenders")
    }

    @Test
    fun `no English document text holds Persian outside the csv block`() {
        val offenders = ArrayList<String>()
        fun walk(n: Any?, path: String) {
            when (n) {
                is JsonPrimitive -> if (n.isString && persian.containsMatchIn(n.content)) offenders += path
                is JsonObject -> n.forEach { (k, v) -> if (!(path.isEmpty() && k in setOf("csv", "ui", "meta"))) walk(v, if (path.isEmpty()) k else "$path.$k") }
                is JsonArray -> n.forEachIndexed { i, v -> walk(v, "$path[$i]") }
                else -> Unit
            }
        }
        walk(en.base, "")
        // enum "csv" fields stay Persian on purpose: the CSV must match the Excel journal in both languages
        assertTrue(offenders.none { !it.endsWith(".csv") }, "Persian left in: ${offenders.filter { !it.endsWith(".csv") }}")
    }

    @Test
    fun `the CSV vocabulary is identical in both languages`() {
        assertEquals(fa.base["csv"], en.base["csv"])
    }

    @Test
    fun `every English live clause renders to its own text with the default parameters`() {
        assertEquals(10, en.numberWords.size)
        var n = 0
        for (a in en.obj("constitution")!!["articles"]!!.jsonArray) for (b in a.jsonObject["blocks"]!!.jsonArray) {
            val o = b.jsonObject
            val refs = o["refs"]?.jsonArray ?: continue
            val t = o["text"]!!.jsonPrimitive.content
            val list = refs.map { r -> r.jsonObject.let { Ref(it["param"]!!.jsonPrimitive.content, it["literal"]!!.jsonPrimitive.content, it["template"]!!.jsonPrimitive.content, it["fmt"]!!.jsonPrimitive.content) } }
            assertEquals(t, Clause.render(t, list, Params.DEFAULTS, en.numberWords, fa = false), o["id"].toString())
            n++
        }
        assertTrue(n >= 20)
    }

    @Test
    fun `English clause values use Latin digits and the percent sign`() {
        assertEquals("1%", Clause.formatRef(0.01, "pct", en.numberWords, fa = false))
        assertEquals("10", Clause.formatRef(10.0, "int", en.numberWords, fa = false))
        assertEquals("two", Clause.formatRef(2.0, "word", en.numberWords, fa = false))
        assertEquals("0.5", Clause.formatRef(0.5, "dec", en.numberWords, fa = false))
        // Persian stays the default the golden vectors pin
        assertEquals("\u06F1\u066A", Clause.formatRef(0.01, "pct", fa.numberWords))
    }

    @Test
    fun `the glossary has 165 terms in ten sections in both languages`() {
        for (b in listOf(fa, en)) {
            val g = b.glossary
            assertEquals(10, g.sections.size)
            assertEquals(165, g.terms.size)
            assertEquals((1..10).toList(), g.sections.map { it.n })
            assertEquals(listOf(15, 16, 17, 12, 21, 19, 21, 13, 15, 16), (1..10).map { g.termsOf(it).size })
            assertTrue(g.terms.all { it.term.isNotBlank() && it.latin.isNotBlank() && it.meaning.isNotBlank() })
            assertTrue(g.sections.all { it.title.isNotBlank() })
        }
        assertTrue(en.glossary.terms.none { persian.containsMatchIn(it.meaning + it.example + it.term) })
        assertTrue(fa.glossary.terms.all { persian.containsMatchIn(it.term) })
    }

    @Test
    fun `glossary search matches either language and ignores case and ZWNJ`() {
        assertTrue(en.glossary.search("liquidation price").isNotEmpty())
        assertTrue(fa.glossary.search("LIQUIDATION").isNotEmpty(), "the Latin name is searchable in Persian")
        val zwnj = fa.glossary.search("\u0627\u0633\u062A\u06CC\u0628\u0644\u200C\u06A9\u0648\u06CC\u0646")
        assertEquals(zwnj, fa.glossary.search("\u0627\u0633\u062A\u06CC\u0628\u0644\u06A9\u0648\u06CC\u0646"))
        assertTrue(zwnj.isNotEmpty())
        assertEquals(165, fa.glossary.search("  ").size)
        assertTrue(fa.glossary.search("zzzz-nothing").isEmpty())
    }

    @Test
    fun `glossary clause references point at real clauses`() {
        val ids = HashSet<String>()
        for (a in fa.obj("constitution")!!["articles"]!!.jsonArray) for (b in a.jsonObject["blocks"]!!.jsonArray) {
            b.jsonObject["id"]?.jsonPrimitive?.content?.let { ids += it }
            (b.jsonObject["items"] as? JsonArray)?.forEach { (it as? JsonObject)?.get("id")?.jsonPrimitive?.content?.let { id -> ids += id } }
        }
        val ref = Regex("clause (\\d+-\\d+)")
        val refs = en.glossary.terms.flatMap { t -> ref.findAll(t.meaning + " " + t.example).map { it.groupValues[1] }.toList() }
        assertTrue(refs.size > 30)
        assertTrue(refs.all { it in ids }, "unknown clauses: ${refs.filter { it !in ids }}")
    }
}
