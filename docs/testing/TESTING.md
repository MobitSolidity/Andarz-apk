# TESTING.md — how Andarz is tested

Status as of the opus5 audit (versionName 0.9.2). "Run" means executed in this sandbox; "pending" means it
needs a device or emulator, which this environment does not have. Nothing pending is reported as passed.

## 1. Automated tests

```bash
./gradlew build                         # every unit test in both variants + lint + guards 1-9
./gradlew :engine:test                  # pure JVM: golden vectors, properties, governor
./gradlew :app:testDebugUnitTest        # Robolectric incl. Compose layout/semantics tests
./gradlew :app:connectedDebugAndroidTest   # instrumented e2e (device/emulator; clear app data first)
```

| Module | Suite | What it proves | Tests | Result |
|---|---|---|---|---|
| engine | `VectorRunnerTest`, `GovernorVectorTest` | Every golden vector of `spec/andarz_ref.py` (sizing, gates, governor, locks) | 275 (all engine suites) | **run, 0 failures** |
| engine | `PropertyTest` | Invariants over random inputs (risk never above cap, R sign, stop monotonicity) | ″ | run |
| data | `JournalLifecycleTest` | Headless worked example through the repositories; V04 lock; partial exits; plan expiry; session rules; ledger; cascade delete; late entry; demo data | 30 per variant | run, 0 failures |
| data | `BackupCryptoTest`, `PlatformDataTest` | AES-GCM container, wrong password, tamper detection, Room migration 1→2, CSV round trip | ″ | run |
| market | `MarketHttpEgressTest` (guard 5) | Only GET to the five hosts over HTTPS/WSS; no body, `Authorization`, `Cookie` | 79 per variant | run, 0 failures |
| market | `ZeroTrafficTest` (guard 6) | No socket while Market is off or not demanded | ″ | run |
| market | Parsers, PriceHub, AlertEvaluator, Calendar, PrefsBackup | Market logic | ″ | run |
| app | `ContentBundleTest` | Every key used in code exists; live clauses render to their text; params match | 68 debug / 63 release* | run, 0 failures |
| app | `AccessibilityLayoutTest` | F9: no horizontal clipping at 200 % font, light theme, 360 × 800 dp; F10: label + state word | ″ | run (debug only, D-71) |
| app | `TouchTargetSourceTest` **new** | No IconButton < 48 dp; every `clickable` has a role | ″ | run |
| app | `UiErrorsTest` **new** | A-01: failures map to defined user-facing keys | ″ | run |
| app | `ProcessDeathTest` **new** | A-03: wizard and calculator drafts survive a Parcel round trip | ″ | run |
| app | `DeepLinksTest` | Only allow-listed routes from notifications | ″ | run |
| app | Wizard/Analysis/Polish/Platform/Prices/MarketFmt/TradeLink logic | View-logic units | ″ | run |
| app | `ColdStartE2ETest` **new, instrumented** | Real activity + Hilt + Room + DataStore: fresh install → onboarding → next page | 1 | **compiles; run pending (device)** |

\* the Compose tests skip themselves in the release unit-test variant, which has no test activity (D-71).

Totals from the last full `./gradlew build` (this audit): engine 275, data 60, market 158, app 131 test
executions across both variants, **0 failures**. JUnit XML lives in `*/build/test-results/`.

Static gates that run in `build`: Android lint (warnings reviewed, `lint-baseline` not used), guards 1–9
(`DECISIONS.md` → Guard log), no Persian in `.kt` (guard 7).

## 2. Not automated, on purpose

- **Screenshot tests** (Roborazzi / Paparazzi): need reference images from a real device run (D-81).
- **Macrobenchmark / baseline profile**: need a device (D-84); ROADMAP.
- **TalkBack spoken output**: semantics are asserted (F10), speech itself is device-only.

## 3. Manual device pass (release gate)

Run on at least three devices: Android 8.0 (API 26, minSdk), Android 14 or 15 (API 34/35, targetSdk), and
one small screen (≤ 360 dp wide). The detailed scripts F1–F10, M5-*, M6-*, M7-*, A1–A12 and the ringing
checklist are in `MANUAL_TEST.md`; this is the release smoke on top of them.

| # | Case | Expected | Status |
|---|---|---|---|
| S1 | Fresh install, cold start, airplane mode | Splash → onboarding in Persian RTL; no network request (A1 method) | pending |
| S2 | Onboarding, sign pledge, first trade F1 | Numbers as in F1 | pending |
| S3 | Kill the app from recents in the middle of the wizard (step 3), reopen | Draft restored at the same step (ProcessDeathTest covers the Parcel part) | pending |
| S4 | *Developer options → Don't keep activities* on; navigate every tab, background/foreground | No crash, state kept | pending |
| S5 | Font size max + display size max | No clipped text on Today, Wizard 4, Journal, Settings | pending |
| S6 | Dark/light/system theme switch; system dark at night | Correct theme, contrast OK | pending |
| S7 | Rotate on every screen | No crash, no lost input | pending |
| S8 | Fill storage until < 50 MB, then save a trade | Snackbar `err.storage`, no crash (A-01) | pending |
| S9 | Backup → uninstall → reinstall → restore | All data back; wrong password refused | pending |
| S10 | App lock with PIN, background past timeout, 5 wrong PINs, kill app | Lock screen, wait survives kill | pending |
| S11 | Notification permission denied (API 33+) then reminders on | Explanation + settings link; no crash | pending |
| S12 | Market on (consent), prices, then internet switch off while on Prices | Off state immediately, no further connection | pending |
| S13 | Price alarm with screen locked, Clock-app mode and own-alarm mode | Rings; Stop/Snooze; opens coin after unlock | pending |
| S14 | Live monitor on, reboot | Foreground notification back only if enabled | pending |
| S15 | TalkBack through Today, Wizard, Journal row, Settings | Every control has a label and role; order follows RTL | pending |
| S16 | Battery: leave the app in background 1 h with Market off | No wake-ups attributable to Andarz in *Battery usage* (P-01) | pending |
| S17 | Cold start time (`adb shell am start -W ir.mobitmaster.andarz/ir.andarz.app.MainActivity`) on the API 26 device | `TotalTime` < 1500 ms (record the value) | pending |
| S18 | Upgrade install over the previous Play version with data | Migration runs, data intact | pending |

## 4. Crash monitoring

No crash SDK (D-86: offline-first law, guard 4). Production crashes and ANRs come from Play Console → Android
vitals; retrace them with the archived `mapping.txt` (RELEASE.md §4). During device testing use
`adb logcat -b crash` and `adb shell dumpsys dropbox --print data_app_crash`.
