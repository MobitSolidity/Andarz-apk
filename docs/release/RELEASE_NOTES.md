# Release notes

## 0.9.0 — first feature-complete build (not published)

Andarz enforces a trader's written constitution and keeps the trading journal, offline-first and in
Persian. This build includes every P0 and P1 item of the master prompt, plus the Market section merged from
March. It has **not** been run on a device. Treat it as a release candidate for device acceptance, not as a
store release.

### Journal and discipline
- Onboarding with the pledge; the constitution reader with live numbers; the security checklist.
- Today: the equity hero, lock banners with countdowns, risk reductions, six limit gauges, the overall verdict,
  open positions with time-stop and monitor check, and the advice of the day.
- A five-step trade wizard: gate reasons in fixed order with number and article, the leverage advisor, and
  counter-trend half size. Confirming the entry records the actual size and slippage. An entry confirmed more
  than 15 minutes after it happened is judged by the rules at its own time and flagged as logged late.
- Trade management covers stop moves, partial exits, events and the share note. Closing supports multiple legs
  with fees and R measured against the initial stop. The review step records violations, the main mistake,
  emotion and lesson.
- The governor implements cool-down, the daily and weekly loss locks, the drawdown lock, the violation lock and
  report, the big-win hold, new-setup and recovery risk reductions, and the trade cap.
- Insights: KPIs for any period by exit date, nine charts with a table view, and setup performance.
- Rules change only inside a review session: tightening takes effect at once, loosening after 24 hours, and
  every change is audited. Reviews come in daily, weekly, monthly (with a withdrawal proposal) and drawdown
  forms. Also included: the ledger, setups with the grade-A rule and the test waiver, and the watch-list.
- Learn pages: summary, review of experiences, additions, journal guide, and an expectancy calculator.
- Demo data: twelve sample DEMO trades to explore the screens; they never touch real numbers.

### Platform
- Encrypted backup and restore (AES-256-GCM, PBKDF2) that includes the Market settings. Also: CSV export in the
  Excel and full profiles, CSV import with preview, and a plain JSON export.
- App lock with biometric and PIN (attempt policy persisted), `FLAG_SECURE`, and deep links held until unlock.
- Reminders through WorkManager, with quiet hours and private lock-screen visibility.
- Dark (default) and light themes, RTL, Persian or Latin digits, Jalali or Gregorian display, and an in-app
  reduce-motion switch.

### Market (opt-in, isolated module)
- Off until the user consents. Prices in USD or Toman, My coins, and a coin sheet with range chart.
- Price alerts that can ring through the Clock app or the app's own alarm. News from a static economic
  calendar that is valid until 2026-10-29.
- Journal links: a live price chip in the wizard, calculator and trade screen; one-shot alerts tied to an open
  trade's take-profit, partial and stop levels; prices on the watch-list; alerts silenced in quiet hours.

### Known limits
- No device or emulator was available. Every device check in `MANUAL_TEST.md` is still pending, including a
  first start of the R8 release APK (R-1).
- The release APK is signed with the debug key and named `0.9.0-unsigned` until the owner provides a keystore
  (O-2). March's committed keystore is compromised and is never used.
- Three wording keys are missing (`MISSING_COPY.md`), so the late-logging tag and hint are not shown yet; the
  flag is stored.
- Open owner decisions: the final application id, store policy for the alarm permissions, the dependency
  mirrors, and `ACCESS_NETWORK_STATE` (`DECISIONS.md`, "Open for the owner").
- A personal discipline tool, not financial advice. It never trades and never connects to an exchange account.
