<div align="center">

# Andarz · اندرز

**A personal trading-discipline engine and journal for Android: it says *yes* or *no* before you enter a trade, with the number and the rule behind the answer.**

Persian-first (RTL) · offline-first · no account · no ads · no tracking

[فارسی](README.fa.md) · [Documentation](docs/README.md) · [Changelog](CHANGELOG.md) · [Audit](docs/audit/AUDIT.md) · [Releases](https://github.com/MobitSolidity/Andarz-apk/releases)

![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4)
![targetSdk](https://img.shields.io/badge/targetSdk-35-informational)
![Status](https://img.shields.io/badge/status-release%20candidate%20(unsigned)-orange)

</div>

---

## Contents

1. [What Andarz is](#what-andarz-is)
2. [Features](#features)
3. [Principles](#principles)
4. [Project status](#project-status)
5. [Architecture](#architecture)
6. [Repository layout](#repository-layout)
7. [Getting started](#getting-started)
8. [Testing and quality gates](#testing-and-quality-gates)
9. [Privacy and security](#privacy-and-security)
10. [Release](#release)
11. [Contributing rules](#contributing-rules)
12. [History of this repository](#history-of-this-repository)
13. [Disclaimer](#disclaimer)

---

## What Andarz is

*Andarz* (اندرز) is Persian for "counsel": the wise advice given **before** a decision. In Sasanian Iran the
*andarzbad* was the chief counsellor. The app plays that role for a futures trader.

The owner wrote a 13-article trading constitution and kept an audited Excel journal. Andarz turns both into
an Android app that **enforces** the constitution instead of merely recording it:

- **Before the entry:** an 8-point checklist, position sizing from the stop and the risk percentage, a
  leverage advisor, and a gate. If any rule fails, entry stays closed and the reason is shown with its
  article number and the numbers that broke it.
- **During the trade:** the stop may only move toward profit; partial exits with fees; time stop; an event log.
- **After the exit:** result in R (against the initial stop), rule compliance, the main mistake, emotion and
  lesson. Daily, weekly and monthly statistics are built from these records.
- **Always:** a governor applies cool-downs, daily and weekly loss caps, a drawdown lock, a violation lock, a
  trade cap and risk reductions. Rules change only inside a review session: tightening applies at once,
  loosening after 24 hours, and every change is audited.

The app is a personal risk-management tool. It never places orders, never connects to an exchange account,
and is not financial advice.

## Features

### Journal and discipline (works with no network at all)
| Area | What it does |
|---|---|
| **Today** | Equity hero, lock banners with countdowns, risk reductions, six limit gauges, the overall verdict, open positions with time-stop and monitor check, the advice of the day |
| **Trade wizard** | Five steps; gate reasons in fixed order with article numbers; leverage advisor with feasible range; counter-trend half size; plan saved, rejected or entered; late entries judged by the rules at their own time |
| **Trade management** | Stop moves (away-moves need confirmation and become violation V04), partial exits, break-even, events, share note |
| **Close and review** | Multi-leg close with fees; R, net, compliance; violations, main mistake (M00–M11), emotion, lesson |
| **Governor** | Cool-down, DAILY_LOSS, WEEKLY_LOSS, DRAWDOWN, VIOLATION, TRADE_CAP, DAY_OVER locks; recovery, violation and new-setup risk reductions; big-win hold |
| **Insights** | 13 KPI tiles with targets, nine charts (each with a table view for accessibility), setup performance |
| **Rules and reviews** | 45 parameters, review sessions, daily/weekly/monthly/drawdown reviews, withdrawal proposal, full audit trail |
| **Ledger, setups, watch-list** | Deposits/withdrawals/adjustments, grade-A rule and test waiver, ten-symbol watch-list |
| **Constitution** | The 13 articles with live numbers that follow the current rules; a pledge to sign |
| **Learn** | Summary, review of experiences, journal guide, expectancy calculator |
| **Data** | Encrypted backup/restore (AES-256-GCM, PBKDF2), CSV export (Excel and full profiles) and import with preview, JSON export, demo data that never touches real numbers |
| **Platform** | App lock (biometric or PIN with persisted back-off), `FLAG_SECURE`, reminders via WorkManager with quiet hours, dark/light theme, Persian or Latin digits, Jalali or Gregorian dates, reduce-motion |

### Market (optional, isolated, off by default)
Turned on by the **internet switch that is the first card in Settings**, after a consent screen that lists
every host. When it is off, the app makes **no network request of any kind**.

- Top-200 coin prices in USD or Toman, the USDT/Toman rate, market cap, BTC dominance, Fear & Greed.
- *My coins*, a coin sheet with a range chart, live prices next to open trades.
- Price alerts (threshold, window, direction, cooldown, targets) that can ring like an alarm, through the
  phone's Clock app or the app's own alarm.
- A static economic-release calendar with reminders (valid until 2026-10-29; then an explicit expired state).
- An opt-in live monitor (foreground service with its own notification and a Stop action).

Sources: CoinGecko, Binance public data stream, Nobitex, Wallex, alternative.me. Requests carry only coin
symbols; nothing from the journal is ever sent.

## Principles

These are enforced by code and by build guards, not just by convention:

1. **Offline-first.** Journal, engine, data layer and backup have no network path. Only the `:market` module
   holds `INTERNET`, and only after explicit consent.
2. **The engine is the contract.** Every formula lives in the pure-Kotlin `:engine` and is checked against
   `spec/andarz_golden_vectors.json` (16 categories), produced by the executable reference `spec/andarz_ref.py`.
   Vectors and the reference are never edited to make a test pass.
3. **One source of wording.** All Persian text lives in `spec/andarz_content_fa.json` and
   `content_market_fa.json`; Kotlin files contain no Persian literal (guard 7).
4. **Your data stays yours.** No account, no server, no analytics or crash SDK, Android backup disabled; the
   only way out is a backup or export the user makes.
5. **Explain every "no".** A refusal always names the rule, the article and the numbers.

## Project status

| | |
|---|---|
| Version | **0.10.0** (release candidate, v2 "Ledger" look) · see [CHANGELOG](CHANGELOG.md) |
| Build | `./gradlew build` green: unit tests in both variants, lint and build guards 1–9 |
| Tests | engine 275 · data 60 · market 158 · app 133 test runs (both variants), 0 failures |
| Dependencies | 159 runtime components, 0 known advisories (OSV) · [SBOM](docs/audit/sbom.txt) |
| Health score | 81 / 100 · [AUDIT.md](docs/audit/AUDIT.md) |
| **Google Play** | **Not ready yet.** Blocking items: final `applicationId` (R-01), upload signing key (R-02), device acceptance pass (R-03), hosted privacy policy (R-04) |

Release builds are signed with the **debug key** until the owner provides an upload key; they are named
`-unsigned` and the build prints a **NOT PUBLISHABLE** warning. Do not distribute them as a store release.

## Architecture

```
                ┌──────────────────────────── :app ────────────────────────────┐
                │ Jetpack Compose UI · Hilt · WorkManager · app lock · nav     │
                │ MarketHostImpl / TradeWatcher (the only bridge to :market)   │
                └───────┬───────────────────────┬──────────────────────┬───────┘
                        │                       │                      │
                ┌───────▼──────┐        ┌───────▼───────┐      ┌───────▼───────┐
                │    :data     │        │    :engine    │      │    :market    │
                │ Room (v2)    │───────▶│ pure Kotlin   │      │ OkHttp, prices│
                │ DataStore    │        │ rules, sizing │      │ alerts, alarm │
                │ backup, CSV  │        │ governor, KPI │      │ calendar, FGS │
                └──────────────┘        │ Jalali, Fmt   │      │ INTERNET only │
                                        └───────────────┘      │ here; no deps │
                                                               └───────────────┘
```

| Module | Kind | Depends on | Responsibility |
|---|---|---|---|
| `:engine` | Kotlin/JVM, no Android | nothing | Formulas, gate, size verdict, governor, KPIs, Jalali calendar, number formatting, live-clause rendering. ≥ 90 % line coverage enforced |
| `:data` | Android library | `:engine` | Room database (schema exported, explicit migrations), DataStore settings, repositories, encrypted backup, CSV, PIN hashing, demo data |
| `:market` | Android library | **no project module** | Everything networked: price sources, hub, alerts and ringing, calendar, live monitor. Exposes a `MarketHost` interface |
| `:app` | Android application | all three | Compose screens, navigation, DI, reminders, lock, wiring Market to the journal |

**Stack:** Kotlin 2.1.0 · AGP 8.7.3 · Gradle 8.9 · JDK 17 · Compose BOM 2024.12 / Material 3 · Hilt 2.53.1 ·
Room 2.6.1 · DataStore 1.1.1 · WorkManager 2.10.0 · kotlinx-serialization 1.7.3 · OkHttp 4.12.0 (`:market`
only) · JUnit 4/5, Robolectric 4.14 · minSdk 26 · target/compileSdk 35. The toolchain is locked on purpose
(decision D-M33); upgrade it only as one planned step with a full device re-test.

**Build guards** (`gradle/guards.gradle.kts`, data in `tools/guards/guard_config.json`) run in every
`./gradlew build` and fail it if: the merged manifest gains a permission outside the allowlist; any module but
`:market` declares `INTERNET`; the module graph changes; a forbidden library reaches a classpath; a Kotlin file
holds Persian text; a forbidden token appears; a shipped string carries an emoji, a "no internet" claim or a
foreign product name. Two more guards are test suites in `:market`: egress policy and zero traffic while off.

## Repository layout

```
.
├── app/                    Android application (Compose UI, DI, navigation, platform)
├── data/                   Room, DataStore, repositories, backup, CSV  (schemas/ = exported Room schemas)
├── engine/                 Pure-Kotlin rules engine and its tests
├── market/                 Isolated, opt-in market module (the only one with network access)
├── spec/                   Read-only inputs: golden vectors, Python reference, content JSON, design tokens
├── tools/guards/           Data for the build guards
├── gradle/                 Version catalog, guards script, wrapper
├── docs/                   Documentation, sorted by purpose (index: docs/README.md)
│   ├── project/            DECISIONS (ADRs), MERGE_MAP, MISSING_COPY, ROADMAP
│   ├── release/            RELEASE playbook, STORE_LISTING, RELEASE_NOTES
│   ├── testing/            TESTING strategy, MANUAL_TEST acceptance script
│   ├── audit/              AUDIT register, Play policy report, SBOM
│   └── reference/          Strings kept for reference only
├── prompts/                The prompts and briefs the app was specified and audited with
│   ├── ANDARZ_MASTER_PROMPT.md   engineering specification
│   ├── KICKOFF_MESSAGE.txt       merge directive (Andarz + March market features)
│   ├── andarz-brief-fa.pdf       Persian introduction and naming brief
│   ├── opus5-review.txt          release-audit prompt
│   └── skills/                   audit, software-production and UI/UX skill prompts
├── CHANGELOG.md
├── README.md / README.fa.md
└── build.gradle.kts, settings.gradle.kts, gradle.properties, gradlew
```

## Getting started

**Requirements:** JDK 17, Android SDK platform 35 and build-tools 35.0.0 (Android Studio Ladybug or newer
works). Point `local.properties` at your SDK (`sdk.dir=/path/to/Android/sdk`) or set `ANDROID_HOME`.

```bash
git clone https://github.com/MobitSolidity/Andarz-apk.git andarz
cd andarz
./gradlew build             # all modules, all unit tests, lint, build guards
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug      # install on a connected device or emulator
```

On machines with little memory:
`./gradlew build --no-daemon -Pkotlin.compiler.execution.strategy=in-process`.

**Versioning:** `versionName` is `andarz.versionName` in `gradle.properties`; `versionCode` is the git commit
count, so every commit builds a higher code; the short commit hash is shown on the About screen.

**Release signing:** set `ANDARZ_KEYSTORE`, `ANDARZ_KEYSTORE_PASSWORD`, `ANDARZ_KEY_ALIAS`,
`ANDARZ_KEY_PASSWORD`, or put `storeFile`, `storePassword`, `keyAlias`, `keyPassword` into a git-ignored
`keystore.properties`. Missing values fail the build loudly; no keystore means a debug-signed, NOT
PUBLISHABLE build. Full steps: [docs/release/RELEASE.md](docs/release/RELEASE.md).

**Refreshing the release calendar:** edit `market/src/main/assets/market_calendar.json` by hand
(`validUntil`, `events[]` with unique `id`, ISO-8601 `at` with offset, `country`, Persian `title`, `impact`
1–3), set `validUntil` to the end of the last covered day, rebuild.

## Testing and quality gates

| Layer | What runs | Where |
|---|---|---|
| Engine contract | Every golden vector + property tests + governor scenarios | `engine/src/test` |
| Data | Repository lifecycle, backup crypto and tamper detection, Room migration, CSV round trip | `data/src/test` |
| Market | Egress policy (guard 5), zero traffic (guard 6), parsers, hub, alerts, calendar | `market/src/test` |
| App | Content keys, view logic, deep-link allowlist, 200 % font layout and semantics (Robolectric), 48 dp targets, process death, error mapping | `app/src/test` |
| End-to-end | Fresh install → onboarding (instrumented, needs a device) | `app/src/androidTest` |
| Manual | Release smoke S1–S18 and the full acceptance script | [TESTING](docs/testing/TESTING.md), [MANUAL_TEST](docs/testing/MANUAL_TEST.md) |

No device or emulator was available while building; every device check is honestly marked *pending*.

## Privacy and security

- `allowBackup=false` and data-extraction rules exclude every domain: nothing goes to cloud backup or
  device-to-device transfer.
- HTTPS/WSS only; cleartext refused app-wide; the five hosts are listed in `network_security_config.xml` and
  enforced again by an OkHttp interceptor (GET only, no body, no `Authorization` or `Cookie`).
- PIN: PBKDF2-HMAC-SHA256, 120 000 iterations, random salt, constant-time compare, persisted back-off.
  Backup: PBKDF2 600 000 → AES-256-GCM with associated data.
- Only the launcher activity and a boot receiver (which checks its action) are exported; notification deep
  links pass an allowlist.
- No analytics, advertising or crash SDK. Crash insight comes from Play Console's Android vitals and the R8
  mapping file kept per release.
- Full register with CVSS/CWE mapping: [docs/audit/AUDIT.md](docs/audit/AUDIT.md).

> **Note:** the history of this repository contains a signing keystore committed by the earlier March app
> (`Bazaaryar/ci/march-ci.jks.b64`, removed from `main`). It is treated as compromised and must never be used.

## Release

Google Play publishing follows [docs/release/RELEASE.md](docs/release/RELEASE.md): upload key, signed AAB,
Play Console checklist, staged rollout (internal → closed ≥ 14 days → 10 % → 50 % → 100 %) and rollback.
Store copy in Persian and English, privacy-policy text and Data safety answers are in
[docs/release/STORE_LISTING.md](docs/release/STORE_LISTING.md). There is deliberately no CI pipeline; every
release step is run by a person (decision D-87).

## Contributing rules

- `./gradlew build` must be green before a commit; never weaken or bypass a build guard.
- Never edit `spec/andarz_golden_vectors.json` or `spec/andarz_ref.py` to make a test pass.
- No Persian text in `.kt`/`.kts` files; add wording to the content JSON. Wording that does not exist yet goes
  into [docs/project/MISSING_COPY.md](docs/project/MISSING_COPY.md).
- Record every design decision in [docs/project/DECISIONS.md](docs/project/DECISIONS.md) (next id: D-90).
- Keep commits small and meaningful; announce breaking changes (schema, backup format, routes).
- Do not add network access outside `:market`, analytics, crash SDKs or new permissions without a decision.

## History of this repository

The repository started as **March** (package `ir.bazaaryar.app`), a price-and-news app. Andarz was then built
from the specification in `prompts/` and absorbed March's features as the isolated `:market` module;
[MERGE_MAP](docs/project/MERGE_MAP.md) records the fate of every March file. After the merge was complete,
March's original sources, its CI workflow and the one-time migration scripts were removed and the Andarz
project moved to the repository root (decision D-89). The earlier layout is preserved at the git tag
[`pre-restructure`](https://github.com/MobitSolidity/Andarz-apk/tree/pre-restructure).

## Disclaimer

Andarz is a personal risk-management and journaling tool. It is **not** financial advice, does not give
signals, does not place trades and does not connect to any exchange account. Trading leveraged products can
lose more than you expect; you alone are responsible for your decisions.

Font: [Vazirmatn](https://github.com/rastikerdar/vazirmatn) (SIL Open Font License 1.1). Third-party
libraries are listed in the SBOM and in the app's licences screen.
