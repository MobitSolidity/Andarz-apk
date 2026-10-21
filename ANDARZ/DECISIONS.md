# DECISIONS.md

Every decision that shapes Andarz, with context, choice and reason. Ids `D-01…D-37` come from §13.1 of
`ANDARZ_MASTER_PROMPT.md`; `D-M01…D-M24` come from K10 of `KICKOFF_MESSAGE.txt`. Anything added while
building is numbered `D-38` and up (general) or `D-M25` and up (merge).

Source priority when two sources disagree (K2): (1) golden vectors, (2) `spec/andarz_ref.py`,
(3) the kickoff message, (4) the master prompt, (5) content JSON wording, (6) design tokens.

---

## Open for the owner

Short list of things only the owner can close. Nothing here blocks development; everything here blocks a
store release.

| # | Open question | Why it is open | Who decides |
|---|---|---|---|
| O-1 | **Final `applicationId`** (today the placeholder `ir.andarz.app`) and a name check for «اندرز» on Bazaar, Myket and Google Play | D-11, D-M02: availability was never verified. Changing the id after a release breaks updates | owner, before the first release |
| O-2 | **Release signing key**: create a new keystore and set `ANDARZ_KEYSTORE`, `ANDARZ_KEYSTORE_PASSWORD`, `ANDARZ_KEY_ALIAS`, `ANDARZ_KEY_PASSWORD` (env or a git-ignored `keystore.properties`) | D-M10: March's committed key is compromised and is never reused. Without these the release build is debug-signed and prints `NOT PUBLISHABLE` | owner |

**Decided by the agent at the owner's request (2026-10-20, D-77); O-1 and O-2 stay open.**

**Closed since the first draft**

| # | Was | Now |
|---|---|---|
| ~~O-3~~ | Calendar refresh before 2026-10-29 | **Decided (D-77).** No network source is added; after `validUntil` News shows the expired state and everything else keeps working. Refresh by hand per `README.md` when a new list is available |
| ~~O-4~~ | Store policy for alarm-style permissions | **Decided (D-77).** Keep all five, each opt-in and off by default; the store listing must justify them (price alarms the user arms). If a store refuses, remove them from `:market`'s manifest and the guard allowlist together |
| ~~O-5 (network state)~~ | Keep or strip `ACCESS_NETWORK_STATE` | **Decided (D-77).** Keep it: it grants no internet access, WorkManager declares it, and stripping it cannot be verified without a device |
| ~~O-6 (mirrors)~~ | Dependency mirrors in `settings.gradle.kts` | **Decided (D-77).** Keep them as fallbacks after `google()` and `mavenCentral()`, which win when reachable. Artifacts are the same coordinates; checksum pinning is a later hardening step |
| ~~O-5~~ | Vazirmatn TTFs missing | **Closed.** The owner added Regular/Medium/Bold; they were renamed to `vazirmatn_regular/medium/bold.ttf` because Android resource names must be lowercase (D-M34). |
| ~~O-6~~ | No build toolchain | **Closed.** JDK 17 (Temurin), the Android SDK at `C:\Android` (platforms 34/35, licenses accepted) and Gradle 8.9 are installed; `./gradlew build` is green (D-M33). |


---

## D-M33 — Build environment (what is verified and what is not)

**Context.** K12 requires an explicit statement when the Android SDK or an emulator is missing, and
forbids claiming that unrun checks passed.

**State as of 2026-10-15, after the owner installed the toolchain:**

| Tool | State |
|---|---|
| JDK | Temurin **17.0.20.1** at `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`. Java 24 is also installed and is what a bare `java` resolves to, so `JAVA_HOME` must point at 17 — Gradle 8.9 does not accept 24. |
| Gradle | **8.9**, now through the committed wrapper (`./gradlew`). |
| Android SDK | `C:\Android` — platforms 34, 35 and build-tools 34.0.0, licenses accepted. `local.properties` holds `sdk.dir=C\:/Android` (forward slashes: a Java properties file eats `\A` in `C\:\Android`, which produced `C:Android` and a "volume label syntax is incorrect" failure). |
| Python | 3.12.10 — the string extractor runs. |
| Git | Repository initialised; the first commit is the kit exactly as received (K1). |

**What has actually been run and is green:**

- `./gradlew build` — compiles `:engine`, `:data`, `:market`, `:app`, runs lint and every unit test.
- `./gradlew verifyGuards` — all seven Gradle guards, each proven to fail on a planted violation.
- `:market:testDebugUnitTest` — 16 tests (13 egress, 3 zero-traffic), 0 failures.
- `:app:testDebugUnitTest` — 10 content tests, 0 failures.
- `:app:assembleDebug` — a debug APK.

**What has NOT been run:** anything that needs a device or an emulator. No emulator image is installed, so
every row of `MANUAL_TEST.md` that needs a running app is still `blocked`, and none is ticked.

### D-M37 — The build overrides a dead proxy per invocation; the owner's global config is left alone

**Context.** `~/.gradle/gradle.properties` sets `systemProp.http(s).proxyHost=127.0.0.1`, port 8080. That
proxy was not running, so every dependency fetch failed with "Could not GET …" while `curl` succeeded —
curl ignores Gradle's proxy setting. **Choice.** Pass `-Dhttp.proxyHost= -Dhttps.proxyHost=` on the Gradle
command line; do not edit the owner's user-level file. **Reason.** That file is the owner's machine
configuration and is presumably right when their proxy is up; a user-level `gradle.properties` also wins
over a project one, so the project could not override it anyway. If the owner would rather not pass the
flags, the line to change is theirs to change.

### D-M38 — Dependency mirrors, because `dl.google.com` is filtered here

**Context.** `dl.google.com/dl/android/maven2` answers **404** for every artifact from this network (not a
connection failure — a filtering answer), so no androidx coordinate resolves. The owner's Gradle init
script already mirrors *plugins* (`~/.gradle/init.d/repo-mirror.init.gradle.kts`) but not *dependencies*.
**Choice.** `settings.gradle.kts` lists `google()` and `mavenCentral()` first, then `maven.myket.ir`,
`maven.aliyun.com/repository/google` and `.../public` as fallbacks. **Reason.** Canonical sources win
whenever they are reachable; the mirrors only catch the 404 fall-through. Same coordinates, same version
catalog — only the transport differs. **Risk, stated plainly:** a mirror is a party that could serve a
tampered artifact. Gradle dependency verification (checksum pinning) is the real answer and belongs in M8
(O-6).

### D-M39 — okio is allowed on `:data`, and the exception is asserted rather than assumed

**Context.** K8.4 guard 4 forbids the bare word `okio` on `:engine` and `:data`. But §2.1 mandates
Preferences DataStore for settings, and `androidx.datastore:datastore-preferences:1.1.1` pulls
`com.squareup.okio:okio` transitively through `datastore-core-okio`. `dependencyInsight` confirms okio has
exactly one path into `:data` and that no OkHttp is anywhere near it. The two requirements contradict each
other.

**Choice.** Keep the guard, make it say what it means. The guard now matches **group coordinates**
(`com.squareup.okhttp3`, `com.squareup.okio`) instead of bare words, and `:data` carries a narrow,
asserted exception: okio may be present **only while `com.squareup.okhttp3` is absent AND
`androidx.datastore` is on the same classpath**. `:engine` still forbids okio outright. `:market` and
`:app` are not checked for okio at all — OkHttp is allowed in `:market` (K2) and okio is its I/O layer,
which is why K8.4's `:market` list never mentioned it; what `:app` must not have is a *direct* okhttp
dependency, checked separately.

**Reason.** K12 forbids weakening a guard to get green, and this is not that: okio is a buffer and
filesystem library that cannot open a socket, and the guard's purpose — no HTTP client reachable from the
journal — is untouched. The planted-violation proof shows the exception is tight: adding okhttp to `:data`
fails on *both* the okhttp token and the "okio together with okhttp3" assertion.

### D-M40 — The merged manifest's library entries, recorded and pinned

**Context.** K8.1 says libraries add a few entries to the merged manifest and requires the actual list to
be recorded with each entry's owner and pinned in the test. Attributed from
`app/build/outputs/logs/manifest-merger-release-report.txt`:

| Entry | Owner | What it is |
|---|---|---|
| `android.permission.USE_FINGERPRINT` | `androidx.biometric:biometric:1.1.0` | The API 26–28 fingerprint path behind `BiometricPrompt`. |
| `android.permission.ACCESS_NETWORK_STATE` | `androidx.work:work-runtime:2.10.0` | WorkManager's network constraints. Andarz never uses one; see O-5. It grants connectivity *state*, not internet access. |
| `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | `androidx.core:core:1.15.0` | A signature-level permission androidx defines so a dynamically registered receiver stays private to the app. It grants nothing to any other app. |

**Choice.** The first two are pinned by name in `allowedMergedPermissions`; the third by the suffix
`.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, because its name embeds the `applicationId` and that is still
a placeholder (O-1). Anything else in the merged manifest fails guard 1. **Reason.** A pinned list is what
makes A2 checkable: the full merged set is printed by the guard on every run.


---

## New decisions of this build

### D-M41 — Where the project lives now, and the second build environment

**Context.** Development moved from the owner's Windows machine to a Linux sandbox, and the owner asked that
the work continue in `github.com/MobitSolidity/march`. That repository already holds March
(`Bazaaryar/`, read-only for us, K1) and the kit in `ANDARZ/`. K1 says the Gradle root is the git root
`ANDARZ/` and never to `git init` in the parent folder. **Choice.** `ANDARZ/` stays the Gradle root and
lives as a folder of the existing `march` repository; no new repository is created and nothing outside
`ANDARZ/` is modified (March's files, `.github/` and the documents at the root are untouched). The local
repository that the previous session created inside `ANDARZ/` (one commit: the kit as received) is not
pushed as a nested repository; its single commit equals `ANDARZ/` on `main` of `march`, which already is
"the kit exactly as received". March's sources are `../Bazaaryar` from the Gradle root. **Reason.** One
remote that the owner already chose, no nested `.git`, and K1's rule that March is never changed holds.

**Environment of this session (Linux sandbox):** OpenJDK 17.0 (`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`;
the default `java` is 21), Android SDK at `/home/user/android-sdk` (platforms 34/35/36, build-tools 35.0.0),
Gradle 8.9 through the wrapper, Python 3. `dl.google.com` is reachable here, so the mirrors of D-M38 are
not exercised but stay as fallbacks. No emulator and no device: every device-only row of `MANUAL_TEST.md`
stays pending. `local.properties` is per-machine and git-ignored.

**Lint flake.** One `./gradlew build` failed in `:app:lintAnalyzeDebugUnitTest` with an internal K2
analysis error ("Error while resolving FirRegularClassImpl from RAW_FIR to SUPER_TYPES", reported by lint
itself as "a bug in lint"); the identical rerun was green. Recorded here so that a repeat is recognised;
if it becomes frequent, the fix is to upgrade AGP/lint, not to disable lint on tests.


### D-M25 — Toolchain versions: AGP 8.7.3 / Kotlin 2.1.0 / Gradle 8.9 / compileSdk 35

**Context.** §2.1 asks for "current stable AGP, compileSdk and targetSdk = latest stable". The only Gradle
distribution available offline is 8.9. **Choice.** Pin AGP 8.7.3 (the newest line that runs on Gradle 8.9),
Kotlin 2.1.0, KSP 2.1.0-1.0.29, Hilt 2.53.1, compileSdk = targetSdk = 35, JDK 17 toolchain, minSdk 26.
**Reason.** A toolchain that can actually run beats a newer one that cannot. Bumping AGP later is a
one-line change in `gradle/libs.versions.toml`; it needs a matching Gradle and a re-run of every guard.

### D-M26 — Gradle configuration cache off

**Context.** The K8.4 guards walk the project graph and resolve runtime classpaths at execution time.
**Choice.** `org.gradle.configuration-cache=false` in `gradle.properties`; `guardClasspaths` is also marked
`notCompatibleWithConfigurationCache`. **Reason.** Correct guards matter more than a faster configuration
phase. Revisit if the guards are rewritten against the Artifacts API.

### D-M27 — OkHttp stays at 4.12.0 for now

**Context.** K8.7 asks for "OkHttp at the newest stable version" and forbids downgrades; March pins 4.12.0.
**Choice.** 4.12.0, the version March already ships. **Reason.** It is not a downgrade, and the newest-stable
check needs a reachable Maven index, which this session does not have (D-M33). Re-check at M8 and bump if a
newer stable exists; the only consumer is `:market`, and the egress tests cover the surface that matters.

### D-M28 — Market JSON parsing keeps `org.json`, added as a **test** dependency

**Context.** K5 (`data/CoinRepository.kt`) requires the parsers to be pure functions testable on the JVM and
asks for a recorded choice between adding `org.json` as a test dependency and moving to
kotlinx-serialization. **Choice.** Keep March's `org.json` parsing code and add `org.json:json` to
`:market`'s **test** classpath only (Android supplies `org.json` at runtime, so nothing is shipped).
**Reason.** D-M23 says port faithfully and do not rewrite: a hand-rolled rewrite to kotlinx-serialization
would change every parser at the same time as the port, which is exactly what makes a port unreviewable.
The parsers become pure `String -> model` functions, which is what the tests need.

### D-M29 — The egress allowlist is injectable for tests, hardwired in production

**Context.** K4 demands an interceptor that enforces host, method and scheme, and tests that prove a POST,
an `http://` URL and an unknown host are refused. **Choice.** `EgressInterceptor(allowedHosts = …)` defaults
to `MarketHosts.all`; production always uses the default. **Reason.** The policy forbids plaintext and
loopback, so a MockWebServer cannot be reached through the real interceptor; a fake `Interceptor.Chain`
(K4 allows it) proves the refusals and the allowed shape without a TLS fixture.

### D-M30 — A refused request is a bug, never a network error

**Context.** March's `retrying` retries twice on any exception. **Choice.** `EgressRefused` is rethrown
immediately and never retried, and it is not mapped onto a `MarketError`. **Reason.** A policy breach means
the code asked for something the rules forbid. Retrying it or showing it as "network error" would hide it.

### D-M31 — `:market` owns the channel identity, `:app` owns the channel wording

**Context.** K6.6 puts the four market channels in a `market` group with names and descriptions from the
content JSON, through `MarketTexts`. **Choice.** `MarketChannel` (ids `price_alerts`, `price_alarm`,
`live_monitor`, `releases`) and `ChannelText` are part of `:market`'s public surface; `MarketTexts` returns
the strings. **Reason.** Ids and importance are behaviour and belong to the module that posts; wording is
Persian and may not live there (law 3).

### D-M32 — M0 seeds the market wording; the bulk March strings arrive with their milestones

**Context.** M0 (K9) asks for `content_market_fa.json` holding "March strings, the proposed copy of K13, the
overrides". K3/K5 say every March UI file is read when its milestone starts (MK1/MK2). **Choice.**
`content_market_fa.json` ships now with the complete K13 copy, the `overrides` block and the `uiArgs`;
`tools/extract_march_strings.py` is in the repo and its report is the input for the MK1/MK2 keys, added
screen by screen as those files are read. **Reason.** Assigning `mkt.*` keys by hand (K7 requires it) is only
honest while reading the screen that uses the string. Seeding the whole file from a mechanical dump would
create keys nothing uses and wording nobody checked. The extractor's own output is reproducible at any time:

    python -I tools/extract_march_strings.py --root ../march --json docs/march_strings_raw.json --en-json docs/march_strings_en.json

*Update: the extractor has since run (225 Persian strings, 120 `tr()` pairs); `docs/march_strings_raw.json`
and `docs/march_strings_en.json` are in the repo. In the GitHub layout (D-M41) March's sources sit next to
`ANDARZ/`, so `--root ..` is the equivalent of `--root ../march`.*

### D-M34 — Vazirmatn is vendored; the family still falls back if a file is missing

**Context.** §2.2 and §6.3 require Vazirmatn 400/500/700 in `app/src/main/res/font` (SIL OFL), and K7
forbids a build-time download (March's `fetchFonts` is explicitly not carried over). The kit shipped no
font files. **Resolution.** The owner supplied Regular, Medium and Bold. They arrived as
`Vazirmatn-Bold.ttf` and friends, which fails the resource merger — Android resource file names must be
lowercase a–z, 0–9 or underscore — so they are committed as `vazirmatn_regular.ttf`,
`vazirmatn_medium.ttf`, `vazirmatn_bold.ttf`.

**Choice.** The typography layer still resolves the three faces **by name at runtime**
(`resources.getIdentifier`) rather than through `R.font.…`, and falls back to the platform family when a
face is absent. **Reason.** A direct `R.font` reference would not compile at all while a file is missing,
which is how this started; resolving by name keeps the build independent of a binary asset. The OFL
license text ships in About (§6.3).


### D-M35 — `usesCleartextTraffic` and the network-security config live in `:app`'s manifest

**Context.** K4 requires `usesCleartextTraffic=false` plus a config that forbids cleartext, while all network
code is in `:market`. **Choice.** Both are `<application>`-level attributes, so they are declared in `:app`'s
manifest and `app/src/main/res/xml/network_security_config.xml`; the host allowlist and the scheme check stay
in `:market` (`MarketHosts`, `EgressInterceptor`). **Reason.** Android has no library-level equivalent. The
guard that matters (only `:market` declares `INTERNET`) is unaffected.

### D-M36 — `:market` declares its permissions from M0, its components from MK2

**Context.** Guard 2 pins the full `:market` half of the K8.1 allowlist, but `AlarmActivity`, the receivers
and `LiveService` only exist from MK2. **Choice.** The manifest carries every K8.1 permission and the
`SET_TIMER`/`SET_ALARM` queries from M0; the components are added in MK2 together with their classes.
**Reason.** A `<activity>` pointing at a class that does not exist fails lint's `MissingClass` with
`abortOnError = true`. The permission guard is the one with security meaning and it is satisfied now.

---

## M1 — Engine A (decisions)

### D-38 — `shortest()` by rounding the exact value, and Persian number words passed in as data

**Context.** 3.1 suggests a `String.format("%.pe")` loop for the shortest representation; law 3 forbids
the words one … ten of the clause `word` format in a `.kt` file, although `andarz_ref.fmt_ref` has them as
a table. **Choice.** (a) `XlMath.shortest` rounds `BigDecimal(x)` (the exact binary value) to p
significant digits with HALF_EVEN and returns the smallest p that round-trips through
`Double.parseDouble`, found by binary search because round-tripping is monotone in p. This is Python's
`repr` and depends on neither `String.format` nor `Double.toString`. (b) `Clause.render` takes the words
as a parameter. The app reads them from `numberWords` in `content_market_fa.json` (copied verbatim from
`WORDS` of the reference); the vector runner reads the same list from
`engine/src/test/resources/number_words_fa.json`. A content test renders every live clause of the
constitution with the defaults and the app's words and requires the original text (10.3). **Reason.**
JDK/ART independence; law 3 without touching the spec file.

### D-39 — Persian digits and separators in `Fmt` are `\u` escapes

**Context.** `Fmt` must emit U+06F0–U+06F9, U+066B, U+066C and U+066A; guard 7 scans source text for
U+0600–U+06FF. **Choice.** They are written as escapes (`'\u06F0'`), which are ASCII in the file.
**Reason.** The guard checks source characters, and its purpose (no *wording* in code) is untouched:
digits and separators are formatting, as 3.10 defines them in code.

### D-40 — Engine data types and nullability

`Plan` fields are nullable exactly where the reference returns `None`; `Fill.rriskp` is null for zero
equity; `Dashboard.allowed` is derived from the six statuses. `TradeRaw` carries optional persisted
derived values (`storedVerdict`, `storedNet`, …) that win over recomputation (D-24) — the vectors pass
none, so they recompute. `Params` keeps every value as `Double` (counts compare as doubles, as in the
reference) and rejects unknown keys and non-finite values. Sums are taken left to right (`Stats.pySum`) so
floating-point results are bit-identical to Python's `sum()`.

### D-41 — The vector JSON is read with kotlinx-serialization in test scope only

`:engine` keeps zero runtime dependencies (K4); kotlinx-serialization-json (already in the catalog, 2.1)
is a `testImplementation`. The vectors are copied to `engine/src/test/resources/` as 10.1 asks.

**M1 result.** 195 vector checks (xlRound 18, plan 16, leverageAdvisor 6, sizeVerdict 12, fill 15, curve 4,
kpi 5, jalali 53, format 37, renderClause 29) and 14 property/extension tests pass; the Jalali property test
walks all 73,414 days. Engine tests take about 3 s. Coverage (JaCoCo ≥ 90%) is measured from M2, when the
governor lands.

## M2 — Engine B (decisions)

### D-42 — Equity queries use a prefix-sum index

**Context.** `equity_before` of the reference sums every ledger row and trade on each call; the governor
calls it per trade (weekly scan, big-win hold), which is quadratic: a 5,000-trade snapshot took ~1 s.
4.5 asks for under 50 ms on a phone. **Choice.** `Governor.EquityIndex` sorts the flows by time once per
snapshot and answers each query by binary search over prefix sums. **Reason.** Same definition (flows
strictly before t, REAL trades only, withdrawals negative); only the order of floating-point additions
changes, which stays orders of magnitude inside the 1e-9 tolerance — all 33 scenarios pass unchanged.
Measured: ~230 ms for 5,000 trades on the JVM under JaCoCo instrumentation; the remaining cost is
`XlMath.round` in the per-trade drawdown and weekly checks. If the device measurement in M7 misses 50 ms,
cache the closed-trade resolution as 4.5 suggests.

### D-43 — Imported history (D-14) is implemented as a filter on lock *sources*

An imported row is skipped as a source of violation events, drawdown breaches (but still advances `cum`
and `peak`), weekly/daily/day-over scans, the trade cap, the cool-down and the big-win hold. It still counts
for equity, the curve, KPIs, the dashboard drawdown, `SETUP_TEST` and grade A. Tests in
`GovernorExtensionTest` show each rule with the imported rows and, as a control, the same rows live.

### D-44 — `testWaived` (D-21, P1) counts DEMO trades of the setup entered up to now

`SETUP_TEST` is skipped when the setup has `testWaived = true` **and** at least `Setup_Test_Trades` DEMO
trades with `entryTime ≤ now`. The flag alone does not waive the test.

### D-45 — An open violating lock has `until = null`

The reference uses `INF` (9999-12-31) and prints it as `null`; Kotlin uses `null` directly and sorts it
last within its priority, as `INF` sorts.

**M2 result.** scenarios 33, reviewSession 4, paramChange 8, paramsAt 2, gradeA 4 pass (51 cases); with
M1 all 248 checks of the vector file pass, plus 24 property/extension tests. Engine line coverage 96.6%,
enforced at ≥ 90% by `:engine:jacocoTestCoverageVerification` in `check`.

## M3 — Data (decisions)

### D-46 — PBKDF2 is our own 20 lines over `Mac("HmacSHA256")`; the password is UTF-8 of its NFC form

10.5 warns that Android's providers differ from the JDK's for non-ASCII passwords and gives this as the
fallback. Using it from the start removes the platform difference. The `crypto.pbkdf2` and `crypto.aesGcm`
vectors pass on the JVM; 600,000 iterations seal+open take ~1.4 s here. **Pending:** the instrumented run of
the same vectors on a device (10.5), which needs an emulator (MANUAL_TEST.md).

### D-47 — Lint runs with K1 UAST (`android.lint.useK2Uast=false`)

Lint's K2 analysis crashed in two of five full builds on `ContentBundleTest.kt` with an internal error that
lint itself labels a lint bug (D-M41). The same checks run with K1 UAST; three forced lint reruns were
green. Lint is not disabled for any source set. Revert when AGP is upgraded (M8).

### D-48 — Repository rules that 4.3/4.4 leave open

- `savePlan` stores the governor's `riskCap` and `gradeAAllowed` *at planning* in `riskPctCap`/`gradeA`; the
  size verdict at entry uses them. A brand-new setup therefore stores the SETUP_TEST cap (0.25%) and the
  worked example at 1% risk is `OVER_CAP` there — the rule of 12-6; with an established setup it is `YES`
  and compliant (both in `JournalLifecycleTest`).
- `confirmEntry` re-runs the governor: a lock that appeared between plan and entry makes `entryAllowed`
  false (D-08). Auto violations (V01 without exchange stops, V02, V03, V06) set `violationAt` once.
- A REAL entry ends an open review session with `TRADE_OPENED` (D-06); sessions time out after 60 min.
- `Start_Equity` changes only through `LedgerRepository.editBaseline` inside a session (D-18); `Chk_N` is
  always refused.
- Room is an `api` dependency of `:data` so `:app` can build the database in its Hilt module.
- `LegRow` lists use hand-written JSON converters: Room's KSP processor cannot see compiler-plugin output.

**M3 result.** 13 tables (schema v1 exported to `data/schemas/`), DAOs, converters, `EngineMapping`,
`EngineFacade` (+ reactive `observe()` on Room invalidation, boundaries and a 60 s tick, debounced 50 ms),
`ParamsRepository`, `JournalRepository`, `LedgerRepository`, settings DataStore, audit rows, backup crypto.
18 data tests pass (Robolectric, in-memory Room), including the worked example headless through the
repositories. The first migration test arrives with schema v2; the `MIGRATIONS` array is already wired.

## M4 — Core flows (decisions)

### D-49 — What M4 ships, and what is device-only

Onboarding (7 pages; BASELINE + NEUTRAL Start_Equity/Risk_Pct; pledge signed with an audit row), Today (hero
with khatam pattern, lock banners with live countdown and need buttons, reductions/hold chips, six gauges,
overall verdict, open positions with time stop and "I checked", advice of the day), the 5-step wizard
(verdict strip, reasons with number + article + fix, leverage advisor, derived items 6–7, emotion gating,
counter-trend half risk, confirmation sheet with slippage and silent-growth warning, the D-28 path), trade
detail (plan, execution, management: floating R, stop moves with V04 confirm, break-even, partial exit,
prayer, "I added", notes/news, monitor), close sheet (legs, sum check, auto reason, preview), neutral result
card, post-trade review (auto and suggested violations, report counter, one mistake) and the Journal list
(day groups with net, search, status/violation filters, four sorts).

**Not verified, needs an emulator:** F1–F4 of 10.6 and the visual checklist 1–4 of 6.9. They are written as
manual steps in MANUAL_TEST.md and marked pending; JVM tests cover the same rules (`JournalLifecycleTest`:
F1 numbers, F2 rejection, F4 lock sequence; `GovernorVectorTest`: F3 cool-down).

### D-50 — No formula in a composable (trap 16)

The wizard's numbers come from `WizardCalc` (a pure class over the engine, tested in `WizardCalcTest`); the
close-reason guess, suggested violations, time stop, floating R and break-even live in `engine.Management`
(tested in `ManagementTest`). Composables only format.

### D-51 — The close-sheet exit time is "minutes ago", default now

M4 has no time picker yet; the field is minutes before now, shown as the resulting date-time and validated
against the entry time. The Jalali date picker of 6.3 replaces it in M6, together with the entry-time picker
of the confirmation sheet (which defaults to now).

### D-52 — Engine `Snapshot` carries the params and `now` it was computed with

So every screen shows the caps the snapshot actually used (`paramsAt(now)`), never DEFAULTS.

### D-53 — Lint found an API-31 call (`Duration.toSeconds`) in app code

Replaced by `Duration.seconds` (API 26). `:engine` is JVM-only and not linted for Android API levels; a scan
found no API-31+ `java.time` calls there. M8 adds an API check for the engine.

## M5 — Analysis and governance (decisions)

### D-54 — Analysis logic is one pure file, tested on the JVM

`ui/analysis/AnalysisLogic.kt` holds the calculator (`CalcLogic`), periods (`Periods`), Insights series
(`InsightsLogic`), rule validation (`RulesLogic`), the liquidity filter (`WatchLogic`) and due reviews
(`ReviewLogic`). Composables only format (trap 16). Every number comes from the engine: `Planner.plan`,
`gate`, `leverageAdvisor`, `rrAfterFees`, `Trades.kpis`, `Governor.classifyChange` and `gradeAEligible`.
Insights uses `Trades.kpis` so the period's drawdown is read from the all-time curve, as in the reference.

### D-55 — The calculator judges numbers, not ticks

The calculator has no checklist, so its gate runs with `chk = Chk_N` (all items): the verdict and the
advisor say whether the numbers pass. The risk field defaults to the governor's `riskCap`. The fee impact
uses the plan's `notionalMax`. *Build a trade* hands the draft to the wizard through a process-local
singleton (`WizardPrefill`), taken once; the route carries no numbers.

### D-56 — Rules: percent typed as percent; reset tightens first

Percent parameters are typed as percent numbers (1 for 1%) and rounded to 10 places after dividing, so 0.7
becomes 0.007 exactly. Values are shown in the clause format of 3.11 (trailing zeros trimmed). Reset to
defaults is a batch of ordinary changes through `ParamsRepository.apply`; tightening and neutral changes go
first so `Risk_Max >= Risk_Pct` holds after each single step, loosening last (delayed as usual). The session
card is shared by Rules and Reviews and polls every 30 s so the 60-minute timeout shows.

### D-57 — Reviews: due rules and what is frozen

Daily is due while today has no DAILY review. Weekly is due for the just-ended week, or for the current one
on Friday; the user may pick any of the last eight unreviewed weeks. Monthly is due for the previous display
month. Each review freezes the KPIs of its period into `kpiSnapshot` (numbers and codes only, law 6); the
payload holds the answers. The drawdown review opens on its own while a DRAWDOWN lock needs REVIEW. The
monthly withdrawal is recorded only after an explicit yes, as a WITHDRAWAL ledger row; marking the security
item sets `lastSecurityReviewAt`.

### D-58 — Watch-list and setups

`WatchRepository` enforces `Watchlist_Max`, upper-cases symbols and refuses duplicates; entering volume or
relative volume counts as a liquidity check. No document names the relative-volume field alone, so the D-22
sentence with the live band labels it (no new wording). Thresholds are DataStore settings editable only
inside a session, with no delay. `SetupRepository.setGrade` refuses grade A unless the engine's
`gradeAEligible` holds; demotion is always allowed; setups are archived, never deleted. Date fields in M5
are day steppers (today, yesterday, minus one day); the Jalali date pickers of 5.15 arrive with M6.

## M6 — Platform features (decisions)

### D-59 — Backup payload from serializable entities

Every Room entity is `@Serializable`, so the payload of 8.3 is the entity fields by name. The decoder ignores
unknown keys and fills missing optional fields from their defaults (forward and backward compatible).
`BackupService` builds, validates (one BASELINE, unique ids, legs summing to 1, known enums, no orphan events or
attachments) and restores in one Room transaction; a failure halfway leaves the database untouched (tested by
throwing inside the transaction). Settings travel as an opaque object owned by `:app` without PIN, lock state or
permission state; `market` is reserved for MK3. Screenshots are not part of the payload yet: attachments have no
picker in this build (D-13 stays open; the `files` map and flag bit are implemented and empty).

### D-60 — Restore is confirmed with the wipe word

The summary dialog (counts, date range, creation time) needs the typed word of `set.wipe.confirm` before the
replace-all, the same word as the wipe itself, so one Persian word guards both destructive actions and no new
wording is needed.

### D-61 — CSV vocabulary from content, codec in `:data`

Persian headers, enum `csv` labels and yes/no/dash are read from the content document into `CsvVocab`
(`fromContent`) and passed to the codec, so `:data` holds no wording (law 3) and its tests read the spec file
through a system property. Numbers use `shortest()` in plain notation. Formula protection applies to free-text
cells only. The excel import follows 9.2 steps 1-7; the full import trusts persisted derived columns (D-24).
Duplicates use the key of 9.2 step 6 against stored trades and earlier rows of the same file. The baseline move
of step 7 is a switch in the preview with an editable amount, never applied silently.

### D-62 — PIN and attempt policy

PBKDF2-HMAC-SHA256 (the backup's own implementation, D-46) over the ASCII digits, 16-byte salt, 120,000
iterations, constant-time compare. Five free attempts; after the fifth failure 30 s, doubling per failure up to
30 min; count and deadline persist in DataStore. The app locks on cold start and after the chosen timeout in the
background; `FLAG_SECURE` follows "hide in recents". `MainActivity` is a `FragmentActivity` for `BiometricPrompt`
(strong biometrics, the PIN as fallback). The PIN-setup dialog shows `set.backup.warn` as the "no recovery"
notice: losing the PIN means wipe and restore from a backup.

### D-63 — Reminders: a pure planner and a reconciler

`ReminderPlanner.plan(snapshot, trades, reviews, settings, now)` returns the desired set of 7 with unique work
names; quiet hours move instants to the window end. `ScheduleReconciler` diffs it against WorkManager jobs by tag
and runs at start, after writes to the rule tables (debounced 2 s), after restore and settings changes, and once a
day. A worker recomputes the plan one second before its instant and posts only when the reminder is still in it,
so stale reminders are suppressed. Report reminders have no own toggle because they belong to a VIOLATION lock.
`POST_NOTIFICATIONS` is requested only when a reminder switch is turned on.

### D-64 — Share note by line index

`ShareNote.fields` splits each template line on runs of two or more spaces and keeps each field's text up to its
last delimiter (`:` or `\u061F`); the test asserts 1,1,1,1,1,1,2,4,4,1,0,4,1,1,1 on the real template. Values come
from content and the user's digit setting; the note is shared as plain text.

### D-65 — Constitution reader

Clauses and list items are rendered with `Clause.render` and live parameters; the rendered values are bold and
coloured and the clause opens Rules (the "comes from setting X" sheet is simplified to a hint line plus the Rules
screen, which shows value, default and article). Search covers clause and paragraph text. "Open in calculator"
prefills vector P01. Amendments open Rules (a session is required there). Re-signing appears when the pledge
version changes.

### D-66 — What M6 leaves for later

Demo data (P1, done in M7: D-68), the 3-step font slider (the system scale already applies), screenshots in
backups (D-13), the learn pages (P1, done in M7: D-69), the spot portfolio (P2) and full Jalali date pickers: dates are entered with day steppers in
M5/M6 screens and CSV accepts Jalali dates. `mkt.set.quiet` and market settings in backups are MK3.

## Android skills review (decisions)

The owner supplied Google's Android skills (`android-skills.zip`). The applicable ones were read in full and
run against the project: play-policy-insights (its scripts, Mode B), android-permissions-security,
android-intent-security, edge-to-edge, jetpack-compose-performance, r8-analyzer and testing-setup. Only
findings confirmed in the code were changed; everything else is recorded as checked. Skills for features
Andarz does not have (camera, cars, Wear, TV, XR, billing, credentials, AppFunctions, Glance, Media3, AGP 9
upgrade, Navigation 3) were not applied.

### D-78 — Deep-link route allowlist (android-intent-security)

`MainActivity` is exported (launcher), and it read the route extra and passed any string to
`navController.navigate`. Another app could therefore open any screen in the graph, including onboarding or
the wizard, after unlock. `DeepLinks.sanitize` now accepts only the shapes Andarz itself puts in
notifications (`today`, `reviews`, `settings`, `security`, `market`, `market/news`, `trade/<id>`,
`market/coin/<id>` with a bounded id), in both `onCreate` and `onNewIntent`, and `onNewIntent` calls
`setIntent`. `DeepLinksTest` checks every route the app generates passes and hostile routes do not. Checked
and already correct: all 11 PendingIntents are `FLAG_IMMUTABLE`; every component except the launcher and the
boot receiver is `exported=false`; the boot receiver accepts only `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED`
(protected broadcasts); the FileProvider shares only `cache/out/`; no custom permission, bound service or
content provider is exported. Runtime permission: POST_NOTIFICATIONS is requested only when the user turns
on a reminder or alert, never at start, and the app works without it (no loop).

### D-79 — Edge-to-edge and lists (edge-to-edge, compose-performance)

Both activities already call `enableEdgeToEdge()` (ComponentActivity, so icon colours are automatic) and
`adjustResize` is set. Gaps fixed: the nav `Scaffold` now uses `contentWindowInsets = safeDrawing` and its
content consumes the padding, so text fields are never under the keyboard and nothing is padded twice; the
two screens outside the Scaffold (onboarding, lock) use `safeDrawingPadding()`, and the lock screen now
scrolls, so its PIN field stays reachable at 200% font with the keyboard open. Lazy lists that mix headers and
rows declare `contentType` (journal, prices, alerts, rules); the Market section chips, a fixed set of five,
use a plain scrolling `Row` instead of a `LazyRow`. `isNavigationBarContrastEnforced` is left at its default:
the skill asks for `false` with a coloured bottom bar, but the Material 3 `NavigationBar` already draws its
own container behind the gesture area, and the change cannot be checked without a device.

### D-80 — R8 configuration (r8-analyzer, heuristic path: AGP 8.7.3)

The analyzer's quantitative paths need AGP 9.3 or R8 9.3.7, which are not used here; the heuristic path was
followed. Removed as redundant or too broad: the Room keep (room-runtime ships it), the kotlinx-serialization
keeps (the library ships R8 rules), `-keep class ir.andarz.engine.** { *; }` (package-wide wildcard; the
engine has no reflection), and `android.enableR8.fullMode=true` (the default since AGP 8.0). Added
`SourceFile,LineNumberTable` so a crash can be retraced with the mapping file. Found by the same review:
the Vazirmatn fonts were looked up with `Resources.getIdentifier`, which resource shrinking cannot see; they
are now referenced as `R.font.*`. The AGP 9 upgrade the skill suggests is not done (toolchain lock, D-M33).

### D-81 — UI and UX fixes from the review

- Settings: action results (backup, CSV, demo data, errors) appeared as a line at the top of a long page,
  usually off screen. They are now a snackbar at the bottom, announced by TalkBack, then cleared.
- Ledger: deleting a deposit, withdrawal or adjustment happened on one tap. It changes equity and every
  limit, so it now asks first (like deleting a trade), and the button is drawn in the red-line colour.
- Journal rows: at least 48 dp tall and announced as buttons.
- Truncated numbers: prices and ranked-bar values could be cut with no ellipsis at 200% font. Values are now
  never truncated (the column widens), labels may wrap to two lines, and every remaining single-line text has
  an ellipsis.
- Testing (testing-setup): the existing stack (JUnit4, Robolectric with Compose tests in `test`, Hilt) is
  respected; no new framework was added. Screenshot tests (Roborazzi/Dropshots) are not added: they need a
  reference device run to be meaningful.

## International-grade audit (opus5-review, decisions)

Full register: `AUDIT.md`. Only the decisions are recorded here.

### D-82 — Failed user actions are reported, never fatal (A-01)

Repositories reject invalid state by throwing (`require`, `TradeStateException`, Room constraint errors).
Every ViewModel coroutine was started with a bare `viewModelScope.launch`, so such an exception reached the
thread's default handler and killed the process. All 71 call sites in 15 files now use `safeLaunch`
(`ui/SafeLaunch.kt`), which maps the failure to `err.storage` (disk full, I/O) or `err.generic` and shows it
in a snackbar in the nav shell and on onboarding. Cancellation is untouched (the handler never sees it). The
stack trace is logged only in debug builds. Both keys already existed in K13; no new copy.

### D-83 — Periodic UI work only while visible (P-01)

The governance screen polled the repository every 30 s from `viewModelScope`, so it kept waking the process
for as long as the screen sat on the back stack, including in the background. Today (1 s), News (1 s) and
the trade link (5 s) ran their clocks with `LaunchedEffect(Unit)`, which is paused but not stopped when the
activity stops. All four now run under `repeatOnLifecycle(STARTED)` (`ui/Ticker.kt`). The background price
monitor is unchanged: it is a user-started foreground service with its own notification (D-M08).

### D-84 — Cold start off the main thread (P-02)

`ContentRepository.preload()` existed but was never called, so the first composition parsed 185 KB of
wording JSON on the main thread, and `Application.onCreate` also created the notification channels and
deleted `cacheDir/out` there. These now run on `Dispatchers.IO` at process start. A broadcast receiver in a
cold process still loads the wording synchronously through the repository's lock, so ordering is safe.
Channel creation is idempotent. A baseline profile is **not** added: it needs a device or emulator run to
generate (`:baselineprofile` with a managed device), which this environment does not have; ROADMAP item.

### D-85 — Touch targets and roles (U-01, U-02)

Three icon buttons were drawn at 24 dp and 40 dp; they are 48 dp now (WCAG 2.2 SC 2.5.8, Android guidance).
Seven `clickable` surfaces now declare `Role.Button`. `TouchTargetSourceTest` enforces both on the sources.

### D-86 — No crash-reporting SDK (waiver of the review's crash-monitoring item)

The review asks for Crashlytics or Sentry. Law K2 / §8.4 forbids any network path outside `:market`, and
guard 4 rejects such libraries on the classpath. Crash monitoring therefore relies on Play Console's Android
vitals (ANR and crash rates, collected by the OS, no SDK) and on the R8 mapping file kept for every release
(D-80) so stack traces can be retraced. A local, opt-in "export last crash" is a ROADMAP item, not built now.

### D-87 — No CI pipeline (waiver of the software-production skill's CI/CD item)

The owner's standing rule is no CI workflow and no release automation (D-74). The skill's CI/CD, SBOM and
release-playbook items are met by documents instead: `RELEASE.md` (exact commands, staged rollout, rollback),
`docs/audit/sbom.txt` (the resolved runtime dependency list that was scanned on OSV) and the guards wired into
`./gradlew build`.

### D-88 — Certificate pinning is not added

Pinning the five public market hosts (CoinGecko, alternative.me, Binance data stream, Nobitex, Wallex) would
break the app silently whenever one of them rotates its certificate, and the app has no update channel of its
own to ship new pins. The traffic carries no user data or credentials (guard 5 rejects `Authorization`,
`Cookie` and any body). HTTPS with the system trust store, cleartext refused (`network_security_config.xml`),
is the chosen level. Recorded as accepted risk S-03 in `AUDIT.md`.

## Owner request — internet switch in Settings (decisions)

### D-76 — One internet switch, first on the Settings page

The owner asked for the internet connection to be controlled by a switch in the app's settings. Settings now
opens with an "Internet in Andarz" card (`mkt.net.title`) whose switch (`mkt.set.enable`) is the master gate
of `:market`, the only module allowed to open a connection (guards 2-4). The switch shows the real state
(`mayUseNetwork` = switch on and consent recorded). Off: no request of any kind leaves the app, and the card
says so (`mkt.off.body`). On for the first time: the consent text and host list appear in a dialog and
accepting records consent and opens the gate in one write (`acceptConsent`); later toggles only flip the gate.
The Market card keeps unit, background monitor, quiet mute and sources; the duplicate switch was removed.
Default stays off (D-M08); backups never carry the switch or the consent (K6.12). All wording is existing
K13 copy, so nothing new is missing.

### D-77 — The open owner items the agent was asked to decide

O-3 (calendar), O-4 (alarm permissions), O-5 (`ACCESS_NETWORK_STATE`) and O-6 (mirrors) are decided as listed
under "Closed" at the top: keep the static calendar with its expired state, keep the opt-in alarm
permissions, keep the network-state permission, keep the mirrors as fallbacks. Each choice keeps current,
tested behaviour and avoids a change that cannot be verified without a device. O-1 (application id) and O-2
(signing key) stay open: the owner does not have them yet; the build stays `-unsigned` and NOT PUBLISHABLE.

## M8 — Release preparation (decisions)

### D-73 — Version stamping

`versionName` is the human version in `gradle.properties` (`andarz.versionName`, now `0.9.0`: feature
complete, device acceptance still open). `versionCode` is `git rev-list --count HEAD`, so every pushed build
is higher than the previous one without anyone editing a number; outside a git checkout it falls back to
`andarz.versionCode`. The short commit is `BuildConfig.GIT_COMMIT` and appears on About next to version and
build. **Reason.** A monotonic code with no manual step, and a build that can be traced to its source.

### D-74 — Release signing and the NOT PUBLISHABLE path (K8.6, D-M10)

March's `Bazaaryar/ci/march-ci.jks.b64` is a signing key committed to a public repository and is treated as
compromised: it was never decoded, copied or referenced, and its alias is not reused. Release signing reads
`ANDARZ_KEYSTORE`, `ANDARZ_KEYSTORE_PASSWORD`, `ANDARZ_KEY_ALIAS`, `ANDARZ_KEY_PASSWORD` from the environment
or a git-ignored `keystore.properties` (keys `storeFile`, `storePassword`, `keyAlias`, `keyPassword`; `*.jks`
and `*.keystore` are git-ignored too). When a keystore is named, every other value must be present and the
file must exist, or configuration fails with the missing name (never a silent debug fallback). Without one,
the release is signed with the debug key, its `versionName` gets the suffix `-unsigned`, and
`assembleRelease` / `bundleRelease` print a boxed `NOT PUBLISHABLE` warning before and after packaging. No CI
workflow and no release automation exist.

### D-75 — R8 release, what was verified

`assembleRelease` runs R8 with resource shrinking; the release APK is about 2.9 MB (gate: 25 MB). The
mapping keeps the kotlinx-serialization companions and `$$serializer` classes of the backup document and the
market models, Room's generated database, and the engine names (readable stack traces). The merged release
manifest holds exactly the K8.1 permission set (guard 1) and `allowBackup=false`; the APK contains no
Firebase, Crashlytics or Play Services classes. Release unit tests run the same suites as debug except the
Compose UI tests (D-71). Not verified: the release APK has not been installed or started anywhere (no
device), so an R8 rule gap that only shows at runtime would not be caught here — MANUAL_TEST R-1.

## M7 — Polish (decisions)

### D-67 — Late logging: a 15-minute threshold, judged at the entry time

**Context.** D-26 says a trade logged after the fact is judged with the governor snapshot at its own entry time
and flagged `loggedLate`, but gives no threshold. **Decision.** The confirmation sheet asks for the entry time as
"minutes ago" (like the close sheet). An entry confirmed more than `LATE_AFTER = 15 min` after it happened is
late: `confirmEntry` evaluates `entryAllowed` with `snapshot(entryTime)` instead of `snapshot(now)` and stores
`loggedLate = true`. Up to 15 minutes is "typing time" and keeps D-08 (a lock that arrived since the plan
counts). Entries in the future are refused. Size verdict and parameters were already `paramsAt(entryTime)`.
**Reason.** Honest history without letting a user dodge a fresh cool-down by back-dating a few minutes: a
back-dated entry is stored with the flag (backup, full CSV). **Copy.** No key for a visible tag or a late hint
exists, and the content test forbids code that names an undefined key, so the chip and hint are not drawn yet;
they are listed in MISSING_COPY and need one line each once `trade.loggedLate` / `wiz.confirm.late` exist.

### D-68 — Demo data: twelve scripted DEMO rows with a reserved id prefix

`DemoData.load()` inserts twelve CLOSED rows (`account = DEMO`, `imported = false`) shaped like vector K01
(entry 100, stop 96, target 108, equity 2000; wins, stop-outs, a partial exit, manual exits, one V06, two shorts),
dated over the twelve days before loading, all reviewed. Ids start with `demo-`, so `remove()` deletes exactly
these rows and never a demo trade the user logged. Both write `DEMO_LOAD` / `DEMO_REMOVE` audit rows; the
`demoLoaded` setting drives the button; wipe resets it. D-15 keeps them out of every rule, limit and KPI, which
the test asserts on the snapshot.

### D-69 — Learn pages: five sections, rendered from `content.learn`

Summary, review of experiences, additions, journal guide and the expectancy explainer are one screen with a
vertical section list (it wraps at 200% font; tabs would clip). Every word is `content.learn.*` or `ui.learn.*`;
static digits follow the digit setting. The mini calculator takes the win rate in percent and R, shows
`learn.calc.result` and the break-even win rate (`ins.breakEven`); `LearnLogic` is pure and tested against the
worked example of the page (0.4 x 2 - 0.6 = 0.2). Reached from More.

### D-70 — Charts: table view, LTR plot, decimation

`ChartCard` gains the "table view" toggle of 6.5 (labelled `common.details` / `common.less`; no dedicated key
exists): it replaces the canvas with a text list of the same points. The plot area is wrapped in an LTR layout
direction (D-10). Line charts decimate to at most 600 points (min/max per bucket, ends kept), remembered per
data list. Crosshair and haptic ticks remain a device-polish item (not required by an exit criterion).

### D-71 — F9 and F10 run under Robolectric

There is no emulator, so `connectedCheck` cannot run. F9 (light theme, 200% font, 360 x 800 dp: no text node
outside the window horizontally; vertical overflow scrolls) and F10 (verdict banner, gauges and checklist rows
expose a label and a state word) are Compose tests in `app/src/test` on Robolectric, with a plain
`Application` so Hilt does not start. They cover Today, a journal row and the checklist row; the full wizard
step 4 needs its ViewModel and stays a device check. To make them pass, the journal row wraps (FlowRow), the
checklist row became the shared `ChecklistRow`, and the verdict banner merges its children and carries a state
description (`a11y.*State`). TalkBack itself remains on the device list. They run in `testDebugUnitTest` (the Compose test
activity is debug-only) and are skipped by assumption in the release unit-test variant.

### D-72 — Empty, loading and error states; motion; icon and splash

`LoadingState` (`common.loading`, polite live region, no spinner) replaces the blank boxes of Today and the
wizard gate; `ErrorState` (assertive, `common.retry`) is available; existing empty states already use the
`*.empty` keys. The only animation (verdict banner size) is 250 ms and is removed by the in-app reduce-motion
switch; the system "remove animations" scale is honored by Compose. Icon, monochrome layer, notification icon
and splash (core-splashscreen, `bg`, alef, no animation) were already in place from M0/M6; their visual check on
light, dark and themed launchers is a device item.

## MK1 — Market data and prices (decisions)

### D-M42 — The WebSocket handshake headers are allowed, on the live-ticks host only

**Context.** K4 limits HTTP headers to `Accept` and the generic `User-Agent`, and the M0 interceptor refuses
everything else. OkHttp's WebSocket upgrade adds the RFC 6455 handshake (`Upgrade`, `Connection`,
`Sec-WebSocket-Key`, `Sec-WebSocket-Version`, `Sec-WebSocket-Extensions`) before the request reaches the
interceptor, so the M0 policy would have refused the only socket K4 allows. **Choice.** Those five names are
accepted only on a request that carries `Upgrade` **and** goes to `data-stream.binance.vision`; anywhere else
they are refused like any other header, and an upgrade to any other host is refused. Cookies and
authorization stay refused on the upgrade too. The socket is opened by `MarketHttp.webSocket()`, through the
same client and interceptor. **Reason.** The handshake carries no user data (the key is 16 random bytes per
connection); the guard is narrowed to exactly what the protocol needs, not weakened. Four new tests in
`MarketHttpEgressTest`.

### D-M43 — PriceHub as a class: gate-and-demand controller, injected sources, virtual time

`PriceHub` keeps every loop and every cadence of March (K5.2) and gains one controller:
`combine(gate, holders > 0)`. Only when both are true do the loops run, inside one `coroutineScope` that
is cancelled the moment either falls — the release of the last holder, or the master switch going off,
closes the socket and stops every request (K4, K6.4). The outside world is a `MarketSources` interface
(`NetSources` over `MarketHttp` in production, a fake in tests), and the clock is `() -> Long`, so
`PriceHubTest` drives the hub with `kotlinx-coroutines-test` virtual time: 60 s / 5 min / 2 min after a 429,
Iran every 10 s, overview every 5 min, 15 s socket liveness, 10 min quote age, 5 s samples. The alert
evaluation of March's publish loop becomes the `onPublish` hook that MK2 fills. `extraSymbols` (MK3) is
already honoured by `wantedSymbols()`.

One behaviour was made exact rather than approximate: March kicks a refresh on every UI `acquire`, which
right after the first acquire made the CoinGecko loop fetch twice at start. The first holder no longer
kicks; a later UI holder still does (it may want the sparkline a background run skipped). Recorded in the
MERGE_MAP notes of `data/PriceHub.kt`.

### D-M44 — Typed values instead of March's localized fields in the models

`UsdtRate.source` and the synthetic USDT/Toman coin's `name` held Persian text in March. They are now
`IranSource` (NOBITEX/WALLEX) and an empty name; the UI composes `mkt.usdt.name` + `mkt.data.*`.
`MarketState.error` is a `MarketError`, rendered by `MarketTexts.error()` (`mkt.err.*`).

### D-M45 — March's screen wording moved by the script, with one helper for literal lines

`tools/move_march_strings.py` gained (a) `quote: n`, which takes the n-th Persian string of a `literal` row
(the whole Kotlin line) with nothing but the quotes removed, and (b) `file:line#2` for the second `tr()` on
one line. 45 keys were moved for Prices, My coins, the coin sheet, the live-mode label, Fear & Greed classes,
the sort pills, the USD/Toman toggle and the Toman suffix. Where March used the same wording twice
(`لحظه‌ای · بایننس` in the header and the sheet; «ارزهای من» as My coins title) one key serves both (K7).
The per-coin rule editor's wording (CoinSheet.kt lines 168–210) moves with MK2.

### D-M46 — The hub is held by the Market screen's lifecycle

K6.4 reason (a) is implemented with a lifecycle observer on the Market destination: ON_START acquires with
`ui = true`, ON_STOP and disposal release. A Market screen in the back stack, or an app in the background,
holds nothing. Reasons (b) and (c) arrive with MK2 (foreground alert reason, LiveService), (d) with MK3.
Section and open coin sheet are saved in `SavedStateHandle` (K6.1, process death); `market/<section>` and
`market/coin/{id}` routes open the screen with that section or sheet.

### D-M47 — MarketFmt keeps March's tiers exactly, including the small-price formula

The tier rule for prices below 0.01 is March's `3 - floor(log10 p)` decimals (capped at 10), which gives
three or four significant digits depending on the mantissa; it is ported as is, not "fixed" to exactly
three (D-M23). Numbers use `Fmt.number` with the digit setting; the clock uses the app's zone setting, not
`Asia/Tehran` hard-coded as in March. The test pins a fixed `+03:30` offset so it does not depend on the
host JDK's tzdata.

### D-M48 — Device-only checks of MK1

Real-host behaviour (the five services answering, CoinGecko or Binance blocked from an Iranian network,
airplane mode, Market off with a firewall log) cannot run here: no emulator and K12 forbids tests against
real hosts. They are rows A3/A4/A5-MK1 in MANUAL_TEST.md, marked pending. JVM tests cover the parsers
(fixtures for all five services), PriceHistory, the merge, cadence, demand and zero traffic, error mapping,
MarketFmt and the Prices filter/sort.

## MK2 — Alerts, ringing, calendar (decisions)

### D-M49 — The alert evaluator is pure; the dispatcher is the only Android half

`AlertEvaluator.evaluate(state, history, settings, rules, watch, now)` returns typed actions (`Target`, `Move`,
`Summary`) and the one-shot targets to clear; `AlertDispatcher` posts them. March's rules are kept line for
line (K5.2), including its memory: cooldown per coin and direction, fired targets per coin+side+price, at
most three moves per round plus a summary, only the strongest move rings. The hub calls the evaluator after
each published snapshot, so with the gate closed no price alert can fire (K4). Table tests in
`AlertEvaluatorTest` (16 cases). K6.5 holds by construction: :market cannot reach :data or :engine.

### D-M50 — Notification wording is assembled in :app from moved pieces

March built titles by concatenation. `tools/move_march_strings.py` gained `join` (a key made of March's
pieces in order, with `{placeholders}` where March inserted a value) and `rename` (a Kotlin template variable
renamed to a neutral placeholder). Every Persian piece is byte-identical to March. K7 transformations: emoji
removed with the one space that followed it, "March" replaced by the app name (it also appears in Latin in
five strings). K6.7: move titles start with ▲/▼, the target title has no emoji, the permission rows show a
core warning icon. 150 market keys in total; none written by hand except `mkt.data.iran` (MK1).

### D-M51 — Calendar asset generated from March's source, ids per occurrence

`tools/convert_march_calendar.py` reads March's `data/Events.kt` and writes
`market/src/main/assets/market_calendar.json` (15 releases, `validUntil` 2026-10-29T23:59:59+03:30, the last
release day). Ids are `yyyy-MM-<march id>` (`2026-10-cpi`), unique per occurrence, so a refreshed list never
re-arms an old reminder: restore ignores saved ids that are not in the current list (`CalendarTest`). A
malformed row is skipped and a broken file gives the expired/empty state; nothing else breaks (K6.10).
The format and the refresh steps are in README.md.

### D-M52 — Who holds the hub, and the monitor follows the switch and the gate

`MarketModule.start()` (from `install`) adds the two non-UI demand reasons of K6.4: (b) while the process is in
the foreground (`ProcessLifecycleOwner`) and sharp-move alerts are on or a target is armed, the hub is held
with `ui = false`; (c) the monitor service runs exactly while `alerts.background && gate.mayUseNetwork`, so
switching Market off also stops the service (K4). After a reboot or an app update `BootReceiver` re-arms the
reminders and starts the monitor only under the same condition (K5.2).

### D-M53 — Lint `MissingPermission` in :market, resolved at one call site

Lint analyses :market alone and does not see POST_NOTIFICATIONS and VIBRATE, which :app declares (K8.1
keeps them there). All market notifications now go through `Channels.notify`, which checks the
POST_NOTIFICATIONS grant at runtime and suppresses the lint id on that one method. VIBRATE is an
install-time permission, always granted in the merged app; the two vibrator calls carry the same
suppression with this reason. No lint check was disabled for the module.

### D-M54 — Quiet hours and the settings the texts use

Price alerts and release reminders are never deferred by quiet hours (K6.6). The P1 switch
`mkt.set.quiet` exists in the dispatcher (`quiet` posts silently and does not ring) and is wired to the
Settings screen in M6. `MarketHostImpl` follows the settings flow for the life of the process, so a
notification built by a receiver uses the user's digit, calendar and zone settings without blocking.

### D-M55 — Device-only checks of MK2

Ringing through the Clock app, the own-alarm fallback, over the lock screen, snooze, auto-stop, the volume
floor, the monitor and reboot restore need a device. They are rows of "Device checklist for the ringing
features (MK2)" in MANUAL_TEST.md, all pending. The JVM covers the evaluator, reminder time math, boot
restore through a fake scheduler and the calendar loader.

## MK3 — Integration (decisions)

### D-M56 — `trade_alert` table, schema version 2

Trade-linked one-shot flags live in `:data` as `trade_alert(tradeId, level, price, armedAt, firedAt)` with a
cascade foreign key to `trade`. Migration 1 -> 2 only creates the table; a JVM test builds version 1 from the
exported `1.json`, migrates through Room and checks data survives. `markFired` is an `UPDATE ... WHERE firedAt IS
NULL` and returns the row count, so exactly one caller posts. `JournalRepository.close` deletes the trade's rows in
the same transaction (they vanish when the trade closes). The backup carries the table; restore keeps rows of OPEN
trades only.

### D-M57 — The trade watcher lives in `:app`

`TradeWatcher` combines open trades and armed rows with `MarketRepository.state`, sets `extraSymbols` and holds one
background acquire while anything is armed (K6.4 d), so journal data never enters `:market` (D-M21). Levels:
take-profit, current stop, and `priceAtR(side, entry, slInitial, Partial_R_Lo)` for the partial level (the existing
engine helper; no engine change). The price is frozen when armed. Only quotes younger than 60 s are evaluated.
Notifications use the discipline channel, private on the lock screen, with `mkt.link.notif.*` and `notif.partial.*`;
the card states with `mkt.alerts.bg.hint` that alerts arrive while the app is open or the monitor runs.

### D-M58 — Symbol resolution

Suffixes `USDT`, `USDC`, `USD`, `PERP`, `.P` are removed repeatedly (separators trimmed) and the base must match
exactly one non-Toman coin of the top 200; otherwise the card shows `mkt.link.nocoin` and nothing is armed.

### D-M59 — Live chip and watch-list prices never demand the hub

The chip (wizard entry, calculator, trade current price) and the watch-list row read the state only; they show a
price when Market is on and something already streams it. The chip copies the number as typed text (D-M06). The
calculator gains a symbol field used only for the chip; *build a trade* still opens the wizard with an empty
symbol (5.7).

### D-M60 — Market settings in the backup; the quiet mute

`MarketPrefs.exportJson/importJson` carry stars, reminded releases, unit, alert settings and rules; the consent
stamp and the master switch are never exported, so a restore leaves Market off (K6.12). Import ignores unknown
fields and keeps current values for invalid ones. After a restore `MarketModule.onRestored` reschedules release
reminders and restarts the monitor only if enabled and the gates are open. `mkt.set.quiet` (`muteInQuiet`, default
off) posts price alerts silently inside the journal's quiet hours, asked through `MarketHost.inQuietHours`.

## The register of §13.1 (D-01 … D-37)

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
| D-11 | `applicationId = ir.andarz.app` is a placeholder; store availability of the name is unverified | Decide before release (O-1) |
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
| D-34 | Quiet hours (default 23:30–07:00) defer every reminder — **as amended by D-M18**: discipline reminders only | Predictable behavior |
| D-35 | Backup container `ANDZ` v1, PBKDF2-HMAC-SHA256 600,000 iterations, AES-256-GCM, AAD `andarz-backup-v1` | Pinned by vectors |
| D-36 | Fee impact on R:R = `(reward − fees) / (risk + fees)`, display only, never part of the gate | The constitution asks to include fees; the gate is workbook parity |
| D-37 | `PLANNED` rows do not count toward trades, positions or open risk and expire after 12 h | Only entered trades are rules-relevant |

---

## The merge register of K10 (D-M01 … D-M24)

Each row is the amendment K2 lists. Where a law or a section of the master prompt is amended, the amendment
is written out, because the prompt itself is not edited.

| Id | Decision | Why |
|---|---|---|
| D-M01 | Andarz is the host; the project lives in `ANDARZ/`; `march/` is read-only | One project; March stays untouched and usable |
| D-M02 | App name Andarz; `applicationId` stays the placeholder `ir.andarz.app` (D-11); the `:market` namespace is `ir.andarz.market`; March names appear only in the About credit (`mkt.about.credit`) | One identity; decide the final id before any release (O-1) |
| D-M03 | Modules and dependency rules of K4: `:engine` → nothing, `:data` → `:engine`, `:market` → no project module, `:app` → all three; nothing depends on `:app`, nothing but `:app` depends on `:market` | Network code cannot reach the journal |
| D-M04 | Laws 1 and 10, §1.1, §1.2, §2.1 and §7 are amended as in K2 | The merged app needs the internet for Market only |
| D-M05 | Egress policy and consent gate of K4 | Only public prices are fetched; the user sees exactly which hosts |
| D-M06 | Market prices never enter the engine; the live-price chip is a copy aid for a number the trader types | A free third-party spot feed is not the trader's exchange price, and the rules are about the trader's own numbers |
| D-M07 | Bottom bar Today, Journal, Market, Calculator, More; Insights moves to More and to a Journal top-bar action; its route and content do not change | Material 3 allows five items; Market is used daily, Insights weekly |
| D-M08 | Market features are opt-in: master switch, sharp-move alerts, ring modes, Clock-app ring and background monitor all default off | March's defaults (alerts on, monitor on, ring on everything) would need special permissions and a foreground service at first launch |
| D-M09 | No in-app updater, no `REQUEST_INSTALL_PACKAGES`, nothing that installs a package | A finance journal must not install packages |
| D-M10 | March's committed keystore (`march/Bazaaryar/ci/march-ci.jks.b64`) is treated as compromised: it is never decoded, copied or reused, and its alias is not reused. Andarz signing reads env vars or a git-ignored `keystore.properties`; without them the release build is debug-signed and prints `NOT PUBLISHABLE`. No CI workflow is created | A signing key committed to a public repository is no identity (O-2) |
| D-M11 | Persian only; March's English strings go to `docs/march_strings_en.json` (reference, not packaged) | Andarz is fa-only (§2.3) |
| D-M12 | Market wording lives in `app/src/main/assets/content_market_fa.json` with an `overrides` block; the spec JSON stays untouched | Keep spec files as delivered |
| D-M13 | Andarz components and tokens only; March's `Theme.kt`, `Motion.kt`, `Components.kt` and palette are not ported; up/down is never shown by colour alone | Law 9, §6 |
| D-M14 | `MarketFmt` on top of `Fmt`; no `android.icu`, no default-locale formatting | §2.1, §3.10 |
| D-M15 | No emoji and no hype wording in any shipped string; flags become ISO country codes, the rocket/arrow/target/zap emoji become ▲ ▼ and plain words | Law 8 and the owner's FOMO rules |
| D-M16 | The calendar is a static JSON asset with `validUntil`, an expired state and no network source | No unverified feed; the owner refreshes the list (O-3) |
| D-M17 | Market preferences live in their own DataStore named `market`; March's theme and language preferences are dropped | Separation of data |
| D-M18 | Quiet hours (D-34) govern the discipline reminders only; price alerts and release reminders are never deferred. P1 adds a "mute price alerts in quiet hours" switch (`mkt.set.quiet`), default off | They are user-defined and time-critical; many US releases land at 21:30 Tehran time |
| D-M19 | Market's starred coins and the discipline watch-list (§5.11) stay two separate lists with separate storage; MK3 adds display-only cross-links | Different meaning, different limits |
| D-M20 | A price alert is information: it never touches the gate, locks, governor, journal or any parameter, and never writes to `:data`. Nothing in `:market` knows what a trade is | Law 2 and the constitution's spirit |
| D-M21 | Trade-linked alerts (MK3) are evaluated in `:app` and posted as Andarz notifications on the discipline channel | Journal data never enters `:market` |
| D-M22 | The hub runs only while demanded and only with the master switch on and consent given; zero traffic otherwise; neither consent nor the switch is restored from a backup | Privacy and battery |
| D-M23 | Port faithfully, flag, do not fix: a suspected March defect goes into the Notes cell of `MERGE_MAP.md` unless it breaks a rule of the kickoff message | Keeps the port reviewable |
| D-M24 | Two channel groups (`discipline`, `market`); `:market` posts with the notification tag `market` | Settings stay tidy; ids cannot collide |

---

## Guard log (K8.4)

Each guard needs a planted-violation proof: plant it, see the build fail, remove it, record the line. All
of these were run on 2026-10-15; every violation was reverted immediately and `verifyGuards` is green
afterwards.

| Guard | What it checks | Where | Planted violation | Result |
|---|---|---|---|---|
| 1 | The merged release manifest equals the K8.1 allowlist; no install-packages permission; `allowBackup=false` | `app/build.gradle.kts` -> `verifyMergedManifestRelease` | `CAMERA` in `:app`'s manifest | **failed as expected** - "the merged manifest declares android.permission.CAMERA, which the K8.1 allowlist does not hold" |
| 2 | Only `:market`'s own manifest declares `INTERNET`; each module declares exactly its K8.1 share | `guardManifestSources` | `INTERNET` in `:app`'s manifest | **failed as expected** - both the "not assigned to it" and the "only :market may" lines |
| 3 | The project dependency graph equals K4 | `guardDependencyGraph` | `implementation(project(":data"))` in `:market` | **failed as expected** - ":market must not depend on :data (K4)" |
| 4 | Forbidden libraries are absent from each module's runtime classpath; `:app` declares no direct okhttp | `guardClasspaths` | `implementation(libs.okhttp)` in `:data` | **failed as expected** - the okhttp3 token *and* "carries okio together with okhttp3", which proves the D-M39 exception is tight |
| 5 | Egress policy: scheme, host, method, body, headers; WebSocket client frames | `:market` `MarketHttpEgressTest` | a POST, an `http://` URL, an unknown host, a loopback host, `Authorization`, `Cookie` - permanent test cases, not plants | **13 tests, 0 failures** |
| 6 | Zero traffic with Market off or not demanded | `:market` `ZeroTrafficTest` | - | **3 tests, 0 failures** |
| 7 | Law 3: no U+0600-U+06FF in any `.kt` of any module, tests included | `guardNoPersianInCode` | a Persian literal in `:market`'s `MarketHosts.kt` | **failed as expected** - "MarketHosts.kt:12 holds U+628" |
| 8 | No `android.icu`, no package installer, no APK mime type, no install permission | `guardForbiddenTokens` | `import android.icu.text.NumberFormat` in `:market` | **failed as expected** - "contains the forbidden token 'android.icu'" |
| 9 | Shipped strings carry no emoji and no claim that the app has no internet; no March/Bazaaryar name outside `mkt.about.credit` | `guardShippedStrings` | U+1F680 in `mkt.tab.prices` | **failed as expected** - "carries the emoji U+1F680" |

Two guards caught real problems in this milestone's own work before any plant was needed:

- **Guard 8** rejected the name of the install-packages permission inside a *comment* in `:market`'s
  manifest, where it was explaining that the permission is deliberately not carried over. A token scan
  cannot tell prose from code, and the guard is right to be blunt: the comment was reworded.
- **Guard 7** rejected `ContentBundleTest`, which held the Persian "no internet" claim that it searches
  for. The test now reads that claim from `tools/guards/guard_config.json` - the same file guard 9 reads -
  so there is one source of truth and no Persian in a `.kt` file.

Lint found one more of the same kind: the bidi isolate characters of Appendix C trap 9 were written as
literal U+2066/U+2069 and tripped `BidiSpoofing`. They are now built from their code points
(`String(Character.toChars(0x2066))`), so no bidirectional control character sits in a source file. M1
moves that helper into `Fmt.isolate` in `:engine`, where 3.13 puts it.

`verifyGuards` aggregates guards 1-4 and 7-9 and is wired into every module's `check`, so `./gradlew
build` runs them; guards 5 and 6 run as `:market:test`.
