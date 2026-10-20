package ir.andarz.app.ui.nav

/**
 * Allowlist for the route extra of [ir.andarz.app.MainActivity] (D-78).
 *
 * MainActivity is exported (it is the launcher), so any app can start it with any extra. Only the route
 * shapes Andarz itself puts into its notifications are accepted; anything else is dropped before it reaches
 * the navigation graph (android-intent-security: validate every incoming extra, apply the same check in
 * onCreate and onNewIntent).
 */
object DeepLinks {
    private val FIXED = setOf(
        AndarzNav.TODAY, AndarzNav.REVIEWS, AndarzNav.SETTINGS, AndarzNav.SECURITY,
        AndarzNav.MARKET, AndarzNav.MARKET_NEWS,
    )

    /** Trade ids are UUIDs or CSV/demo ids; coin ids are CoinGecko slugs. Both: short, no slash. */
    private val ID = Regex("^[A-Za-z0-9._-]{1,64}$")

    fun isAllowed(route: String?): Boolean {
        if (route.isNullOrEmpty() || route.length > 100) return false
        if (route in FIXED) return true
        val trade = route.removePrefix("trade/")
        if (trade != route) return ID.matches(trade)
        val coin = route.removePrefix("market/coin/")
        if (coin != route) return ID.matches(coin)
        return false
    }

    fun sanitize(route: String?): String? = route?.takeIf { isAllowed(it) }
}
