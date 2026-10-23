package ir.andarz.app.ui

import ir.andarz.app.platform.ReminderPlanner
import ir.andarz.app.ui.nav.AndarzNav
import ir.andarz.app.ui.nav.DeepLinks
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** D-78: only the routes Andarz puts into its own notifications pass the MainActivity route allowlist. */
class DeepLinksTest {
    @Test
    fun everyRouteTheAppGeneratesIsAllowed() {
        listOf(
            ReminderPlanner.ROUTE_TODAY, ReminderPlanner.ROUTE_REVIEWS, ReminderPlanner.ROUTE_BACKUP, ReminderPlanner.ROUTE_SECURITY,
            AndarzNav.trade("6f1c2a9e-1b2c-4d5e-8f90-123456789abc"), AndarzNav.trade("demo-01"),
            "market", "market/news", "market/coin/bitcoin", "market/coin/usd-coin",
        ).forEach { assertTrue(DeepLinks.isAllowed(it), it) }
    }

    @Test
    fun foreignOrMalformedRoutesAreDropped() {
        listOf(
            null, "", "onboarding", "wizard", "settings/../onboarding", "trade/", "trade/a/b", "market/coin/",
            "market/coin/x?y=1", "trade/" + "a".repeat(65), "licenses", "rules", "https://evil.example",
        ).forEach { assertFalse(DeepLinks.isAllowed(it), it.toString()) }
        assertNull(DeepLinks.sanitize("onboarding"))
    }
}
