# MISSING_COPY.md

Wording the app needs and no delivered document provides. Format per §0.3:

    key — where it is needed — proposed English description

Rules: never invent Persian legal-sounding text, and never invent a trading rule or a parameter. A key
listed here renders as `[[key]]` in debug builds only; the owner supplies the Persian.

---

## Open

*(nothing yet)*

The wording of M0 is complete: the base document `spec/andarz_content_fa.json` covers the journal, and
`app/src/main/assets/content_market_fa.json` carries the K13 copy for the Market section, its `uiArgs` and
the two `overrides`.

---

## Expected additions, by milestone

Not missing yet — these are the places where a gap will appear, so the owner knows what is coming. The
March wording behind MK1 and MK2 is **moved, not written**: it is extracted from the March sources with
`tools/extract_march_strings.py` and keyed by hand (K7, D-M32), so it does not belong on this list.

| Milestone | What may need new wording | Note |
|---|---|---|
| MK1 | `mkt.err.*` — one sentence per typed `MarketError` (`NoNetwork`, `Timeout`, `RateLimited`, `Blocked`, `Server`, `BadData`), each naming its service | March's `Net.friendly()` has a Persian sentence for most of these; `BadData` is new (it was `e.message` in March) and will need a line |
| MK1 | The offline state with the age of the last data (K6.2) | March shows a data age; the exact sentence comes from the extraction |
| MK2 | Channel names and descriptions for `price_alerts`, `price_alarm`, `live_monitor`, `releases` (K6.6) | March has all four; they move with `PriceAlerts.ensureChannel` and `AlarmRinger.ensureChannel` |
| MK2 | The ISO country chip that replaces the flag emoji (K6.7) | A code, not a word: no new Persian needed |
| MK3 | Nothing expected — K13 already supplies `mkt.link.*`, `mkt.chip.*` and `mkt.set.quiet` | |

## Keys that exist and are deliberately not used yet

| Key | Why it is unused today |
|---|---|
| `notif.partial.*` | Reserved by §7 for a manual price alert; implemented in MK3 for the partial-exit level of a trade-linked alert (K9.1 b) |
| `mkt.set.quiet` | The quiet-hours mute switch is P1 (MK3, K6.6) |
| `mkt.chip.*`, `mkt.link.*` | The live-price chip and trade-linked alerts are MK3 |

A test reports unused `mkt.*` keys (K7), with an allow-list for the rows above.
