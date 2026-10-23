package ir.andarz.app.ui

import android.os.Bundle
import android.os.Parcel
import androidx.core.os.bundleOf
import ir.andarz.app.ui.analysis.CalcDraft
import ir.andarz.app.ui.wizard.WizardDraft
import ir.andarz.engine.Side
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Process death (A-03): the drafts kept in SavedStateHandle must survive a real Parcel round trip, which is what
 * the system does when it kills the app in the background. A non-serializable field would crash on onStop.
 */
@RunWith(RobolectricTestRunner::class)
class ProcessDeathTest {
    @Suppress("DEPRECATION")
    private fun <T : java.io.Serializable> roundTrip(value: T): Any? {
        val p = Parcel.obtain()
        try {
            bundleOf("k" to value).writeToParcel(p, 0)
            p.setDataPosition(0)
            val back = Bundle.CREATOR.createFromParcel(p)
            back.classLoader = value.javaClass.classLoader
            return back.getSerializable("k")
        } finally { p.recycle() }
    }

    @Test fun wizardDraftSurvives() {
        val d = WizardDraft(step = 3, symbol = "BTCUSDT", side = Side.SHORT, setupId = "s1", entry = "100", sl = "104", tp = "92", manualMask = 0x1F)
        assertEquals(d, roundTrip(d))
    }

    @Test fun calculatorDraftSurvives() {
        val d = CalcDraft(Side.LONG, "2000", "100", "96", "108", "5", "1", "0.04", "ETHUSDT")
        assertEquals(d, roundTrip(d))
    }
}
