# AUDIT.md — Andarz, international-grade audit (opus5-review + master-code-audit + software-production + ui-ux)

| | |
|---|---|
| Scope | Whole repository (then under `ANDARZ/`, now the repo root; modules `:engine`, `:data`, `:market`, `:app`), build scripts, manifests, resources, docs |
| Baseline commit | `969c08d` (0.9.1, code 49) — audit fixes in `3be93f1..` (see §7) |
| Method | Read the code first; grep sweeps; recomputation (contrast, listing lengths); OSV dependency scan; full `./gradlew build bundleRelease` |
| Not done | No device or emulator was available: nothing on the device list was run, and no finding is called fixed on the strength of a device test |
| Standards | OWASP MASVS v2 / MASTG, OWASP ASVS 4 (where applicable), CWE, CVSS 3.1, WCAG 2.2 AA, Google Play Developer Policy (2025), Android core app quality |

## 0. Project fingerprint

| Item | Value |
|---|---|
| Type | Native Android app, Kotlin, Jetpack Compose (single activity), RTL Persian |
| Toolchain | Kotlin 2.1.0, AGP 8.7.3, Gradle 8.9, JDK 17 — locked (D-M33) |
| SDK | minSdk 26, targetSdk 35, compileSdk 35 (`app/build.gradle.kts:47-53`) |
| Architecture | MVVM + Hilt 2.53.1; Room 2.6.1 (schema v2, exported, explicit migration `MIGRATION_1_2`); DataStore 1.1.1; WorkManager 2.10.0; OkHttp 4.12.0 only in `:market` |
| Modules | `:engine` pure JVM (rules maths, golden vectors) → `:data` (Room, backup, CSV) → `:app`; `:market` isolated (no project deps; only module with `INTERNET`), enforced by guards 2–4 |
| Build | `./gradlew build` = tests in both variants + lint + guards 1–9: **BUILD SUCCESSFUL** (this audit) |
| Release | R8 full mode + resource shrinking; release APK 2.87 MB; AAB 6.46 MB (includes R8 mapping and library baseline profiles); debug-signed `-unsigned` until O-2 |
| Dependencies | 159 runtime components (`docs/audit/sbom.txt`); OSV.dev scan: **0 known advisories** |
| Backend | None. No accounts, no server, no analytics/crash SDK (law K2, §8.4) |

## 1. Health score

| Dimension (weight) | Score | Notes |
|---|---|---|
| Stability (25) | 21 | A-01 (process kill on rejected action) fixed; A-02/A-03 hardened; device run pending |
| Security & privacy (25) | 23 | Strong baseline (no backup, cleartext off, PBKDF2 + AES-GCM, FLAG_SECURE, egress allowlist); pinning not adopted (accepted, D-88) |
| Performance (15) | 12 | P-01/P-02 fixed; no app-specific baseline profile; cold start not measured on a device |
| UI/UX & accessibility (15) | 13 | 72 contrast pairs recomputed AA-pass; 48 dp & roles enforced; TalkBack speech unverified |
| Tests (10) | 8 | 624 JVM test executions green; 1 instrumented e2e written, not run |
| Release readiness (10) | 4 | Signing key, applicationId, privacy-policy URL, store assets and device pass still open |
| **Total** | **81 / 100** | Code quality is release-grade; **the product is not yet publishable** (§6) |

## 2. Findings register

Severity: **P0** blocks release · **P1** fix before production · **P2** fix soon · **P3** improvement.
Status: **Fixed** (commit) · **Open** · **Accepted** (decision recorded) · **Owner** (needs the owner).
CVSS only for security-relevant findings.

| ID | Sev | Area | File:line | Problem | Risk | Fix | CVSS 3.1 / CWE | Status |
|---|---|---|---|---|---|---|---|---|
| A-01 | P1 | Stability | 71 sites in 15 files, e.g. `ui/platform/SettingsViewModel.kt`, `ui/trade/TradeViewModel.kt`; fix `ui/SafeLaunch.kt:40` | `viewModelScope.launch` without a handler; repositories throw on invalid state (`require`, `TradeStateException`, `SQLiteFullException`) | Any rejected action or full disk kills the process (crash rate in vitals) | `safeLaunch` with `CoroutineExceptionHandler` → snackbar `err.storage`/`err.generic` (`UiErrorSnackbarHost`, nav shell + onboarding); `UiErrorsTest` | CWE-248 | **Fixed** `3be93f1` |
| A-02 | P2 | Stability | `ui/trade/TradeScreen.kt:177,186,199,317` (`t.entry!!`, `t.notional!!`, `t.entryTime!!`) | Management maths dereferences execution fields of an OPEN trade | A row restored from an old backup / hand-edited import without them crashes the screen | Management and close sheet shown only when the fields exist; otherwise read-only + error line | CWE-476 | **Fixed** `1e1538d` |
| A-03 | P2 | Stability | `ui/wizard/WizardViewModel.kt:102,132`, `ui/analysis/CalculatorScreen.kt:73` | Drafts in `SavedStateHandle` had no test of the Parcel path | A non-serializable field added later would crash on `onStop` | `ProcessDeathTest` round-trips both drafts through a Parcel | CWE-502 (n/a: own types only) | **Fixed** `1e1538d` |
| A-04 | P3 | Stability | `platform/ConstitutionScreen.kt:98-226` (24 `!!`), `data/csv/Csv.kt:94-110` | `!!` on fields of the bundled JSON | Only reachable if the shipped asset is malformed | Kept: `ContentBundleTest` parses and renders every article and CSV profile on every build, so a malformed asset fails the build, not the user | — | **Accepted** |
| A-05 | P3 | Stability | `market/**` 13 `catch (_: Exception)` (e.g. `alarm/AlarmRinger.kt:143`, `data/PriceHub.kt:230`) | Empty catches | Could hide errors | Reviewed one by one: each is a deliberate fallback (ring path → own alarm, mirror → next host, permission screen → app settings) | CWE-390 (n/a) | **Accepted** |
| P-01 | P2 | Battery | `ui/analysis/RulesScreen.kt:103` (was `while(true){refresh();delay(30_000)}` in `viewModelScope`); `ui/today/TodayScreen.kt:71`, `ui/market/NewsScreen.kt:85`, `ui/market/LiveLink.kt:72` | Polling/tickers not bound to visibility | Process kept waking in background while the screen was on the back stack | `ui/Ticker.kt`: `repeatOnLifecycle(STARTED)` for all four | — | **Fixed** `d64cba9` |
| P-02 | P2 | Cold start | `AndarzApp.kt:51-60`; `content/ContentRepository.kt:32` (`preload()` never called) | 185 KB JSON parse, channel creation and `cacheDir/out` purge on the main thread | Slower first frame, ANR risk on low-end API 26 devices | Moved to `Dispatchers.IO` at process start; lock-guarded lazy load keeps receivers safe | — | **Fixed** `d64cba9` |
| P-03 | P3 | Startup | — | No app-specific baseline profile (only library profiles in the AAB) | 10–30 % slower first launches than possible | Needs a device to generate (`:baselineprofile`) | — | **Open** (ROADMAP) |
| U-01 | P2 | A11y | `ui/market/MyCoinsScreen.kt:111` (24 dp), `ui/market/NewsScreen.kt:252,256` (40 dp) | Icon buttons below 48 dp | Missed taps; WCAG 2.2 SC 2.5.8 | 48 dp; `TouchTargetSourceTest` | — | **Fixed** `c6c174b` |
| U-02 | P3 | A11y | `MarketParts.kt:140`, `MyCoinsScreen.kt:76,105`, `AlertsScreen.kt:226,262`, `RulesScreen.kt:181`, `ReviewsScreen.kt:193` | `clickable` without `role` | TalkBack does not say "button" | `Role.Button`; enforced by the same test | — | **Fixed** `c6c174b` |
| U-03 | — | A11y | `assets/design_tokens.json` | Contrast claims | — | All 72 claimed pairs recomputed with the WCAG formula: match within 0.02, all ≥ 4.5 (text) / ≥ 3 (UI, charts). Kotlin token constants equal the JSON | — | **Verified** |
| U-04 | P3 | A11y | `ui/market/MarketFmt.kt:71` | Up/down colour | Colour-only meaning? | No: ▲/▼ glyph always precedes the number | — | **Verified** |
| U-05 | P2 | UX | device | TalkBack spoken order, 200 % font on every screen, rotation, small screens | Unknown on hardware | TESTING.md S5, S7, S15 | — | **Open** (device) |
| S-01 | — | Security | `AndroidManifest.xml:15-25`, `res/xml/data_extraction_rules.xml` | Backup / cleartext | — | `allowBackup=false`, all domains excluded for cloud and device transfer, `usesCleartextTraffic=false`, network config refuses cleartext and lists the 5 hosts | MASVS-STORAGE-1, NETWORK-1 | **Verified** |
| S-02 | — | Security | `data/settings/PinLock.kt:14,34`; `data/backup/BackupCrypto.kt:34,74` | Credential & backup crypto | — | PIN: PBKDF2-HMAC-SHA256, 16-byte salt, 120 000 iterations, constant-time compare, attempt back-off persisted. Backup: PBKDF2 600 000 → AES-256-GCM, AAD, bounded iterations on import | MASVS-CRYPTO-1/2 | **Verified** |
| S-03 | P3 | Security | `res/xml/network_security_config.xml` | No certificate pinning | MITM with a user-installed or rogue CA could alter *public* prices shown (no user data travels) | Not pinned: rotation would break the app with no update path; system trust anchors only (user CAs not trusted on API 24+) | AV:N/AC:H/PR:N/UI:R/S:U/C:N/I:L/A:N = **3.1**, CWE-295 | **Accepted** (D-88) |
| S-04 | — | Security | `market/.../calendar/Reminders.kt:93-95`; manifests | Exported components | — | Only `MainActivity` (launcher) and `BootReceiver` (checks action) are exported; deep-link extra passes an allowlist (`ui/nav/DeepLinks.kt:30`) | MASVS-PLATFORM-1 | **Verified** |
| S-05 | — | Privacy | `MainActivity.kt:75-76` | Recents / screenshots | — | `FLAG_SECURE` while the lock is on and "hide in recents" chosen | MASVS-PLATFORM-3 | **Verified** |
| S-06 | — | Privacy | repo-wide grep | Secrets, logs | — | No API keys or secrets in source or Gradle; no `Log.*` outside `BuildConfig.DEBUG`; R8 strips debug paths | MASVS-STORAGE-2, CWE-532 | **Verified** |
| S-07 | — | Supply chain | `docs/audit/sbom.txt` | Vulnerable dependencies | — | 159 components, OSV: none | — | **Verified** |
| S-08 | P1 | Signing | `Bazaaryar/ci/march-ci.jks.b64` (March; removed from `main`, still in history) | Committed public keystore | Anyone could sign as March | Never used, never decoded; Andarz has strict env/properties signing (D-74) | CWE-321 | **Accepted** (not used) |
| R-01 | **P0** | Release | `app/build.gradle.kts:51` | `applicationId` not confirmed final (O-1) | Cannot change after first upload | Owner decision | — | **Owner** |
| R-02 | **P0** | Release | `app/build.gradle.kts:28,64,343` | No upload key (O-2); builds are debug-signed `-unsigned` | Play rejects; NOT PUBLISHABLE | RELEASE.md §1–2 | — | **Owner** |
| R-03 | **P0** | Release | TESTING.md §3, MANUAL_TEST.md | No device acceptance pass | Unknown crash/ANR on real hardware; FGS/full-screen video for Play | Run S1–S18 on 3 devices | — | **Open** (device) |
| R-04 | **P0** | Policy | STORE_LISTING.md §3 | Privacy policy not hosted | Mandatory with these permissions; rejection | Text drafted; owner hosts it and adds contact e-mail | — | **Owner** |
| R-05 | P1 | Policy | `market/src/main/AndroidManifest.xml:30` | `USE_FULL_SCREEN_INTENT` on a non-alarm app | Play may revoke on API 34+ | Fallback exists (`AlarmRinger.kt:124`); declaration text in STORE_LISTING §5 | — | **Open** (declaration) |
| R-06 | P2 | Policy | `market/src/main/AndroidManifest.xml:33`, `alarm/ClockRing.kt:39-52` | `SYSTEM_ALERT_WINDOW` for the Clock-app ring path | Reviewer questions | Justification in STORE_LISTING §5; drop it if Play objects (own alarm stays) | — | **Open** (review) |
| R-07 | P1 | Release | STORE_LISTING §2 | Store graphics (512 icon PNG, feature graphic, screenshots) missing | Cannot submit listing | Needs a device for screenshots | — | **Open** |
| R-08 | P3 | Process | — | No CI pipeline | Human error in release | Waived by owner rule; RELEASE.md checklist + build guards instead | — | **Accepted** (D-87) |
| R-09 | P3 | Observability | — | No crash SDK | Fewer crash details | Android vitals + mapping retrace (D-86) | — | **Accepted** |

## 3. Permission justification (merged release manifest)

| Permission | Module | Why | When requested | Play note |
|---|---|---|---|---|
| `POST_NOTIFICATIONS` | app | Discipline reminders, price alerts | When the user turns reminders/alerts on (API 33+) | — |
| `USE_BIOMETRIC` (+ merged `USE_FINGERPRINT`) | app | App lock | When the lock is enabled | — |
| `VIBRATE` | app | Alarm/reminder vibration | Normal | — |
| `INTERNET`, `ACCESS_NETWORK_STATE` | market | Public price data, offline detection | Used only after the switch + consent (D-76) | Data safety: no user data |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | market | Opt-in live price monitor | User starts it | Declaration + video |
| `WAKE_LOCK` | market | Alarm ringing window | During a ring | — |
| `RECEIVE_BOOT_COMPLETED` | market | Restore reminders/monitor after reboot | Normal | — |
| `SCHEDULE_EXACT_ALARM` | market | Price alarm snooze, release reminders | User-grantable special access, checked | Not `USE_EXACT_ALARM` |
| `USE_FULL_SCREEN_INTENT` | market | Alarm-style ring over the lock screen | API 34+: checked, falls back | Declaration (R-05) |
| `SET_ALARM` | market | Hand an alarm to the Clock app | Normal | — |
| `SYSTEM_ALERT_WINDOW` | market | Start the Clock app from background for a ring | Only when "ring via Clock app" is chosen | R-06 |
| `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | merged | AndroidX internal | — | — |

Guard 1 (`app/build.gradle.kts` → `verifyMergedManifestRelease`) fails the build if this set changes.

## 4. Compliance checklist

| Standard | Item | Result |
|---|---|---|
| MASVS-STORAGE | Sensitive data only in app sandbox; no backup; no logs | Pass |
| MASVS-CRYPTO | Standard algorithms, no hard-coded keys, adequate KDF | Pass |
| MASVS-AUTH | Local auth (biometric/PIN) with back-off | Pass (device check F7 pending) |
| MASVS-NETWORK | TLS only, cleartext refused; pinning | Pass / pinning accepted risk (S-03) |
| MASVS-PLATFORM | Exported components minimal; intents validated; FLAG_SECURE | Pass |
| MASVS-CODE | Up-to-date deps, R8, no debuggable release | Pass |
| MASVS-RESILIENCE | Root/tamper detection | Not applicable (no server-side value to protect) |
| MASVS-PRIVACY | Data minimisation, consent before network, user deletion | Pass |
| GDPR / privacy by design | No personal data processed by the developer; consent screen before any connection; uninstall deletes all | Pass (policy URL R-04 open) |
| WCAG 2.2 AA | 1.4.3 contrast, 1.4.11 non-text contrast, 1.4.4/1.4.10 resize/reflow (200 % tested), 2.5.8 target size, 4.1.2 name/role/value, 1.4.1 use of colour | Pass on JVM checks; TalkBack/device pending |
| Play policy | Target API 35; data safety; FGS type; exact alarm; full-screen intent; no misleading claims | Pass in code; declarations R-04/R-05 open |

## 5. Software-production items

| Item | Where |
|---|---|
| Versioning (SemVer name + monotonic code) | D-73, `gradle.properties` |
| SBOM | `docs/audit/sbom.txt` |
| ADRs | `DECISIONS.md` (D-01…D-88, D-M01…, O-*) |
| Release playbook, staged rollout, rollback | `RELEASE.md` |
| Test strategy | `TESTING.md` |
| Changelog | `CHANGELOG.md` |
| Roadmap | `ROADMAP.md` |
| CI/CD | Waived (D-87) |

## 6. Verdict

**Ready for Google Play: NO** — not for production, and not yet for an internal-testing upload.

The code is in release shape (green build, all guards, no known vulnerable dependency, the stability,
battery and accessibility findings of this audit fixed). What blocks publication is outside the code:

1. **R-01** final `applicationId` (owner).
2. **R-02** upload key and a signed AAB without the NOT PUBLISHABLE box (owner).
3. **R-03** device acceptance pass (TESTING.md §3, MANUAL_TEST.md) — including the FGS video.
4. **R-04** hosted privacy policy with a contact address (owner).

After those, the P1 items R-05 (full-screen-intent declaration) and R-07 (store graphics) are needed before
the listing can be submitted. Then follow RELEASE.md §4 (internal → closed ≥ 14 days → staged production).

## 7. Sign-off

| Phase | Commits | Summary |
|---|---|---|
| 1 Stability | `3be93f1`, `1e1538d` | safeLaunch + error snackbar; OPEN-row guard; process-death test |
| 2 Security | — (no code change needed) | Verified S-01…S-08; pinning decision D-88 |
| 3 Performance | `d64cba9` | Lifecycle-bound tickers/poll; cold start off main thread |
| 4 UI/UX/a11y | `c6c174b` | 48 dp targets, roles, source test; contrast recomputed |
| 5 Tests | `1e1538d`, this commit | 4 new test classes incl. instrumented e2e; TESTING.md |
| 6 Release | this commit | RELEASE.md, STORE_LISTING.md, CHANGELOG.md, ROADMAP.md, SBOM, D-82…D-88 |

Breaking changes: **none** (no schema, backup format, route or public API changed).

Auditor: AI agent (this session). Not signed off for release — see §6.
