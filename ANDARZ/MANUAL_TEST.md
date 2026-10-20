# MANUAL_TEST.md — acceptance script

Every line is observable on a device. Run it on Android 8 (API 26) and on a current Android.
Items 1–24 are §12 of `ANDARZ_MASTER_PROMPT.md`; A1–A12 are K11 of `KICKOFF_MESSAGE.txt`.

**Status column** — `pending` nothing to run yet · `blocked` needs a toolchain or device this machine does
not have (see D-M33) · `pass` / `fail` with the date and what was observed.

> **What has run, as of M8.** `./gradlew build` is green with every K8.4 guard; unit tests: engine 275, data
> 30, app 60, market 79 per variant (debug and release both run), including F9/F10 under Robolectric (D-71).
> `./gradlew assembleRelease` succeeds (R8, about 2.9 MB, debug-signed and marked `NOT PUBLISHABLE`, D-74).
>
> **No emulator or device is available**, so every row below that needs a running app is still `blocked` or
> `pending` and none is ticked (K12). Rows a build alone can answer are marked `partial` with what was checked.

---

## Formulas and rules

| # | Step | Expected | Status |
|---|---|---|---|
| 1 | Wizard, calculator and journal with the worked example: equity 2000, LONG, entry 100, SL 96, TP 108, leverage 5 | `sld` 4٪, R:R 2, risk $20, position $500, margin $100 (5٪), liquidation 80.50, liq÷SL 4.88×, allowed. Close at 108 with fee 0.6 → gross 40, net 39.40, R 1.97, equity 2039.40, verdict `YES`, compliant. Stop at 96, same fee → net −20.60, R −1.03 | blocked |
| 2 | Enter a plan that breaks several rules at once | Gate reasons appear in the fixed order DIRECTION, CHECKLIST, LEVERAGE, RR, MARGIN, LIQUIDATION, each with its number and its article; limits compare after 4-decimal rounding (R:R exactly 1.5 passes) | blocked |
| 3 | Confirm entries with actual sizes just over the cap, up to `Risk_Max`, and far above it | Verdicts `YES`, `GRADE_A_ONLY` (grade-A sizing allowed) or `OVER_CAP` (not allowed), `RED_LINE` above `Risk_Max`; a trade entered through a lock is non-compliant | blocked |
| 4 | Close a trade with two legs and a fee | Gross is the sum over legs, one total fee, R against the **initial** stop; the equity curve moves only by trade results | blocked |
| 5 | Insights for a period | KPIs, statuses and break-even win rate match the vectors; the period filters on the **exit** date | blocked |
| 6 | Stop out once, then twice, then enter a fourth trade in a day | Entry blocked for `Cooldown_Min` 30 minutes; two consecutive stop-outs end the day; the fourth entry of the day is blocked by `TRADE_CAP` | blocked |
| 7 | Lose 3٪ in a day, 6٪ in a week, 10٪ from the peak | Daily lock until the next local midnight; weekly lock until Saturday **and** a WEEKLY review of that week; drawdown lock ≥ 7 days **and** a review, then risk 0.5٪ for the next 20 trades | blocked |
| 8 | Record a violation; then three violations inside 30 days | 48 h pause, a report of ≥ 20 characters required, and closing the position while it is still open; three events inside the window cut risk to 0.5٪ for 20 trades | blocked |
| 9 | Close a trade at R > 3; use a brand-new setup; promote a setup | 24 h hold on raising risk and on grade-A sizing; a new setup trades at 0.25٪ for 20 trades; promotion needs 30 closed real trades with positive expectancy | blocked |
| 10 | Change a parameter outside and inside a review session | Blocked by an open position and for 2 h after a losing close; tightening applies at once, loosening after 24 h; every change carries a reason and an audit row | blocked |

## Journal features (parity with the Excel workbook)

| # | Step | Expected | Status |
|---|---|---|---|
| 11 | Open Rules | All 45 parameters are visible; all are editable except `Chk_N` (locked) and `Start_Equity` (through the ledger baseline); a change moves every screen and every clause number | blocked |
| 12 | Record one full trade | Symbol, side, kind, setup, higher-timeframe trend, 8-item checklist, emotion, equity, entry, SL, TP, leverage, order type, notional, exit legs, fee, reason, violations, mistake, emotion after, lesson, screenshots and events are all stored | blocked |
| 13 | Open Today | Six dashboard gauges, the open-positions list, the obligations list and the advice of the day are present | blocked |
| 14 | Run each review type | Daily, weekly, monthly and drawdown reviews exist; the monthly review proposes and records the withdrawal; the ledger holds baseline, deposits, withdrawals and adjustments | blocked |
| 15 | Open setups, watch-list, constitution | Setups with the promotion rule; watch-list capped at 10; the mistake and violation lists; the security checklist; the constitution reader with live numbers and the pledge | blocked |
| 16 | Export and import, offline | CSV in the Excel profile and the full profile, JSON export, CSV import with a preview, the share note, and encrypted backup and restore all work with the network off | blocked |

## Experience and quality

| # | Step | Expected | Status |
|---|---|---|---|
| 17 | Switch theme, digits and calendar | Dark is the default; light and system work; RTL is correct; Persian and Latin digits and Jalali/Gregorian display work on every screen | blocked |
| 18 | Close a winning and a losing trade | The same neutral result card; no confetti, no streaks, no praise | blocked |
| 19 | 200٪ font, TalkBack, reduced motion on Today, wizard, trade detail, Journal | Nothing is clipped or overlapping; every control is announced; with animations removed nothing moves | blocked |
| 20 | Open every chart | The x axis runs left → right; a table view is available for screen readers | partial: LTR plot and table toggle in code (D-70); device check pending |

## Security and privacy

| # | Step | Expected | Status |
|---|---|---|---|
| 21 | Inspect the release APK (as amended by K2) | `INTERNET` appears **only** through `:market`'s manifest and every guard of K8.4 is green; no analytics, crash or Play Services dependency; `allowBackup=false` | partial (M8): aapt2 on the release APK shows the K8.1 set and `allowBackup=false`; no Firebase/Crashlytics/GMS classes; guards green |
| 22 | Turn the app lock on | Biometric and PIN with the attempt policy (5 free, then 30 s doubling to 30 min); `FLAG_SECURE` hides the app in recents; no journal content is composed while locked | blocked |
| 23 | Back up, then restore with a wrong password and a corrupted file | AES-256-GCM with PBKDF2 passes the crypto vectors; a wrong password, a flipped tag bit, a truncated file and a bad magic each fail with their own message and change nothing | partial: crypto vectors and each failure mode pass in `PlatformDataTest`; the messages on a device pending |
| 24 | `git grep -nP "[\\x{0600}-\\x{06FF}]" -- "*.kt"` | No hit. All wording comes from the content JSON; `MISSING_COPY.md` is empty or explained | pass (M8): 0 hits over the 139 tracked `.kt`/`.kts` files of ANDARZ (the guard also runs in every build); MISSING_COPY lists three keys with reasons |

---

## Market additions (K11)

| # | Step | Expected | Status |
|---|---|---|---|
| A1 | Fresh install; never open Market. Use every journal flow with the network on and off, while watching connections | **Zero** connections. Record how it was checked: `adb shell ss -tnp` / a proxy with the device pointed at it / a firewall log. Journal, engine, backup and every non-Market screen work offline | blocked |
| A2 | Inspect the merged release manifest | It equals the K8.1 allowlist exactly, plus the three recorded library entries of D-M40; the install-packages permission is absent; `allowBackup=false` | **partial** - guard 1 asserts all of this on every build and prints the merged set; a device install is still unverified |
| A3 | First open of Market | Prices shows the off state (`mkt.off.*`); its action opens the consent sheet; **"not now"** leaves Market off and silent; **"accept"** turns Market on and starts the hub. With the switch off, News and its release reminders still work | blocked |
| A4 | Prices with Market on | Top-200 list with rank, price and 24 h change; search by symbol and by name; star; the USDT/Toman card; the overview strip (market cap, BTC dominance, Fear & Greed); the USD/Toman toggle; refresh; the live-mode label; the offline state with the age of the last data; one typed error per service | blocked |
| A5 | Coin sheet | MK1: stats, range chart, star. MK2: per-coin rule with a percent threshold and above/below targets | blocked |
| A6 | Alerts | A threshold alert respects window, direction, cooldown and scope; target alerts fire once; each ring mode behaves; the Clock-app path and the own-alarm fallback both ring; Stop, Snooze 5 min, auto-stop at 3 min; it rings over the lock screen; tapping opens the coin **after** the app lock | blocked |
| A7 | News | Events grouped by day, impact filter, countdown to the next release, reminder 10 min before, Clock-app alarm offered within 24 h, the "released" state, and the expired state after `validUntil` | blocked |
| A8 | Monitor | Off by default; on shows the foreground notification with Stop; it survives a reboot only when enabled; off leaves no open socket | blocked |
| A9 | Isolation | Each planted violation of K8.4 fails the build; `git grep` finds no `ir.andarz.engine` or `ir.andarz.data` import under `market/`, and no `ir.andarz.market` import under `engine/` or `data/` | **pass (2026-10-15)** - all seven plants failed as expected (guard log in `DECISIONS.md`); the import grep is clean |
| A10 | Voice | No emoji; no English UI text; no claim that the app has no internet; no hype wording; no "March" or "Bazaaryar" in any shipped string except `mkt.about.credit` | blocked |
| A11 | Journal intact | Items 1–24 above pass and every golden vector passes; the diff of `engine/` and `data/` between the end of M4 and the end of the project holds only what the milestones and MK3 require | blocked |
| A12 | `MERGE_MAP.md` | No row says `todo`; every March source and resource file is ported, adapted, reimplemented, converted or dropped, with a reason | **in progress** — the map is complete, the rows are being closed per milestone |

---

## Device checklist for the ringing features (MK2)

Only a real phone can show these. Each one needs the matching switch turned on first.

| Step | Expected | Status |
|---|---|---|
| Alert rings through the phone's Clock app | A 1-second timer is handed over with no UI; the phone rings; the alert details stay in a normal notification | pending (MK2) |
| Clock-app path unavailable → own alarm | The app's own full-screen alarm rings instead, so the phone always rings | pending (MK2) |
| Ring over the lock screen | `AlarmActivity` appears over the lock screen, turns the screen on, and is not in recents | pending (MK2) |
| Snooze | Silences now and rings again after 5 minutes | pending (MK2) |
| Auto-stop | Ringing stops by itself after 3 minutes; the alarm-stream volume is restored to what it was | pending (MK2) |
| Alarm volume floor | A muted alarm stream is raised to 70 % while ringing and restored afterwards | pending (MK2) |
| Monitor start and stop | The foreground notification appears with a Stop action and is refreshed at most every 20 s | pending (MK2) |
| Reboot | Release reminders come back; the monitor comes back **only** when it was enabled | pending (MK2) |
| App update (`MY_PACKAGE_REPLACED`) | Same as reboot: reminders are re-registered | pending (MK2) |
| Deep links through the lock | A price alert opens `market/coin/{id}`, a release reminder `market/news`, the monitor `market` — each after the app lock | pending (MK2) |

## How to check "zero connections" (A1)

Pick one and write down which was used and what it showed:

1. **adb**: `adb shell ss -tnp | grep <pid>` while the journal is used — expect no established socket for the app.
2. **Proxy**: point the device's Wi-Fi at a logging proxy; expect no request from the app.
3. **Firewall**: an on-device firewall (e.g. a no-root VPN logger) with the app allowed; expect an empty log.

The same check, repeated after Market has been accepted and then switched back off, must again show nothing.

## M4 core flows (pending: needs an emulator or device; nothing below has been run)

| Id | Steps | Expected | State |
|---|---|---|---|
| F1 | Onboard (equity 2000, risk 1%), sign the pledge, create a setup, wizard: LONG 100/96/108, 5x, all 8 items, isolated + exchange stops + can monitor; enter; confirm 100 / $500; close at 108 fee 0.6; review M00 + lesson | Result net +$39.40, R +1.97; Today equity $2,039.40. A brand-new setup shows `OVER_CAP` (SETUP_TEST 0.25%, D-48) | pending |
| F2 | Wizard with TP 105.2 (R:R 1.3) | Strip "denied: R:R", detail with 1.5 and article 4 item 6; "save as rejected" stores REJECTED | pending |
| F3 | Close a trade at its stop | Cool-down banner with minutes left; FAB muted with lock; 30 min later allowed | pending |
| F4 | Move the stop away, confirm | V04; VIOLATION lock needs close + report; after close only report; 20-char report → lock until +48 h | pending |
| 6.9-1..4 | 360×800 dp, dark/light, 100%/200% | Hero + six gauges + verdict visible without scrolling; isolates keep `BTCUSDT`, `+$39.40`, `−0.5R` readable | pending |

## M5 analysis and governance (pending: needs an emulator or device; nothing below has been run)

JVM tests cover the calculator numbers and the fee impact (vector D-36), period ranges, the Insights series,
rule validation and reset order, the liquidity filter and due reviews (`AnalysisLogicTest`), and the
repository side of F5, setups and the watch-list (`JournalLifecycleTest`). These rows are the device part.

| Id | Steps | Expected | State |
|---|---|---|---|
| F5 | Open a position; Rules -> start session. Close it; start again; Risk_Pct 1 -> 0.5 with a reason; then 0.5 -> 1.5 | Blocked with `rev.session.blocked.OPEN_POSITION` (and `RECENT_LOSS` with its time after a losing close); 0.5 applies now (chip "tighter"); 1.5 shows "in 24 hours", the loosen warning, and a row under pending changes; *cancel* removes it and the history shows both | pending |
| M5-1 | Calculator with 2000 / LONG / 100 / 96 / 108 / 5x, fee 0.12 | Nine numbers as in the wizard, allowed banner, advisor 1-9 recommending 5, "from 2.00 to 1.91"; *build a trade* opens the wizard at step 1 with the numbers filled and the symbol empty | pending |
| M5-2 | Insights with the 12 trades of K01 imported, each period chip, dark and light theme | 13 tiles with status and target; equity, drawdown, R histogram, weekly compliance, hour/symbol/setup bars, mistakes, violations; no clipped labels; TalkBack reads one summary per chart (`ins.chart.alt`) | pending |
| M5-3 | Reviews: daily, weekly (pick an earlier week), monthly with a positive month, drawdown | Each saves once; weekly needs the rule; monthly offers the withdrawal and records it in the ledger after *yes*; a WEEKLY_LOSS lock of that week clears | pending |
| M5-4 | Setups: create three, promote a new one | `setups.limit` hint at three; promote disabled with `setups.notEligibleA`; archive and undo | pending |
| M5-5 | Watch-list: add ten symbols, then an eleventh; thresholds outside and inside a session | Add disabled at ten with `wl.full`; thresholds editable only inside a session | pending |
| M5-6 | Ledger: withdraw more than equity; edit the baseline outside, then inside a session | `ledger.negative`; baseline shows `ledger.baseline.locked` outside; inside, the edit also moves `Start_Equity` (one audit row each) | pending |
| A11y-1 | TalkBack over Rules rows, review checkboxes, chart cards; 200% font on Calculator and Insights | Each row is one node with label and value; checkboxes announce on/off; nothing clipped | pending |

## M6 platform features (pending: needs an emulator or device; nothing below has been run)

JVM tests cover the backup round trip, atomic restore, payload compatibility and validation, wipe, both CSV
profiles with duplicates and row errors, Jalali dates, the PIN policy (`PlatformDataTest`), the share-note field
counts, quiet hours and the reminder plan (`PlatformLogicTest`).

| Id | Steps | Expected | State |
|---|---|---|---|
| F6 | Settings -> backup with a password; wipe (type the word); restore with a wrong password, then the right one | Wrong password shows `err.backup.wrongPassword` and changes nothing; the right one shows the summary, then all trades, ledger and reviews are back and Today shows the same equity | pending |
| F7 | Turn the lock on with a PIN; background past the timeout; enter a wrong PIN five times | Lock screen with no journal content; after the fifth failure a 30 s wait that survives killing the app; biometric prompt when chosen | pending |
| F8 | Digits Persian/Latin and Jalali/Gregorian in Settings | Today, Journal, Insights and dates switch at once | pending |
| M6-1 | Export CSV (excel and full), open in a spreadsheet, import both into a fresh install | 27 and 50 columns, BOM, Persian headers; preview counts; imported trades counted in Insights, no lock created | pending |
| M6-2 | Constitution: open article 3, tap a live number, "open in calculator"; sign the pledge | Values follow current Rules; calculator prefilled with 2000/100/96/108/5x; pledge stored with date | pending |
| M6-3 | Close a trade at a stop, wait for the cool-down; open a trade on 15m and wait 10 candles; set quiet hours around now | `cooldown-end` and `time-stop` notifications arrive (deferred to the end of quiet hours); text on the lock screen shows only the app name; tapping opens the trade after the lock screen | pending |
| M6-4 | Settings -> Market: switch off while Prices is open; About -> network page | Off state at once, no connection; hosts listed and the credit line present | pending |
| A9 | Plant each K8.4 violation | Each fails the build (guard log) | done on the JVM guards; re-run before release |
| A10 | Read every shipped string | No emoji, no English UI text, no "no internet" claim, no March name outside the credit | done by guard 9 |

## MK3 integration (pending: needs a device with a network; nothing below has been run)

JVM tests: one-shot firing per level on a fake clock, stale quotes ignored, no symbols after close
(`TradeLinkTest`); rows deleted on close and the 1 -> 2 migration (`PlatformDataTest`); market settings round
trip without consent or switch (`MarketPrefsBackupTest`).

| Id | Steps | Expected | State |
|---|---|---|---|
| MK3-1 | Open a BTCUSDT trade, arm take-profit and stop alerts, leave the app open | When the price crosses, one private notification per level; tapping opens the trade after the lock; never again for that level; closing the trade removes the card's switches | pending |
| MK3-2 | Symbol FOOUSDT or a duplicated ticker | `mkt.link.nocoin`; nothing armed | pending |
| MK3-3 | Wizard step 4, calculator with a symbol, trade current price, with Market on | Chip with price, age and source; tap copies the number; after 60 s without ticks `mkt.chip.stale` | pending |
| MK3-4 | Watch-list with BTCUSDT while Prices is open | Price and 24 h change under the symbol; nothing with Market off | pending |
| MK3-5 | Back up, wipe, restore | Stars, rules and alert settings return; Market stays off until the user turns it on | pending |
| MK3-6 | `mkt.set.quiet` on, quiet hours around now, trigger an alert | Notification posted silently; no ring | pending |
| A11 | Golden vectors and the 24 items | Vectors pass in every build (engine 275 tests); `engine/` unchanged in MK3; `data/` gained only `trade_alert` | partial (device items pending) |
| A12 | MERGE_MAP | No todo rows | done |

## MK1 market data and prices (pending: needs a device with a network; nothing below has been run)

JVM tests cover parsing, merge, cadence, demand, zero traffic and formatting (D-M48). These rows are the
real-host and device part of A3, A4 and the MK1 part of A5.

| Id | Steps | Expected | State |
|---|---|---|---|
| MK1-1 | Fresh install, airplane mode off, open Market | Off state; no connection in the log of "How to check zero connections" | pending |
| MK1-2 | Accept the consent sheet | Within a few seconds: USDT/Toman card on top, 200 coins, overview strip, live label "Binance" (or "Nobitex" when Binance is blocked) with the clock | pending |
| MK1-3 | Leave Market for Today, wait 30 s | The connection log shows the socket closed and no further request (K6.4 a) | pending |
| MK1-4 | Airplane mode with Market on | The list stays with its last time; the header shows `mkt.err.noNetwork` under the last data | pending |
| MK1-5 | On a network where CoinGecko answers 403/451 | `mkt.err.blocked` naming CoinGecko; USDT/Toman and live prices still work | pending |
| MK1-6 | USD/Toman toggle, search "btc" and "Bitcoin", each sort pill, star and unstar | Prices switch units; USDT/Toman stays pinned; stars appear in My coins | pending |
| MK1-7 | Open a coin, then rotate or kill the process from the recents screen and reopen | The sheet shows stats, 7-day chart and star; section and sheet survive process death | pending |
| MK1-8 | Settings off (master switch) while Prices is open | Off state at once; the socket closes | pending (switch UI in M6; testable via the off state's action once M6 lands) |

## M7 polish (device items pending: needs an emulator or device)

JVM tests: late logging and demo data (`JournalLifecycleTest`), expectancy and decimation (`PolishLogicTest`),
F9 and F10 under Robolectric (`AccessibilityLayoutTest`, D-71).

| Id | Steps | Expected | State |
|---|---|---|---|
| F9 | Light theme, font 200%, 360 x 800 dp: Today, wizard step 4, Journal | No clipped or overlapping text | partial: Today, journal row and checklist row pass on Robolectric; wizard step 4 on a device pending |
| F10 | TalkBack on the verdict banner, gauges, checklist rows | Label plus state word read | partial: semantics asserted on Robolectric; spoken output pending |
| M7-1 | Confirm an entry with "minutes ago" = 120 after a stop-out 10 minutes ago | Entry recorded with `loggedLate` (visible tag waits for `trade.loggedLate`), judged by the rules at that time | pending |
| M7-2 | Settings > data > load sample trades; open Journal, Insights; remove | Twelve demo rows with the demo chip; equity, limits and KPIs unchanged; removal leaves hand-made demo trades | pending |
| M7-3 | More > learn pages; expectancy calculator 40 / 2 | All five sections readable at 200%; result +0.20R, break-even about 33% | pending |
| M7-4 | Insights: toggle the table view on each chart | The canvas is replaced by a readable list; TalkBack reads it line by line | pending |
| M7-5 | Reduce motion on (in-app and system) | The verdict banner changes size without animation | pending |
| M7-6 | Launcher icon on light, dark and themed launchers; cold start | Alef mark readable; splash shows the mark on `bg` with no animation | pending |

### §6.9 visual checklist (dark and light, 100% and 200%, RTL)

| # | Item | State |
|---|---|---|
| 1 | Today fits hero, six gauges and verdict on 360 x 800 without scrolling | pending (device) |
| 2 | Digits and Latin symbols never reorder (`BTCUSDT`, `+$39.40`, `−0.5R`, `80.50`) | partial: every value goes through `Fmt.isolate` (unit-tested); visual check pending |
| 3 | Every "no" shows reason + number + article; no gray-on-gray; khatam contrast | partial: gate reasons carry all three (M4 tests); contrast check pending |
| 4 | Price ladder labels apart for very close and very wide stops | pending (device) |
| 5 | Charts read left to right, labels apart, TalkBack summary | partial: LTR plot and summary node in code (D-70); visual check pending |
| 6 | Lock, unlock and denied verdict understandable by screen reader alone | partial: live regions and state words (F10); spoken check pending |
| 7 | No animation over 400 ms; nothing moves with animations removed | partial: one 250 ms animation, removed by reduce motion (D-72) |

### Quality gates (10.7)

| Gate | State |
|---|---|
| Lint zero errors | `./gradlew build` runs lint: green |
| Engine coverage >= 90% | measured in M1 (JaCoCo); engine unchanged since |
| Snapshot <= 50 ms with 5,000 trades | JVM test measures it on every build (printed); device timing pending |
| Cold start <= 1.5 s, Today first frame <= 300 ms | pending (device) |
| Release APK <= 25 MB, no `INTERNET` outside `:market`, no Persian literal in `.kt` | guards in every build; APK size measured in M8 |

## M8 release script (pending: needs a device)

| Id | Steps | Expected | State |
|---|---|---|---|
| R-1 | Install `app-release.apk` (debug-signed) on Android 8 and a current Android; run onboarding, the worked example, a backup and a restore, Market on with one alert | No crash: R8 kept every serializer, Room and Hilt class; backup file restores on the debug build too | pending |
| R-2 | About | Version `0.9.0-unsigned`, build = commit count, short commit | pending |
| R-3 | Build with a real keystore in `keystore.properties` | No `NOT PUBLISHABLE` box; version without `-unsigned`; `apksigner verify --print-certs` shows the new certificate, not March's | pending (owner, O-2) |
| R-4 | Name a keystore but omit its password | Configuration fails naming `ANDARZ_KEYSTORE_PASSWORD` | pass (M8): `ANDARZ_KEYSTORE=<file> ./gradlew help` fails with "release signing: ANDARZ_KEYSTORE_PASSWORD (or storePassword in keystore.properties) is missing" |

