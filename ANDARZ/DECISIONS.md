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
| O-3 | **Economic-release calendar refresh**: the shipped list ends on 2026-10-29 (7 Aban 1405); after that date News shows the expired state | D-M16, K6.10: the calendar is a static asset with no network source. Format and refresh steps are in `README.md` | owner, before 2026-10-29 |
| O-4 | **Store policy for the alarm-style permissions** of K8.1 (`USE_FULL_SCREEN_INTENT`, `SCHEDULE_EXACT_ALARM`, `SET_ALARM`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE_SPECIAL_USE`) on every target store | Each store judges these differently; all are opt-in and off by default (D-M08), but a store may still ask for a justification | owner, before publishing |
| O-5 | **`ACCESS_NETWORK_STATE` in the merged manifest** — keep it, or strip it with `tools:node="remove"` | D-M40: WorkManager declares it for network constraints, which Andarz never uses. It grants no internet access, only connectivity state. Removing it tightens the permission surface but risks a `SecurityException` from WorkManager's constraint tracker, which cannot be tested without a device | owner, before publishing |
| O-6 | **Dependency mirrors** (`maven.myket.ir`, `maven.aliyun.com`) are in `settings.gradle.kts` | D-M38: `dl.google.com` answers 404 from this network, so androidx cannot be fetched without them. A mirror is a supply-chain trust decision; consider Gradle dependency verification (checksum pinning) before release | owner |

**Closed since the first draft**

| # | Was | Now |
|---|---|---|
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
