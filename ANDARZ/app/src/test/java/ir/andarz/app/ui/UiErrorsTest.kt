package ir.andarz.app.ui

import android.database.sqlite.SQLiteFullException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/** A-01: a failed user action maps to a defined, user-facing message key and never crashes. */
@RunWith(RobolectricTestRunner::class)
class UiErrorsTest {
    @Test fun storageFailuresMapToStorageKey() {
        assertEquals("err.storage", UiErrors.keyFor(SQLiteFullException("full")))
        assertEquals("err.storage", UiErrors.keyFor(IOException("disk")))
    }

    @Test fun everythingElseMapsToGenericKey() {
        assertEquals("err.generic", UiErrors.keyFor(IllegalArgumentException("bad")))
        assertEquals("err.generic", UiErrors.keyFor(IllegalStateException("state")))
    }
}
