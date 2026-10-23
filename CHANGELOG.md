# Changelog

All notable changes. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
SemVer for `versionName`; `versionCode` is the git commit count (D-73). User-facing release notes for each
build are in `RELEASE_NOTES.md`.

## [0.9.2] — audit release (not published)

### Fixed
- A failed action (invalid state rejected by the journal, full storage) no longer closes the app; a
  message appears at the bottom of the screen instead (A-01, D-82).
- An open trade with missing execution data (old backup, hand-edited import) is shown read-only instead of
  crashing the trade screen (A-02).
- Three icon buttons were smaller than 48 dp (My coins remove, News reminder and clock) (U-01, D-85).
- Seven tappable cards now announce themselves as buttons to TalkBack (U-02).

### Performance
- Clocks and the rules-session poll stop when the app is in the background (P-01, D-83).
- Wording, notification channels and the outgoing-file purge are prepared off the main thread at start
  (P-02, D-84).

### Tests
- `UiErrorsTest`, `TouchTargetSourceTest`, `ProcessDeathTest` (JVM) and `ColdStartE2ETest` (instrumented, run
  on a device).

### Docs
- `AUDIT.md` (findings register, health score, verdict), `TESTING.md`, `RELEASE.md`, `STORE_LISTING.md`,
  `ROADMAP.md`, `docs/audit/sbom.txt`; decisions D-82…D-88.

No breaking change: database schema (v2), backup format (`ANDZ` v1), routes and settings are unchanged.

## [0.9.1] — Android skills review

### Fixed
- Notification deep links accept only allow-listed routes (D-78).
- Edge-to-edge insets: content never under the status, navigation bar or keyboard (D-79).
- Fonts referenced through `R.font` so resource shrinking keeps them; R8 rules trimmed (D-80).
- Settings results as snackbar; ledger delete asks first; no truncated numbers at 200 % font (D-81).

## [0.9.0] — first feature-complete build

See `RELEASE_NOTES.md` § 0.9.0.
