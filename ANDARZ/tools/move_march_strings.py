# -*- coding: utf-8 -*-
"""Move March wording into `content_market_fa.json` byte-identically (K7 of the kickoff message).

March keeps its wording inline in Kotlin. Andarz may not (law 3), so each sentence moves into the market
content file under a key of the form `mkt.<area>.<name>`. The wording itself is **moved, never improved**:
the only transformations K7 allows are applied here, and nothing else touches the text.

Allowed transformations (K7):
  1. emoji removed;
  2. the product names March / Bazaaryar / «مارچ» / «بازاریار» replaced by the app name;
  3. English dropped (it lives in `docs/march_strings_en.json`, which is never packaged).

Everything else is byte-identical: Persian digits, ZWNJ (U+200C) and punctuation stay exactly as March
has them.

Where the text comes from
-------------------------
`docs/march_strings_raw.json` — the extractor's output — keyed by `file:line`. Nothing is retyped by hand,
so an accidental edit of a Persian sentence is impossible; if the source moves, the lookup fails loudly
instead of silently producing wrong copy.

Usage
-----
    python -I tools/move_march_strings.py --check        # verify the mapping still resolves
    python -I tools/move_march_strings.py --apply        # write the keys into content_market_fa.json
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
RAW = os.path.join(ROOT, "docs", "march_strings_raw.json")
TARGET = os.path.join(ROOT, "app", "src", "main", "assets", "content_market_fa.json")

# ---------------------------------------------------------------------------
# The mapping. One row per key: where the March wording lives, and what it is
# called in Andarz. `args` must match the `{placeholder}` names in the moved text.
#
# Areas:
#   err.*      typed failures of :market -> one sentence per MarketError (K4)
#   channel.*  the four market notification channels (K6.6)
#   data.*     a data source's own name, used inside those error sentences
# ---------------------------------------------------------------------------
MAPPING: list[dict] = [
    # ---- data source names (Net.kt's `friendly()` takes a service name) ----
    # March names the two Iranian exchanges in Persian; its other services by their Latin brand, which
    # needs no key. These are the names that appear inside the error sentences below.
    {"key": "mkt.data.nobitex", "at": "data/IranMarket.kt:54", "args": []},
    {"key": "mkt.data.wallex", "at": "data/IranMarket.kt:84", "args": []},
    # March never had a localized "all Iranian sources are down" line: the failure of the Iran path was a
    # plain IOException. Andarz shows the typed error instead, so this one sentence is added and recorded
    # in MISSING_COPY.md for the owner (K12: never invent trading rules; this is only an error label).
    {"key": "mkt.data.iran", "literal": "نوبیتکس و والکس", "args": [], "added": True},

    # ---- typed errors, one per MarketError variant (K4) ----
    {"key": "mkt.err.noNetwork", "at": "data/Net.kt:40", "args": []},
    {"key": "mkt.err.timeout", "at": "data/Net.kt:41", "args": ["service"]},
    {"key": "mkt.err.rateLimited", "at": "data/Net.kt:43", "args": ["service"]},
    {"key": "mkt.err.blocked", "at": "data/Net.kt:44", "args": ["service"]},
    {"key": "mkt.err.server", "at": "data/Net.kt:45", "args": ["service", "code"]},
    # March's fallback when the exception was of no known kind (`e.message ?: ...`). Andarz uses it for a
    # service that answered something unreadable (MarketError.BadData).
    {"key": "mkt.err.badData", "at": "data/Net.kt:47", "args": []},

    # ---- the four market notification channels (K6.6) ----
    {"key": "mkt.channel.price_alerts.name", "at": "notify/PriceAlerts.kt:54", "args": []},
    {"key": "mkt.channel.price_alerts.desc", "at": "notify/PriceAlerts.kt:55", "args": []},
    {"key": "mkt.channel.price_alarm.name", "at": "notify/AlarmRinger.kt:73", "args": []},
    {"key": "mkt.channel.price_alarm.desc", "at": "notify/AlarmRinger.kt:74", "args": []},
    {"key": "mkt.channel.live_monitor.name", "at": "notify/LiveService.kt:122", "args": []},
    {"key": "mkt.channel.releases.name", "at": "notify/Reminders.kt:57", "args": []},

    # ---- MK1: Prices (ui/MarketScreen.kt) ----
    {"key": "mkt.prices.title", "at": "ui/MarketScreen.kt:172", "args": []},
    {"key": "mkt.prices.search", "at": "ui/MarketScreen.kt:128", "args": []},
    {"key": "mkt.prices.clear", "at": "ui/MarketScreen.kt:133", "args": []},
    {"key": "mkt.prices.empty", "at": "ui/MarketScreen.kt:113", "args": []},
    {"key": "mkt.prices.refresh", "at": "ui/MarketScreen.kt:192", "args": []},
    # March appends the error sentence after this prefix (trailing space kept).
    {"key": "mkt.prices.failed", "at": "ui/MarketScreen.kt:158", "args": []},
    {"key": "mkt.sort.RANK", "at": "ui/MarketScreen.kt:63", "quote": 0, "args": []},
    {"key": "mkt.sort.GAIN", "at": "ui/MarketScreen.kt:64", "quote": 0, "args": []},
    {"key": "mkt.sort.LOSS", "at": "ui/MarketScreen.kt:65", "quote": 0, "args": []},
    {"key": "mkt.sort.VOLUME", "at": "ui/MarketScreen.kt:66", "quote": 0, "args": []},
    # Live-mode label, keyed by the LiveMode enum name.
    {"key": "mkt.live.BINANCE", "at": "ui/MarketScreen.kt:151", "args": []},
    {"key": "mkt.live.NOBITEX", "at": "ui/MarketScreen.kt:152", "args": []},
    {"key": "mkt.live.POLLING", "at": "ui/MarketScreen.kt:153", "args": []},
    {"key": "mkt.live.CONNECTING", "at": "ui/MarketScreen.kt:154", "args": []},
    # Fear & Greed classes, keyed by alternative.me's raw classification without spaces.
    {"key": "mkt.fg.ExtremeFear", "at": "ui/MarketScreen.kt:203", "args": []},
    {"key": "mkt.fg.Fear", "at": "ui/MarketScreen.kt:204", "args": []},
    {"key": "mkt.fg.Neutral", "at": "ui/MarketScreen.kt:205", "args": []},
    {"key": "mkt.fg.Greed", "at": "ui/MarketScreen.kt:206", "args": []},
    {"key": "mkt.fg.ExtremeGreed", "at": "ui/MarketScreen.kt:207", "args": []},
    {"key": "mkt.ov.cap", "at": "ui/MarketScreen.kt:219", "args": []},
    {"key": "mkt.ov.dominance", "at": "ui/MarketScreen.kt:225", "args": []},
    {"key": "mkt.ov.fg", "at": "ui/MarketScreen.kt:232", "args": []},

    # ---- MK1: the USD/Toman toggle (MainActivity.kt) and the Toman suffix (ui/Format.kt) ----
    {"key": "mkt.unit.toman", "at": "MainActivity.kt:161", "args": []},
    {"key": "mkt.unit.usd", "at": "MainActivity.kt:161#2", "args": []},
    {"key": "mkt.unit.tomanSuffix", "at": "ui/Format.kt:71", "args": []},
    # The synthetic USDT/Toman coin's name (data/PriceHub.kt); the source name is appended after " · ".
    {"key": "mkt.usdt.name", "at": "data/PriceHub.kt:306", "args": []},

    # ---- MK1: My coins (ui/WatchScreen.kt). The title is mkt.tab.coins (same wording, K7: one key). ----
    {"key": "mkt.coins.empty", "at": "ui/WatchScreen.kt:50", "args": []},
    {"key": "mkt.coins.reminders", "at": "ui/WatchScreen.kt:62", "args": []},
    {"key": "mkt.coins.remindersEmpty", "at": "ui/WatchScreen.kt:64", "args": []},
    {"key": "mkt.coins.remove", "at": "ui/WatchScreen.kt:112", "args": []},
    {"key": "mkt.coins.usdtPair", "at": "ui/WatchScreen.kt:115", "args": []},

    # ---- MK1: the read-only part of the coin sheet (ui/CoinSheet.kt) ----
    {"key": "mkt.sheet.rank", "at": "ui/CoinSheet.kt:100", "quote": 0, "args": []},
    {"key": "mkt.sheet.star", "at": "ui/CoinSheet.kt:106", "quote": 0, "args": []},
    {"key": "mkt.sheet.h24", "at": "ui/CoinSheet.kt:122", "quote": 0, "args": []},
    {"key": "mkt.sheet.d7", "at": "ui/CoinSheet.kt:123", "quote": 0, "args": []},
    {"key": "mkt.sheet.liveIran", "at": "ui/CoinSheet.kt:127", "quote": 0, "args": []},
    {"key": "mkt.sheet.chart7d", "at": "ui/CoinSheet.kt:137", "quote": 0, "args": []},
    {"key": "mkt.sheet.chartSession", "at": "ui/CoinSheet.kt:137", "quote": 1, "args": []},
    {"key": "mkt.sheet.collecting", "at": "ui/CoinSheet.kt:147", "quote": 0, "args": []},
    {"key": "mkt.sheet.high24", "at": "ui/CoinSheet.kt:152", "quote": 0, "args": []},
    {"key": "mkt.sheet.low24", "at": "ui/CoinSheet.kt:153", "quote": 0, "args": []},
    {"key": "mkt.sheet.vol24", "at": "ui/CoinSheet.kt:154", "quote": 0, "args": []},
    {"key": "mkt.sheet.cap", "at": "ui/CoinSheet.kt:155", "quote": 0, "args": []},
    {"key": "mkt.sheet.ath", "at": "ui/CoinSheet.kt:156", "quote": 0, "args": []},
    {"key": "mkt.sheet.athDist", "at": "ui/CoinSheet.kt:157", "quote": 0, "args": []},
]

# March gives `live_monitor` and `releases` no description, so `ChannelText.description` is empty for them
# rather than invented (K7: wording is moved, never written).

EMOJI = re.compile(
    "[\U0001F000-\U0001FAFF\U0001F1E6-\U0001F1FF☀-⛿✀-➿⬀-⯿⏩-⏺️]"
)

PRODUCT_NAMES = re.compile(r"Bazaaryar|مارچ|بازاریار")


def load_raw() -> dict[str, dict]:
    with open(RAW, encoding="utf-8") as fh:
        rows = json.load(fh)
    out: dict[str, dict] = {}
    for r in rows:
        base = r["file"].split("Bazaaryar/app/src/main/java/ir/bazaaryar/app/")[-1] + ":" + str(r["line"])
        key, n = base, 1
        while key in out:  # several tr() calls on one line: the second is "file:line#2", and so on
            n += 1
            key = base + "#" + str(n)
        out[key] = r
    return out


def resolve(raw: dict[str, dict], entry: dict, app_name: str) -> str:
    """The exact wording for one key, after the three allowed transformations."""
    if "literal" in entry:
        text = entry["literal"]
    else:
        row = raw.get(entry["at"])
        if row is None:
            raise SystemExit(
                "no March string at %s for key %s. Re-run tools/extract_march_strings.py --root ../march "
                "--json docs/march_strings_raw.json" % (entry["at"], entry["key"])
            )
        text = row["fa"]
        if "quote" in entry:
            # A `literal` row is the whole Kotlin line; take the n-th double-quoted string that holds
            # Persian. The quotes are the only thing removed: the text between them is kept byte for byte.
            quoted = [q for q in re.findall(r'"((?:[^"\\]|\\.)*)"', text) if re.search("[\u0600-\u06FF]", q)]
            if entry["quote"] >= len(quoted):
                raise SystemExit("key %s: no Persian string #%d at %s" % (entry["key"], entry["quote"], entry["at"]))
            text = quoted[entry["quote"]]

    text = EMOJI.sub("", text)
    text = PRODUCT_NAMES.sub(app_name, text)
    return text


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass

    ap = argparse.ArgumentParser(description="Move March wording into the market content file (K7).")
    ap.add_argument("--check", action="store_true", help="resolve every row and report, write nothing")
    ap.add_argument("--apply", action="store_true", help="write the keys into content_market_fa.json")
    args = ap.parse_args()
    if not (args.check or args.apply):
        ap.error("choose --check or --apply")

    raw = load_raw()
    with open(TARGET, encoding="utf-8") as fh:
        doc = json.load(fh)
    app_name = doc["meta"].get("appName") or json.load(
        open(os.path.join(ROOT, "spec", "andarz_content_fa.json"), encoding="utf-8")
    )["meta"]["app"]["name"]

    ui = doc["ui"]
    ui_args = doc["uiArgs"]
    added, changed = [], []

    for entry in MAPPING:
        text = resolve(raw, entry, app_name)
        found = sorted(set(re.findall(r"\{([A-Za-z0-9_]+)\}", text)))
        if found != sorted(entry["args"]):
            raise SystemExit(
                "key %s: uiArgs says %s but the moved text carries %s"
                % (entry["key"], sorted(entry["args"]), found)
            )
        if entry.get("added"):
            added.append(entry["key"])
        if ui.get(entry["key"]) != text:
            changed.append(entry["key"])
        ui[entry["key"]] = text
        if found:
            ui_args[entry["key"]] = found
        elif entry["key"] in ui_args:
            del ui_args[entry["key"]]

    if args.check:
        print("# %d keys resolved, %d would change" % (len(MAPPING), len(changed)))
        for entry in MAPPING:
            print("%-34s %-22s %s" % (entry["key"], entry.get("at", "(added)"), resolve(raw, entry, app_name)))
        return 0

    doc["ui"] = dict(sorted(ui.items()))
    doc["uiArgs"] = dict(sorted(ui_args.items()))
    doc["meta"]["source"] = (
        "KICKOFF_MESSAGE.txt K13 (copy) + tools/move_march_strings.py, which moves March wording "
        "byte-identically from the sources listed in its mapping (K7, D-M32)"
    )
    with open(TARGET, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(doc, fh, ensure_ascii=False, indent=1)
        fh.write("\n")

    print("wrote %d keys to %s" % (len(MAPPING), os.path.relpath(TARGET, ROOT)))
    if changed:
        print("changed: " + ", ".join(changed))
    if added:
        print("added (see MISSING_COPY.md): " + ", ".join(added))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
