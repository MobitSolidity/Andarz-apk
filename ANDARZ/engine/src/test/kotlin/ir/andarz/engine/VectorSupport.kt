package ir.andarz.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.abs

/** Shared helpers for the golden-vector runner (10.1). */
object Vectors {
    val root: JsonObject by lazy {
        val text = Vectors::class.java.getResource("/andarz_golden_vectors.json")!!.readText(Charsets.UTF_8)
        Json.parseToJsonElement(text).jsonObject
    }

    val numberWords: List<String> by lazy {
        val text = Vectors::class.java.getResource("/number_words_fa.json")!!.readText(Charsets.UTF_8)
        Json.parseToJsonElement(text).jsonObject["numberWords"]!!.jsonArray.map { it.jsonPrimitive.content }
    }

    private val tolAbs: Double by lazy { root["meta"]!!.jsonObject["tolerance"]!!.jsonObject["abs"]!!.jsonPrimitive.content.toDouble() }
    private val tolRel: Double by lazy { root["meta"]!!.jsonObject["tolerance"]!!.jsonObject["rel"]!!.jsonPrimitive.content.toDouble() }

    fun cases(category: String): List<JsonObject> = root[category]!!.jsonArray.map { it.jsonObject }

    /** `input.params` overrides the defaults (10.1). */
    fun params(input: JsonObject): Params {
        val o = input["params"] as? JsonObject ?: return Params.DEFAULTS
        return Params.of(o.mapValues { it.value.jsonPrimitive.content.toDouble() })
    }

    fun closeEnough(got: Double, exp: Double): Boolean = abs(got - exp) <= tolAbs + tolRel * abs(exp)

    /**
     * Deep comparison of an actual value (already converted to JSON) with the expected JSON. Doubles use the
     * tolerance; integers in the expectation also match an equal double; strings that parse as offset
     * instants compare as instants; `-0.0` equals `0.0`. Returns a list of mismatch descriptions.
     */
    fun diff(path: String, got: JsonElement, exp: JsonElement, out: MutableList<String> = ArrayList()): List<String> {
        when {
            exp is JsonNull -> if (got !is JsonNull) out += "$path: expected null, got $got"
            exp is JsonObject -> {
                if (got !is JsonObject) { out += "$path: expected object, got $got"; return out }
                for ((k, v) in exp) diff("$path.$k", got[k] ?: JsonNull, v, out)
                for (k in got.keys - exp.keys) out += "$path.$k: unexpected key"
            }
            exp is JsonArray -> {
                if (got !is JsonArray) { out += "$path: expected array, got $got"; return out }
                if (got.size != exp.size) { out += "$path: expected ${exp.size} items, got ${got.size}: $got"; return out }
                exp.indices.forEach { diff("$path[$it]", got[it], exp[it], out) }
            }
            exp is JsonPrimitive -> {
                if (got !is JsonPrimitive || got is JsonNull) { out += "$path: expected $exp, got $got"; return out }
                if (exp.isString) {
                    val ei = instantOrNull(exp.content)
                    if (ei != null && got.isString) {
                        if (instantOrNull(got.content) != ei) out += "$path: expected $exp, got $got"
                    } else if (!got.isString || got.content != exp.content) out += "$path: expected $exp, got $got"
                } else if (exp.booleanOrNull != null) {
                    if (got.booleanOrNull != exp.booleanOrNull) out += "$path: expected $exp, got $got"
                } else {
                    val e = exp.doubleOrNull!!
                    val g = got.doubleOrNull
                    if (g == null || !closeEnough(g, e)) out += "$path: expected $exp, got $got"
                }
            }
        }
        return out
    }

    private fun instantOrNull(s: String): Instant? =
        if (s.length >= 20 && s[4] == '-' && s[10] == 'T') runCatching { OffsetDateTime.parse(s).toInstant() }.getOrNull() else null

    // ---- small JSON builders for converting engine results
    fun j(x: Double?): JsonElement = if (x == null) JsonNull else JsonPrimitive(x)
    fun j(x: Int?): JsonElement = if (x == null) JsonNull else JsonPrimitive(x)
    fun j(x: Boolean?): JsonElement = if (x == null) JsonNull else JsonPrimitive(x)
    fun j(x: String?): JsonElement = if (x == null) JsonNull else JsonPrimitive(x)
    fun obj(vararg pairs: Pair<String, JsonElement>): JsonObject = JsonObject(linkedMapOf(*pairs))
    fun arr(items: List<JsonElement>): JsonArray = JsonArray(items)

    // ---- readers
    fun JsonObject.d(k: String): Double = this[k]!!.jsonPrimitive.content.toDouble()
    fun JsonObject.dOrNull(k: String): Double? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDouble()
    fun JsonObject.s(k: String): String = this[k]!!.jsonPrimitive.content
    fun JsonObject.sOrNull(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
    fun JsonObject.i(k: String): Int = this[k]!!.jsonPrimitive.content.toDouble().toInt()
    fun JsonObject.b(k: String): Boolean = this[k]!!.jsonPrimitive.booleanOrNull!!
    fun JsonObject.bOrNull(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull
    fun JsonObject.strings(k: String): List<String> =
        (this[k] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()
    fun JsonObject.legs(k: String = "legs"): List<Leg> =
        (this[k] as? JsonArray)?.map { val o = it.jsonObject; Leg(o.d("price"), o.d("fraction")) } ?: emptyList()
}
