package ir.andarz.app.ui.nav

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.graphics.vector.ImageVector
import ir.andarz.app.R

/**
 * The bottom bar of K6.1, built from one list so the owner can change it in a single place.
 *
 * Reading order is right to left: Today sits at the right (§5.0). `visible = false` hides a destination
 * from the bar without removing its route — Insights, for instance, lives in More and in a top-bar action
 * on Journal, and its route and content are unchanged (D-M07).
 */
data class AndarzDestination(
    val route: String,
    /** The `content.ui` key of the label. No Persian in code (law 3). */
    val labelKey: String,
    val icon: ImageVector? = null,
    @DrawableRes val iconRes: Int? = null,
    val visible: Boolean = true,
)

object AndarzNav {
    const val TODAY = "today"
    const val JOURNAL = "journal"
    const val MARKET = "market"
    const val CALCULATOR = "calculator"
    const val MORE = "more"

    /** Market's four sections (K6.1). The route carries ids only, so it survives process death. */
    const val MARKET_PRICES = "market/prices"
    const val MARKET_COINS = "market/coins"
    const val MARKET_NEWS = "market/news"
    const val MARKET_ALERTS = "market/alerts"
    const val MARKET_COIN = "market/coin/{id}"

    /** Insights keeps its own route; it is reached from More and from a Journal action (D-M07). */
    const val INSIGHTS = "insights"

    val bottomBar: List<AndarzDestination> = listOf(
        AndarzDestination(TODAY, "nav.today", icon = Icons.Filled.Home),
        AndarzDestination(JOURNAL, "nav.journal", icon = Icons.AutoMirrored.Filled.List),
        AndarzDestination(MARKET, "mkt.nav.market", iconRes = R.drawable.ic_nav_market),
        AndarzDestination(CALCULATOR, "nav.calculator", iconRes = R.drawable.ic_nav_calculator),
        AndarzDestination(MORE, "nav.more", icon = Icons.Filled.MoreVert),
    )
}

/** The four sections of the Market destination, in the order of the segmented row (K6.1). */
enum class MarketSection(val route: String, val labelKey: String) {
    PRICES(AndarzNav.MARKET_PRICES, "mkt.tab.prices"),
    COINS(AndarzNav.MARKET_COINS, "mkt.tab.coins"),
    NEWS(AndarzNav.MARKET_NEWS, "mkt.tab.news"),
    ALERTS(AndarzNav.MARKET_ALERTS, "mkt.tab.alerts"),
}
