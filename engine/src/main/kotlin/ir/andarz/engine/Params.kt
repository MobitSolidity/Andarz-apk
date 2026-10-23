package ir.andarz.engine

/** Which way a parameter must move to make the rules stricter (3.2). It drives 3.9. */
enum class Tighter { LOWER, HIGHER, NEUTRAL }

/**
 * The 45 parameters of 3.2 as one immutable value. Every rule reads its numbers from here.
 *
 * Values are doubles, as in the reference (counts such as `Max_Trades_Day` compare as doubles there too).
 * Keys are exactly the keys of `meta.defaultParams` in the vectors and `params[].key` in the content JSON.
 */
class Params private constructor(private val values: Map<String, Double>) {

    operator fun get(key: String): Double =
        values[key] ?: throw IllegalArgumentException("unknown parameter $key")

    /** A copy with some keys replaced. Unknown keys are rejected so a typo cannot pass silently. */
    fun with(overrides: Map<String, Double>): Params {
        overrides.keys.forEach { require(it in DEFAULT_VALUES) { "unknown parameter $it" } }
        overrides.values.forEach { require(it.isFinite()) { "parameter values must be finite" } }
        return Params(values + overrides)
    }

    fun with(key: String, value: Double): Params = with(mapOf(key to value))

    fun toMap(): Map<String, Double> = values

    // ---- typed accessors (core, articles 2-13)
    val startEquity get() = this["Start_Equity"]
    val riskPct get() = this["Risk_Pct"]
    val riskMax get() = this["Risk_Max"]
    val levMax get() = this["Lev_Max"]
    val marginMax get() = this["Margin_Max"]
    val maintRate get() = this["Maint_Rate"]
    val rrMin get() = this["RR_Min"]
    val liqMult get() = this["Liq_Mult"]
    val openRiskMax get() = this["Open_Risk_Max"]
    val maxPositions get() = this["Max_Positions"]
    val dailyLossCap get() = this["Daily_Loss_Cap"]
    val weeklyLossCap get() = this["Weekly_Loss_Cap"]
    val ddCap get() = this["DD_Cap"]
    val maxTradesDay get() = this["Max_Trades_Day"]
    val withdrawPct get() = this["Withdraw_Pct"]
    val complianceTarget get() = this["Compliance_Target"]
    val pfMin get() = this["PF_Min"]
    val minTrades get() = this["Min_Trades"]
    val chkN get() = this["Chk_N"]

    // ---- governor
    val cooldownMin get() = this["Cooldown_Min"]
    val consecStops get() = this["Consec_Stops"]
    val stopoutR get() = this["Stopout_R"]
    val ddLockDays get() = this["DD_Lock_Days"]
    val recoveryRisk get() = this["Recovery_Risk"]
    val recoveryTrades get() = this["Recovery_Trades"]
    val violationPauseH get() = this["Violation_Pause_H"]
    val violationTrigger get() = this["Violation_Trigger"]
    val violationWindowD get() = this["Violation_Window_D"]
    val reducedRisk get() = this["Reduced_Risk"]
    val reducedTrades get() = this["Reduced_Trades"]
    val bigWinR get() = this["BigWin_R"]
    val bigWinDayPct get() = this["BigWin_Day_Pct"]
    val bigWinHoldH get() = this["BigWin_Hold_H"]
    val setupTestRisk get() = this["Setup_Test_Risk"]
    val setupTestTrades get() = this["Setup_Test_Trades"]
    val marginTarget get() = this["Margin_Target"]
    val timeStopCandles get() = this["Time_Stop_Candles"]
    val reviewAfterLossH get() = this["Review_After_Loss_H"]
    val loosenDelayH get() = this["Loosen_Delay_H"]
    val watchlistMax get() = this["Watchlist_Max"]
    val reportMinChars get() = this["Report_Min_Chars"]
    val partialExitPct get() = this["Partial_Exit_Pct"]
    val partialRLo get() = this["Partial_R_Lo"]
    val partialRHi get() = this["Partial_R_Hi"]
    val trailLockR get() = this["Trail_Lock_R"]

    override fun equals(other: Any?): Boolean = other is Params && other.values == values
    override fun hashCode(): Int = values.hashCode()
    override fun toString(): String = "Params($values)"

    companion object {
        /** Defaults of 3.2, equal to `meta.defaultParams` of the vectors and `DEFAULTS` of the reference. */
        private val DEFAULT_VALUES: Map<String, Double> = linkedMapOf(
            "Start_Equity" to 2000.0, "Risk_Pct" to 0.01, "Risk_Max" to 0.02, "Lev_Max" to 10.0,
            "Margin_Max" to 0.10, "Maint_Rate" to 0.005, "RR_Min" to 1.5, "Liq_Mult" to 2.0,
            "Open_Risk_Max" to 0.03, "Max_Positions" to 3.0, "Daily_Loss_Cap" to 0.03,
            "Weekly_Loss_Cap" to 0.06, "DD_Cap" to 0.10, "Max_Trades_Day" to 3.0, "Withdraw_Pct" to 0.30,
            "Compliance_Target" to 0.90, "PF_Min" to 1.3, "Min_Trades" to 30.0, "Chk_N" to 8.0,
            "Cooldown_Min" to 30.0, "Consec_Stops" to 2.0, "Stopout_R" to -0.5, "DD_Lock_Days" to 7.0,
            "Recovery_Risk" to 0.005, "Recovery_Trades" to 20.0, "Violation_Pause_H" to 48.0,
            "Violation_Trigger" to 3.0, "Violation_Window_D" to 30.0, "Reduced_Risk" to 0.005,
            "Reduced_Trades" to 20.0, "BigWin_R" to 3.0, "BigWin_Day_Pct" to 0.03, "BigWin_Hold_H" to 24.0,
            "Setup_Test_Risk" to 0.0025, "Setup_Test_Trades" to 20.0, "Margin_Target" to 0.05,
            "Time_Stop_Candles" to 10.0, "Review_After_Loss_H" to 2.0, "Loosen_Delay_H" to 24.0,
            "Watchlist_Max" to 10.0, "Report_Min_Chars" to 20.0, "Partial_Exit_Pct" to 0.5,
            "Partial_R_Lo" to 1.0, "Partial_R_Hi" to 1.5, "Trail_Lock_R" to 3.0,
        )

        /** `TIGHTER` of the reference. `Chk_N` is neutral there; the UI also locks it (3.2). */
        val TIGHTER: Map<String, Tighter> = run {
            val lower = setOf(
                "Risk_Pct", "Risk_Max", "Lev_Max", "Margin_Max", "Open_Risk_Max", "Max_Positions",
                "Daily_Loss_Cap", "Weekly_Loss_Cap", "DD_Cap", "Max_Trades_Day", "Consec_Stops",
                "Recovery_Risk", "Violation_Trigger", "Reduced_Risk", "BigWin_R", "BigWin_Day_Pct",
                "Setup_Test_Risk", "Margin_Target", "Time_Stop_Candles", "Watchlist_Max",
            )
            val higher = setOf(
                "Maint_Rate", "RR_Min", "Liq_Mult", "Compliance_Target", "PF_Min", "Min_Trades",
                "Cooldown_Min", "Stopout_R", "DD_Lock_Days", "Recovery_Trades", "Violation_Pause_H",
                "Violation_Window_D", "Reduced_Trades", "BigWin_Hold_H", "Setup_Test_Trades",
                "Review_After_Loss_H", "Loosen_Delay_H", "Report_Min_Chars",
            )
            DEFAULT_VALUES.keys.associateWith {
                when (it) {
                    in lower -> Tighter.LOWER
                    in higher -> Tighter.HIGHER
                    else -> Tighter.NEUTRAL
                }
            }
        }

        val KEYS: List<String> = DEFAULT_VALUES.keys.toList()

        val DEFAULTS: Params = Params(DEFAULT_VALUES)

        /** Defaults with [overrides] applied (keys must be known). */
        fun of(overrides: Map<String, Double>): Params = DEFAULTS.with(overrides)
    }
}
