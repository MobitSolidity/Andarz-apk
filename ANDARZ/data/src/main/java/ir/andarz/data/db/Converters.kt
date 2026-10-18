package ir.andarz.data.db

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * JSON columns for lists (4.2). Built from `JsonElement` by hand rather than generated serializers, so the
 * Room annotation processor sees only plain types. Doubles are validated by the repositories (trap 14).
 */
class Converters {
    @TypeConverter
    fun legsToJson(v: List<LegRow>): String = JsonArray(
        v.map {
            JsonObject(
                mapOf(
                    "price" to JsonPrimitive(it.price), "fraction" to JsonPrimitive(it.fraction),
                    "time" to (it.time?.let(::JsonPrimitive) ?: JsonNull), "fee" to JsonPrimitive(it.fee),
                ),
            )
        },
    ).toString()

    @TypeConverter
    fun legsFromJson(v: String): List<LegRow> = if (v.isEmpty()) emptyList() else Json.parseToJsonElement(v).jsonArray.map {
        val o = it.jsonObject
        LegRow(
            price = o["price"]!!.jsonPrimitive.content.toDouble(),
            fraction = o["fraction"]!!.jsonPrimitive.content.toDouble(),
            time = (o["time"] as? JsonPrimitive)?.longOrNull,
            fee = (o["fee"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0,
        )
    }

    @TypeConverter fun stringsToJson(v: List<String>): String = JsonArray(v.map(::JsonPrimitive)).toString()

    @TypeConverter
    fun stringsFromJson(v: String): List<String> =
        if (v.isEmpty()) emptyList() else Json.parseToJsonElement(v).jsonArray.map { it.jsonPrimitive.content }

    @TypeConverter fun longsToJson(v: List<Long>): String = JsonArray(v.map(::JsonPrimitive)).toString()

    @TypeConverter
    fun longsFromJson(v: String): List<Long> =
        if (v.isEmpty()) emptyList() else Json.parseToJsonElement(v).jsonArray.map { it.jsonPrimitive.content.toLong() }
}
