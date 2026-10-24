package ir.andarz.app.content

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The trading and exchange glossary of Learn (D-92), read from `assets/glossary.json`.
 *
 * The asset holds both languages side by side: `{ "fa": ..., "en": ... }` for every translatable field. The term's
 * Latin name (`latin`) is the same in both. Pure Kotlin so the data can be checked on the JVM.
 */
data class Glossary(val sections: List<Section>, val terms: List<Term>) {
    data class Section(val n: Int, val title: String, val note: String)

    data class Term(
        val section: Int,
        /** The term in the display language. */
        val term: String,
        /** The international (Latin) name, shown as the equivalent. */
        val latin: String,
        val meaning: String,
        val example: String,
    ) {
        /** Case- and space-insensitive match on every visible field. */
        fun matches(q: String): Boolean {
            val k = normalize(q)
            if (k.isEmpty()) return true
            return listOf(term, latin, meaning, example).any { normalize(it).contains(k) }
        }
    }

    fun termsOf(section: Int): List<Term> = terms.filter { it.section == section }

    fun search(q: String): List<Term> = terms.filter { it.matches(q) }

    companion object {
        val EMPTY = Glossary(emptyList(), emptyList())

        /** Lower-case, ZWNJ and tatweel removed, Arabic yeh/kaf folded to Persian, spaces collapsed. */
        fun normalize(s: String): String = s.lowercase()
            .replace("\u200C", "").replace("\u0640", "")
            .replace('\u064A', '\u06CC').replace('\u0643', '\u06A9')
            .replace(Regex("\\s+"), " ").trim()

        fun from(doc: JsonObject?, lang: String): Glossary {
            if (doc == null) return EMPTY
            fun pick(o: JsonObject, k: String): String = when (val v = o[k]) {
                is JsonPrimitive -> v.content
                is JsonObject -> (v[lang] ?: v["fa"])?.jsonPrimitive?.content.orEmpty()
                else -> ""
            }
            val sections = (doc["sections"] as? JsonArray).orEmpty().map { it.jsonObject }.map {
                Section(it["n"]!!.jsonPrimitive.intOrNull ?: 0, pick(it, "title"), pick(it, "note"))
            }
            val terms = (doc["terms"] as? JsonArray).orEmpty().map { it.jsonObject }.map {
                Term(
                    section = it["section"]!!.jsonPrimitive.intOrNull ?: 0,
                    term = pick(it, "term"),
                    latin = pick(it, "latin"),
                    meaning = pick(it, "meaning"),
                    example = pick(it, "example"),
                )
            }
            return Glossary(sections, terms)
        }
    }
}
