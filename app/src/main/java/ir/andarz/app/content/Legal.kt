package ir.andarz.app.content

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * The terms of use, privacy policy and risk warning (D-96), read from the `legal` block of the app document.
 * The text lives in content JSON like every other string (law 3); the same block feeds the hosted page
 * (`tools/legal/build_legal_site.py`), so the in-app and online versions can never drift apart.
 *
 * [version] is bumped whenever the meaning changes; the app then asks for acceptance again.
 */
data class LegalDoc(val id: String, val title: String, val sections: List<LegalSection>)

data class LegalSection(val heading: String, val paragraphs: List<String>)

data class Legal(
    val version: Int,
    val updated: String,
    val contact: String,
    val url: String,
    val docs: List<LegalDoc>,
) {
    fun doc(id: String): LegalDoc? = docs.firstOrNull { it.id == id }

    /** True when the user has accepted this version (or a later one) on this device. */
    fun isAccepted(acceptedVersion: Int): Boolean = version > 0 && acceptedVersion >= version

    companion object {
        /** The three documents every build must ship, in display order. */
        val REQUIRED_IDS = listOf("terms", "privacy", "risk")

        fun parse(o: JsonObject?): Legal? {
            o ?: return null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.content
            val version = (o["version"] as? JsonPrimitive)?.intOrNull ?: return null
            val docs = (o["docs"] as? JsonArray)?.mapNotNull { d ->
                val dj = d as? JsonObject ?: return@mapNotNull null
                val id = (dj["id"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                val title = (dj["title"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                val sections = (dj["sections"] as? JsonArray)?.mapNotNull { sec ->
                    val sj = sec as? JsonObject ?: return@mapNotNull null
                    val h = (sj["h"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                    val ps = (sj["p"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
                    LegalSection(h, ps)
                }.orEmpty()
                LegalDoc(id, title, sections)
            }.orEmpty()
            return Legal(version, s("updated") ?: "", s("contact") ?: "", s("url") ?: "", docs)
        }
    }
}
