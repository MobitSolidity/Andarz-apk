#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Extract every user-visible Persian string from the March sources (K7 of the kickoff message).

March keeps its wording inline, as `tr("<fa>", "<en>")` calls and as plain Persian literals. Andarz may
hold no Persian in code at all (law 3), so every one of those strings has to move into
`app/src/main/assets/content_market_fa.json`. This script *finds* them; a human assigns the keys
(`mkt.<area>.<name>`), because only a human can tell which sentences are the same sentence.

What it reports for every hit:
  file, line, the Persian text, the English text (when the hit is a `tr(...)` pair) and the placeholders
  it carries. Kotlin templates (`$x`, `${x.y}`) are rewritten to the `{x}` form the content JSON uses.

Usage
  python -I tools/extract_march_strings.py --root ../march
  python -I tools/extract_march_strings.py --root ../march --json docs/march_strings_raw.json
  python -I tools/extract_march_strings.py --root ../march --en-json docs/march_strings_en.json

The script only reads; it never writes inside --root. March is a read-only reference (K1).
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys

PERSIAN = re.compile(r"[؀-ۿ]")
# $name and ${expr} — Kotlin string templates.
TEMPLATE = re.compile(r"\$\{([^{}]*)\}|\$([A-Za-z_][A-Za-z0-9_]*)")


def placeholders(text: str) -> list[str]:
    """The placeholder names of a Kotlin template string, in order of first appearance."""
    out: list[str] = []
    for m in TEMPLATE.finditer(text):
        raw = (m.group(1) or m.group(2) or "").strip()
        if not raw:
            continue
        # ${c.symbol} -> symbol ; ${fa(pct)} -> pct ; ${s.window.label} -> label
        name = raw
        if "(" in name:
            inner = name[name.find("(") + 1 : name.rfind(")")] if ")" in name else name
            name = inner.strip() or name
        name = name.split(".")[-1].strip()
        name = re.sub(r"[^A-Za-z0-9_]", "", name)
        if name and name not in out:
            out.append(name)
    return out


def to_braces(text: str) -> str:
    """Rewrite Kotlin templates to the `{name}` form used by the content JSON."""

    def repl(m: re.Match[str]) -> str:
        raw = (m.group(1) or m.group(2) or "").strip()
        name = raw
        if "(" in name:
            inner = name[name.find("(") + 1 : name.rfind(")")] if ")" in name else name
            name = inner.strip() or name
        name = name.split(".")[-1].strip()
        name = re.sub(r"[^A-Za-z0-9_]", "", name)
        return "{" + name + "}" if name else raw

    return TEMPLATE.sub(repl, text)


def read_string_literal(src: str, i: int) -> tuple[str, int] | None:
    """Read a Kotlin string literal starting at src[i] == '"'. Returns (raw text, index after closing quote)."""
    if i >= len(src) or src[i] != '"':
        return None
    # Raw string """...""" — kept whole.
    if src.startswith('"""', i):
        end = src.find('"""', i + 3)
        if end < 0:
            return None
        return src[i + 3 : end], end + 3
    out: list[str] = []
    j = i + 1
    while j < len(src):
        c = src[j]
        if c == "\\" and j + 1 < len(src):
            nxt = src[j + 1]
            out.append({"n": "\n", "t": "\t", "r": "\r", '"': '"', "\\": "\\", "$": "$", "'": "'"}.get(nxt, "\\" + nxt))
            j += 2
            continue
        if c == '"':
            return "".join(out), j + 1
        if c == "\n":
            return None
        out.append(c)
        j += 1
    return None


def skip_ws(src: str, i: int) -> int:
    while i < len(src) and src[i] in " \t\r\n":
        i += 1
    return i


def find_tr_calls(src: str) -> list[tuple[int, int, str, str]]:
    """Every tr("fa", "en") call: (start offset, end offset, fa, en).

    The end offset matters: a call whose literals sit on the lines after `tr(` spans several lines, and
    every one of them is covered by the call. Without it, those lines were reported a second time as
    stray `literal` hits.
    """
    hits: list[tuple[int, int, str, str]] = []
    for m in re.finditer(r"\btr\s*\(", src):
        i = skip_ws(src, m.end())
        first = read_string_literal(src, i)
        if first is None:
            continue
        fa, i = first
        i = skip_ws(src, i)
        if i >= len(src) or src[i] != ",":
            continue
        i = skip_ws(src, i + 1)
        second = read_string_literal(src, i)
        if second is None:
            continue
        en, end = second
        if not PERSIAN.search(fa):
            continue
        hits.append((m.start(), end, fa, en))
    return hits


def line_of(src: str, offset: int) -> int:
    return src.count("\n", 0, offset) + 1


def scan_file(path: str, rel: str) -> list[dict]:
    with open(path, "r", encoding="utf-8") as fh:
        src = fh.read()
    rows: list[dict] = []
    covered: set[int] = set()

    for start, end, fa, en in find_tr_calls(src):
        ln = line_of(src, start)
        covered.update(range(ln, line_of(src, end) + 1))
        rows.append(
            {
                "file": rel,
                "line": ln,
                "kind": "tr",
                "fa": to_braces(fa),
                "en": to_braces(en),
                "faRaw": fa,
                "args": placeholders(fa) or placeholders(en),
            }
        )

    # Any other line that carries Persian: enum labels, comments, leftovers.
    for ln, line in enumerate(src.splitlines(), start=1):
        if not PERSIAN.search(line):
            continue
        if ln in covered:
            continue
        stripped = line.strip()
        rows.append(
            {
                "file": rel,
                "line": ln,
                "kind": "comment" if stripped.startswith(("//", "*", "/*")) else "literal",
                "fa": to_braces(stripped),
                "en": "",
                "faRaw": stripped,
                "args": placeholders(stripped),
            }
        )
    return rows


def main() -> int:
    # Windows consoles default to cp1252, which cannot print Persian. The report is UTF-8 either way.
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass

    ap = argparse.ArgumentParser(description="Find every Persian string in the March sources (K7).")
    ap.add_argument("--root", default=os.path.join("..", "march"), help="the march/ clone (read-only)")
    ap.add_argument("--json", default=None, help="write the raw hits as JSON here")
    ap.add_argument("--en-json", default=None, help="write a key->English skeleton here (reference only)")
    ap.add_argument("--quiet", action="store_true", help="write files only, no report on stdout")
    args = ap.parse_args()

    root = os.path.abspath(args.root)
    if not os.path.isdir(root):
        print("not a directory: " + root, file=sys.stderr)
        return 2

    rows: list[dict] = []
    files = 0
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in (".git", "build", ".gradle", ".idea")]
        for name in sorted(filenames):
            if not name.endswith(".kt"):
                continue
            full = os.path.join(dirpath, name)
            rel = os.path.relpath(full, root).replace(os.sep, "/")
            files += 1
            rows.extend(scan_file(full, rel))

    rows.sort(key=lambda r: (r["file"], r["line"]))

    if not args.quiet:
        print("# March Persian strings — " + str(len(rows)) + " hits in " + str(files) + " .kt files")
        print("# file:line\tkind\targs\tfa\ten")
        for r in rows:
            print(
                r["file"] + ":" + str(r["line"]) + "\t" + r["kind"] + "\t" +
                ",".join(r["args"]) + "\t" + r["fa"] + "\t" + r["en"]
            )
        kinds: dict[str, int] = {}
        for r in rows:
            kinds[r["kind"]] = kinds.get(r["kind"], 0) + 1
        print("")
        print("# by kind: " + ", ".join(k + "=" + str(v) for k, v in sorted(kinds.items())))
        print("# next: assign a key mkt.<area>.<name> to every 'tr' and 'literal' row and put it into")
        print("#       app/src/main/assets/content_market_fa.json ('ui' + 'uiArgs'). Comments are dropped.")

    if args.json:
        os.makedirs(os.path.dirname(os.path.abspath(args.json)) or ".", exist_ok=True)
        with open(args.json, "w", encoding="utf-8") as fh:
            json.dump(rows, fh, ensure_ascii=False, indent=1)
            fh.write("\n")

    if args.en_json:
        # A skeleton for a future English locale: the key is filled in by hand, so the English text is
        # keyed by its source position until then (K7: reference only, never packaged).
        out = {
            "_note": "Reference only, not packaged. English wording of the ported March strings (K7).",
            "strings": [
                {"at": r["file"] + ":" + str(r["line"]), "fa": r["fa"], "en": r["en"], "args": r["args"]}
                for r in rows
                if r["kind"] == "tr" and r["en"]
            ],
        }
        os.makedirs(os.path.dirname(os.path.abspath(args.en_json)) or ".", exist_ok=True)
        with open(args.en_json, "w", encoding="utf-8") as fh:
            json.dump(out, fh, ensure_ascii=False, indent=1)
            fh.write("\n")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
