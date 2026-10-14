# Andarz (اندرز) — Master Build Prompt

Version 1.0 · 2026-10-10 · This prompt is in English · The app is Persian (fa-IR, RTL) · Target: Android (Kotlin, Jetpack Compose)

> **Merged build.** When the workspace also contains the March price-and-news app (`march/`), the kickoff message `KICKOFF_MESSAGE.txt` amends this prompt (its section K2 lists every amended place) and wins where the two differ. Without `march/`, ignore this note.

## 0. Mission, inputs and working rules

### 0.1 Mission

You are a senior Android engineer. Build **Andarz** (اندرز, "counsel"): a Persian-first, offline-first Android app that **enforces its owner's written trading constitution** and keeps his **trading journal**. It replaces an audited Excel workbook and a 13-article constitution, and adds what a spreadsheet cannot: hard stops, cool-downs, locks, reviews, reminders and a calm, modern interface.

Before every entry the app answers one question: *"May I enter this trade, and how big?"* The answer is yes or no, always with numbers and the article it comes from. After the trade it records the result in R, checks compliance, and turns the history into weekly and monthly numbers.

The app is a personal risk-management tool, not financial advice. It never trades, never connects to an exchange, never sends data anywhere.

### 0.2 Companion files (copy them into `/spec` of the repo; they are inputs)

| File | What it is | How to use it |
|---|---|---|
| `andarz_golden_vectors.json` | 248 checks in 16 categories: plan, gate, size verdict, fill, equity curve, leverage advisor, KPIs, 33 governor scenarios, review-session and parameter rules, Jalali calendar, number formatting, backup crypto, live constitution clauses | Generate unit tests from it (§10). Every case must pass at tolerance 1e-9. |
| `andarz_ref.py` | Executable specification in plain Python. It produced the vectors. Its workbook-parity part was differentially tested against the Excel file (about 9,900 comparisons, zero mismatches); the governor part is defined here | Read it, then port function by function to Kotlin. Do not ship it. |
| `jalali_ref.py` | Jalali ↔ Gregorian conversion, verified against `jdatetime` for 73,414 days (both directions) | Port it. |
| `andarz_content_fa.json` | All user-visible Persian text (839 strings), the 13-article constitution with live-parameter bindings, lists (violations, mistakes, checklist), parameter metadata (45 parameters), KPI definitions, CSV profiles, calendar names, advice-of-the-day pool | Bundle as an asset and load at start-up. Kotlin code contains no Persian text. |
| `andarz_design_tokens.json` | Colors for dark and light themes with checked contrast, shapes, spacing, type scale, motion | Generate the theme from it (§6). |

**Source priority when two sources disagree:** (1) golden vectors, (2) `andarz_ref.py`, (3) this prompt, (4) content JSON for wording, (5) design tokens for visuals. Higher wins. Write every conflict into `DECISIONS.md`.

### 0.3 How to work

- Work milestone by milestone (§11). A milestone ends only when `./gradlew build` (lint + all unit tests) is green. Commit at the end of each milestone with a conventional message.
- Do not ask the owner questions. When something is unspecified, choose the simplest option that fits the constitution's spirit (survival first, nothing silent), and record it in `DECISIONS.md` (id, context, choice, reason).
- Missing wording: add the key to `MISSING_COPY.md` and show `[[key]]` in debug builds only. Never invent trading rules, parameters or Persian legal-sounding text.
- Never edit a golden vector or a number in `andarz_ref.py` to make a test pass. If you believe a vector is wrong, write the evidence in `DECISIONS.md` and keep the vector.
- Prefer small pure functions with tests over clever abstractions. No premature modularization beyond §2.

### 0.4 The ten laws (non-negotiable)

1. **Offline.** No `INTERNET` permission, no analytics or crash SDK, no Google Play Services, `allowBackup=false`.
2. **One engine.** Every rule and formula lives in the pure-Kotlin `:engine` module. The UI never re-implements a formula.
3. **No Persian text in code.** All wording comes from `ContentRepository` (the content JSON). A unit test fails the build if a `.kt` file contains a character in U+0600–U+06FF.
4. **Numbers are doubles with Excel-style rounding** exactly as in §3.1. Never format or parse numbers with the default `Locale`.
5. **A "no" always carries a reason, a number and an article.**
6. **History is never rewritten silently.** Derived trade fields are stored when they become known; changing a parameter never recomputes old trades.
7. **Rules change only in review sessions**, enforced in code: tightening applies at once, loosening applies 24 hours later (§3.9).
8. **The app never celebrates a win**: no confetti, streaks or praise. A win and a loss get the same neutral result card.
9. **Dark theme is the default.** Every screen must work in the light theme, in RTL, at 200% font scale and with TalkBack.
10. **All data stays on the phone.** The only ways out are the user's explicit backup, CSV, JSON and share actions.

## 1. Product definition

### 1.1 User and context

One user: a crypto-futures trader in Iran who writes in Persian, keeps a USD-denominated trading account (example: 2,000 USD) and trades on a phone. Android 8.0+ (minSdk 26). Many devices in this market have no Google Play Services and are installed from Bazaar or Myket, so the app must run without GMS. Connectivity is unreliable; nothing may depend on a network.

### 1.2 Scope

In scope (details in §5):

- The **gate**: checklist, position sizing, leverage advice, liquidation distance, a verdict with reasons.
- The **governor**: locks (violation pause, drawdown, weekly loss, daily loss, day over, trade cap, cool-down), risk reductions, big-win hold, parameter governance.
- The **journal**: plan → entry → management → exit → review, with partial exits, stop moves, events, screenshots, mistakes, violations.
- **Insights**: KPIs, equity curve, drawdown, R distribution, mistakes, setups.
- **Reviews**: daily close, weekly, monthly, drawdown review; withdrawal suggestion and ledger.
- **Constitution** reader with live numbers, pledge, learning pages, security checklist, setups, watch-list.
- **Backup** (encrypted), CSV import/export (including the Excel journal format), app lock, local notifications.

Out of scope: exchange or price-feed connections, order placement, signals, social features, cloud sync, ads, accounts, AI-generated advice, iOS. Do not add them.

### 1.3 Name and voice

*Andarz* means wise counsel. In Sasanian Iran the *andarzbad* was the chief advisor who spoke before the decision; the app plays that role before the entry button. Voice: calm, short, respectful, second person (informal "تو" form), no emoji, no exclamation marks in rule messages, always the number behind a "no". The app tagline is «اول بقا، بعد سود» (survival first, profit second).

## 2. Technology and structure

### 2.1 Stack

| Area | Choice |
|---|---|
| Language / build | Kotlin 2.x, Gradle Kotlin DSL with a version catalog, JDK 17 toolchain, current stable Android Gradle Plugin; compileSdk and targetSdk = latest stable; **minSdk 26** |
| UI | Jetpack Compose (current BOM), Material 3, Navigation Compose with type-safe routes, `core-splashscreen` |
| State | ViewModel + `StateFlow`; unidirectional data flow; `collectAsStateWithLifecycle` |
| DI | Hilt (KSP) |
| Persistence | Room (KSP, exported schemas, migration tests) for domain data; Preferences DataStore for settings |
| Background | WorkManager (inexact scheduling is fine) |
| Serialization | kotlinx-serialization-json |
| Images | Coil 3 only for decoding local files |
| Security | `androidx.biometric`; JCA for PBKDF2 and AES-GCM |
| Time | `java.time` (no desugaring needed at minSdk 26) |
| Tests | JUnit, kotlin-test, Turbine, Compose UI test, Robolectric only where needed |
| Forbidden | Retrofit/OkHttp, Firebase, Play Services, Crashlytics, any analytics or ads SDK, SQLCipher (decision D-12), Joda, `android.icu` for calendars (use §3.12) |

### 2.2 Modules and layout

```
andarz/
  spec/                     companion files (read-only inputs)
  engine/                   pure Kotlin/JVM library: no Android, no Compose
    src/main/kotlin/ir/andarz/engine/   XlMath, Params, Planner, Stats, Limits, Governor, Fmt, Clause, Jalali, Model.kt
    src/test/kotlin/                    VectorRunner + property tests
  data/                     Android library: Room, DataStore, repositories, backup (crypto), CSV
  app/                      Compose UI, navigation, Hilt, notifications, workers
    src/main/assets/content_fa.json     copied from spec/ at build time
    src/main/res/font/                  Vazirmatn 400/500/700 (OFL)
  DECISIONS.md  MISSING_COPY.md  README.md
```

`applicationId` is a placeholder: `ir.andarz.app`. Change it before any store release; the name's availability on Bazaar, Myket and Google Play was not verified.

### 2.3 Build and manifest rules

- Permissions: `POST_NOTIFICATIONS` (runtime, Android 13+), `USE_BIOMETRIC`, `VIBRATE`. Nothing else. No storage permissions: use the Storage Access Framework (`CreateDocument`, `OpenDocument`) and the Photo Picker.
- `android:allowBackup="false"`, `dataExtractionRules` and `fullBackupContent` exclude everything. `android:supportsRtl="true"`. `android:localeConfig` lists `fa` only.
- R8 on for release. `FLAG_SECURE` on the window when the setting "hide in recents" is on (default on when app lock is on).
- One activity (`MainActivity`), Compose-only UI. Edge-to-edge with insets handled.
- CI-style Gradle tasks: `./gradlew :engine:test`, `./gradlew build`, `./gradlew connectedCheck` (UI tests).


## 3. The engine (`:engine`) — rules and formulas

Everything in this section is implemented in `andarz_ref.py`. Port it function by function; the prose below tells you *why* and lists the edge cases. All functions are pure: no clock, no I/O, no Android. Time always arrives as a parameter (`now`).

### 3.1 Conventions

- **Numbers** are `Double`. Money is USD. Percentages are fractions (1% = 0.01).
- **Excel-style rounding** `xlRound(x, d)`: round half away from zero on the *shortest decimal representation* of the double. Python does `Decimal(repr(x)).quantize(…, ROUND_HALF_UP)`. In Kotlin build the shortest representation yourself, because `Double.toString` is not guaranteed shortest on every ART version:

```kotlin
fun shortest(x: Double): String {                 // JDK/ART independent
    for (p in 0..16) {                            // p+1 significant digits
        val s = String.format(java.util.Locale.ROOT, "%.${p}e", x)
        if (s.toDouble() == x) return s
    }
    return x.toString()
}
fun xlRound(x: Double, d: Int): Double =
    java.math.BigDecimal(shortest(x)).setScale(d, java.math.RoundingMode.HALF_UP).toDouble()
```

  `RoundingMode.HALF_UP` rounds ties away from zero, as Excel does. Vectors `xlRound` (18 cases) include 1.005 → 1.01, 2.675 → 2.68, −0.5 → −1.
- **Time**: instants are `Instant`; local arithmetic uses a `ZoneId` parameter (production default `Asia/Tehran`; the vectors use the fixed offset +03:30 because Iran has no DST). A *local day* is 00:00–24:00 in that zone. **The trading week starts on Saturday** and ends on Friday. Ranges of dates are inclusive.
- **Side** is `LONG` or `SHORT`. **Account** is `REAL` or `DEMO`; demo trades are ignored by every rule, limit and KPI.
- **R** (risk multiple) is always measured against the *initial* stop: `R = net / (notional × sld)`.
- **Guards.** The reference never divides by zero: where a denominator can be 0 or a value is missing it returns `None` (money and ratios) or `0.0` (`dd`, `dailyPct`, `weeklyPct`, `openRiskPct`). Port every guard literally, make those results nullable (`Double?`), and let a check whose input is `null` add no reason. A plan with entry = SL has `rr`, `notionalMax`, `margin`, `marginPct` and `liqX` all `null`, and `DIRECTION` rejects it. `Infinity` and `NaN` must never leave the engine. No vector has a null plan value, so write those tests yourself.

### 3.2 Parameters (45)

All rules read parameters from one immutable `Params` object. Defaults below equal `defaultParams` in the vector file and `params` in the content JSON (which also holds Persian labels, notes, ranges and steps). "Tighter" is the direction that makes the rule stricter; it drives §3.9.

| Key | Default | Tighter | Meaning (article) |
|---|---|---|---|
| `Start_Equity` | 2000 | neutral | Starting equity; mirrors the BASELINE ledger entry, not editable directly (2-1) |
| `Risk_Pct` | 1% | lower | Risk per trade (3-1) |
| `Risk_Max` | 2% | lower | Ceiling, grade-A setups only (3-1) |
| `Lev_Max` | 10 | lower | Absolute leverage cap (3-3) |
| `Margin_Max` | 10% | lower | Margin per position / equity (3-4) |
| `Maint_Rate` | 0.5% | higher | Maintenance margin rate for the liquidation estimate (3-5) |
| `RR_Min` | 1.5 | higher | Minimum reward-to-risk (4, item 6) |
| `Liq_Mult` | 2 | higher | Liquidation distance ÷ stop distance, minimum (3-5) |
| `Open_Risk_Max` | 3% | lower | Sum of open risk (3-6) |
| `Max_Positions` | 3 | lower | Simultaneous positions (3-6) |
| `Daily_Loss_Cap` | 3% | lower | (3-7) |
| `Weekly_Loss_Cap` | 6% | lower | (3-7) |
| `DD_Cap` | 10% | lower | Drawdown from the P&L-curve peak (3-7) |
| `Max_Trades_Day` | 3 | lower | (8-1) |
| `Withdraw_Pct` | 30% | neutral | Monthly withdrawal share of realized profit, 0–50% (7-2) |
| `Compliance_Target` | 90% | higher | (13-3) |
| `PF_Min` | 1.3 | higher | Profit-factor target (12-3) |
| `Min_Trades` | 30 | higher | Trades before judging a setup (12-4) |
| `Chk_N` | 8 | locked (the reference says neutral) | Checklist items; never editable (4) |
| `Cooldown_Min` | 30 | higher | Pause after a stop-out (8-1) |
| `Consec_Stops` | 2 | lower | Consecutive stop-outs that end the day (8-1) |
| `Stopout_R` | −0.5 | higher | A manual exit at or below this R counts as a stop-out (app rule) |
| `DD_Lock_Days` | 7 | higher | Minimum drawdown pause (3-7) |
| `Recovery_Risk` / `Recovery_Trades` | 0.5% / 20 | lower / higher | Risk after a drawdown release (3-7) |
| `Violation_Pause_H` | 48 | higher | Pause after a red-line violation (13-1) |
| `Violation_Trigger` / `Violation_Window_D` | 3 / 30 | lower / higher | Violations within the window that reduce risk (13-2) |
| `Reduced_Risk` / `Reduced_Trades` | 0.5% / 20 | lower / higher | Risk after repeated violations (13-2) |
| `BigWin_R` / `BigWin_Day_Pct` / `BigWin_Hold_H` | 3 / 3% / 24 | lower / lower / higher | Big-win thresholds and hold (3-8) |
| `Setup_Test_Risk` / `Setup_Test_Trades` | 0.25% / 20 | lower / higher | New-setup test (12-6) |
| `Margin_Target` | 5% | lower | Target margin used only to *recommend* a leverage (3-4) |
| `Time_Stop_Candles` | 10 | lower | Time stop in entry-timeframe candles (6-5) |
| `Review_After_Loss_H` | 2 | higher | No rule edits until this long after a losing close (12-5) |
| `Loosen_Delay_H` | 24 | higher | Delay before a loosening change takes effect (app rule) |
| `Watchlist_Max` | 10 | lower | (2-2) |
| `Report_Min_Chars` | 20 | higher | Minimum violation-report length (13-1) |
| `Partial_Exit_Pct` / `Partial_R_Lo` / `Partial_R_Hi` / `Trail_Lock_R` | 50% / 1.0 / 1.5 / 3.0 | neutral | Management guidance, never a lock (6-1, 6-2) |

### 3.3 Planning numbers

Position size comes only from the formula of article 3-2: **Position = Equity × Risk% ÷ SL distance%**. Leverage is chosen afterwards to supply the margin, never the other way round.

```
sld         = |entry − sl| / entry
rr          = |tp − entry| / |entry − sl|                 (null if entry = sl)
riskUsd     = equity × riskPct                            (riskPct = governor.riskCap, §3.9; default Risk_Pct)
notionalMax = riskUsd / sld
margin      = notionalMax / lev
marginPct   = margin / equity
liq         = LONG : entry × (1 − 1/lev + Maint_Rate)
              SHORT: entry × (1 + 1/lev − Maint_Rate)
liqDist     = |entry − liq| / entry
liqX        = liqDist / sld
qty         = notional / entry                            (display only: units of the asset)
```

Worked example (vector `P01`): equity 2000, long, entry 100, SL 96, TP 108, leverage 5 → `sld` 4%, `rr` 2, `riskUsd` 20, `notionalMax` 500, `margin` 100 (5%), `liq` 80.5, `liqX` 4.875, no reasons, allowed.

### 3.4 The gate

`gate(...)` returns the ordered list of reasons that apply (empty means allowed). Order is fixed:

| # | Reason | Condition |
|---|---|---|
| 1 | `DIRECTION` | LONG needs `sl < entry` and `tp > entry`; SHORT needs `sl > entry` and `tp < entry` |
| 2 | `CHECKLIST` | `chk < Chk_N` (`chk` = number of ticked items) |
| 3 | `LEVERAGE` | `lev > Lev_Max` |
| 4 | `RR` | `xlRound(rr, 4) < RR_Min` |
| 5 | `MARGIN` | `xlRound(marginPct, 4) > Margin_Max` |
| 6 | `LIQUIDATION` | `xlRound(liqX, 4) < Liq_Mult` |

The 4-decimal rounding removes float noise at the limits (R:R of exactly 1.5, margin of exactly 10%, liquidation exactly 2×); vectors `P08`–`P10` pin it. A leverage above the cap usually also breaks the liquidation rule (`P03`): list both.

### 3.5 Actual risk, size verdict, compliance

After the entry the user supplies the *actual* position value from the exchange. With `rrisk = notional × sld` and `rriskp = rrisk / equity`:

```
r5 = xlRound(rriskp, 5)
r5 ≤ cap × 1.05          → YES                       (cap = riskCap stored in trade.ctx; default Risk_Pct)
r5 ≤ Risk_Max            → GRADE_A_ONLY   if grade-A sizing was allowed at entry (ctx.gradeA)
                           OVER_CAP       otherwise   (app verdict; Excel had no such case)
else                     → RED_LINE                  (red line 2 of article 9)
```

`ok` (the "followed the rules" flag, the basis of the compliance KPI) is `violations.isEmpty() && gateAllowed && verdict ∈ {YES, GRADE_A_ONLY}`. `gateAllowed` is stored at entry confirmation and is `plan gate has no reasons && entryAllowed` (§3.9, decision D-08), so trading through a lock shows up as non-compliance.

### 3.6 Fill and result

`legs = [{price, fraction}]`, fractions sum to 1 (assert within 1e-9). A single exit is one leg of fraction 1.

```
gross = Σ  sign × (price_i − entry) / entry × notional × fraction_i      sign = +1 LONG, −1 SHORT
net   = gross − fee                                                       fee = commissions + funding, USD
R     = net / (notional × sld)                                            null if the risk is 0
result= WIN if net > 0, LOSS if net < 0, else EVEN
equityAfter = equity + net
```

Vectors `F01`–`F15` cover take-profit (net 39.4, R 1.97), stop with fee (net −20.6, R −1.03), shorts, partial exits, exit at entry with fee, and bigger-than-planned size.

### 3.7 Leverage advisor

For `L = 1 … floor(Lev_Max)`: compute the plan and the gate with that leverage; `L` is feasible when the reasons contain neither `MARGIN` nor `LIQUIDATION`. Return `feasibleMin`, `feasibleMax`, `recommended` = the feasible `L` whose `marginPct` is closest to `Margin_Target` (ties → lower `L`), and the real-valued bounds:

```
lMinReal    = (riskPct / sld) / Margin_Max          leverage below this breaks the margin cap
lMaxLiqReal = 1 / (Liq_Mult × sld + Maint_Rate)     leverage above this breaks the liquidation rule
```

No feasible leverage (vector `L03`: a 0.5% stop) means the trade must be rejected: do not raise the risk or the leverage (article 3-3). Vectors `L01`–`L06`.

### 3.8 Equity, curve, KPIs, dashboard limits

**Ledger.** Entries: `BASELINE` (+), `DEPOSIT` (+), `ADJUSTMENT` (signed), `WITHDRAWAL` (stored positive, subtracts). `equityBefore(t) = Σ ledger flows with time < t + Σ net of REAL trades with exitTime < t`. `equityNow(now) = equityBefore(now + 1 µs)`. Start-of-day and start-of-week equity are `equityBefore(local 00:00 of the day / of the week's Saturday)`. A withdrawal changes equity but never the P&L curve.

**P&L curve** (article 3-7: only trade results, no deposits or withdrawals), trades in exit order, ties broken by `(exitTime, entryTime, id)`:

```
cum_k  = Start_Equity + Σ_{i ≤ k} net_i
peak_k = max(Start_Equity, cum_1 … cum_k)
dd_k   = (peak_k − cum_k) / peak_k
```

**KPIs** for trades whose exit *date* is inside `[from, to]` (inclusive), `n` = their count:

| KPI | Formula | Empty value |
|---|---|---|
| compliance | count(ok) / n | null |
| expectancy | Σ R / n | null |
| avgWinR / avgLossR | mean R of wins / of losses | null |
| realRR | avgWinR / \|avgLossR\| (needs both) | null |
| winRate | count(net > 0) / n | null |
| profitFactor | Σ positive net / \|Σ negative net\| (needs a loss) | null |
| maxDD | max of `dd_k` over the period's trades, taken from the **all-time** curve (not re-based per period; parity with Excel) | null |
| avgChk | mean `chk` | null |
| net, sumR | sums | 0 |
| withdrawSuggest | max(0, net) × Withdraw_Pct | 0 |

Status codes: `n`: `ENOUGH_DATA` if `n ≥ Min_Trades` else `LOW_DATA`; compliance `ON_TARGET` if ≥ `Compliance_Target` else `BELOW_TARGET`; expectancy `POSITIVE` if > 0 else `NON_POSITIVE`; realRR `ON_TARGET` if ≥ `RR_Min`; profitFactor `ON_TARGET` if ≥ `PF_Min`; maxDD `IN_RANGE` if < `DD_Cap` else `OVER_CAP`; avgChk `COMPLETE` if ≥ `Chk_N` else `INCOMPLETE`. Statuses are omitted for null KPIs. Tallies count `mistake` ids and `violations` ids (vector `K01` shows the shape; `M00` "no mistake" is counted, the UI hides it from "top mistakes"). Break-even win rate for a given R is `1 / (1 + R)`; expectancy is `E = p × R − (1 − p)` (article "why win rate is not enough").

**Dashboard limits** (the six gauges of the Today screen):

```
dailyPct  = dayNet  / startOfDayEquity            weeklyPct = weekNet / startOfWeekEquity     (week from Saturday)
openRiskPct = Σ rrisk of open REAL trades / equityNow
trades    : TRADES_CAP    if tradesToday ≥ Max_Trades_Day
daily     : DAILY_RED     if xlRound(−dailyPct, 6)  ≥ Daily_Loss_Cap
weekly    : WEEKLY_RED    if xlRound(−weeklyPct, 6) ≥ Weekly_Loss_Cap
drawdown  : DD_RED        if xlRound(ddNow, 6)      ≥ DD_Cap                (ddNow from the governor's re-based curve, §3.9)
positions : POSITIONS_OVER if open > Max_Positions; POSITIONS_FULL if open = Max_Positions
openRisk  : RISK_FULL     if openRiskPct + Risk_Pct > Open_Risk_Max + 0.000001
allowed   = all six are OK
```

`tradesToday` counts entered REAL trades by local *entry* date, open ones included. Gauge colors: `OK` below two thirds of the cap, "near" (saffron) from two thirds of the cap, red when the status is not `OK`.

### 3.9 The governor (locks, reductions, hold, parameter rules)

`governor(now, trades, ledger, reviews, setups, params, setupId?)` → `{canEnter, locks, riskCap, reductions, gradeAAllowed, bigWinHoldUntil, tradesToday, openPositions, ddNow, dailyNet}`. Port `governor`, `drawdown_state`, `violation_events`, `violation_triggers`, `big_win_hold` literally; the table is the human-readable contract. 33 scenarios (`G01`–`G33`) pin every row.

**Stop-out** := `exitReason == STOP_LOSS`, or `net < 0 && R ≤ Stopout_R`.

| Lock (priority) | Trigger | Active until | Needs |
|---|---|---|---|
| `VIOLATION` (1) | a REAL trade has violations with `violationAt ≤ now`. One *event* per trade; `start = max(violationAt, exitTime)` | closed trade: `start + Violation_Pause_H`. Open trade: no end until it is closed | `REPORT` while the trimmed report is shorter than `Report_Min_Chars`; `CLOSE_POSITION` while open |
| `DRAWDOWN` (2) | after some trade, `xlRound(dd, 6) ≥ DD_Cap` on the governor's curve | `breachAt + DD_Lock_Days` **and** a saved review of type WEEKLY, MONTHLY or DRAWDOWN completed at or after `breachAt` | `REVIEW` until such a review exists |
| `WEEKLY_LOSS` (3) | in any week, running net ÷ start-of-week equity reaches −`Weekly_Loss_Cap` (`xlRound(·, 6)`) | next Saturday 00:00 **and** a WEEKLY review whose `weekStart` is that week's Saturday | `WEEKLY_REVIEW` until saved |
| `DAILY_LOSS` (4) | today, after any exit, running net ÷ start-of-day equity reaches −`Daily_Loss_Cap` | next local midnight | — |
| `DAY_OVER` (5) | `Consec_Stops` consecutive stop-outs among today's closed trades in exit order | next local midnight | — |
| `TRADE_CAP` (6) | entered REAL trades today ≥ `Max_Trades_Day` | next local midnight | — |
| `COOLDOWN` (7) | `latestStopOutExit + Cooldown_Min > now` | that instant | — |

Locks are sorted by `(priority, until)`; `canEnter = locks.isEmpty()`. Every "until" comparison is strict (`now < until`): scenario `G33` shows a cool-down ending exactly at `now` is already over. A lock with unmet `needs` stays even when its time has passed (`G15`, `G17`, `G19`).

**Drawdown walk.** Walk the closed trades in order keeping `cum`, `peak`. On breach record `breachAt`. The lock is *released* at `max(breachAt + DD_Lock_Days, earliest qualifying review completedAt)`; releases are applied lazily before the next trade (or `now`) and **re-base the peak to the current `cum`** (so the dashboard drawdown restarts from the release; `G18`). Each release also starts a `DD_RECOVERY` reduction.

**Risk reductions** (the `riskCap` is the minimum of `Risk_Pct` and every active reduction):

| Code | Start | Risk | For |
|---|---|---|---|
| `DD_RECOVERY` | each drawdown release | `Recovery_Risk` | next `Recovery_Trades` entered REAL trades |
| `VIOLATIONS` | the instant a rolling window of `Violation_Window_D` days holds `Violation_Trigger` violation events; the window is then emptied | `Reduced_Risk` | next `Reduced_Trades` |
| `SETUP_TEST` | (the chosen setup) fewer than `Setup_Test_Trades` entered REAL trades of that setup so far | `Setup_Test_Risk` | the remainder |

A reduction is active while `used < n`, where `used` counts entered REAL trades with `entryTime ≥ start` (open ones count). Extension P1 (decision D-21): a setup that already has `Setup_Test_Trades` DEMO trades is exempt from `SETUP_TEST` (it is the "or in demo" clause of article 12-6); implement as an optional `testWaived` flag on the setup, default false, with your own test.

**Big-win hold.** For each closed trade: if `R > BigWin_R`, or the day's running net ÷ start-of-day equity `> BigWin_Day_Pct` (`xlRound(·, 6)`), a hold runs from that exit for `BigWin_Hold_H` hours. `bigWinHoldUntil` is the latest end if still in the future. Effect: raising risk is not allowed and grade-A sizing is disabled.

**Grade-A sizing** is allowed only when `setup.grade == A && reductions.isEmpty() && bigWinHoldUntil == null`. A setup may be *promoted* to grade A only when `gradeAEligible`: at least `Min_Trades` closed REAL trades and mean R > 0 (`A01`–`A04`). `n` counts every closed REAL trade of the setup; a trade without an R counts in `n` and adds nothing to the sum (the vectors pass plain R lists).

**Entry decision.** `entryAllowed = gate.reasons.isEmpty() && dashboard.allowed && governor.canEnter`. The positions and open-risk limits live in the dashboard, not in locks, so `governor.canEnter` can be true while `dashboard.allowed` is false (`G30`). Always use the conjunction.

**History rows (extension without a vector, decision D-14).** A trade flagged `imported = true` (rows brought in from CSV) is *history*: it counts for equity, the P&L curve, KPIs, tallies, the `SETUP_TEST` counter and `gradeAEligible`, but it never creates a lock, a cool-down, a violation event, a drawdown breach or release, a risk reduction or a big-win hold, and it is ignored by the daily, weekly, day-over and trade-cap scans. (An imported row without an exit is an open position and counts in the positions and open-risk gauges only.) The drawdown walk still advances `cum` and `peak` over imported trades. The vectors contain no imported trades, so write the tests of §10.4 item 2 yourself.

**Auto-detected violations.** `V02` when the size verdict is `RED_LINE`; `V03` when `lev > Lev_Max` or the "isolated" toggle is off; `V04` when a stop moves away from price or is removed; `V06` when the entry is confirmed with `chk < Chk_N`. `V01`, `V05`, `V08`, `V10` are self-reported; `V07`, `V09` are *suggested* (entry inside a cool-down or with emotion `REVENGE`; a losing position held beyond three times its time stop) and the user confirms. The ids and Persian labels are in the content JSON (`enums.violation`).

**Review sessions and parameter changes** (article 12-5: rules change only in a review session, not mid-trade and not after a loss):

- `canStartSession(now, trades)`: blocked with `OPEN_POSITION` while any REAL trade is open; blocked with `RECENT_LOSS` until `lastLosingExit + Review_After_Loss_H` (`R01`–`R04`).
- `classifyChange(key, old, new)`: `NONE` if equal; `NEUTRAL` if the parameter's tighter direction is neutral; `TIGHTEN` if the change moves in the tighter direction, else `LOOSEN`. `effectiveAt = appliedAt + Loosen_Delay_H` for `LOOSEN`, else `appliedAt` (`X01`–`X08`).
- `paramsAt(base, changes, t)`: apply changes whose `effectiveAt ≤ t`, ordered by `(effectiveAt, appliedAt)` (`Y01`, `Y02`).
- Every change stores key, old, new, kind, appliedAt, effectiveAt, reason (required), session id, and an audit-log line. Pending changes can be cancelled before `effectiveAt`. Constraint checks: `min ≤ value ≤ max` from the content JSON, and `Risk_Max ≥ Risk_Pct`.
- The engine functions take one `Params`; the app resolves the right `Params` for `now` with `paramsAt` and passes it in. Stored trades keep the values that were true when they were entered (law 6).

### 3.10 Number formatting and input parsing

Display (`Fmt`, vectors `format.*`):

- Persian digits `۰۱۲۳۴۵۶۷۸۹`, decimal separator `٫` (U+066B), thousands separator `٬` (U+066C), minus sign `−` (U+2212). With the "Latin digits" setting use `0-9`, `.` and `,` but keep U+2212. A value that rounds to zero never prints a sign (`−0.004` → `۰٫۰۰`).
- `formatNumber(x, decimals, fa, group, trim)` rounds with `xlRound`; `formatSigned` prints `+` for positive and `−` for negative, nothing for zero; `formatPercent(x, decimals, fa, signed)` multiplies by 100 (rounded with `xlRound`) and appends `٪` (or `%`).
- Money, R values and symbols are rendered inside a left-to-right isolate (U+2066 … U+2069) so they stay readable inside RTL text.
- Static Persian digits in constitution and learn text are converted to Latin digits at render time when the user turns Persian digits off.

Input (`normalizeInput`, 17 cases): accept Persian (U+06F0–06F9) and Arabic-Indic (U+0660–0669) digits; drop `٬`, ZWNJ (U+200C) and spaces; `٫` → `.`; `−` → `-`; if both `,` and `.` appear, every comma is a thousands separator; if only commas appear, look at the text after the **last** comma: exactly three characters and something before it → every comma is a thousands separator (`1,234` → 1234, `1,234,567` → 1234567), otherwise the last comma is the decimal point and earlier commas are dropped (`12,5` → 12.5, `,5` → 0.5); the final text must match `^[+-]?(\d+\.?\d*|\.\d+)$` (no exponent, `inf` or `nan`) or the result is null.

### 3.11 Live constitution clauses

Clauses in the content JSON carry `refs: [{param, literal, template, fmt}]`. Rendering replaces the first occurrence of `literal` by `template` with `{v}` filled from the live value of `param` (or the derived value `@SL_Floor = Risk_Pct / (Margin_Max × Lev_Max)`). Formats: `pct` → `xlRound(v × 100, 2)` printed with trailing zeros trimmed plus `٪`; `int` → the value as an integer in Persian digits (only integer-valued parameters use it); `dec` → up to two decimals, trimmed; `word` → the Persian word for 1…10 (`یک` … `ده`), otherwise `dec`. With default parameters the output equals the original text exactly (vectors `T01`–`T19`); `T20`–`T29` change parameters. In the reader, rendered numbers are highlighted; tapping one opens a sheet "comes from setting X (now Y, default Z)" linking to the rules screen.

### 3.12 Jalali calendar

Implement the jalaali-js algorithm from `jalali_ref.py` (truncating division, not floor): `toJalali(LocalDate)`, `toGregorian(y, m, d)`, `isLeap(y)`, `monthLength(y, m)`, `weekdayIndexSat0(date)`, `weekStart(date)`. Display like «شنبه ۱۸ مهر ۱۴۰۵». All storage and computation stay Gregorian/UTC; Jalali is display and the date pickers only. Month names and weekday names are in the content JSON (`calendar`). The user can switch the display calendar to Gregorian. Vectors `jalali.*`.

### 3.13 Engine API

Keep it small and total. Suggested surface (names may differ, behavior may not):

```kotlin
object XlMath { fun round(x: Double, d: Int): Double }
data class Params(/* 45 fields */) { companion object { val DEFAULTS: Params; fun of(map: Map<String, Double>): Params } }
object Planner {
  fun plan(side: Side, equity: Double, entry: Double, sl: Double, tp: Double, lev: Double, p: Params, riskPct: Double? = null): Plan
  fun gate(side: Side, entry: Double, sl: Double, tp: Double, lev: Double, chk: Int, plan: Plan, p: Params): List<GateReason>
  fun sizeVerdict(rriskp: Double, p: Params, riskCap: Double? = null, gradeAAllowed: Boolean = true): SizeVerdict
  fun fill(side: Side, entry: Double, sl: Double, equity: Double, notional: Double, legs: List<Leg>, fee: Double): Fill
  fun compliant(violations: List<String>, gateAllowed: Boolean, verdict: SizeVerdict): Boolean
  fun leverageAdvisor(side: Side, equity: Double, entry: Double, sl: Double, tp: Double, chk: Int, p: Params, riskPct: Double? = null): Advisor
}
object Stats { fun curve(nets: List<Double>, start: Double): List<CurvePoint>; fun kpis(rows: List<ClosedRow>, from: LocalDate, to: LocalDate, p: Params): Kpis }
object Limits { fun dashboard(/* trades today, nets, equities, dd, open count, open risk */): Dashboard }
object Governor {
  fun snapshot(now: Instant, zone: ZoneId, p: Params, ledger: List<LedgerEntry>, trades: List<TradeRaw>, reviews: List<Review>, setups: List<Setup>, setupId: String?): Snapshot
  fun canStartSession(now: Instant, trades: List<TradeRaw>, p: Params): SessionCheck
  fun classifyChange(key: String, old: Double, new: Double): ChangeKind
  fun changeEffectiveAt(key: String, old: Double, new: Double, appliedAt: Instant, p: Params): Pair<Instant, ChangeKind>
  fun paramsAt(base: Params, changes: List<ParamChange>, t: Instant): Params
  fun gradeAEligible(closedRs: List<Double?>, p: Params): GradeA
}
object Fmt { /* number, signed, percent, normalizeInput, toFaDigits, isolate */ }
object Clause { fun render(text: String, refs: List<Ref>, p: Params): String }
object Jalali { /* §3.12 */ }
```

`snapshot` resolves raw trades into derived fields (`resolveTrade`: `rrisk`, `rriskp`, `verdict` from the stored `ctx`, and when closed `net`, `R`, `gross`, `result`, `ok`), then returns equity (now, start of day, start of week), the dashboard and the governor result. Engine tests must run in under five seconds in total on a laptop.


## 4. Data model (`:data`)

### 4.1 Principles

- **Room** is the source of truth for the journal; **Preferences DataStore** holds settings. Export schemas (`room.schemaLocation`), write a migration test for every version bump, never use destructive migration.
- IDs are random UUID strings (stable across backup/restore and CSV round trips). Instants are `Long` epoch milliseconds (UTC). The display zone is a setting (default `Asia/Tehran`); local times are never stored. Money, prices, ratios: `Double`. Enumerations are stored as the string ids found in `content.enums` (`"LONG"`, `"M03"`, …).
- The database contains **no Persian text** except what the user typed (setup names, notes, lessons, reports). Audit rows store codes and numbers; the UI renders them through the content JSON.
- **Derived fields are persisted when they become known** (law 6) and win over recomputation (decision D-24). A repository never "refreshes" an old trade because a parameter changed.
- Every destructive or retroactive action (parameter change, trade correction or deletion, ledger edit, restore, wipe, import) writes an `audit_log` row.
- `account = DEMO` rows are stored like real ones but are excluded from every rule, limit, lock, equity and KPI (decision D-15).

### 4.2 Tables

**`trade`** — one row per planned, open or finished trade. Field names deliberately equal the `csv.full` columns of the content JSON.

```kotlin
@Entity(tableName = "trade", indices = [Index("status"), Index("entryTime"), Index("exitTime"), Index("setupId"), Index("account")])
data class TradeEntity(
  @PrimaryKey val id: String,
  val account: String,                 // REAL | DEMO
  val status: String,                  // PLANNED | OPEN | CLOSED | REJECTED | CANCELLED
  val symbol: String,                  // upper case, e.g. "BTCUSDT"
  val side: String,                    // LONG | SHORT
  val kind: String,                    // SCALP | INTRADAY | SWING — frozen once OPEN (article 2-3)
  val setupId: String?,                // required for PLANNED/OPEN/CLOSED ("no setup, no trade")
  val timeframe: String,               // entry timeframe id: 1m 3m 5m 15m 30m 1h 4h 1d
  val htfTrend: String,                // UP | DOWN | RANGE
  val counterTrendReason: String?,     // required when the side opposes htfTrend
  val emotion: String,                 // before entry: CALM | FOMO | GREED | FEAR | REVENGE | BORED
  val checklistMask: Int,              // bit (i-1) = item i is yes; items 6 and 7 are derived at commit (D-09)
  val chk: Int,                        // popcount(checklistMask), stored
  // ---- plan (written when the plan is saved) ----
  val equity: Double,                  // equity typed/confirmed before entry (engine field "eq")
  val plannedEntry: Double,
  val slInitial: Double,               // engine field "sl"; the basis of every R
  val tp: Double,
  val lev: Double,
  val riskPctChosen: Double,           // what the plan was sized with (<= riskPctCap, or <= Risk_Max for grade A)
  val riskPctCap: Double,              // governor.riskCap at planning  -> engine ctx.riskCap
  val gradeA: Boolean,                 // governor.gradeAAllowed at planning -> engine ctx.gradeA
  val entryOrder: String,              // LIMIT | LIMIT_LADDER | MARKET_CONFIRMED | MARKET_OTHER
  val isolated: Boolean, val slTpOnExchange: Boolean, val canMonitor: Boolean,
  @Embedded(prefix = "plan_") val plan: PlanSnapshot,   // sld rr riskUsd notionalMax margin marginPct liq liqDist liqX
  val gateReasons: String,             // gate codes joined by '|' ("" = none)
  val gatePlanOk: Boolean,             // plan gate had no reasons
  val entryAllowed: Boolean,           // gate ∧ dashboard.allowed ∧ governor.canEnter at that moment
  val gateAllowed: Boolean,            // gatePlanOk ∧ entryAllowed, frozen at entry confirmation (D-08)
  // ---- execution (written when the entry is confirmed) ----
  val entry: Double?,                  // actual average fill
  val notional: Double?,               // actual position value from the exchange
  val entryTime: Long?,
  val slCurrent: Double?,              // moves only toward profit (violation V04 otherwise)
  val rrisk: Double?, val rriskp: Double?, val verdict: String?,   // YES | GRADE_A_ONLY | OVER_CAP | RED_LINE
  // ---- exit (written when the trade is closed) ----
  val exitTime: Long?,
  val legs: List<Leg>,                 // JSON via TypeConverter: {price, fraction, time, fee}; partial exits append here
  val fee: Double,                     // total commissions + funding, USD (sum of leg fees + closing fee)
  val exitReason: String?,             // TAKE_PROFIT STOP_LOSS TRAILING_STOP BREAKEVEN MANUAL TIME_STOP EMERGENCY LIQUIDATED
  val gross: Double?, val net: Double?, val r: Double?, val result: String?, val equityAfter: Double?, val ok: Boolean?,
  // ---- review ----
  val violations: List<String>,        // V01..V10
  val violationAt: Long?,              // first violation instant (auto: the moment it was detected)
  val violationReport: String?,
  val mistake: String?,                // M00..M11, exactly one
  val emotionAfter: String?, val lesson: String?, val reviewedAt: Long?,
  // ---- bookkeeping ----
  val imported: Boolean,               // history rows from CSV (D-14): never create locks
  val loggedLate: Boolean,             // P1, D-26
  val monitorChecks: List<Long>,       // instants the user pressed "I checked"
  val editedAt: Long?,                 // set by a numeric correction (D-23)
  val createdAt: Long, val updatedAt: Long,
)
```

- `Leg(price: Double, fraction: Double, time: Long?, fee: Double)`; fractions of a closed trade sum to 1 (±1e-9).
- Engine mapping (`TradeRaw`): `id, account, setupId, side, entry, sl = slInitial, tp, lev, eq = equity, chk, gateAllowed, notional, entryTime, exitTime, legs (price, fraction), fee, exitReason, violations, violationAt, violationReport, mistake, ctx = {riskCap = riskPctCap, gradeA}`, plus the persisted derived values `verdict, rrisk, rriskp, net, R, ok` and the flag `imported`. Only `OPEN` and `CLOSED` rows have an `entryTime` and go to the engine.
- `REJECTED` rows (plans the gate denied, saved on purpose: "a missed trade is not a loss") and `CANCELLED` rows (abandoned or expired plans) stay in the journal for statistics ("rules saved me N times") but never reach the engine.

**`trade_event`**: `(id, tradeId, time, type, payload JSON)`; `type` ∈ `SL_MOVE {from, to, away}`, `PARTIAL_EXIT {price, fraction, fee}`, `NEWS {text}`, `EMOTION {emotion}`, `PRAYER {}`, `NOTE {text}`, `ADD_REPORTED {}`. `slMoves` in the CSV is the `SL_MOVE` subset.

**`attachment`**: `(id, tradeId, fileName, mime, width, height, bytes, createdAt)`; the file lives in `filesDir/shots/<id>.jpg` (decision D-13).

**`setup`**: `(id, name, entryRule, invalidation, target, notes, grade A|B, archived, testWaived, createdAt, updatedAt)`.

**`watch_symbol`**: `(id, symbol, volume24hUsd?, relVolume?, levels, notes, checkedAt?, addedAt)`.

**`ledger_entry`**: `(id, time, type BASELINE|DEPOSIT|WITHDRAWAL|ADJUSTMENT, amount, note, createdAt)`; amounts are stored positive except `ADJUSTMENT` (signed) — exactly the engine's convention (§3.8). Exactly one `BASELINE` exists; it is created in onboarding at 00:00 local of that day.

**`review`**: `(id, type DAILY|WEEKLY|MONTHLY|DRAWDOWN, completedAt, periodStart, periodEnd, payload JSON, kpiSnapshot JSON)`. `periodStart` is an ISO date: the day (DAILY), the week's Saturday (WEEKLY, matched by `weekStart` in the engine), the first day of the display-calendar month (MONTHLY), the breach date (DRAWDOWN). `kpiSnapshot` freezes the KPIs shown at the time (law 6).

**`param_change`**: `(id, key, oldValue, newValue, kind TIGHTEN|LOOSEN|NEUTRAL, appliedAt, effectiveAt, reason, sessionId?, cancelledAt?, createdAt)`. Parameters are never stored as a table of "current values": `params(now) = paramsAt(DEFAULTS, changes where cancelledAt == null, now)`. Onboarding choices (starting equity, risk, zone) are written as `NEUTRAL` changes with `sessionId = null` and `effectiveAt = appliedAt`.

**`review_session`**: `(id, startedAt, endedAt?, endReason USER|TIMEOUT|TRADE_OPENED)`.

**`pledge`**: `(id, version, name, signedAt, startEquity, riskPct, textSnapshot)`; **`security_check`**: `(clauseId "10-1".."10-6" PK, checked, checkedAt?)`; **`spot_asset`**: `(id, asset, thesis, invalidation, weightCap, weight, stagedNotes, lastReviewAt?, createdAt)`; **`audit_log`**: `(id, time, type, refId?, payload JSON)` with types `PARAM_CHANGE, PARAM_CANCEL, TRADE_EDIT, TRADE_DELETE, LEDGER_EDIT, LEDGER_DELETE, PLEDGE_SIGNED, BACKUP_EXPORT, BACKUP_RESTORE, CSV_IMPORT, CSV_EXPORT, DATA_WIPE, DEMO_LOAD, DEMO_REMOVE`.

**DataStore (settings)**: theme (`DARK|LIGHT|SYSTEM`, default `DARK`), digits (`FA|EN`, default `FA`), calendar (`JALALI|GREGORIAN`, default `JALALI`), zone id, reduceMotion, haptics, lock (enabled, method, timeoutSeconds, hideRecents, `pinHash`, `pinSalt`, `pinFailCount`, `pinLockedUntil`), notification toggles and quiet hours, `lastBackupAt`, `backupRemindDays` (30), liquidity thresholds `Liq_Vol_Min = 100_000_000`, `Rel_Vol_Lo = 0.7`, `Rel_Vol_Hi = 3.0` (decision D-22: informational only), `readArticles` (set of article numbers), `lastSecurityReviewAt`, `onboardingDone`, `adviceEnabled`, `demoLoaded`.

### 4.3 Trade lifecycle

```
(wizard) ──save plan──▶ PLANNED ──confirm entry──▶ OPEN ──close──▶ CLOSED ──review──▶ (reviewedAt set)
            │               │ discard / expire (12 h)
            │               └────────────────────────▶ CANCELLED
            └──gate denied, "save as rejected"──────▶ REJECTED
```

- A `PLANNED` row does not count toward trades-today, open positions or open risk; it expires after `PLAN_EXPIRY_HOURS = 12` (sweep at app start and in a worker).
- "I entered anyway" after a denial creates an `OPEN` row through the confirmation sheet (decision D-28): `gateAllowed = false` and the matching violations are recorded automatically (`V06` checklist, `V03` leverage/isolated, `V02` size when the verdict is `RED_LINE`).
- A trade that currently has an **active lock** anywhere in the governor result is read-only for numeric corrections and cannot be deleted (decision D-23); notes, lesson, mistake, emotion and screenshots are always editable.

### 4.4 What is computed and persisted when

| Moment | Persisted |
|---|---|
| Plan saved | `plan` snapshot (engine `plan` with `riskPctChosen`), `gateReasons`, `gatePlanOk`, `entryAllowed`, `riskPctCap`, `gradeA`, checklist, `chk` |
| Entry confirmed | `entry`, `notional`, `entryTime`, `slCurrent = slInitial`, `rrisk`, `rriskp`, `verdict` (computed with `paramsAt(entryTime)`), auto violations + `violationAt`, `gateAllowed = gatePlanOk ∧ entryAllowed` |
| SL moved / partial exit / note | one `trade_event`; partial exits also append a `Leg`; an away-move or deletion of the stop adds `V04` |
| Closed | `exitTime`, `legs`, `fee`, `exitReason`, then `gross, net, r, result, equityAfter` and `ok = compliant(violations, gateAllowed, verdict)` (stored `verdict`, not recomputed) |
| Review saved | `mistake, emotionAfter, lesson`, self-reported violations (+ `violationAt` when first added), `violationReport`, `reviewedAt` |

### 4.5 Reactive state

`EngineFacade.snapshot(now)` is a pure function over the tables. Expose it as a `Flow<Snapshot>` that is recomputed (a) on any change of `trade`, `ledger_entry`, `review`, `param_change`, `setup`, and (b) when a **boundary** passes: the earliest `lock.until`, the next local midnight, the next week start, the earliest time-stop. While the app is in the foreground also tick every 60 seconds. Debounce bursts of writes by 50 ms. With 5,000 trades a snapshot must finish in under 50 ms on a mid-range phone; if it does not, cache the closed-trade resolution (it is immutable) and recompute only the open trades and the day.

---

## 5. Screens and flows

General rules for every screen:

- **Strings** come from `content.ui` by key (the prefix of each screen is given below). `{placeholders}` are filled by the caller with numbers already formatted for the user's digit setting. Keys that are missing are listed in `MISSING_COPY.md`.
- Every number comes from the engine; every "no" shows its reason, its number and its article (`common.article`, `common.clause` take the article text).
- Forms validate on blur and on submit with the `err.*` strings; the primary action is disabled only while a *required* field is empty, never silently for other reasons.
- Destructive actions use a confirm sheet; irreversible ones (wipe) need the typed word from `set.wipe.word`.
- Priority tags: **P0** ships in v1, **P1** after P0 is green, **P2** only if time remains.

### 5.0 Navigation

Bottom bar (RTL: Today sits at the right): `nav.today`, `nav.journal`, `nav.calculator`, `nav.insights`, `nav.more`. An extended FAB "new trade" (`today.newTrade`) floats above the bar on Today and Journal; when entry is blocked it is drawn muted with a lock icon (`today.newTrade.blocked`) but stays tappable and opens the wizard at step 1, which explains the block.

| Route | Content | Priority |
|---|---|---|
| `onboarding/*` | first-run flow | P0 |
| `today` | home | P0 |
| `wizard` (+ `confirmEntry/{id}` sheet) | new trade | P0 |
| `trade/{id}` (+ close sheet, review) | detail and management | P0 |
| `journal` (+ filters sheet, import/export) | list | P0 |
| `calculator` | stateless calculator | P0 |
| `insights` | KPIs and charts | P0 (KPIs, equity, drawdown); P1 (other charts) |
| `reviews`, `reviews/new/{type}`, `rules/session` | reviews and review sessions | P0 |
| `rules` | parameters | P0 |
| `setups`, `watchlist`, `ledger` | lists | P0 |
| `constitution`, `constitution/{n}` | reader, pledge | P0 |
| `learn/*`, `security`, `spot` | study pages, checklist, long-term portfolio | P1, P1, P2 |
| `settings/*`, `about` | appearance, lock, notifications, backup, data | P0 |

Notification taps are deep links into these routes (§7). `MainActivity` is `singleTask`; the back stack is restored after process death (route arguments are ids only).

### 5.1 Onboarding (`onb.*`) — P0

Seven pages in a pager with a step indicator: welcome → privacy → how it works → starting numbers → pledge → app lock → done.

- **Starting numbers**: name (pledge only), equity in USD (`onb.setup.equity.hint`: trading account only, never the spot portfolio), risk per trade (default from `DEFAULTS`, range `params.Risk_Pct.min…max`; hint shows the constitution default), time zone (default `Asia/Tehran`, a searchable list). On finish write: the `BASELINE` ledger entry (00:00 local today, amount = equity) and `NEUTRAL` param changes for `Start_Equity` and `Risk_Pct` (D-18).
- **Pledge**: shows the 13 articles' headings with a "read first" button (`onb.pledge.read`) that opens the reader; the pledge text is `constitution.pledge.template` with `{name}`, `{date}`, `{startEquity}`, `{riskPct}` filled. **Sign** stores a `pledge` row; **later** lets the user explore the app but every route that creates a trade shows `today.pledgeMissing` / `err.noPledge`.
- **Lock**: offer biometric and PIN (§8.2); skippable.
- Never ask for notification permission on the first screen; ask when the user first enables a reminder (Android 13+).

### 5.2 Today (`today.*`, `lock.*`, `need.*`, `reduction.*`, `bigwin.*`) — P0

```
┌────────────────────────────────────────────────────────────┐
│ اندرز · شنبه ۱۸ مهر ۱۴۰۵                              [🔒]   │ top bar (lock icon only when locked)
│ ┌────────────────────────────────────────────────────────┐ │
│ │ سرمایهٔ حساب                    (subtle khatam pattern) │ │ hero card
│ │ $ 2,039.40                                              │ │
│ │ امروز +39.40 (+1.97٪)   هفته +39.40   افت از اوج 0٪     │ │
│ └────────────────────────────────────────────────────────┘ │
│ [ lock banner: title · reason with number · countdown · action buttons ]   ← only when locked
│ [ chips: risk cap 0.5٪ for 14 more trades ] [ big-win hold until 14:20 ]
│ سقف‌های امروز                                                │
│  معاملات 1/3   زیان روزانه 0٪/3٪   زیان هفتگی 0٪/6٪          │ six LimitGauges, 2 × 3
│  افت از اوج 0٪/10٪  پوزیشن‌ها 1/3   ریسک باز 1٪/3٪           │
│ ✓ مجاز به معاملهٔ جدید — همهٔ سقف‌ها آزاد است                  │ overall verdict
│ پوزیشن‌های باز  [card] [card]                                │
│ کارهای عقب‌افتاده  ·  اندرز امروز                              │
└────────────────────────────────────────────────────────────┘
                                         [ + معاملهٔ جدید ]  (FAB)
```

- **Hero**: `equity.now`; day P&L = `dailyNet` and `dailyPct` (÷ start-of-day equity); week P&L = week net and `weeklyPct`; `ddNow`. Positive/negative values carry sign **and** icon; no celebratory styling.
- **Lock banner** (one per lock, in the engine's order, the first one expanded): title `lock.<CODE>.title`, body `lock.<CODE>.body` with its `uiArgs` (cap, days, hours, minutes, n, risk), `lock.until` countdown (re-rendered every second from the boundary ticker; `lock.COOLDOWN.left` shows minutes), and one button per `need` (`need.action.report|review|close`): *report* opens the violation-report editor (counter `review.report.min`), *review* opens `reviews/new/WEEKLY` (or the DRAWDOWN form), *close* opens the offending trade. When a lock's time is over but a need remains, show `lock.untilDone`.
- **Reductions and hold**: one chip per active reduction (`reduction.*`, with `reduction.cap` showing the effective cap); `bigwin.hold` with `bigWinHoldUntil` formatted.
- **Six gauges** (`today.tradesToday … today.openRisk`): value / cap per §3.8; the status text is `today.status.<CODE>`; the ratio is clamped to 1; colors: `OK` below ⅔ of the cap → firouzeh, from ⅔ → saffron ("near"), status ≠ `OK` → anar. Count-based gauges (trades, positions) draw segments. Each gauge is one TalkBack node: «label, value of cap, state» (`a11y.allowedState|cautionState|redState`).
- **Overall verdict** line: `today.overall.ok|no`, or `today.allowed.yes|no` in the long form; it equals `entryAllowed`-ignoring-the-plan, i.e. `dashboard.allowed ∧ governor.canEnter`.
- **Open positions**: `OPEN` trades (REAL first, DEMO with a chip). Card: symbol, side chip, entry, current SL, TP, time-stop remaining (`trade.timeStop.left`), last monitor check, buttons *manage* and *close*, and a one-tap "I checked" (`trade.monitor.check`). Empty: `today.noOpen`.
- **Obligations** (`today.obligations`): computed list, each row a deep link: pending post-trade reviews (`review.pending`), a violation report that is needed, daily close not done (`rev.due.daily`), weekly review due (`rev.due.weekly`; due from Friday 18:00 local, overdue after the week ends), monthly review due (`rev.due.monthly`; first day of a new display-calendar month), security review older than 30 days (`sec.*`), backup older than `backupRemindDays` (`set.backup.last`), watch-list liquidity recheck older than 30 days (`wl.recheck`), pledge missing.
- **Advice of the day** (`today.advice`, hidden when `adviceEnabled` is off): `adviceOfDay[localDate.toEpochDay() mod 25]` (decision D-32), with its source ("lesson n" / "article x-y") as a small caption.
- **Close the day** button (`today.closeDay`): opens `reviews/new/DAILY` after 18:00 local, or when at least one trade exists today; `today.noTradesToday` otherwise.
- Pull-to-refresh is not needed (everything is reactive).

### 5.3 New-trade wizard (`wiz.*`, `size.*`) — P0

Five steps with a linear progress header (`wiz.step.n`), a persistent bottom bar (back / next) and a persistent **verdict strip** below the header from step 2 on (see 6.3). Wizard state lives in a ViewModel (`SavedStateHandle`); leaving the wizard keeps the draft in memory for 10 minutes and asks (`wiz.commit.cancel`) before discarding. The account toggle `wiz.demo.toggle` (hint `wiz.demo.hint`) sits in the header menu.

**Step 1 — Permission (`wiz.step.gate`).** Calls `snapshot(now, setupId = null)`. If `locks` is non-empty: show the lock banners exactly as on Today and stop (`wiz.gate.blocked`); *back* is the only way on. If the pledge is missing: `err.noPledge`. If a gauge is red: name it. Otherwise `wiz.gate.fine` and auto-advance after the user taps *next*.

**Step 2 — Setup and feeling (`wiz.step.setup`).**
- Symbol: search field over the watch-list (`wiz.symbol.hint`); free text allowed with the warning `wiz.symbol.notInList` (D-27: a warning, not a gate reason).
- Side (segmented), kind (segmented, `wiz.kind.hint`), setup (required, `err.setupRequired`; `wiz.setup.new` opens the setup form inline), entry timeframe (chips), higher-timeframe trend (`wiz.htf`), emotion before entry (`wiz.emotion`; six chips from `enums.emotion`).
- On setup selection call `snapshot(now, setupId)` again: the effective `riskCap`, active reductions (a `SETUP_TEST` shows `setups.test`) and `gradeAAllowed` update the strip.
- Emotion ≠ `CALM`: show `wiz.emotion.warn` and make checklist item 8 un-tickable (D-09). `REVENGE` additionally records a *suggested* `V07` (the user confirms it at review).
- Counter-trend (side opposes `htfTrend`, i.e. LONG with `DOWN` or SHORT with `UP`): show `wiz.counterTrend`, require `counterTrendReason`, and cap the chosen risk at half of `riskCap` (P1, D-25; in P0 only the reason is required).

**Step 3 — Checklist (`wiz.step.checklist`).** The eight items from `content.checklist` (`short` as label, `full` as body, `clauseId` as a link into the reader). Items 1–5 and 8 are switches; items 6 and 7 are **derived** (D-09) and shown read-only with "completed from your numbers in the next step" until step 4 supplies values, then green/red with the reason. `wiz.checklist.score` shows `n of 8`; `wiz.checklist.rule` and `wiz.checklist.incomplete` explain the consequence. A light haptic accompanies each tick; no sound.

**Step 4 — Numbers (`wiz.step.numbers`).** Fields in this order: equity (`wiz.equity`, prefilled from the ledger-based `equity.now`, hint `wiz.equity.fromLedger`; if the typed value differs by more than 1% show `wiz.equity.mismatch` with a one-tap "use ledger value"), entry, SL, TP, leverage (stepper plus chips from the advisor), chosen risk (stepper from 0.10% to `maxRisk` in 0.05% steps, default `riskCap`; `maxRisk = Risk_Max` only when `gradeAAllowed`, otherwise `riskCap`; `wiz.riskPct.reduced` explains a reduced cap), entry order (`wiz.entryOrder`; `MARKET_OTHER` shows `wiz.entryOrder.warn`), and three required switches: `wiz.slTpOnExchange`, `wiz.canMonitor`, `wiz.isolated`.

```
 entry [ 100 ]  SL [ 96 ]  TP [ 108 ]  leverage [ 5× ]  risk [ 1.00٪ ]
 ┌ price ladder ─────────┐   ┌ live calculation (wiz.calc.*) ──────────┐
 │ TP    108.00  +8.00%  │   │ SL distance        4.00٪               │
 │       ▲ +2.00R        │   │ R:R                2.00                │
 │ Entry 100.00          │   │ allowed risk       $20.00              │
 │       ▼ −1.00R        │   │ allowed position   $500.00             │
 │ SL     96.00  −4.00%  │   │ margin needed      $100.00  (5.00٪)    │
 │ Liq    80.50 −19.50%  │   │ liquidation price  80.50  (19.50٪)     │
 └───────────────────────┘   │ liquidation ÷ SL   4.88×               │
 [ ✓ مجاز  ]  or  [ ✗ ممنوع: R:R، مارجین ]  with expandable reasons       └─────────────────────────────────────────┘
 leverage advisor: «اهرم‌های مجاز: 3× تا 10× · پیشنهاد: 5× (مارجین 5٪)»  [استفاده]
```

  - Recompute on every keystroke with `Planner.plan` / `gate` / `leverageAdvisor` (inputs parsed by `Fmt.normalizeInput`; unparsable or ≤ 0 values show `err.number` / `err.positive` and blank the dependent cells with an em dash). `entry = SL` shows `err.entryEqSl`.
  - Direction errors show both the inline `err.slDirection` / `err.tpDirection` and the gate reason `DIRECTION`.
  - The `chk` passed to the gate is the manual ticks plus derived items 6 and 7 (6: entry, SL, TP valid and `xlRound(rr,4) ≥ RR_Min`; 7: no `LEVERAGE`/`MARGIN`/`LIQUIDATION` reason, `isolated` on, and `dashboard.status.openRisk == OK`).
  - **Reasons list** (`wiz.gate.reasons.title`): for each reason in engine order show label `wiz.gate.reason.<R>`, detail `wiz.gate.detail.<R>` with its arguments (numbers formatted, the article in the text) and the fix `wiz.gate.fix.<R>`. Under `CHECKLIST` list the unchecked items by their `short` names (items 6 and 7 are marked "auto": they clear themselves when the other reasons are fixed). `wiz.gate.deniedWith` is the one-line summary used in the strip.
  - **Advisor** (`wiz.adv.*`): when a feasible range exists show `wiz.adv.range` and the recommended chip `wiz.adv.recommended` (`wiz.adv.use` applies it). Explanations: `wiz.adv.tight` with `lev = ceil(lMinReal)` when `lMinReal > Lev_Max` (the stop is so close that the margin cap needs more leverage than allowed); `wiz.adv.far` with `lev = floor(lMaxLiqReal)` when `lMaxLiqReal < Lev_Max` (the liquidation-distance rule, not the leverage cap, is the binding upper bound). When no integer leverage is feasible show `wiz.adv.none` (plus `tight` when it applies) — then the only honest action is *reject* (article 3-3); never offer to raise risk or leverage.
  - **Fees**: a collapsible "include fees" row (`wiz.calc.fees`) shows R:R after round-trip fees using the formula of §5.7; it never changes the gate.
  - `wiz.calc.note` explains that the liquidation price is an estimate (isolated, no fees or funding).

**Step 5 — Review and commit (`wiz.step.review`).** Summary of every field, the formula line (`wiz.review.size`, `wiz.review.formula`), the verdict, and the actions:

| State | Primary | Secondary |
|---|---|---|
| allowed | `wiz.commit.enter` (opens the confirmation sheet) | `wiz.commit.plan` (save as PLANNED), `wiz.commit.cancel` |
| denied | `wiz.commit.reject` (save as REJECTED; toast `wiz.commit.rejected`) | `wiz.commit.cancel`; a de-emphasized text button "entered anyway" opens the confirmation sheet in the D-28 mode |
| locked | back to Step 1 | — |

**Confirmation sheet (`wiz.confirm.*`).** "Was the entry executed?" — actual average entry price, actual position value in USD (`wiz.confirm.note`), entry time (default now; Jalali date + 24 h time picker), and the three exchange toggles again if they were not set. On confirm: compute `rrisk`, `rriskp`, `verdict`, slippage `(entry − plannedEntry) / plannedEntry` signed by side (`wiz.confirm.slippage`), then apply the auto-violations of §3.9, persist (§4.4), schedule notifications (§7), and go to the trade detail. If `verdict` is `OVER_CAP` or `RED_LINE` show `size.silentGrowth` and the verdict text before saving; `RED_LINE` adds `V02` automatically. The button is disabled while `slTpOnExchange` is off; a secondary text button records `V01` and confirms anyway (so that history stays honest). `canMonitor = false` blocks confirmation (no violation; the user discards or waits — article 6-7).

### 5.4 Trade detail and management (`trade.*`) — P0

Header: symbol, side chip, status chip, account chip, imported/edited badges, overflow menu (duplicate `trade.duplicate`, share note `trade.shareNote`, delete `trade.delete.confirm`, correct numbers — D-23). Sections as collapsible cards in this order: plan (`trade.section.plan`), execution, management (OPEN only), result (CLOSED only), review.

```
┌ BTCUSDT · لانگ · باز ───────────────────────────── ⋮ ┐
│ قیمت فعلی [ 104.2 ]  →  R لحظه‌ای +1.05R   سود/زیان باز +$21.0  │  (local only, never stored)
│ SL فعلی 96.00 (اولیه 96.00 — مبنای R)   TP 108.00   خروج زمانی: ۷ کندل مانده │
│ [ انتقال SL به ورود ]  [ خروج پله‌ای ]  [ قاعدهٔ دعا ]  [ خبر ]  [ احساس ]  [ یادداشت ] │
│ پایش: آخرین بررسی ۱۴:۰۵ [ بررسی کردم ]                                  │
│ رویدادها: ۱۴:۲۰ حد ضرر از ۹۶ به ۱۰۰ رفت ·  ۱۴:۱۰ خروج پله‌ای …         │
│ [ بستن معامله ]                                                       │
└──────────────────────────────────────────────────────┘
```

- **Floating R**: with an optional typed `currentPrice`, `floatingPnl = sign × (price − entry) / entry × notional × (1 − Σ closedFractions)` plus realized legs; `floatingR = floatingPnl / rrisk`. Display only. When `floatingR ≥ Partial_R_Lo` and no partial exit exists, show `trade.partial.due` with a button.
- **Move stop** (`trade.moveSl`): a field and a "to break-even" shortcut that sets `entry ± entry × BREAKEVEN_FEE_BUFFER` (0.001; D-30). *Toward profit* (LONG: `new ≥ current`, SHORT: `new ≤ current`) is accepted at once and logged (`trade.moveSl.log`). *Away* or *remove* shows `trade.moveSl.away` and requires `trade.moveSl.away.confirm`, which records `V04`. `slInitial` never changes.
- **Partial exit** (`trade.partial.*`): price, fraction of the *original* position (default `Partial_Exit_Pct`), optional fee; appends a `Leg` and a `PARTIAL_EXIT` event; then offers to move the stop to break-even (`trade.partial.hint`). Remaining fraction = 1 − Σ fractions; a partial exit can never exceed it.
- **Trail** text (`trade.trail`, `trade.trail.lock` — the 50–70% lock only above `Trail_Lock_R` R) is guidance, not a mechanism.
- **Time stop** (`trade.timeStop.*`): `elapsed = floor((now − entryTime) / timeframeMinutes)`, `left = max(0, Time_Stop_Candles − elapsed)`. *Due* when `elapsed ≥ Time_Stop_Candles` and the trade has no partial exit and its stop is not yet at or beyond entry (D-29). A notification is scheduled for the due instant (§7).
- **Prayer rule** (`trade.prayer`, `trade.prayer.hint`): a button that logs `PRAYER` and prompts a partial exit of 50%. **Greed** text `trade.greed` is shown when `floatingR ≥ Trail_Lock_R`.
- **Never add** (`trade.noAdd`): a button `trade.noAdd.report` for the honest case: it adds `V05` (auto-logged as `ADD_REPORTED`) and the VIOLATION lock appears at once.
- **News / emotion / note**: free events (`trade.news.add`, `trade.emotionNow`, `trade.note`).
- **Monitor** (`trade.monitor.*`): appends the instant to `monitorChecks`.
- **Result and review** sections show the stored values; editing rules follow §4.3 and D-23.

### 5.5 Closing, result and post-trade review (`close.*`, `result.*`, `review.*`) — P0

**Close sheet.** Exit legs table (`close.legs`): rows price + fraction (percent); previously recorded partial legs are pre-filled and read-only, and the remaining fraction is pre-filled on one new row; `close.addLeg` adds rows; `close.sum` shows the running sum and `err.legsSum` blocks saving unless it is 100%. Then fee (`close.fee`, the total still owed beyond leg fees), reason (`close.reason`, auto-guess from the final leg: equal to `slCurrent` → `STOP_LOSS` (or `BREAKEVEN` when `slCurrent` is at entry, `TRAILING_STOP` when beyond entry), equal to `tp` → `TAKE_PROFIT`, otherwise `MANUAL`; `close.autoReason` tells the user a guess was made; `LIQUIDATED` and `EMERGENCY` are manual choices), and exit time (≥ entry time, not in the future: `err.dateOrder`, `err.futureTime`). A **preview** card (`close.preview`) shows gross, net, R, result and equity after, computed by `Planner.fill` on the form values; saving persists §4.4 and recomputes the snapshot.

**Result card (`result.*`)** — the same neutral layout for a win and a loss: net (`result.net`), gross, R (`result.r`, with `result.rVsInitial`), equity after, a compliance line (`result.compliant.yes|no`) and, when not compliant, the causes (`result.why` with `result.why.GATE|VIOLATION|SIZE`). A compliant loss shows `result.goodLoss`; a non-compliant win shows `result.badWin`. No animation beyond a 150 ms fade; no confetti, no streak counters.

**Post-trade review (`review.*`).** Order of the questions follows article 8-5: `review.ruleFirst` ("which rule did I break?") → violations (`review.violations`: multi-select of V01–V10; auto-detected ones are pre-ticked, locked and annotated with `review.autoViolation*`; suggested ones (V07, V09) are pre-ticked as "suggested" and need a confirm tap) → violation report when any violation exists (`review.report`, `review.report.min` counter; saving is blocked below `Report_Min_Chars` trimmed characters — `err.reportShort`) → exactly one main mistake (`review.mistake`, M00–M11, required) → emotion after exit → one-sentence lesson (`review.lesson`) → optional screenshots (Photo Picker, up to 5; D-13). *Later* (`review.skip`) leaves the trade pending (`review.pending` on Today). The suggestion rules: `V07` when the entry happened inside a cool-down or with emotion `REVENGE`; `V09` when a losing position is held longer than three times its time stop.

### 5.6 Journal (`journal.*`) — P0

- **List**: reverse-chronological by entry time (sort options `journal.sort.*`), grouped by display-calendar day with day net. Row: date, symbol, side chip, R chip (signed), net USD, setup, compliance icon, tags (demo, imported, pending review, open). Count line `journal.count`.
- **Search and filters** (sheet): status, result, symbol, setup, account, compliance, has-violation (`journal.filter.*`), text search over symbol/setup/lesson/notes (`journal.search.hint`). Filter state survives rotation; `journal.filter.clear`.
- **Detail** = the trade screen of §5.4.
- **Export** (`journal.export.*`): CSV in the `excel` profile (27 input columns, Persian headers, UTF-8 BOM) or the `full` profile (50 columns); share note (§5.4 template); the file is written to the cache and shared through `FileProvider` (`journal.export.share`).
- **Import** (`journal.import.*`): choose a CSV (`OpenDocument`), detect the profile by header names, show a preview (`journal.import.preview`: rows ok / skipped, with `journal.import.row.invalid` per bad row and `journal.import.dup` for duplicates), confirm, write in one transaction with `imported = true`. Import rules are in the content JSON (`csv.excel.import`) and §9. Imported rows count for equity, curve, KPIs and setup experience but never create a lock (D-14).
- **Empty states**: `journal.empty`, `journal.emptyFilter`.

### 5.7 Calculator (`calc.*`) — P0

A stateless page (nothing is saved, nothing affects limits: `calc.demoNote`). Inputs: side, equity (button `calc.useEquity`), entry, SL, TP, leverage, risk (default `riskCap`; label `calc.riskCap`), optional round-trip fee percent (`calc.withFees`). Output: the same nine numbers as the wizard, the gate verdict and the advisor, plus the **fee impact**:

```
reward   = notional × |tp − entry| / entry
risk     = notional × sld
fees     = notional × feePct
rrAfter  = (reward − fees) / (risk + fees)          (display only, decision D-36)
```

Example: notional 500, `feePct` 0.12% → fees 0.6; `rrAfter = 39.4 / 20.6 = 1.9126…`, shown as "from 2.00 to 1.91" (`calc.feeImpact`). The button `calc.toPlan` opens the wizard prefilled (symbol empty); the constitution reader's example opens the calculator prefilled with vector `P01` (`con.example.try`).

### 5.8 Insights (`ins.*`) — P0 for KPIs, equity, drawdown; P1 for the rest

- **Period** chips (`ins.period.*`): this week (from Saturday), this month (display calendar, D-31), last 30 days, all, custom (two Jalali date pickers). The period is applied to the **exit date** (`ins.period.exitDate`); REAL trades only.
- **KPI grid** (`ins.kpi.*`, definitions in `content.kpiDefs`): 13 tiles with value, status chip (`ins.status.*`, shape + text, not color alone) and target text (`ins.kpi.target` with the live `targetParam`). `n < Min_Trades` shows `ins.status.LOW_DATA`. Null values print `ins.kpi.na`. Under the grid: break-even win rate for the current real R:R (`ins.breakEven`, `1 / (1 + R)`) and the expectancy formula line `ins.expectancy.formula`.
- **Charts** (drawn with `Canvas`, §6.5): equity (`ins.chart.equity`, with note `ins.chart.equity.note`: P&L only, no deposits or withdrawals), drawdown, R histogram (bins `<−2`, `[−2,−1)`, `[−1,0)`, `[0,1)`, `[1,2)`, `[2,3)`, `≥3`), weekly compliance trend, average R by entry hour (4-hour buckets), by symbol, by setup, top mistakes (excluding `M00`), violation counts. Empty: `ins.chart.empty`.
- **Setup table** (`ins.setups.*`): n, win rate, expectancy, profit factor per setup, with `ins.setups.judge` while `n < Min_Trades`.
- `ins.top.mistake` shows the most frequent non-`M00` mistake of the period.

### 5.9 Reviews and review sessions (`rev.*`) — P0

- **Reviews hub** (`rev.title`): due items on top (`rev.due.*`), history (`rev.history`), buttons to start each review type.
- **Daily close** (`rev.daily.*`): four self-check items (`rev.daily.item.*`) plus an automatic list of today's trades that are still incomplete (missing mistake/lesson, open positions without SL/TP confirmation); saving writes a `DAILY` review.
- **Weekly** (`rev.weekly.*`): `weekStart` is the Saturday (default: the current or just-ended week; the user may pick an earlier unreviewed week). KPIs of the week are computed and frozen; the five questions of `content.learn.journal.weeklyQuestions` become five text fields (`rev.weekly.q.hint`); the most frequent mistake is prefilled (`rev.weekly.topMistake`); one rule/habit for next week (`rev.weekly.rule`) is required. Saving writes a `WEEKLY` review with `periodStart = weekStart`, which releases a `WEEKLY_LOSS` lock when it belongs to the locked week (§3.9).
- **Monthly** (`rev.monthly.*`): KPIs of the month, thresholds shortcut (`rev.monthly.thresholds`, opens a review session), withdrawal suggestion (`rev.monthly.withdraw`: `withdrawSuggest` of the month's realized net; "record" creates a `WITHDRAWAL` ledger entry after confirmation), security checklist confirmation (`rev.monthly.security`).
- **Drawdown review** (`rev.drawdown.*`): two questions; qualifies for the drawdown release (§3.9). Offered automatically while a `DRAWDOWN` lock needs `REVIEW`.
- **Review session** (`rev.session.*`, D-06): *Start* calls `canStartSession(now, trades)`; when blocked show `rev.session.blocked.OPEN_POSITION` or `rev.session.blocked.RECENT_LOSS` (with the `until` time) and nothing else. While open (max 60 minutes, ended by *End*, by timeout, or automatically when a REAL trade is entered) the Rules screen is editable; otherwise it is read-only with `params.needsSession`.

### 5.10 Rules (`params.*`) — P0

All 45 parameters in two groups (`params.group.core`, `params.group.governor`), in the order of the content JSON. A row shows label, current value (formatted by the parameter's `kind`: `usd` → grouped, 2 decimals; `pct` → the `pct` clause format of §3.11 (`1٪`, `0٫۲۵٪`); `mult` → number plus `×`; `r` → number plus `R`, signed when negative; `dec`, `int`, `min`, `hours`, `days` → plain numbers, because the unit is already part of the Persian label), the article (`params.article`), the default (`params.default`) and a lock icon for `Chk_N` (`params.locked`) and for `Start_Equity` (edited only through the ledger baseline: `ledger.baseline.locked`).

Editing (session open): numeric field with the parameter's `min`, `max`, `step` (`params.invalid.min|max`); `Risk_Max ≥ Risk_Pct` (`params.invalid.riskOrder`); the live change kind chip from `classifyChange` (`params.change.TIGHTEN|LOOSEN|NEUTRAL`, `LOOSEN` states the delay `Loosen_Delay_H`), a mandatory reason (`params.reason`, `params.reason.hint`) and, for `LOOSEN`, the warning `params.warn.loosen`. *Apply* writes a `param_change` (applied now, effective per `changeEffectiveAt`) and an audit row. **Pending changes** are listed (`params.pending.*`) with *cancel* until `effectiveAt`. **History** (`params.audit*`). *Reset to defaults* (`params.reset`) is a batch of ordinary changes through the same pipeline (its loosening parts are delayed too).

### 5.11 Setups and watch-list (`setups.*`, `wl.*`) — P0

**Setups**: list with grade chip, closed-trade count, expectancy, status, and the test banner when fewer than `Setup_Test_Trades` real trades exist (`setups.test`). Form: name, entry rule, invalidation, target, notes. *Promote* to grade A is enabled only when `gradeAEligible` is true (`setups.eligibleA`, otherwise `setups.notEligibleA`); *demote* is always allowed. Archive instead of delete when trades reference the setup. More than two active setups shows `setups.limit` (a hint, not a block). P1: the `testWaived` switch (`setups.demo.test`, D-21).

**Watch-list**: at most `Watchlist_Max` symbols (`wl.limit`, `wl.full`). Row: symbol, 24 h futures volume (USD), relative volume (current volume ÷ 20-period average), key levels, notes, last liquidity check. The indicator (`wl.filter.pass|fail`, `wl.volume`, `wl.relVolume`) compares against the DataStore thresholds of D-22 (informational; checklist item 3 stays manual). Rows not checked for 30 days show `wl.recheck`. Thresholds are editable only inside a review session (no delay).

### 5.12 Ledger and withdrawals (`ledger.*`) — P0

Shows `equity.now` (`ledger.equity`), the entries, an add form (`ledger.add`: type, amount, date, note) and the monthly withdrawal rule text (`ledger.withdraw.rule` with the live `Withdraw_Pct`, `ledger.withdraw.hint` with the previous month's realized net and suggested amount). Rules: a withdrawal larger than the current equity is refused (`ledger.negative`); the `BASELINE` row is read-only outside a review session (`ledger.baseline.locked`; changing it also changes `Start_Equity`, both in one transaction and one audit row); `ADJUSTMENT` carries `ledger.adjust.hint`; `ledger.note.curve` explains that flows never touch the P&L curve or drawdown but do change start-of-day and start-of-week equity. Edits and deletions of entries write `audit_log`.

### 5.13 Constitution, learn pages, security checklist — P0 / P1 / P1

- **Constitution reader** (`con.*`, P0): intro, table of contents (`con.toc`), article pages rendering `blocks` (`clause`, `p`, `formula`, `list` — ordered, with item ids — and `example`). Clause text is rendered by `Clause.render` with live parameters; rendered numbers are highlighted and tappable (sheet "comes from setting X: now Y, default Z" → Rules; `con.live`, `con.live.hint`). Search over clauses (`con.search`), read tracking (`con.mark.read`, `con.readProgress`), formula blocks in a monospace-friendly LTR box, the worked example with *open in calculator*, the pledge page (`con.pledge.*`: view, sign, re-sign when the version changes) and `con.amend` (opens a review session; amendments are rule changes, not text edits). Static Persian digits convert to Latin when the user turned Persian digits off (§3.10).
- **Learn** (`learn.*`, P1): summary (thesis, four axes, twelve lessons), review of experiences (5 items: experience / correction / reason / solution, the important one marked `learn.important`), additions (10 items), journal guide (stages, mistake labels, quick template), and an expectancy explainer with a tiny calculator (`learn.calc.*`: `E = p × R − (1 − p)`).
- **Security checklist** (`sec.*`, P1): the six clauses of article 10 as checkboxes with `checkedAt`; *mark all* (`sec.markAll`) sets `lastSecurityReviewAt`; incomplete count `sec.incomplete`; a monthly reminder (§7).

### 5.14 Spot portfolio (`spot.*`) — P2

Separate from the trading account (`spot.note`). Assets with thesis, invalidation, weight cap and current weight (percent of the portfolio, typed by the user), `spot.overWeight` when weight > cap, staged entry/exit notes, quarterly review (`spot.review`, `spot.reviewDue`), and the reminder that ETF flows, exchange reserves and open interest are inputs, not reasons (`spot.signals`). It never touches equity, limits or KPIs.

### 5.15 Settings, security, backup, about (`set.*`, `about.*`) — P0

Sections: appearance (theme, digits, calendar, zone, reduce motion, haptics; font size follows the system scale and is also offered as a 3-step slider), security (app lock, PIN, timeout, hide in recents — §8.2), notifications (§7), backup (§8.3), data (CSV in/out, JSON export of everything `set.exportAll` with a plaintext warning, wipe with typed confirmation, demo data load/remove — P1), about (version, build, open-source licenses, the meaning of the name `about.meaning`, privacy statement `about.privacy`, disclaimer `about.disclaimer`, font credit `about.fonts`). Demo data (`set.demoData.*`, P1) loads 12 `DEMO` trades flagged `imported = false, account = DEMO` so screens can be explored without touching real statistics.

### 5.16 Feature checklist (for the final acceptance, §12)

Every item of the audited Excel journal has a home: parameters sheet → Rules; journal columns → wizard + trade detail + review; plan formulas (`sld … liqX`) → wizard step 4 and calculator; verdict formulas → size verdict and result card; dashboard (six limits) → Today; KPIs and monthly indicators → Insights; reviews (daily/weekly/monthly) → Reviews; setups, watch-list, mistakes list, violations list, security list → their screens; equity ledger and withdrawals → Ledger; the quick note template → share note. Anything in the constitution that is a number is a parameter; anything that is a sentence is a clause rendered live.


## 6. Visual design (`:app`, `ui/theme`, `ui/components`)

### 6.1 Intent

Andarz should feel like a **calm instrument panel**, not a casino: dark, quiet, precise, with color used only to carry meaning. One hero per screen; money and R are the biggest numbers; depth comes from tonal surfaces rather than shadows; the Persian type is generous (line height 1.6). The app never celebrates and never nags: it states the rule, the number and the article.

Semantic colors (from `andarz_design_tokens.json → semantics`):

| Meaning | Token | Used for |
|---|---|---|
| allowed, profit, permission | `firouzeh` (turquoise) | allowed verdict, positive R, primary actions, "on target" |
| caution, grade-A-only, near a limit | `saffron` | gauges from ⅔ of the cap, `GRADE_A_ONLY`, pending/hold notes |
| red line, loss, locked | `anar` (pomegranate) / `anarFill` for filled buttons | denied verdict, locks, violations, negative R |
| information, charts | `lapis`, `violet` | secondary series, links, neutral info |

**Colour is never the only signal**: every state also has an icon and a text label, and every signed number has its `+`/`−`.

### 6.2 Theme generation

- Generate `AndarzColors` (custom CompositionLocal) and a Material 3 `ColorScheme` for each theme from the JSON tokens. A unit test loads the JSON and asserts that every Kotlin color constant equals its token (no drift).
- Mapping (dark and light use the same roles; values come from each theme's `colors`):

| Material 3 role | Token |
|---|---|
| `background` / `surfaceContainerLowest` | `bg` |
| `surface` / `surfaceContainerLow` | `surface` |
| `surfaceVariant` / `surfaceContainer` | `surfaceAlt` |
| `surfaceContainerHigh` / `surfaceContainerHighest` | `surfaceHigh` |
| `onBackground`, `onSurface` | `text` |
| `onSurfaceVariant` | `textMuted` |
| `outline` | `outline` (UI outlines ≥ 3:1) |
| `outlineVariant` | `divider` |
| `primary`, `onPrimary`, `primaryContainer`, `onPrimaryContainer` | `firouzeh`, `onFirouzeh`, `firouzehContainer`, `firouzeh` |
| `secondary`, `secondaryContainer`, `onSecondaryContainer` | `lapis`, `lapisContainer`, `lapis` |
| `tertiary`, `tertiaryContainer`, `onTertiaryContainer` | `saffron` (light: fills use `saffronFill`), `saffronContainer`, `saffron` |
| `error`, `errorContainer`, `onErrorContainer` | `anar`, `anarContainer`, `anar`; filled error buttons use `anarFill` + `onAnarFill` |
| `scrim` | black at 60% |

- Extra tokens live in `AndarzColors`: `allowed`, `caution`, `redline`, `info`, `violet`, `chartSeries[0..4]`. In the light theme `saffron` text is `#8A5600`; the chart amber `#B87300` is for graphics only and never for text (see `chartSeriesNote`).
- Theme switch: `DARK` (default), `LIGHT`, `SYSTEM`. Dynamic color (Material You) is **not** used: the palette is part of the identity.
- Shapes: 8 / 14 / 20 / 28 dp (`radiusSmall/Medium/Large/Sheet`). Spacing: 4 dp grid with the scale `4 8 12 16 20 24 32 48`; screen padding 16; card padding 16; minimum touch target 48 dp. Elevation is tonal in the dark theme; the light theme may use 1–2 dp shadows.

### 6.3 Typography, digits, bidi

- Font: **Vazirmatn** static TTFs 400/500/700 in `res/font` (SIL OFL; ship the license text in About). Scale from tokens: display 32/700, headline 24/700, title 18/700, bodyLarge 16/400, body 15/400, label 12/500; line-height factor 1.6 for Persian text. Enable `fontFeatureSettings = "tnum"` for numbers where supported.
- All layout uses `start`/`end`. Directional icons use `autoMirrored`. The app forces RTL (locale `fa`), and everything must still work if a device reports LTR.
- **Bidi rule**: money, R values, prices, percentages with signs, symbols (`BTCUSDT`) and dates in numeric form are rendered inside a left-to-right isolate (U+2066 … U+2069) by `Fmt.isolate`. Presentation helpers (app layer only, tested with two examples each): `money(x) = isolate(sign + "$" + number)` → `+$39.40`, `−$20.60`; `rChip(r) = isolate(signed(r, 2) + "R")` → `+1.97R`; `pct(x)` per §3.10.
- Number inputs: a `NumberField` composable that accepts Persian/Arabic digits and separators while typing (normalize with `Fmt.normalizeInput` on change, keep the raw text for display), uses `KeyboardType.Decimal`, shows the parsed value formatted below the field when it differs from the raw text, and never loses the caret position. Date inputs use a **Jalali date picker** you implement on top of §3.12 (month grid starting on Saturday, Friday highlighted as weekend, year/month jump) when the display calendar is Jalali; use the Material date picker for Gregorian. Time uses the Material 3 24-hour picker.
- Never truncate decision-critical text (reasons, numbers, locks): let it wrap. Test at 200% font scale.

### 6.4 Signature components

Build these once in `ui/components`, preview each in `@Preview` (dark/light, RTL, 200%), and reuse them everywhere.

1. **VerdictBanner** (`state: ALLOWED | DENIED | LOCKED | CAUTION`): rounded 14 dp container tinted with the semantic container color, 1 dp border of the semantic color at 40%, icon 28 dp, title in `title` style, one line of numbers (`body`), an expandable "why" list for `DENIED`. It is a polite live region (`liveRegionMode = Polite`): TalkBack announces changes of state. The compact **VerdictStrip** (40 dp) is the same content on one line and stays pinned under the wizard header.

```
 ✓ مجاز                                         ✗ ممنوع: R:R، مارجین                     🔒 توقف بعد از حد ضرر
   ریسک ۱٪ = $20 · مارجین ۵٪ · لیکوئید ۴٫۸۸×      R:R برابر ۱٫۳؛ دست‌کم ۱٫۵ لازم است (ماده ۴، بند ۶)  ▾   ۱۲ دقیقه مانده
```

2. **LimitGauge**: label, "value of cap" text, a 6 dp rounded track with fill = `min(1, value / cap)`; colors per §5.2; segmented variant for counts; one TalkBack node with `stateDescription`. Size ≥ 48 dp tall including text.
3. **PriceLadder**: vertical scale, higher prices at the top for LONG and SHORT alike. Markers: TP (firouzeh), Entry (text), SL (anar), Liquidation (anar, dashed; saffron when `liqX < Liq_Mult × 1.25`). Each marker shows the price, the distance in % from entry and the R multiple (TP = `rr`, SL = −1 R). Sizes are proportional to price distance, with a minimum 24 dp gap between labels (push apart, keep order). When direction is wrong the markers are drawn but the ladder carries a red outline.
4. **DisciplineRing**: 96 dp ring, 10 dp stroke, track in `divider`, progress in `firouzeh` (≥ `Compliance_Target`) or `saffron` (below), a small tick at the target angle, the percentage in the center, caption `ins.kpi.compliance`. With `n < Min_Trades` the ring is dashed and shows `ins.status.LOW_DATA`.
5. **RChip**: pill 28 dp, signed R value, container color by sign (firouzeh / anar / neutral for 0), arrow icon up/down/flat.
6. **LockBanner**: anar container with a 4 dp start-edge stripe, lock icon, title, body, tabular countdown, one button per `need`. Never dismissible while the lock is active.
7. **ChecklistRow**: 56 dp+ row with a round "stamp" toggle (150 ms scale 0.9 → 1 when switched on); derived rows (6, 7) show an "auto" badge and cannot be toggled; item 8 shows the emotion warning when disabled.
8. **SectionCard**, **StatTile** (KPI value + status chip + target), **EmptyState** (icon + one sentence + optional action), **ConfirmSheet**, **ReasonField** (multiline with the live character counter).
9. **KhatamPattern**: a decorative tiling of eight-pointed stars (two overlapping squares, the second rotated 45°) on a 48 dp tile, 1 dp strokes in `text` color at **≤ 6% alpha**, clipped to the Today hero card only. It is static, never animated, and must not reduce the contrast of any text over it.

### 6.5 Charts (own `Canvas` code, no chart library)

- All charts live in `ui/charts`, share one `ChartScaffold` (title, optional subtitle, legend, plot area, "table view" toggle) and are driven by plain data classes produced in the ViewModel.
- **The x axis always runs left → right in time order, even in RTL** (D-10): wrap the plot in `CompositionLocalProvider(LocalLayoutDirection provides Ltr)`; axis labels use `Fmt` (Persian digits when enabled) and short dates in the display calendar.
- Grid: 3–4 horizontal lines in `divider` at 1 dp; no vertical grid. Axis labels in `label` style with `textMuted`. The plot honors a 16 dp inset so strokes are not clipped.
- Series colors come from `chartSeries` in order; never rely on color alone: direct labels at line ends, markers for the last point, dashed styles for reference lines.
- **Equity**: line 2.5 dp (`firouzeh`) over a vertical gradient fill (18% → 0%), start-equity baseline dashed `outline`, the period's start/end highlighted. **Drawdown**: area hanging from the top (0%) down, `anar` at 25% alpha, a dashed saffron line at `DD_Cap` labelled with its value. **R histogram**: bars at 70% of the bin width; negative bins `anar`, positive `firouzeh`, the zero bin neutral; counts above the bars. **Category charts** (hour, symbol, setup, mistakes, violations): horizontal bars sorted by value, label at the start edge, value at the end edge.
- **Interaction**: long-press or drag shows a crosshair and a tooltip card (`ins.chart.point`); haptic tick per data point (if enabled). **TalkBack**: the chart node's description is `ins.chart.alt` with a computed summary (count, first → last value, extreme), and the "table view" toggle replaces the canvas with an accessible list of the same data.
- Performance: decimate to at most 600 points (min/max per bucket) for line charts; draw once into a `drawWithCache` path; recompute only when data changes. Animation: 250 ms path reveal on first draw unless motion is reduced.

### 6.6 Motion and haptics

- Durations 150 / 250 / 400 ms (`fast`, `standard`, `emphasized`), Material standard easing. Wizard steps use a 24 dp horizontal shared-axis slide in the reading direction plus fade. Sheets use the Material 3 defaults.
- **No bounce, no confetti, no counting-up numbers, no pulsing** anywhere. The only continuous motion allowed is the one-second tick of a lock countdown (text change, not animation).
- Reduced motion: honor `Settings.Global.ANIMATOR_DURATION_SCALE == 0` and the in-app switch → crossfades only (≤ 100 ms).
- Haptics (switchable): a light tick on checklist toggles and gate state changes, a double tick on destructive confirmations, nothing on results.

### 6.7 Brand assets

- **Adaptive icon** (`iconConcept` in the tokens): background = vertical gradient `#101A33 → #0A1016`; foreground = an upright, slightly tapered **alef** stroke in `firouzeh` inside the 66 dp safe zone plus a short `saffron` bar beneath it; monochrome layer = the alef only (themed icon). Starting geometry in a 108 × 108 viewport: stroke polygon `M50.5 24 L57.5 24 L55.5 76 L52.5 76 Z` with rounded joins, bar `x 46…62, y 82…86` with rounded ends; refine it visually and check it on a light, dark and themed launcher. Ship `ic_launcher`, `ic_launcher_round`, `ic_launcher_foreground`, `ic_launcher_monochrome` as vector drawables.
- **Splash**: `core-splashscreen`, background `bg`, the alef mark, no animation.
- **Notification small icon**: monochrome alef (24 dp, white, transparent background).
- App name `اندرز` comes from `app.name` (the manifest label must be a string resource generated from the content JSON at build time, because Kotlin sources may not contain Persian).
- Tagline «اول بقا، بعد سود» appears on the welcome page and About only.

### 6.8 Accessibility

- TalkBack: every control has a label (`a11y.*` and the visible strings), toggles expose on/off state (`a11y.toggleOn|toggleOff`), progress indicators expose `a11y.progress`, gauges and the verdict expose state words, charts expose summaries (6.5). Traversal order follows reading order in RTL; use `traversalIndex` only where the visual order differs.
- Touch targets ≥ 48 dp; checklist rows are fully tappable. Fonts follow the system scale up to 200% without clipping; test with the largest setting. Contrast: use the token pairs that the JSON lists as verified; never place text on `outline`.
- Announce lock/unlock transitions and verdict changes through live regions; never auto-focus a destructive button.

### 6.9 Visual acceptance checklist (check in the emulator, dark and light, at 100% and 200% font, RTL)

1. Today fits the hero, six gauges and the verdict on a 360 × 800 dp screen without scrolling (the rest scrolls).
2. The Persian digits and Latin symbols never reorder or flip (isolates work): `BTCUSDT`, `+$39.40`, `−0.5R`, `80.50`.
3. Every "no" shows reason + number + article; no gray-on-gray text; no text over the khatam pattern below 7:1.
4. The price ladder keeps labels apart for stops closer than 0.2% and for very wide stops.
5. Charts read left → right, labels do not overlap, TalkBack reads the summary.
6. Locking, unlocking and a denied verdict are understandable with the screen reader alone.
7. No animation longer than 400 ms; with "remove animations" on, nothing moves.

---

## 7. Notifications and background work

- **Channels** (`notif.channel.*`): `discipline` (default importance, vibration), `reviews` (low), `system` (low). Create them at app start. Request `POST_NOTIFICATIONS` (API 33+) only when the user first enables a reminder or confirms their first entry, with a short rationale; if denied, show an inline hint in settings and keep everything else working. No exact-alarm permission, no battery-optimization exemption: delivery may slip a few minutes under Doze and that is accepted.
- **Privacy**: `VISIBILITY_PRIVATE` with a public version that shows only the app name; no equity, no P&L, no symbol on the lock screen.
- **Source of truth**: notifications are *derived from state*, never from events alone. A `ScheduleReconciler` computes the **desired set** of reminders from the current snapshot and diffs it against the scheduled WorkManager jobs (unique work names below); it runs at app start, after any write to `trade`, `ledger_entry`, `review`, `param_change`, after restore, after a time-zone or clock change, and once a day from a periodic worker. It cancels stale jobs and enqueues missing ones. Workers do no network work and no heavy computation: they re-check the condition at fire time (a stale notification is suppressed) and post.

| Reminder (unique work name) | Fires at | Only if | Opens |
|---|---|---|---|
| `cooldown-end` | latest stop-out exit + `Cooldown_Min` | the cool-down is still the active lock | Today |
| `lock-end-<code>-<until>` | each lock's `until` | no unmet `needs` remain | Today |
| `time-stop-<tradeId>` | `entryTime + Time_Stop_Candles × timeframeMinutes` | trade still OPEN and the §5.4 due-condition holds | trade |
| `open-reminder-<tradeId>` | every 4 h while OPEN (08:00–23:00 local) | REAL trade OPEN | trade |
| `report-due-<tradeId>` | when a violated trade is closed, then every 6 h | report shorter than `Report_Min_Chars` | trade review |
| `daily-close` | 21:00 local | today has trades without a DAILY review, or a position is open | reviews/DAILY |
| `weekly-review` | Friday 18:00 local | no WEEKLY review for the current week | reviews/WEEKLY |
| `monthly-review` | 1st of the display-calendar month, 10:00 | no MONTHLY review for the previous month | reviews/MONTHLY |
| `backup-reminder` | `lastBackupAt + backupRemindDays` (09:00) | toggle on | settings/backup |
| `security-review` | `lastSecurityReviewAt + 30 d` (09:00) | toggle on | security |
| `advice-daily` | 09:00 local | `adviceEnabled` | Today |

- Texts: `notif.<name>.title|body` with their placeholders (`notif.timeStop.*` uses `{symbol}` and `{n}`). `notif.partial.*` is reserved for a future manual price alert (P2); in P0 the same sentence is shown in-app on the trade screen.
- **Quiet hours** (`set.notif.quiet`, default 23:30–07:00, D-34): any reminder that would fire inside the window is deferred to its end. Per-kind toggles: cooldown/lock end, time stop, reviews, open positions, backup, advice (`set.notif.*`).
- Tapping a notification starts `MainActivity` with an internal deep link (explicit `PendingIntent`, `FLAG_IMMUTABLE`); the app lock screen is shown first when enabled, then the target route.
- No home-screen widget, no ongoing notification, no foreground service.

---

## 8. Security and privacy

### 8.1 Threat model (be honest in the code comments and in About)

Protects against: someone who picks up the phone, other apps (no storage permissions; private storage), a stolen backup file (encrypted), accidental loss (backup + restore). It is **not** tamper-proof against its own user: the owner can wipe the app, but the app makes loosening the rules slow and visible (review sessions, 24 h delay, audit log). The database is not encrypted at the application level (decision D-12): Android file-based encryption and the app lock are the layers; backups are encrypted with a user password.

### 8.2 App lock

- Methods: biometric (`BiometricPrompt`, `BIOMETRIC_STRONG`) with the app PIN as fallback; or PIN only. PIN is 4–6 digits.
- PIN storage: PBKDF2-HMAC-SHA256, 16-byte random salt, 120,000 iterations, 32-byte hash, compared in constant time; only hash, salt and iteration count are stored in DataStore; the PIN is never stored or logged.
- Attempt policy (D-33): 5 free attempts; then wait 30 s, doubling on each further failure up to 30 min. `pinFailCount` and `pinLockedUntil` persist across restarts; a correct PIN resets them (`set.lock.pin.tries`, `set.lock.pin.wait`). There is no PIN recovery: the only way out is wipe + restore from an encrypted backup; say so in the PIN-setup sheet.
- Lock timing: after `timeoutSeconds` ∈ {0, 60, 300} in the background (`ProcessLifecycleOwner`), and always on a cold start. While locked, **no journal content is composed**. With `hideRecents` on, set `FLAG_SECURE` on the window (default on when the lock is on).

### 8.3 Encrypted backup

Container (all integers big-endian):

```
offset  size  field
0       4     magic  "ANDZ"
4       1     format version = 1
5       1     kdf id: 1 = PBKDF2-HMAC-SHA256
6       4     iterations (production 600000; read from the file, accept 10000 … 5000000)
10      16    salt (SecureRandom, new for every backup)
26      12    nonce (SecureRandom, new for every backup)
38      1     flags: bit0 = payload gzip-compressed before encryption, bit1 = screenshots included
39      …     AES-256-GCM ciphertext followed by the 16-byte tag
```

- Key = PBKDF2-HMAC-SHA256(UTF-8 of the NFC-normalized password, salt, iterations, 32 bytes). AES-GCM with the 12-byte nonce and **AAD = the ASCII bytes of `andarz-backup-v1`** (the vector `crypto.aesGcm` pins this exactly; `crypto.pbkdf2` pins the KDF at 10,000 iterations; production reads `crypto.productionDefaults`). Password: at least 8 characters, typed twice (`err.passwordShort`, `err.passwordMismatch`); the password is never stored; show `set.backup.warn`. Derive on `Dispatchers.Default` with a progress indicator.
- File name `andarz-backup-YYYYMMDD-HHMM.andz` (Gregorian, Latin digits), written through `ActivityResultContracts.CreateDocument("application/octet-stream")`; then update `lastBackupAt` and write the audit row.
- **Payload** (UTF-8 JSON, then gzip, then encrypt): `{"format":"andarz-backup","version":1,"createdAt":ms,"appVersion":"…","zone":"Asia/Tehran","tables":{"trade":[…],"trade_event":[…],"attachment":[…],"setup":[…],"watch_symbol":[…],"ledger_entry":[…],"review":[…],"param_change":[…],"review_session":[…],"pledge":[…],"security_check":[…],"spot_asset":[…],"audit_log":[…]},"settings":{…},"files":{"<attachmentId>":"<base64 JPEG>"}}`. Rows serialize the entity fields by name; unknown fields are ignored and missing optional fields get defaults (forward and backward compatibility). `settings` excludes the PIN hash, lock state and notification permission. `files` is present only when the user ticked "include screenshots" (D-13).
- **Restore** (`set.backup.restore.*`): pick file → password → decrypt (`err.backup.notAndarz` for a wrong magic, `err.backup.version` for a newer version, `err.backup.wrongPassword` for a failed tag) → validate (one `BASELINE`, unique ids, leg fractions sum to 1, known enums) → summary (counts, date range, creation time) → typed confirmation → **replace-all in one Room transaction**; screenshots are written to files first and cleaned up on failure; a failed restore leaves the existing data untouched. Afterwards reconcile reminders, write `BACKUP_RESTORE`, and show `set.backup.restore.done`. Refuse files above 100 MB.
- Reminder every 30 days (§7). A plaintext JSON export exists (`set.exportAll`) behind a warning that it is not encrypted.

### 8.4 Data at rest and hygiene

- Everything lives in app-private storage; `allowBackup=false`, and `dataExtractionRules` / `fullBackupContent` exclude all domains.
- Screenshots: copy from the Photo Picker, decode, **re-encode as JPEG (EXIF removed)**, longest side 1600 px, quality 80, saved as `filesDir/shots/<id>.jpg` (D-13).
- Outgoing files (CSV, backup, note) are created in `cacheDir/out/` and shared through `FileProvider` with read-only temporary grants; the folder is purged at the next start.
- Logging: no `Log` calls in release (strip with R8 `-assumenosideeffects`); never log amounts, notes or passwords. No analytics, no crash-reporting SDK.
- **Build guards** (tests that fail the build): the merged manifest contains no `INTERNET` permission; the runtime classpath contains none of `okhttp`, `retrofit`, `firebase`, `play-services`, `crashlytics`, `sqlcipher`; no `.kt` file contains U+0600–U+06FF (law 3).
- Open-source licenses: generate an offline list at build time (AboutLibraries Gradle plugin or a hand-written JSON); do not use Google's OSS-licenses plugin (it pulls Play Services).

---

## 9. Import and export

### 9.1 Common CSV rules

UTF-8 **with BOM**, delimiter `,`, RFC 4180 quoting (CRLF line ends; quote fields containing comma, quote or newline; double the quotes). Numbers are written with `.` as decimal separator, no grouping, and the **shortest round-trip representation** (reuse `shortest()` of §3.1). Booleans `1`/`0`. **Spreadsheet-formula protection** for free-text cells (`symbol`, `setup`, `lesson`, `violationReport`, notes): when the text starts with `=`, `+`, `-`, `@`, TAB or CR, prefix a single quote on export and strip one leading quote of such cells on import. Writing happens off the main thread; imports run in one transaction and write an audit row.

### 9.2 Profile `excel` — the 27 input columns of the Excel journal

Headers and order are `csv.excel.columns`; value mapping is `csv.excel.map`. Export: one row per `OPEN`/`CLOSED` trade of the current filter (REAL by default). Formula columns are not exported. Persian words in cells come from content: yes/no from `common.yes` / `common.no`, the dash from `common.dash`, labels from `enums.*[].csv`. `exit` = Σ(price × fraction) of the legs; `exd` empty for open trades; `shot` = first attachment file name.

**Import** follows `csv.excel.import`, spelled out:

1. Decode as UTF-8 (strip BOM) else `err.csv.encoding`. Normalize headers (trim, remove ZWNJ, map Arabic `ي ك` to `ی ک`) and match them to the known headers, else `err.csv.header`. The profile is detected from the header set; unknown extra columns are ignored.
2. A row is a trade only when `sym, side, eq, entry, sl, tp, lev, notl` are all present. Numbers are parsed with `Fmt.normalizeInput`; dates accept Gregorian `yyyy-mm-dd` or Jalali `yyyy/mm/dd` (year < 1700 ⇒ Jalali). Rows that fail (missing numbers, `entry = sl`, exit before entry) are listed with their row number and skipped (`journal.import.row.invalid`).
3. Times: `entryTime` = the date at 12:00 local + (row index) seconds; `exitTime` = `exd` (or the date) at 12:00 + (row index) seconds and at least one minute after `entryTime`; no exit when `exit` is empty (status OPEN). `legs = [{exit, 1.0}]`, `exitReason` = `TAKE_PROFIT` if `exit == tp`, `STOP_LOSS` if `exit == sl`, otherwise `MANUAL`.
4. `chk` = number of yes cells among `c1…c8` (the cells are labelled "yes" via `common.yes`); `checklistMask` accordingly. `gateAllowed` = the engine's plan gate for the row's own numbers with the parameters in force at import time (no governor, because history is not enforced). `verdict`, `rrisk`, `rriskp`, `net`, `R`, `ok` are computed once with `ctx = {riskCap: Risk_Pct, gradeA: true}` and then persisted.
5. `setup` is matched by name (create a grade-B setup when missing); `viol` label → `V01…V10` (`—` or empty = none, `violationAt` = exit time); `mist` label → `M00…M11` (unknown labels are appended to `lesson` and the mistake becomes `M00`); `emo` label → emotion id; `kind` label → kind id. Defaults for fields the sheet does not have: `timeframe = 15m`, `htfTrend = RANGE`, `entryOrder = LIMIT`, switches true, `plannedEntry = entry`, `riskPctChosen = riskPctCap = Risk_Pct`, `account = REAL`, `imported = true`.
6. Duplicates (same symbol, side, entry, SL, TP, date and notional) are skipped and counted (`journal.import.dup`). The preview (`journal.import.preview`) is shown before anything is written; the note `journal.import.note` is displayed.
7. If imported trades start before the ledger `BASELINE`, the preview offers to move the baseline back to the start of the earliest imported day and to set its amount to `equityNow − Σ net of the imported rows` (editable). Never silently.

### 9.3 Profile `full` — native export

The 50 columns of `csv.full.columns`: times as ISO-8601 with offset, checklist `c1…c8` as `1/0`, `legs`, `slMoves` and `events` as JSON cells, `violations` and `gateReasons` joined by `|`. It carries every field the engine and the statistics use. Screenshots, monitor checks and plan-time switches are not included (use backup or JSON for a complete copy). Import of this profile trusts the persisted derived columns (`verdict`, `net`, `R`, `ok`, …) and recomputes only what is missing (decision D-24); missing plan-time fields get the defaults of §9.2 step 5; rows keep `imported = true` unless the column says otherwise.

### 9.4 JSON

`set.exportAll` writes the same document as the backup payload (uncompressed, unencrypted, without screenshots) for the user's own tooling. Restore never accepts a plaintext JSON.

### 9.5 Share note

`trade.shareNote` / `journal.export.note` produce plain text from the quick-note template `content.learn.journal.quickTemplate` (15 lines). Fill the values **by line index** so no Persian label appears in code. Algorithm per line: split the line on runs of two or more spaces into tokens; a token that contains a delimiter (`:` or the Arabic question mark — write it as the escape `\u061F` in Kotlin, never as a literal) is a *field*: keep its text up to and including its **last** delimiter and drop the remainder (that remainder is option text, for example the kind list on line 2); a token without a delimiter is option text (for example the yes/no on line 12) and is dropped. Output each field as `label + " " + value`, the fields of one line joined by four spaces. The value lists below are in field order; a field that has two values (line 1) joins them with ` / `; line 10 (the separator, no fields) is copied verbatim. A unit test runs the algorithm on the real template and expects exactly 1,1,1,1,1,1,2,4,4,1,0,4,1,1,1 fields on lines 0–14.

| Line | Field values, in order |
|---|---|
| 0 | entry date and time in the display calendar |
| 1 | symbol / side label |
| 2 | kind label |
| 3 | setup name |
| 4 | higher-timeframe trend label |
| 5 | `chk` (of 8) |
| 6 | equity; chosen risk % |
| 7 | entry; SL; TP; R:R |
| 8 | leverage; margin; notional; liquidation |
| 9 | emotion before entry |
| 10 | (separator, unchanged) |
| 11 | exit (weighted average); fee; net; R |
| 12 | compliance yes/no (`common.yes`/`common.no`) |
| 13 | main mistake label |
| 14 | lesson |

Share with `Intent.ACTION_SEND` (`text/plain`); nothing is copied to the clipboard.


## 10. Tests

Tests are part of the product. A milestone is done only when its tests exist and pass. Use a fake `Clock` (interface `Clock { fun now(): Instant }`, injected through Hilt) and a fixed zone (`+03:30`) in every time-dependent test.

### 10.1 Vector runner (`:engine`, the most important test)

Copy `spec/andarz_golden_vectors.json` to `engine/src/test/resources/`. One dynamic test (JUnit 5 `@TestFactory`, or a parameterized test) per case, **named by its `id`** (`P01`, `G18`, …) so a failure names the vector.

- Every case is `{id, desc, input, expected}`. `input.params` (optional) overrides keys of `meta.defaultParams`. Doubles compare with `|got − exp| ≤ abs + rel × |exp|` where `abs = rel = 1e-9` (`meta.tolerance`); strings, booleans, integers, `null`s and enum ids compare exactly; ISO instants are parsed with `OffsetDateTime` and compared as `Instant`s; `-0.0` equals `0.0`.
- `null` in `expected` means "undefined" (an empty KPI, for instance). No vector has a null plan value; the guard rule of §3.1 covers it and you test it yourself.

| Category | Cases | Input → call → expected |
|---|---|---|
| `xlRound` | 18 | `{x, d, expected}` → `XlMath.round(x, d)` (no `id`) |
| `plan` | 16 | `{side, equity, entry, sl, tp, lev, chk, [riskPct]}` → `Planner.plan` + `gate` → `{sld, rr, riskUsd, notionalMax, margin, marginPct, liq, liqDist, liqX, reasons[], allowed}` |
| `leverageAdvisor` | 6 | `{side, equity, entry, sl, tp, [riskPct]}` (chk = `Chk_N`) → `{lMinReal, lMaxLiqReal, feasibleMin, feasibleMax, recommended}` |
| `sizeVerdict` | 12 | `{rriskp, [riskCap], [gradeAAllowed]}` → verdict string |
| `fill` | 15 | `{side, entry, sl, equity, notional, legs, fee, violations, gateAllowed, [ctx]}` → `{rrisk, rriskp, verdict, gross, net, R, result, ok, equityAfter}` (`verdict` from `ctx.riskCap/gradeA`) |
| `curve` | 4 | `{start, nets}` → list of `{cum, peak, dd}` |
| `kpi` | 5 | `{trades (raw), from, to}` → resolve trades, curve, `kpis`, tallies → `{n, …, status{}, mistakeTally, violationTally, curve[{id, cum, peak, dd}]}` |
| `scenarios` | 33 | `{now, ledger, trades (raw), reviews, setups, [setupId], [params]}` → `Governor.snapshot` → `{equity{now, startOfDay, startOfWeek}, dashboard{…}, governor{canEnter, locks[{code, until, needs[]}], riskCap, reductions[{code, riskPct, remaining}], gradeAAllowed, bigWinHoldUntil, tradesToday, openPositions}}`; `needs` and reductions compare as sets/sorted by code, locks by order |
| `reviewSession` | 4 | `{now, trades}` → `{allowed, reason, until}` |
| `paramChange` | 8 | `{key, old, new, appliedAt}` → `{kind, effectiveAt}` |
| `paramsAt` | 2 | `{changes[{key, value, appliedAt, effectiveAt}], at}` → map of the changed keys to their values at `at` |
| `gradeA` | 4 | `{closedRs}` → `{eligible, n, expectancy}` |
| `jalali` | 53 checks | `pairs` (both directions, `yyyy-mm-dd`), `leapYears` (year → bool), `monthLengths` (year → 12 ints), `weekdaySaturdayZero`, `weekStart` |
| `format` | 37 | `number`, `signed`, `percent`, `normalizeInput`, `persianDigits` |
| `renderClause` | 29 | `{clauseId, text, refs, [params]}` → `Clause.render` |
| `crypto` | 2 + constants | PBKDF2 and AES-GCM primitives (in `:data`, same JSON) |

Scenario trades are *raw* (no derived fields): the engine must resolve them (§3.9). In total the file holds 248 checks in 16 categories; the runner prints a per-category summary and fails if a category is missing.

### 10.2 Worked-example acceptance test

A readable test (engine) and a UI flow (§10.6, F1) both reproduce the constitution's example: equity 2000, LONG, entry 100, SL 96, TP 108, leverage 5 → `sld 4%`, `rr 2`, risk $20, position $500, margin $100 (5%), liquidation 80.5, `liqX 4.875`, allowed; closed at 108 with fee 0.6 → gross 40, net 39.4, R 1.97, equity 2039.4, verdict `YES`, compliant. Stopped at 96 with the same fee → net −20.6, R −1.03.

### 10.3 Property tests (seeded `Random(42)`, ≥ 5,000 iterations each)

- `XlMath`: idempotent (`round(round(x,d),d) == round(x,d)`), odd (`round(−x,d) == −round(x,d)`), ties go away from zero.
- `plan`: `notionalMax × sld == riskUsd`; `margin × lev == notionalMax`; LONG liquidation below entry, SHORT above; `liqX` strictly decreases when leverage increases; `allowed ⇔ reasons.isEmpty()`.
- `leverageAdvisor`: the feasible set is an interval; `recommended` ∈ feasible and minimizes `|marginPct − Margin_Target|` (ties → smaller); every feasible `L` has no `MARGIN`/`LIQUIDATION` reason and every infeasible `L` ≤ `Lev_Max` has at least one.
- `fill`: exit at the entry price with zero fee → `EVEN` and net 0; exit at the stop with zero fee → `R = −1` (±1e-9); splitting a leg in two at the same price keeps gross unchanged.
- `curve`: `peak` is non-decreasing; `dd ∈ [0, 1)`; `cum_k = start + Σ net_i`.
- `snapshot`: invariant under any shuffle of the trade/ledger/review input lists (within tolerance); `canEnter ⇔ locks.isEmpty()`; `riskCap ≤ Risk_Pct`; adding a `DEMO` trade never changes the result.
- `Fmt`: `normalizeInput(number(x, d, fa, group)) == xlRound(x, d)` for random `x`, both digit modes, grouped and ungrouped; `number` never prints a sign for values that round to zero.
- `Jalali`: for **every day** from Jalali 1300-01-01 to 1500-12-29 (73,414 days) `toGregorian(toJalali(d)) == d` and consecutive days advance by exactly one day; leap years and month lengths match the vectors.
- **Content integrity**: for every clause in `constitution.articles` with `refs`, `Clause.render(text, refs, DEFAULTS) == text`; every `uiArgs` entry has exactly the placeholders found in its `ui` string; every `ui` key referenced from Kotlin exists; every `params[].key` exists in `Params`; every enum id used in code exists in `enums`.

### 10.4 Extension tests (behaviors that have no vector — write them)

1. **Persisted derived fields win** (D-24): a trade with a stored `verdict = GRADE_A_ONLY` stays so after `Risk_Max` is tightened.
2. **Imported history** (D-14): imported losing weeks, imported violations without reports and imported drawdown breaches create **no** locks, reductions or holds, yet count for equity, curve, KPIs, the `SETUP_TEST` counter and `gradeAEligible`.
3. **Time stop** (D-29): due after `Time_Stop_Candles × tf` unless a partial exit exists or the stop is at/beyond entry; for each timeframe id.
4. **Stop moves**: LONG moves up accepted, down → `V04`; SHORT mirrored; deleting the stop → `V04`; `slInitial` never changes and R always uses it.
5. **Break-even shortcut** (D-30): LONG `entry × 1.001`, SHORT `entry × 0.999`.
6. **Fee impact** (D-36): notional 500, entry 100, TP 108, SL 96, fee 0.12% → `rrAfter = 1.9126…`; zero fee equals `rr`.
7. **Share note**: the field-count test of §9.5 on the real template; a full trade fills every field.
8. **Wizard**: items 6–7 derived; emotion ≠ `CALM` blocks item 8; counter-trend caps the chosen risk at half (P1).
9. **Late logging** (P1, D-26) and **`testWaived`** (P1, D-21), each with its own cases.
10. **Review session** timeout and auto-end when a REAL trade is entered.

### 10.5 Data-layer tests (`:data`; Robolectric or instrumented)

- Room: migration tests for every schema version; DAO and repository tests with an in-memory database: full trade lifecycle, partial exits, SL moves, events, attachments cascade, soft rules (audit rows on edits and deletes), `paramsAt` with cancelled changes, ledger-based equity.
- Backup: the `crypto.pbkdf2` and `crypto.aesGcm` vectors, run **both as a JVM test and as an instrumented test on a device or emulator** (Android's security providers differ from the JDK's, and the vector password is Persian to pin the UTF-8 encoding; if the device result differs, implement PBKDF2 yourself over `Mac("HmacSHA256")`, RFC 8018 §5.2, about 20 lines); round trip with production iterations (one test, ~1–2 s); wrong password; flipped bit in the tag; truncated file; bad magic; newer version; restore atomicity (inject a failure halfway: the database is unchanged); a 5,000-trade backup in under 5 s.
- CSV: export → import round trip for both profiles (build the fixture from the 12 trades of vector `K01`); spreadsheet-formula protection; BOM handling; Jalali dates; duplicate detection; the baseline-move preview of §9.2 step 7.
- Settings and lock: PIN hash verification, attempt/lockout policy persisted across process restart.

### 10.6 UI flow tests (Compose, `connectedCheck`)

| Id | Flow |
|---|---|
| F1 | Onboarding (equity 2000, risk 1%) → pledge → wizard with the worked example → confirm entry (100, $500) → close at 108 with fee 0.6 → result net +39.4, R +1.97 → review (M00, lesson) → Today shows equity 2,039.40 |
| F2 | R:R 1.3 → denied with reason `RR`, number and article → save as rejected |
| F3 | Stop-out → cool-down banner with countdown → entry blocked → fake clock +30 min → allowed |
| F4 | Move the stop away → `V04` → `VIOLATION` lock needs *close* → close → needs *report* → 20-character report → lock until +48 h |
| F5 | Rules: session blocked with an open position; tightening applies now; loosening shows "in 24 hours" and the pending list; cancel works |
| F6 | Backup with password → wipe → restore; wrong password shows the exact error |
| F7 | App lock: wrong PIN ×5 → wait; biometric fallback path |
| F8 | Digits/calendar switches: Persian digits everywhere in Today and Journal (no ASCII digits outside symbol isolates); Jalali ↔ Gregorian |
| F9 | 200% font and light theme on Today, wizard step 4 and Journal: no clipped or overlapping text (assert node bounds inside the window) |
| F10 | TalkBack semantics: verdict banner, gauges and checklist rows expose labels and state |

### 10.7 Quality gates

Lint with zero errors; engine line coverage ≥ 90% (JaCoCo); cold start ≤ 1.5 s on a mid-range device; Today first frame after unlock ≤ 300 ms; snapshot ≤ 50 ms with 5,000 trades; release APK ≤ 25 MB; no `INTERNET`; no Persian literal in `.kt`.

---

## 11. Milestones

Work in this order. Each milestone ends with a green `./gradlew build`, updated `DECISIONS.md`, and a commit.

| # | Scope | Exit criteria |
|---|---|---|
| **M0 Skeleton** | Gradle (version catalog), modules `:engine :data :app`, Hilt, `spec/` copied, `ContentRepository` (asset JSON, typed accessors, `ui(key, args)`), theme from tokens, Vazirmatn, RTL shell with bottom navigation and placeholder screens, build guards (§8.4), `Clock` | App starts on a device in dark RTL with the right font; guards fail on a planted `INTERNET` permission and on a planted Persian literal |
| **M1 Engine A** | `XlMath`, `Fmt`, `Clause`, `Jalali`, `Params`, `Planner` (plan, gate, sizeVerdict, fill, compliant, advisor), `Stats`, `Limits`; vector runner for categories `xlRound plan leverageAdvisor sizeVerdict fill curve kpi jalali format renderClause` | All those vectors pass; property tests of §10.3 for them |
| **M2 Engine B** | `Governor`: locks, drawdown walk, reductions, big-win hold, sessions, change classification, `paramsAt`, grade A, snapshot; imported-history extension | `scenarios reviewSession paramChange paramsAt gradeA` pass; §10.4 items 1–2; coverage ≥ 90% |
| **M3 Data** | Entities, DAOs, converters, repositories, `EngineFacade` + reactive snapshot, `ParamsRepository`, audit, DataStore, schema export, first migration test | Lifecycle and persistence tests pass; the worked example runs headless through the repositories |
| **M4 Core flows** | Onboarding, Today, wizard (5 steps, confirmation sheet), trade detail and management, close sheet, result, post-trade review, Journal list/detail/filters | F1–F4 pass on an emulator; visual checklist 1–4 of §6.9 |
| **M5 Analysis and governance** | Calculator, Insights (KPIs, equity, drawdown), Reviews and sessions, Rules, Setups, Watch-list, Ledger | F5 passes; charts verified visually in both themes; accessibility pass 1 |
| **M6 Platform features** | Constitution reader and pledge, settings, app lock, notifications and reconciler, encrypted backup/restore, CSV and JSON import/export, share note | F6–F8 and §10.5 pass |
| **M7 Polish (P1)** | Remaining charts, counter-trend half size, late logging, `testWaived`, demo data, learn pages, security checklist, advice of the day, quiet hours, empty/error states, final icon and splash, light-theme pass, 200% font pass, TalkBack pass, performance pass | F9–F10 pass; §6.9 fully checked; quality gates met |
| **M8 Release prep** | R8, signing placeholder, version stamping, offline license list, README, final `DECISIONS.md`, manual test script, release notes; P2 (spot portfolio) only if time remains | `./gradlew assembleRelease` succeeds; acceptance checklist of §12 signed off |

---

## 12. Acceptance checklist

Check every line on a real device (Android 8 and a current one). Each item is observable.

**Formulas and rules**
1. The worked example of §10.2 reproduces in the wizard, the calculator and the journal.
2. Gate reasons appear in the fixed order with number + article; limits are compared after 4-decimal rounding.
3. Size verdict (`YES`, `GRADE_A_ONLY`, `OVER_CAP`, `RED_LINE`) and `ok` follow §3.5; a trade entered through a lock is non-compliant.
4. Multi-leg exits, fees and R against the initial stop are right; the equity curve contains only trade results.
5. KPIs, statuses and break-even win rate match the vectors; periods use the exit date.
6. After a stop-out, entry is blocked for `Cooldown_Min` minutes; two stop-outs end the day; the fourth trade of the day is blocked.
7. Daily 3% and weekly 6% losses lock until the next day / next Saturday (weekly also needs the weekly review); the 10% drawdown locks ≥ 7 days and needs a review, then risk is 0.5% for 20 trades.
8. A violation locks for 48 h and needs a ≥ 20-character report (and closing the position if still open); three violations in 30 days cut risk to 0.5% for 20 trades.
9. A big win holds risk and grade A for 24 h; a new setup trades at 0.25% for 20 trades; grade A needs 30 trades with positive expectancy.
10. Rules change only in a review session (blocked by an open position and for 2 h after a loss); tightening is immediate, loosening takes 24 h; every change has a reason and an audit row.

**Journal features (parity with the Excel workbook)**
11. All 45 parameters are editable (except the locked one) and drive every screen and every clause number.
12. A trade records: symbol, side, kind, setup, higher-timeframe trend, 8-item checklist, emotion, equity, entry, SL, TP, leverage, order type, notional, exit legs, fee, reason, violations, mistake, emotion after, lesson, screenshots, events.
13. The six dashboard limits, the open-positions list, obligations and the advice of the day are on Today.
14. Daily, weekly, monthly and drawdown reviews exist; the monthly review proposes and records the withdrawal; the ledger keeps baseline, deposits, withdrawals, adjustments.
15. Setups (with promotion rules), the watch-list (≤ 10), the mistakes and violations lists, the security checklist and the constitution reader with live numbers and the pledge exist.
16. CSV (Excel profile and full profile) and JSON export, CSV import with preview, share note, encrypted backup and restore all work offline.

**Experience and quality**
17. Dark theme is the default; light theme and system mode work; RTL is correct; Persian and Latin digit modes and Jalali/Gregorian display work everywhere.
18. No celebration, no streaks, no confetti; a win and a loss use the same neutral result card.
19. 200% font scale, TalkBack and reduced motion work on Today, wizard, trade detail, Journal.
20. Charts read left → right and have a table alternative for screen readers.

**Security and privacy**
21. No `INTERNET` permission; no analytics/crash/Play Services dependency; `allowBackup=false`.
22. App lock (biometric + PIN with the attempt policy), `FLAG_SECURE` for recents, locked content never composed.
23. Backups are AES-256-GCM with PBKDF2 and pass the crypto vectors; a wrong password or corrupted file fails with the right message and changes nothing.
24. No Persian literal in any `.kt` file; all strings come from the content JSON; `MISSING_COPY.md` is empty or explained.

---

## 13. Decisions and priorities

### 13.1 Decision register (seed of `DECISIONS.md`; add your own below D-37)

| Id | Decision | Why |
|---|---|---|
| D-01 | Start-of-day and start-of-week equity come from the ledger (equity just before local midnight / the week's Saturday), not from "now minus today's P&L" | Deposits and withdrawals must not distort the percentage limits |
| D-02 | The dashboard and governor re-base the drawdown peak at each release; the KPI `maxDD` is not re-based (all-time curve) | Release means a fresh start for the lock; KPI parity with the workbook |
| D-03 | New verdict `OVER_CAP` for risk above `cap × 1.05` and up to `Risk_Max` when grade-A sizing was not allowed; non-compliant | The workbook had no reduced caps |
| D-04 | Multi-leg exits: gross is the sum over legs, one total fee, R against the initial risk | Partial exits are part of the constitution |
| D-05 | Stop-out = `STOP_LOSS`, or a negative net with `R ≤ Stopout_R` | A manual exit near the stop is still a stop-out |
| D-06 | Review sessions last ≤ 60 min, are blocked by an open REAL position and by `Review_After_Loss_H` after a losing close, end on user action, timeout or a REAL entry | Article 12-5 |
| D-07 | A WEEKLY review carries `weekStart` and releases only that week's lock; WEEKLY/MONTHLY/DRAWDOWN reviews qualify for the drawdown release; DAILY reviews never release a lock | Unambiguous release rules |
| D-08 | Stored `gateAllowed` = plan gate clean ∧ `entryAllowed` (governor + dashboard) | Trading through a lock must show as non-compliance |
| D-09 | Emotion ≠ `CALM` makes checklist item 8 un-tickable; items 6 and 7 are derived from the numbers | Prevents ticking boxes without checking |
| D-10 | Chart x axes always run left → right | Time is read the same way in every locale |
| D-11 | `applicationId = ir.andarz.app` is a placeholder; store availability of the name is unverified | Decide before release |
| D-12 | No SQLCipher or app-level DB encryption | Device encryption + app lock + encrypted backups; fewer moving parts |
| D-13 | Screenshots are downscaled JPEGs (EXIF stripped) in private storage; in backups only on request | Size and privacy |
| D-14 | Imported rows get 12:00 local timestamps (+ row-index seconds) and are *history*: they count for equity, curve, KPIs and setup experience but never create locks, reductions or holds | Old data must not lock the user |
| D-15 | `DEMO` trades are excluded from every rule, limit and KPI | Practice must not change real limits |
| D-16 | One violation *event* per trade, however many violations it has | Keeps pause and the 3-in-30-days rule predictable |
| D-17 | Week starts Saturday; Friday is the weekend; no holiday data is bundled | Iranian trading week; avoids stale data |
| D-18 | `Start_Equity` mirrors the `BASELINE` ledger entry; both change together, only in a review session | One source of truth |
| D-19 | Default zone `Asia/Tehran`; tests use fixed `+03:30` | Iran has no DST |
| D-20 | In the light theme the chart amber `#B87300` is graphics-only (3.76:1); text uses `#8A5600` | Contrast rules |
| D-21 | Optional `testWaived` exempts a setup with ≥ `Setup_Test_Trades` demo trades from `SETUP_TEST` (P1) | Article 12-6 "or in demo" |
| D-22 | Liquidity thresholds (100 M USD, relative volume 0.7–3) are DataStore settings, informational only | Article 12-3 says tune them; they are not engine rules |
| D-23 | While any lock is active, closed REAL trades are read-only for numeric corrections and cannot be deleted; all corrections and deletions are audited and badged | A lock must not be removable by editing history |
| D-24 | Persisted derived fields win; recomputation only fills gaps | Law 6 |
| D-25 | Counter-trend trades need a written reason and half the risk (reason in P0, half size in P1) | Article 4 item 1 |
| D-26 | Late logging (P1): a trade logged after the fact is judged with the governor snapshot at its own entry time and flagged `loggedLate` | Honest history |
| D-27 | A symbol outside the watch-list is a warning, not a gate reason | The gate has six fixed reasons |
| D-28 | "I entered anyway" after a denial records the matching violations and `gateAllowed = false` | History stays honest |
| D-29 | Time stop is due after `Time_Stop_Candles` entry-timeframe candles unless a partial exit exists or the stop is at/beyond entry | The app does not know prices |
| D-30 | Break-even shortcut = entry ± 0.1% | Covers typical round-trip fees; the field stays editable |
| D-31 | "Month" means the display-calendar month (Jalali by default) | Matches how the user thinks |
| D-32 | Advice of the day = `adviceOfDay[epochDay mod 25]` | Deterministic, no state |
| D-33 | PIN attempt policy: 5 free, then 30 s doubling up to 30 min, persisted | Tiny key space needs lockout |
| D-34 | Quiet hours (default 23:30–07:00) defer every reminder | Predictable behavior |
| D-35 | Backup container `ANDZ` v1, PBKDF2-HMAC-SHA256 600,000 iterations, AES-256-GCM, AAD `andarz-backup-v1` | Pinned by vectors |
| D-36 | Fee impact on R:R = `(reward − fees) / (risk + fees)`, display only, never part of the gate | The constitution asks to include fees; the gate is workbook parity |
| D-37 | `PLANNED` rows do not count toward trades, positions or open risk and expire after 12 h | Only entered trades are rules-relevant |

### 13.2 Priorities

- **P0** (v1): everything marked P0 in §5, the engine and all vectors, backup/restore, lock, notifications, CSV.
- **P1**: counter-trend half size, late logging, `testWaived`, remaining charts, demo data, learn pages, security checklist, advice of the day, quiet hours, share note, JSON export.
- **P2**: spot portfolio, manual price alerts, tablet two-pane layout.

---

## Appendix A — Glossary (Persian ↔ English)

| فارسی | English | | فارسی | English |
|---|---|---|---|---|
| اندرز | counsel (the app) | | حد ضرر / SL | stop-loss |
| اساس‌نامه | constitution | | حد سود / TP | take-profit |
| ژورنال | journal | | اهرم | leverage |
| ستاپ | setup | | مارجین | margin |
| ماده / بند | article / clause | | ایزوله | isolated margin |
| ریسک | risk (loss at the stop) | | لیکوئید | liquidation |
| ارزش پوزیشن | position value (notional) | | ریسک‌فری | break-even (risk-free) |
| سرمایهٔ حساب | account equity | | خروج پله‌ای | partial exit |
| افت از اوج | drawdown | | حد ضرر دنبال‌شونده | trailing stop |
| سقف | cap | | قاعدهٔ دعا | prayer rule |
| قفل / توقف | lock / pause | | خط قرمز | red line |
| نقض | violation | | بازبینی / مرور | review |
| جلسهٔ بازبینی | review session | | تعهدنامه | pledge |
| فهرست پیگیری | watch-list | | امید ریاضی | expectancy |
| ضریب سود | profit factor | | درصد رعایت قوانین | compliance rate |
| اسپات / فیوچرز | spot / futures | | فومو | FOMO |

## Appendix B — Content JSON map (`andarz_content_fa.json`)

| Key | Shape | Used by |
|---|---|---|
| `meta` | `{schema, locale, direction, app{name, latin, tagline, disclaimer}, sourceDocument, rules[]}` | app name, About |
| `params` | 45 × `{key, label, note, kind, default, min, max, step, group, article, tighter, locked}` | Rules, `Params`, validation |
| `enums` | 24 enumerations: `side kind emotion htfTrend timeframe entryOrder exitReason tradeStatus account result sizeVerdict gateReason lockCode lockNeed reductionCode dashboardStatus kpiStatus ledgerType reviewType paramChangeKind setupGrade tradeEvent violation mistake`; items `{id, label, csv?, …}` (`timeframe.minutes`, `emotion.calm`, `entryOrder.warn`, `violation.{clauseId, detect, detectRule}`) | everywhere |
| `checklist` | 8 × `{n, id, short, full, clauseId, text, needsCalmEmotion}` | wizard step 3 |
| `kpiDefs` | 13 × `{id, label, formula, targetText, targetParam, targetTemplate, format, statusKey}` | Insights |
| `calendar` | Jalali and Gregorian month names, weekdays (Saturday first), short names, `weekStart`, `weekend` | date UI |
| `csv` | `excel{columns[27], map, import, …}`, `full{columns[50], formats, …}` | import/export |
| `constitution` | `intro`, `articles[13]{n, title, blocks[]}` (blocks: `clause`, `p`, `formula`, `list`, `example`; clauses carry `refs`), `pledge{template, fields, version}` | reader, pledge |
| `learn` | `summary`, `review`, `additions`, `journal` (incl. `quickTemplate`, `weeklyQuestions`) | learn pages, weekly review, share note |
| `security` | 6 × `{id "10-n", text}` | security checklist |
| `adviceOfDay` | 25 × `{id, text, source}` | Today |
| `ui` | 839 flat keys (`group.sub.key`), `{placeholder}` arguments | all screens |
| `uiArgs` | key → list of placeholder names (114 keys) | tests, callers |

## Appendix C — Traps that cost a day each

1. `String.format`, `NumberFormat`, `toUpperCase()` and friends use the **default locale**; on a `fa` device `%d` prints Persian digits and `toDouble()` then throws. Always `Locale.ROOT` for machine formatting; display formatting only through `Fmt`.
2. `Double.toString` is not guaranteed to be the shortest representation on every ART version; use `shortest()` of §3.1 before `BigDecimal`.
3. `RoundingMode.HALF_UP` is "away from zero" for ties; `Math.round` and banker's rounding are both wrong here.
4. Compare `now < until` strictly; a cool-down ending exactly now is over (`G33`).
5. The week starts on **Saturday**; `DayOfWeek.SATURDAY` arithmetic: `weekStart = date − ((dow.value − 6 + 7) % 7)`.
6. Jalali conversion uses **truncating** division (as in `jalali_ref.py`), not `floorDiv`.
7. Instants in JSON carry offsets (`+03:30`); parse with `OffsetDateTime`, compare as `Instant`.
8. Persian text is forbidden in `.kt`; the Arabic question mark must be `؟`; the manifest label comes from a generated string resource.
9. Isolate every Latin token and number in RTL text (`U+2066 … U+2069`), including notification texts and CSV share notes.
10. `Modifier.align(Alignment.Start)` flips in RTL; `Arrangement.Start` too. Never write `Left`/`Right`; icons that point must be auto-mirrored; charts opt out of RTL on purpose.
11. Material 3 `DatePicker` is Gregorian; the Jalali picker is yours.
12. `BiometricPrompt` with `DEVICE_CREDENTIAL` cannot have a negative button; the app PIN is a separate screen.
13. WorkManager is inexact and survives reboot; never assume a notification fired — derive state from the database.
14. Room `Double` columns accept NaN/∞; validate before inserting. Never use `Float`.
15. A trade's derived fields are history; do not recompute them in a repository `get`.
16. Do not let the UI compute rules: if you find a formula in a Composable, move it to `:engine` and test it.
17. Process death: wizard drafts live in `SavedStateHandle`; sensitive fields (none are secrets) still must not be logged.
18. Compose previews need the Vazirmatn font and `LayoutDirection.Rtl`; build a `PreviewTheme` wrapper.
19. `FLAG_SECURE` blocks screenshots in your own testing; make it conditional on the setting.
20. Do not add a dependency "just for charts, dates or storage": the allowed set is §2.1.

## Appendix D — Files you maintain

- `DECISIONS.md`: start with the table of §13.1; every decision you add (numbered after D-37) has *context, choice, reason*.
- `MISSING_COPY.md`: a list of `key — where it is needed — proposed English description` (never invent Persian legal-sounding text; wait for the owner).
- `README.md`: build/run/test commands, module map, how the spec files are used, how to regenerate the theme check, how to run the manual test script.
- `MANUAL_TEST.md`: the acceptance checklist of §12 as checkable steps with expected values.

## Appendix E — Kickoff message for a standalone build (paste as the first message to the coding agent)

For the merged build with the March price app, use `KICKOFF_MESSAGE.txt` from the kit instead; it supersedes the text below.

> Read `ANDARZ_MASTER_PROMPT.md` completely, then `spec/andarz_ref.py`, `spec/andarz_golden_vectors.json` (meta and one case per category), `spec/andarz_content_fa.json` (top-level keys and `params`) and `spec/andarz_design_tokens.json`. Create the repository exactly as §2.2 describes and do **M0**. Do not ask me questions: decide, and write the decision in `DECISIONS.md`. When `./gradlew build` is green and M0's exit criteria hold, commit, give me a five-line status (what works, what is risky, what you decided), and continue with M1. Never edit a golden vector or `andarz_ref.py` to make a test pass.
