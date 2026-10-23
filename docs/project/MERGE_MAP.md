# MERGE_MAP.md — every March file, and what happened to it

One row per file of `march/` (read-only reference, K1). Paths are relative to
`march/Bazaaryar/app/src/main/java/ir/bazaaryar/app/` unless they start with `res/`, `app/` or `Bazaaryar/`.

**Treatments** (K5) — `PORT` copy, change package and imports, keep the logic · `ADAPT` keep the logic,
change the structure · `REIMPLEMENT` same information and behaviour, built from Andarz components ·
`CONVERT` change the data format · `DROP` do not carry over.

**Status** — `todo` · `partial` (what is done is named) · `done`.

A suspected March defect goes in the Notes cell and is **not** silently fixed (D-M23).

Acceptance A12 is met when no row says `todo` and every row has a reason.

---

## Data layer

| March file | Treatment | Goes to | Status | Notes |
|---|---|---|---|---|
| `data/Models.kt` | ADAPT | `:market` `model/` | done | Keep `Coin`, `LiveQuote`, `UsdtRate`, `MarketOverview`, `LiveMode`, `MarketState`, `Source`, `AlertSettings`, `CoinRule` and the enums. Every `tr()` and `label` is removed: labels become content keys (law 3). Defaults per D-M08 (alerts, monitor, ring all off). `EconEvent` moves to the calendar loader (see `data/Events.kt`). **MK1 done:** `model/Models.kt`. `UsdtRate.source` is `IranSource`, `MarketState.error` a `MarketError` (D-M44). |
| `data/Net.kt` | ADAPT | `:market` `net/MarketHttp.kt`, `net/EgressInterceptor.kt` | done | **M0 done:** the client with March's timeouts (connect 10 s, read 20 s, ping 20 s), `retrying` (2 attempts, 1.5 s pause), the localized `friendly()` replaced by typed `MarketError`/`toMarketError`, and the new egress interceptor of K4. **MK1:** the `num()` helper travels with the parsers that use it. **MK1 done:** `num()` in `data/Parsers.kt`; `MarketHttp.webSocket()` opens the socket through the same interceptor (D-M42). |
| `data/CoinRepository.kt` | PORT | `:market` `data/Parsers.kt`, `data/Sources.kt` | done | Top-200 markets with the 7-day sparkline, global overview, Fear & Greed. Parsers become pure `String -> model` functions testable on the JVM; `org.json` stays and is added as a **test** dependency (D-M28). **MK1 done:** `data/Parsers.kt` (pure) + `data/Sources.kt` `NetSources.top/overview`; fixture tests in `ParsersTest`. |
| `data/BinanceStream.kt` | PORT | `:market` `data/Sources.kt` | done | miniTicker WebSocket. The SUBSCRIBE-frame check of K4 is already written (`net.WsPolicy`, M0) and the port must build its frames through it. **MK1 done:** `NetSources.ticks()`; frames only via `WsPolicy.subscribeFrame`; symbols via `WsPolicy.streamOf`. |
| `data/IranMarket.kt` | PORT | `:market` `data/Parsers.kt`, `data/Sources.kt` | done | USDT/Toman from Nobitex, fallback Wallex. Its `tr()` texts become typed errors (`IRAN_PRIMARY`, `IRAN_FALLBACK`). **MK1 done:** `NetSources.iran()`; failure is a `MarketException` with the first typed error. |
| `data/PriceHistory.kt` | PORT | `:market` `data/PriceHistory.kt` | done | Session samples and range queries behind the 5/15/60-minute windows. **MK1 done:** `data/PriceHistory.kt`, unchanged; `PriceHistoryTest`. |
| `data/PriceHub.kt` | ADAPT | `:market` `data/PriceHub.kt` | done | The singleton becomes a class built by `MarketModule` with client, prefs, clock and dispatchers injected, so tests drive it with virtual time. Keep every loop and the cadence of K5.2. Add the consent and master-switch gate, drop the language handling, add the `extraSymbols` setter (MK3). **MK1 done:** `data/PriceHub.kt` (D-M43); the first acquire no longer kicks a second fetch; alert evaluation is the `onPublish` hook for MK2. |
| `data/Prefs.kt` | ADAPT | `:market` `MarketPrefs.kt` | partial | DataStore renamed to `market` (D-M17). **M0 done:** the store plus `marketEnabled` and `consentAt`. **MK1/MK2:** `watch`, `alarms`, `toman`, `alert_*`, `user_name`, `coin_rules`. **MK3:** `exportJson`/`importJson`. Dropped: `theme` and `lang` — Andarz owns both. **MK1 done:** all March keys (defaults per D-M08), injectable `DataStore` for tests. MK3 left: export/import. |
| `data/Events.kt` | CONVERT | `:market` `assets/market_calendar.json` + a loader | done | JSON `{validUntil, events:[{id, at (ISO-8601 with +03:30), country, title, titleEn?, impact, previous, forecast}]}`. Ids are unique per occurrence (e.g. `2026-10-cpi`) so a refreshed list never re-arms an old reminder. The flag-emoji map is dropped and the country becomes an ISO code chip (K6.7). **MK2 done:** `tools/convert_march_calendar.py` writes the asset from March's source (titles byte-identical, ids `yyyy-MM-<id>`); `calendar/Calendar.kt` loads it (`CalendarTest`). |

## Notifications, alarms, background

| March file | Treatment | Goes to | Status | Notes |
|---|---|---|---|---|
| `notify/PriceAlerts.kt` | ADAPT | `:market` `alerts/` | done | Split into a **pure evaluator** (state, history, settings, rules, watch, now → actions) and a **dispatcher**. Text through `MarketTexts`. Keep thresholds, windows, cooldown, scope, direction, one-shot targets, ring modes, at most 3 individual alerts per round plus one summary, and the test buttons. The emoji in the titles become ▲ ▼ and plain words (K6.7). **MK2 done:** `alerts/AlertEvaluator.kt` (pure, `AlertEvaluatorTest`) + `alerts/AlertDispatcher.kt`; wording via `MarketTexts`; emoji replaced by arrows (K6.7). |
| `notify/AlarmRinger.kt` | PORT | `:market` `alarm/` | done | Also holds `AlarmReceiver` and `ClockApp`. Text through `MarketTexts`. `ClockApp.setAlarm` keeps `ZoneId.systemDefault()`: the Clock app reads hour and minute in device time, not in the app's zone setting. **MK2 done:** `alarm/AlarmRinger.kt` (+ `AlarmReceiver`), `alarm/ClockRing.kt` holds `ClockApp`. |
| `notify/ClockRing.kt` | PORT | `:market` `alarm/` | done | `appVisible` is set by `:app`'s lifecycle observer through `MarketModule`. **MK2 done:** `alarm/ClockRing.kt`; `appVisible` via `MarketModule.setAppVisible` from `ProcessLifecycleOwner` in `AndarzApp`. |
| `notify/LiveService.kt` | ADAPT | `:market` `monitor/` | done | Opt-in only (D-M08). Texts and the open intent come from `MarketHost`. Posts with the notification tag `market` (D-M24). **MK2 done:** `monitor/LiveService.kt`; started/stopped by `MarketModule` from the switch and the gate. |
| `notify/Reminders.kt` | ADAPT | `:market` `calendar/` | done | Holds `Reminders`, `ReminderReceiver`, `BootReceiver`. Events come from the calendar loader. `BootReceiver` restores market state only — reminders always, the service only when enabled — after a reboot and after an app update. The 10-minute lead stays. **MK2 done:** `calendar/Reminders.kt` over a `ReminderScheduler` (fake in tests). |
| `AlarmActivity.kt`, `ui/AlarmScreen.kt` | ADAPT | `:market` `alarm/AlarmActivity.kt` | done | Its own minimal dark Compose screen with no dependency on `:app`'s theme. Labels through `MarketTexts`. "Open coin" uses `MarketHost.openIntent`. `taskAffinity ${applicationId}.alarm`, `showWhenLocked`, `turnScreenOn`, `excludeFromRecents`. It lives outside the app lock because it shows market data only (K6.11). **MK2 done:** `alarm/AlarmActivity.kt` with its own dark screen; "open coin" through `MarketHost.openIntent`. |

## Screens

| March file | Treatment | Goes to | Status | Notes |
|---|---|---|---|---|
| `ui/MarketScreen.kt` | REIMPLEMENT | `:app` `ui/market/PricesScreen` | done | Header (live-mode label, refresh, data age), overview strip (market cap, BTC dominance, Fear & Greed), search, coin rows. Built from Andarz components; the disclaimer `mkt.disclaimer` sits at the bottom. **MK1 done:** plus the USD/Toman toggle (K6.2) and the sort pills. |
| `ui/CoinSheet.kt` | REIMPLEMENT | `:app` `ui/market/CoinSheet` | done | Stats, range chart, star (MK1); per-coin rule editor with percent threshold and above/below targets (MK2). **MK1 done:** read-only part. MK2: rule editor. **MK2 done:** rule editor; input via `Fmt.normalizeInput`. |
| `ui/WatchScreen.kt` | REIMPLEMENT | `:app` `ui/market/MyCoinsScreen` | done | Starred coins as cards; keeps the block of reminded releases. **MK1 done:** the reminders block shows March's empty line until the MK2 calendar fills it. |
| `ui/NewsScreen.kt` | REIMPLEMENT | `:app` `ui/market/NewsScreen` | done | Events by day, impact filter, next-release countdown, reminder bell, Clock-app alarm for releases within 24 h, "released" state, plus the expired state of K6.10. Works with the master switch off, because the calendar is local. **MK2 done**, with the expired state and the validity line. |
| `ui/AlertsScreen.kt` | REIMPLEMENT | `:app` `ui/market/AlertsScreen` | done | Alert settings, per-coin rules, permission rows (notifications, full-screen, exact alarms, overlay) with their settings intents, test buttons. Shows its settings with the master switch off plus the note `mkt.link.needMarket`. **MK2 done.** The update section of March is dropped (D-M09). |
| `ui/Format.kt` | REIMPLEMENT | `:app` `ui/market/MarketFmt` | done | `MarketFmt` on top of `Fmt` (D-M14): March's price tiers (≥1000 → 0 decimals, ≥1 → 2, ≥0.01 → 4, below that three significant digits, at most 10), market cap with T/B/M, percent with sign **and** arrow. Honours the digit and calendar settings; prices, symbols and hosts are LTR isolates. Input parsing follows §3.10, never March's `parseNum`. **MK1 done** (D-M47): the small-price rule is March's `3 - floor(log10 p)` exactly; `MarketFmtTest`. |
| `ui/Components.kt`, `ui/Motion.kt` | DROP | — | done | Andarz has its own components (§6.4) and motion tokens (§6.6); D-M13. |
| `ui/Theme.kt`, `ui/I18n.kt` | DROP | — | done | Andarz owns theme and language. `I18n.kt`'s `tr()` is exactly what law 3 forbids; its Persian half is extracted into `content_market_fa.json` (K7), its English half into `docs/march_strings_en.json` (D-M11). |
| `ui/UpdateUi.kt`, `update/Updater.kt` | DROP | — | done | D-M09: no in-app updater, nothing that installs a package. |
| `MainActivity.kt`, `MainViewModel.kt` | DROP | — | done | Only the behaviours carry over: notification permission per §7; `EXTRA_COIN` becomes `MarketTarget.Coin`; hub demand by lifecycle (K6.4); `ClockRing.appVisible` from a lifecycle observer; channels created at app start; saved reminders re-registered at start. **M0 done:** `MarketTarget` and the `MarketHost.openIntent` contract. Rest in MK1/MK2. **MK2 done:** channels and reminders at start, `appVisible`, demand reason (b) in `MarketModule.start`; notification permission asked when an alert or the monitor is switched on. |

## Manifest, resources, build

| March file | Treatment | Goes to | Status | Notes |
|---|---|---|---|---|
| `app/src/main/AndroidManifest.xml` | ADAPT | `market/src/main/AndroidManifest.xml` | done | **M0 done:** the K8.1 permission set and the `SET_TIMER`/`SET_ALARM` queries. **MK2:** `AlarmActivity`, the three receivers and `LiveService` with its `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` — declared only once their classes exist, or lint's `MissingClass` fails the build (D-M36). **Dropped:** `REQUEST_INSTALL_PACKAGES`, the update `FileProvider`, the launcher activity, and the application attributes `allowBackup`/`theme`/`icon`. `usesCleartextTraffic="false"` moves to `:app`, where application attributes belong (D-M35). **MK2 done:** `AlarmActivity`, the three receivers and `LiveService` with the special-use property (K8.2). |
| `res/drawable/ic_stat_pulse.xml` | PORT | `:market` as `ic_stat_market` | done | Status-bar icon: monochrome with transparency. **MK2 done:** `market/src/main/res/drawable/ic_stat_market.xml`, same path data. |
| `res/drawable/ic_launcher.xml`, `ic_launcher_foreground.xml` | DROP | — | done | Andarz brand assets (§6.7): the alef mark. |
| `res/mipmap-anydpi-v26/ic_launcher.xml` | DROP | — | done | Same. |
| `res/values/colors.xml` | DROP | — | done | Colours come from `spec/andarz_design_tokens.json` (D-M13). |
| `res/values/themes.xml`, `res/values-night/themes.xml` | DROP | — | done | Andarz theme; dark is the default (law 9). |
| `res/values/strings.xml` | DROP | — | done | The app label is a generated string resource built from `meta.app.name` of the content JSON (Appendix C trap 8). |
| `res/xml/update_paths.xml` | DROP | — | done | Belonged to the updater (D-M09). |
| `app/build.gradle.kts` | DROP | — | done | Reference only, for the dependency set (Compose, DataStore, coroutines, core-ktx, OkHttp). Not carried over: the signing block, `fetchFonts` (K7 forbids a build-time font download — see D-M34), `baseVersion` and the CI versioning. |
| `app/proguard-rules.pro` | DROP | — | done | Andarz owns the build setup. |
| `Bazaaryar/build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/wrapper/gradle-wrapper.properties`, `.gitignore` | DROP | — | done | Same. |
| `Bazaaryar/ci/build-and-release.yml`, `Bazaaryar/.github/`, `.github/workflows/build-apk.yml` | DROP | — | done | K8.6: no CI workflow and no release automation is created. |
| `Bazaaryar/ci/march-ci.jks.b64` | DROP | — | done | **A signing key committed to a public repository.** Treated as compromised: never decoded, copied or reused, and its alias is not reused (D-M10, O-2). |
| `Bazaaryar-android_2.zip` | DROP | — | done | Never copied (K1, K8.6). *Not present in this clone.* |
| `Bazaaryar/README.md` | DROP | — | done | Reference for the data-source facts only; its updater and CI sections are obsolete. |

## Files in `march/` that are not March code

| File | Treatment | Status | Notes |
|---|---|---|---|
| `march/KICKOFF_MESSAGE.txt` | DROP | done | A copy of the kit's own kickoff message; the authoritative copy is `ANDARZ/KICKOFF_MESSAGE.txt`. |
| `march/اندرز — معرفی‌نامه و پرامپت ساخت اپلیکیشن.docx` / `.pdf` | DROP | done | The owner's own source documents for the constitution and the prompt. Not code; the delivered `spec/` files are the inputs (§0.2). |

---

## Behaviour that must survive the port unchanged (K5.2)

Checked off when the owning milestone proves it with a test. None of it is redesigned (D-M23).

| Behaviour | Milestone | Status |
|---|---|---|
| CoinGecko top 200 every 60 s on screen, every 5 min in background, back-off 2 min on HTTP 429 | MK1 | done (`PriceHubTest`) |
| Nobitex (fallback Wallex) USDT/Toman every 10 s; overview every 5 min | MK1 | done (`PriceHubTest`) |
| Binance ticks ~1 s; Nobitex prices when the socket has been quiet for 15 s; history samples every 5 s; a quote older than 10 min is unused | MK1 | done (`PriceHubTest`) |
| Background-only runs stream watch-list and rule coins only | MK1 | done (`PriceHubTest`) |
| Alert windows 5 min / 15 min / 1 h / 24 h; per-coin percent override; direction up/down/both; cooldown; scope watch-list or all; include USDT/Toman; one-shot absolute targets; ring modes off/targets/all; ≤ 3 individual alerts per round plus one summary; name in the greeting | MK2 | done (tested on the JVM; device part pending in MANUAL_TEST.md) |
| Ringing: looping alarm tone on the ALARM stream, repeating vibration, partial wake lock, full-screen over the lock screen, Stop and Snooze 5 min, auto-stop after 3 min, alarm volume raised to a 70 % floor and restored | MK2 | done (tested on the JVM; device part pending in MANUAL_TEST.md) |
| Clock app first (a 1-second timer, no UI) when allowed; the app's own alarm as the fallback, so the phone always rings | MK2 | done (tested on the JVM; device part pending in MANUAL_TEST.md) |
| Calendar: sorted by time, impact 1–3, countdown, reminder 10 min before (inexact while idle), Clock-app alarm only within 24 h, "released" state, filter all/high, grouping by day, counter for simultaneous releases | MK2 | done (tested on the JVM; device part pending in MANUAL_TEST.md) |
| Live monitor: foreground service of type `specialUse` with a Stop action, notification refreshed at most every 20 s, restarted after boot only when enabled | MK2 | done (tested on the JVM; device part pending in MANUAL_TEST.md) |
| Default stars: `bitcoin`, `ethereum`, `solana`, `ripple`, `usdt-irt` | MK1 | done (`DEFAULT_WATCH`) |

## M6 integration rows (K6.6, K6.9, K6.11)

| Item | Status | Notes |
|---|---|---|
| Settings -> Market section (K6.9) | done | Master switch (`mkt.set.enable` with its hint), USD/Toman default, background monitor (enabled only while the gate is open), link to Alerts, sources list built from `MarketHosts`. The quiet-hours mute (`mkt.set.quiet`, P1) lands with MK3. |
| About -> network page (K6.9) | done | `mkt.net.title/body/sent`, the host list from `MarketModule.hosts`, the credit line `mkt.about.credit`; the threat-model comment states that Market uses the internet. |
| Channel groups (K6.6) | done | Discipline group with `discipline`, `reviews`, `system` (tag `discipline`); Market group unchanged (tag `market`). |
| Deep links through the lock (K6.11) | done | `MainActivity` keeps the notification route while locked and hands it to the nav host after unlock; `onNewIntent` covers a running app. `AlarmActivity` stays outside the lock. |
| Backup of market settings | done | MK3: `settings.market` from `MarketPrefs.exportJson`; consent and switch excluded; `onRestored` after restore (D-M60). |
| Trade-linked alerts, live chip, watch-list prices, quiet mute (K9.1 b-e) | done | MK3: `TradeWatcher`, `TradeLink`, `LiveLink.kt`, `muteInQuiet` (D-M56..D-M60). |

## Suspected March defects (flagged, not fixed — D-M23)

| Where | Observation | Decision |
|---|---|---|
| `data/PriceHub.kt` `wantedSymbols()` | The symbol set is recomputed every 10 s in a watchdog coroutine that throws `Resubscribe` to restart the socket. A set that flaps between two values can resubscribe repeatedly. | Ported as is. MK1's virtual-time tests confirm the 10 s watchdog; a flapping set is not reachable from the UI (stars and rules change by user action only). Not changed. |
| `notify/PriceAlerts.kt` `firedTargets` | The one-shot guard is an in-memory `HashSet`, so a process restart can fire the same target again before `clearTarget` has been written. | Ported as is; `MarketPrefs` already clears the target, so the window is small. Re-check in MK2. |
| `data/PriceHub.kt` `compose()` | A live quote is accepted only when it is within 30 % of the CoinGecko price. A genuine 30 %+ move would be ignored until the next CoinGecko poll. | Deliberate sanity check in March (same-ticker tokens); kept. |
