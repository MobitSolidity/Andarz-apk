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
    return {r["file"].split("Bazaaryar/app/src/main/java/ir/bazaaryar/app/")[-1] + ":" + str(r["line"]): r
            for r in rows}


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
