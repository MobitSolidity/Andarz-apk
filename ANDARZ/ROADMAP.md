# ROADMAP.md

Ordered by what unblocks publication first. IDs refer to `AUDIT.md`.

## Now — before the first Play upload (owner + device)

| # | Item | Owner | Ref |
|---|---|---|---|
| 1 | Decide the final applicationId | owner | R-01, O-1 |
| 2 | Create the upload key; build a signed AAB with no NOT PUBLISHABLE box | owner | R-02, O-2, RELEASE.md §1–2 |
| 3 | Host the privacy policy; add contact e-mail | owner | R-04, STORE_LISTING §3 |
| 4 | Device pass S1–S18 on API 26, API 34/35 and a small screen; run `connectedDebugAndroidTest` | owner/tester | R-03, TESTING.md |
| 5 | Record the FGS video; fill the full-screen-intent and FGS declarations | owner | R-05 |
| 6 | Screenshots (both themes), feature graphic, 512 px icon | owner | R-07 |

## Next — during closed testing (≥ 14 days)

- Generate an app baseline profile (`:baselineprofile` module + managed device) and a startup macrobenchmark;
  record cold-start times (P-03).
- Screenshot tests (Roborazzi) once reference images from a device exist (D-81).
- Fill the copy listed in `MISSING_COPY.md` (e.g. the visible "logged late" chip).
- Decide `SYSTEM_ALERT_WINDOW` from review feedback; drop it if Play objects (R-06).
- Translate the privacy policy page into Persian.

## Later

- Optional, local "export last crash report" (no network; user shares it by hand) — D-86 alternative.
- Refresh the static economic calendar before `validUntil` (2026-10-29) or ship it as a dated content update.
- Toolchain upgrade (AGP 9, Kotlin 2.2, Compose BOM 2025) as one planned step with a full device re-test
  (D-M33 lock).
- Tablet / foldable layouts (two-pane journal), currently phone-first.
- English UI (the store listing already exists in English; the app is Persian only).
