# -*- coding: utf-8 -*-
"""Convert March's hard-coded release list (data/Events.kt) into market/src/main/assets/market_calendar.json.

K5 (row data/Events.kt, CONVERT) and K6.10: the calendar is a static asset with a `validUntil` field;
there is no network source. Titles are copied byte for byte from the Kotlin source (K7); only the flag
emoji map is dropped (K6.7: the UI shows the ISO country code instead).

JSON format (documented for the owner in README.md, "Refreshing the release calendar"):

    {
      "validUntil": "2026-10-29T23:59:59+03:30",   # after this instant News shows the expired state
      "source": "...",                              # free text, not shown
      "events": [
        {
          "id": "2026-10-cpi",                      # unique per occurrence
          "at": "2026-10-14T16:00:00+03:30",        # ISO-8601 with offset
          "country": "US",                          # ISO 3166 alpha-2
          "title": "...",                           # Persian title
          "titleEn": "...",                         # optional, reference only
          "impact": 3,                              # 1..3
          "previous": "",                           # optional
          "forecast": ""                            # optional
        }
      ]
    }

Usage:
    python -I tools/convert_march_calendar.py            # writes the asset from ../Bazaaryar
    python -I tools/convert_march_calendar.py --check    # prints, writes nothing
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SRC = os.path.join(ROOT, "..", "Bazaaryar", "app", "src", "main", "java", "ir", "bazaaryar", "app", "data", "Events.kt")
OUT = os.path.join(ROOT, "market", "src", "main", "assets", "market_calendar.json")

ROW = re.compile(
    r'EconEvent\("(?P<id>[^"]+)",\s*t\("(?P<at>[^"]+)"\),\s*"(?P<country>[A-Z]{2})",\s*"(?P<title>[^"]+)",\s*(?P<impact>[1-3])'
    r'(?P<rest>[^\n]*)\),'
)
NAMED = re.compile(r'(previous|forecast|en)\s*=\s*"([^"]*)"')
OFFSET = "+03:30"  # March: Tehran time, UTC+03:30, no DST


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except (AttributeError, ValueError):
        pass
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()

    text = open(SRC, encoding="utf-8").read()
    events = []
    for m in ROW.finditer(text):
        named = dict(NAMED.findall(m.group("rest")))
        e = {
            "id": m.group("at")[:7] + "-" + m.group("id"),
            "at": m.group("at") + OFFSET,
            "country": m.group("country"),
            "title": m.group("title"),
            "impact": int(m.group("impact")),
        }
        if named.get("en"):
            e["titleEn"] = named["en"]
        if named.get("previous"):
            e["previous"] = named["previous"]
        if named.get("forecast"):
            e["forecast"] = named["forecast"]
        events.append(e)
    if not events:
        raise SystemExit("no EconEvent rows found in " + SRC)
    events.sort(key=lambda e: e["at"])
    ids = [e["id"] for e in events]
    if len(set(ids)) != len(ids):
        raise SystemExit("duplicate ids")
    doc = {
        "validUntil": events[-1]["at"][:10] + "T23:59:59" + OFFSET,
        "source": "Federal Reserve calendar, BLS release schedule. Converted by tools/convert_march_calendar.py. "
                  "Refresh by hand; see README.md.",
        "events": events,
    }
    out = json.dumps(doc, ensure_ascii=False, indent=1) + "\n"
    if args.check:
        print(out)
        return 0
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(out)
    print("wrote %d events, validUntil %s" % (len(events), doc["validUntil"]))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
