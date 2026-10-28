package ir.andarz.app.content

import ir.andarz.app.ui.platform.SettingsDoc
import ir.andarz.data.settings.Settings
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * D-96 / D-97: the terms, privacy policy and risk warning ship in both languages with the same structure, name
 * every host Market can reach, carry the support address, and are never accepted on the user's behalf.
 */
class LegalContentTest {
    private fun asset(name: String): File = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        .firstOrNull { it.isFile } ?: error("asset $name not found; run :app:prepareContentAssets")

    private fun t(n: String) = asset(n).readText(Charsets.UTF_8)

    private val fa by lazy { ContentBundle.parse(t(ContentRepository.BASE_ASSET), t(ContentRepository.MARKET_ASSET), t(ContentRepository.GLOSSARY_ASSET), t(ContentRepository.APP_ASSET)) }
    private val en by lazy { ContentBundle.parse(t(ContentRepository.BASE_ASSET_EN), t(ContentRepository.MARKET_ASSET_EN), t(ContentRepository.GLOSSARY_ASSET), t(ContentRepository.APP_ASSET_EN)) }

    @Test
    fun `both languages ship the three documents with the same structure and version`() {
        val a = assertNotNull(fa.legal)
        val b = assertNotNull(en.legal)
        assertEquals(Legal.REQUIRED_IDS, a.docs.map { it.id })
        assertEquals(Legal.REQUIRED_IDS, b.docs.map { it.id })
        assertEquals(a.version, b.version)
        assertEquals(a.contact, b.contact)
        assertEquals(a.url, b.url)
        for ((x, y) in a.docs.zip(b.docs)) {
            assertEquals(x.sections.size, y.sections.size, "sections of ${x.id}")
            for ((s, r) in x.sections.zip(y.sections)) assertEquals(s.paragraphs.size, r.paragraphs.size, "paragraphs of ${x.id}/${r.heading}")
        }
        assertTrue(fa.validate().isEmpty(), fa.validate().joinToString("\n"))
        assertTrue(en.validate().isEmpty(), en.validate().joinToString("\n"))
    }

    @Test
    fun `the support address and the hosted URL are the owner's`() {
        val l = assertNotNull(fa.legal)
        assertEquals("mobitmaster@gmail.com", l.contact)
        assertEquals("https://mobitsolidity.github.io/Andarz-apk/", l.url)
    }

    @Test
    fun `the privacy policy names every host Market can reach, in both languages`() {
        for (b in listOf(fa, en)) {
            val text = b.legal!!.doc("privacy")!!.sections.flatMap { it.paragraphs }.joinToString(" ")
            for (h in ir.andarz.market.MarketHosts.all) assertTrue(h in text, "privacy policy does not name $h")
        }
    }

    @Test
    fun `nothing is accepted by default and a lower version asks again`() {
        val l = fa.legal!!
        assertEquals(0, Settings().legalAcceptedVersion)
        assertFalse(l.isAccepted(Settings().legalAcceptedVersion))
        assertFalse(l.isAccepted(l.version - 1))
        assertTrue(l.isAccepted(l.version))
    }

    @Test
    fun `a backup never carries the acceptance`() {
        val doc = SettingsDoc.of(Settings(legalAcceptedVersion = 7, legalAcceptedAt = 123L))
        assertFalse(doc.keys.any { it.startsWith("legal") }, doc.keys.toString())
    }

    @Test
    fun `the About line addresses every trader, not one owner`() {
        assertTrue(fa.uiOrNull("app.about.audience")!!.isNotBlank())
        assertTrue(en.uiOrNull("app.about.audience")!!.contains("any trader"))
    }

    @Test
    fun `the hosted page is generated from the same legal blocks`() {
        val page = listOf(File("../docs/index.html"), File("docs/index.html")).firstOrNull { it.isFile }
            ?: error("docs/index.html missing; run tools/legal/build_legal_site.py")
        val html = page.readText(Charsets.UTF_8)
        for (b in listOf(fa, en)) {
            for (d in b.legal!!.docs) for (s in d.sections) for (p in s.paragraphs) {
                val esc = p.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                assertTrue(esc in html, "docs/index.html is stale: missing '" + p.take(40) + "'; run tools/legal/build_legal_site.py")
            }
        }
    }
}
