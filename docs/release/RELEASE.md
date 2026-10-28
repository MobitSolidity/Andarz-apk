# RELEASE.md — build, sign, upload, roll out, roll back

> **2026-10-27 (D-95):** the owner publishes on Myket and Cafe Bazaar only; follow `IRAN_STORES.md`. This
> Google Play document is kept for a possible later Play release. The package name is now
> `ir.mobitmaster.andarz`, and the privacy policy is the in-app legal text hosted at
> https://mobitsolidity.github.io/Andarz-apk/ (D-96).

The playbook for publishing Andarz on Google Play. There is no CI and no release automation, by the owner's
rule (D-74, D-87): every step below is run by a person. Commands are verified in this repo unless marked
**(owner)**. **Blocked until:** O-1 (final applicationId) and O-2 (upload key) are decided, and the device pass
of `TESTING.md` §3 is green.

## 0. Preconditions

- [ ] JDK 17 (`java -version` → 17.x). Android SDK 35, build-tools 35.0.0. Do not upgrade AGP/Kotlin (D-M33).
- [ ] Clean tree on `main`: `git status` is empty and `git log -1` is the commit you mean to ship.
- [ ] `andarz.versionName` in `gradle.properties` bumped (SemVer). `versionCode` is the commit count (D-73),
      so it rises by itself. Check: `git rev-list --count HEAD` > the last code uploaded to Play.
- [ ] `CHANGELOG.md` has a section for this version.
- [ ] **(owner)** O-1: `applicationId` in `app/build.gradle.kts:51` is final. **It can never change after the
      first upload.** Today it is `ir.andarz.app`.

## 1. Upload key (once) **(owner)**

Play App Signing is mandatory for new apps: Google holds the *app signing key*; you keep an *upload key*.

```bash
keytool -genkeypair -v -keystore ~/keys/andarz-upload.jks -alias andarz-upload \
  -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12
```

- Keep the `.jks` and both passwords in a password manager and an offline copy. **Never commit it**: `*.jks`,
  `*.keystore` and `keystore.properties` are git-ignored.
- **Never** use March's `Bazaaryar/ci/march-ci.jks.b64` (removed from `main`, still in git history); it is a
  compromised public key (D-74, D-M10).
- Lost upload key → Play Console → *Setup → App signing → Request upload key reset* (days, not hours).

## 2. Build the signed bundle

Either environment variables:

```bash
export ANDARZ_KEYSTORE=$HOME/keys/andarz-upload.jks
export ANDARZ_KEYSTORE_PASSWORD='…'
export ANDARZ_KEY_ALIAS=andarz-upload
export ANDARZ_KEY_PASSWORD='…'
```

or `keystore.properties` at the repo root (git-ignored):

```properties
storeFile=/home/you/keys/andarz-upload.jks
storePassword=…
keyAlias=andarz-upload
keyPassword=…
```

Then:

```bash
./gradlew clean build bundleRelease        # build = all tests, lint and guards 1-9
```

Low-memory machines (what the audit used):
`./gradlew build bundleRelease --no-daemon -Pkotlin.compiler.execution.strategy=in-process`.

**Expected:** `BUILD SUCCESSFUL`, and **no** `NOT PUBLISHABLE` box in the output. If that box appears, the bundle
is debug-signed and its versionName ends in `-unsigned`: **do not upload it** (Play rejects debug-signed
bundles anyway).

Outputs:

| File | Use |
|---|---|
| `app/build/outputs/bundle/release/app-release.aab` | Upload to Play |
| `app/build/outputs/mapping/release/mapping.txt` | Also embedded in the AAB (`BUNDLE-METADATA/…/proguard.map`); archive a copy per version for retracing (D-80) |

Verify before upload:

```bash
keytool -printcert -jarfile app/build/outputs/bundle/release/app-release.aab | grep -E 'Owner|SHA256'
# Owner must be YOUR upload certificate, not "CN=Android Debug"
sha256sum app/build/outputs/bundle/release/app-release.aab > app-release.aab.sha256
```

Optional local install test of the exact bundle (bundletool):

```bash
java -jar bundletool.jar build-apks --bundle=app/build/outputs/bundle/release/app-release.aab \
  --output=andarz.apks --ks=$ANDARZ_KEYSTORE --ks-key-alias=$ANDARZ_KEY_ALIAS
java -jar bundletool.jar install-apks --apks=andarz.apks
```

ABI, density and language splits are produced by Play from the AAB; nothing is configured in Gradle (two
small native libraries from AndroidX: `libandroidx.graphics.path.so`, `libdatastore_shared_counter.so`).

Tag the release: `git tag -a andarz-vX.Y.Z -m "Andarz X.Y.Z (code N)" && git push origin andarz-vX.Y.Z`.

## 3. Play Console — first-time setup **(owner)**

- [ ] Developer account verified (identity, and for organisations a D-U-N-S number).
- [ ] *Create app*: name "اندرز — ژورنال ترید", default language fa-IR, App, Free.
- [ ] Personal accounts created after Nov 2023: **closed test with ≥ 12 testers for ≥ 14 days** before
      production access can be requested.
- [ ] *App content*, with the answers in `STORE_LISTING.md` §4–5:
  - [ ] Privacy policy URL (hosted, public HTTPS)
  - [ ] Ads: no
  - [ ] App access: all functionality available without credentials
  - [ ] Content rating questionnaire
  - [ ] Target audience: 18+
  - [ ] Data safety form
  - [ ] Financial features declaration
  - [ ] Foreground service declaration (specialUse) with a video
  - [ ] Full-screen intent declaration
  - [ ] Government / news / health: no
- [ ] *Main store listing*: titles, descriptions, icon 512, feature graphic, screenshots (STORE_LISTING.md
      §1–2), both fa-IR and en-US.
- [ ] *Store settings*: category Finance; contact e-mail.
- [ ] *Countries*: pick deliberately. Iranian exchanges (Nobitex, Wallex) are among the price sources; Play is
      not available in Iran, and sanctions screening is the owner's call.

## 4. Staged rollout

| Stage | Track | Audience | Exit criteria (all must hold) | Duration |
|---|---|---|---|---|
| 1 | Internal testing | ≤ 100 named testers (owner + friends) | Installs from Play; TESTING.md §3 smoke passes on ≥ 3 devices (one Android 8, one 14+, one small screen); no crash in Android vitals | 1–3 days |
| 2 | Closed testing | ≥ 12 testers (required for new personal accounts) | 14 days; crash-free users ≥ 99.5 %; ANR rate < 0.47 %; no P0/P1 open | ≥ 14 days |
| 3 | Production 10 % | Random 10 % | Same thresholds for 48 h; no new 1-star cluster on one topic | 2 days |
| 4 | Production 50 % | | Same for 48 h | 2 days |
| 5 | Production 100 % | | | |

Android vitals "bad behaviour" thresholds (Play): user-perceived crash rate 1.09 %, ANR rate 0.47 %. Stay well
under them; Play lowers visibility above them.

There is no crash SDK (D-86). Crash and ANR stacks come from *Android vitals → Crashes and ANRs*; retrace
them with the archived `mapping.txt` of that versionCode
(`retrace mapping.txt stack.txt`, from the SDK's `cmdline-tools/latest/bin/retrace`).

## 5. Rollback

Play cannot revert an installed version, so "rollback" means stopping and superseding.

1. **Halt** the rollout: *Release → Production → Manage → Halt rollout*. New installs and updates get the
   previous version. Users who already updated keep the bad one.
2. **Fix forward**: revert the faulty commit on `main` (`git revert <sha>`), bump `andarz.versionName` patch,
   rebuild (§2). `versionCode` is higher automatically (commit count). Upload, start at 10 %.
3. **Data safety of a rollback**: the Room schema only moves forward (v2, migrations in
   `AndarzDatabase.MIGRATIONS`). A fix release must **not** lower the schema version. If the bad release ran
   a migration, the fix must keep that schema. `fallbackToDestructiveMigration` is not used, so a mistake
   cannot silently wipe journals.
4. If the problem is in Market only: tell users to turn the internet switch off (Settings, first card); the
   journal needs no network.
5. Users' own encrypted backup is the last line of defence; the release notes of any fix should remind them.

## 6. After each release

- [ ] Archive `app-release.aab`, its `.sha256` and `mapping.txt` under the tag.
- [ ] Watch Android vitals daily for the first week.
- [ ] Answer reviews; move recurring reports into `ROADMAP.md`.
