package ir.andarz.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
import kotlin.test.assertFailsWith

/** D-96: nothing is accepted by default, an explicit acceptance persists with its time, and version 0 is refused. */
class LegalAcceptanceTest {
    private val dir: File = Files.createTempDirectory("andarz-legal").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val file = File(dir, "s.preferences_pb")
    private val repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    @Test
    fun `a fresh install has accepted nothing`() = runBlocking {
        val s = repo.current()
        assertEquals(0, s.legalAcceptedVersion)
        assertEquals(0L, s.legalAcceptedAt)
    }

    @Test
    fun `accepting stores the version and the time`() = runBlocking {
        repo.acceptLegal(1, 1_700_000_000_000L)
        val s = repo.current()
        assertEquals(1, s.legalAcceptedVersion)
        assertEquals(1_700_000_000_000L, s.legalAcceptedAt)
        repo.acceptLegal(2, 1_800_000_000_000L)
        assertEquals(2, repo.current().legalAcceptedVersion)
    }

    @Test
    fun `version zero is refused`() {
        assertFailsWith<IllegalArgumentException> { runBlocking { repo.acceptLegal(0, 1L) } }
    }
}
