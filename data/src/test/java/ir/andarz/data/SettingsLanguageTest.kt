package ir.andarz.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import ir.andarz.data.settings.CalendarMode
import ir.andarz.data.settings.Digits
import ir.andarz.data.settings.Language
import ir.andarz.data.settings.Settings
import ir.andarz.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** D-90 / D-91: the language switch and the amount mask persist, and the language carries digits and calendar. */
class SettingsLanguageTest {
    private val dir: File = Files.createTempDirectory("andarz-settings").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "s.preferences_pb") })

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    @Test
    fun `defaults are Persian with amounts visible`() = runBlocking {
        val s = repo.current()
        assertEquals(Language.FA, s.language)
        assertFalse(s.hideAmounts)
        assertEquals(Settings().digits, s.digits)
    }

    @Test
    fun `switching to English sets Latin digits and the Gregorian calendar, and back`() = runBlocking {
        repo.setLanguage(Language.EN)
        var s = repo.current()
        assertEquals(Language.EN, s.language)
        assertEquals(Digits.EN, s.digits)
        assertEquals(CalendarMode.GREGORIAN, s.calendar)
        // digits stay adjustable on their own afterwards
        repo.setDigits(Digits.FA)
        assertEquals(Language.EN, repo.current().language)
        assertEquals(Digits.FA, repo.current().digits)
        repo.setLanguage(Language.FA)
        s = repo.current()
        assertEquals(Language.FA, s.language)
        assertEquals(Digits.FA, s.digits)
        assertEquals(CalendarMode.JALALI, s.calendar)
    }

    @Test
    fun `the amount mask toggles and persists`() = runBlocking {
        repo.setHideAmounts(true)
        assertTrue(repo.current().hideAmounts)
        repo.setHideAmounts(false)
        assertFalse(repo.current().hideAmounts)
    }
}
