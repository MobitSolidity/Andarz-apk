# Andarz (اندرز) — build kit

Everything a coding agent needs to build **Andarz**: an offline-first, Persian-first Android app that enforces a trader's written constitution and keeps his trading journal (position sizing, risk limits, profit and loss in R, locks, reviews, statistics, encrypted backup). In the merged build it also carries the market features of the March app (live prices, USDT/Toman, price alerts that can ring, economic-release calendar) as an isolated, opt-in **Market** section.

## Contents

| Path | What it is |
|---|---|
| `KICKOFF_MESSAGE.txt` | The first message for the coding agent: the merge directive (Andarz + March). It adds to the prompt and says which places of it it amends (its section K2). |
| `ANDARZ_MASTER_PROMPT.md` | The engineering prompt (English, about 135 KB). Read it completely first. Its Appendix E holds the kickoff of a standalone build without March. |
| `spec/andarz_golden_vectors.json` | 248 numeric checks in 16 categories: the contract for the pure-Kotlin engine. |
| `spec/andarz_ref.py` | Executable reference in Python (no dependencies); it produced the vectors. |
| `spec/jalali_ref.py` | Jalali <-> Gregorian reference. |
| `spec/andarz_content_fa.json` | All Persian text, the 45 parameters, the constitution, lists, CSV profiles. |
| `spec/andarz_design_tokens.json` | Colors (dark and light, contrast-checked), type, spacing, motion. |

## How to use (merged build)

1. Put two folders side by side in one workspace folder:

   ```
   <workspace>/
     ANDARZ/    this kit, unzipped
     march/     a clone of github.com/MobitSolidity/march  (its Gradle project is march/Bazaaryar/)
   ```

2. Open the coding agent (for example Claude Code) on `<workspace>`, the folder that contains both, so it can read `march/`. Paste `KICKOFF_MESSAGE.txt` as the first message.
3. The agent creates the project inside `ANDARZ/` (a git repository of its own). `march/` is never changed.
4. Let it work one milestone at a time: M0 to M4, then MK1 and MK2 (market data, alerts, calendar), then M5, M6, MK3 (integration), M7, M8. A milestone is done when `./gradlew build` is green and its exit criteria hold. After each one read `DECISIONS.md`; the section "Open for the owner" at its top lists what is yours to decide.
5. Never let the agent edit a vector or `andarz_ref.py` to make a test pass.

Standalone build (no March): unzip the kit into an empty git repository and paste the message of Appendix E of the prompt instead.

## Status and known limits

- Kit version 1.1 (1.0 plus the merge directive). The engine section was compared line by line with the reference; the vectors were regenerated from the reference and came out identical. The UI and Android sections and the merge directive have had automatic consistency checks only (keys, decisions, vector ids, cross-references, coverage of every March file, hosts, Persian copy) and my own read-through. An independent review of the prompt has not been done; nothing has been compiled or run.
- The market part depends on third-party services (CoinGecko, Binance, Nobitex, Wallex, alternative.me). Reachability from Iran was not verified.
- March's economic calendar is a static list that ends on 2026-10-29 (7 Aban 1405). After that date the News section shows an expired state until the JSON is refreshed by hand; the directive adds no news source on purpose.
- March's repository contains a signing key in public (`Bazaaryar/ci/march-ci.jks.b64`). Treat it as compromised and never sign Andarz with it; the directive requires a new key from secrets and no CI.
- March's alarm-style permissions (full-screen intent, exact alarms, display over other apps) are kept behind opt-in switches, but some app stores restrict them: check the policy of the target store before publishing.
- The name «اندرز» and the placeholder `applicationId` (`ir.andarz.app`) were not checked against Bazaar, Myket or Google Play.
- The governor (locks, risk reductions, big-win hold) has no Excel twin: `andarz_ref.py` and 33 scenarios define it. The imported-history rule (section 3.9, D-14) has no vector; the prompt asks for dedicated tests.
- A personal discipline tool, not financial advice. The app never trades and never connects to an exchange account.

## Refreshing the release calendar

The News section reads `market/src/main/assets/market_calendar.json`. There is no network source on purpose.
After `validUntil`, News shows the expired state (`mkt.cal.expired.*`) and everything else keeps working.

Format:

```json
{
  "validUntil": "2026-10-29T23:59:59+03:30",
  "source": "free text, not shown",
  "events": [
    {"id": "2026-10-cpi", "at": "2026-10-14T16:00:00+03:30", "country": "US",
     "title": "Persian title", "titleEn": "optional", "impact": 3, "previous": "", "forecast": ""}
  ]
}
```

- `id` must be unique per occurrence (`yyyy-MM-name`), so a refreshed list never re-arms an old reminder.
- `at` is ISO-8601 with its offset; `impact` is 1 to 3; `country` is an ISO 3166 alpha-2 code.
- Edit the JSON by hand, or update March's `data/Events.kt` and run `python -I tools/convert_march_calendar.py`.
- Set `validUntil` to the end of the last day the list covers, then rebuild.
