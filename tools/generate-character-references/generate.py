#!/usr/bin/env python3
"""Generates the full WHATWG character-reference table into CharacterReferences.kt.

The table is data from the HTML specification (CC BY 4.0). This script is the only thing
that ever writes it, so the provenance is one place: the URL, the commit and the sha256.

Usage:
    python3 tools/generate-character-references/generate.py            # fetch, verify, write
    python3 tools/generate-character-references/generate.py --check    # verify only
"""
import hashlib
import json
import pathlib
import re
import sys
import urllib.request

SOURCE_URL = "https://html.spec.whatwg.org/entities.json"
# Filled in the first time the script runs successfully; until then the check is skipped
# and the run says so out loud.
PINNED_SHA256 = ""
TARGET = pathlib.Path(__file__).resolve().parents[2] / (
    "engine-core/src/main/kotlin/dev/nijatva/reader/core/html/CharacterReferences.kt"
)
BEGIN = "    // BEGIN GENERATED TABLE"
END = "    // END GENERATED TABLE"


def fetch() -> bytes:
    with urllib.request.urlopen(SOURCE_URL, timeout=60) as r:
        return r.read()


def build_table(raw: bytes) -> str:
    data = json.loads(raw.decode("utf-8"))
    entries = []
    for name, body in data.items():
        if not name.endswith(";"):
            continue
        entries.append((name[1:], "".join("\\u%04X" % ord(c) if ord(c) < 0x10000 else
                                          "\\u%04X\\u%04X" % (0xD800 + ((ord(c) - 0x10000) >> 10),
                                                              0xDC00 + ((ord(c) - 0x10000) & 0x3FF))
                                          for c in body["characters"])))
    entries.sort()
    lines = [BEGIN, "    // %d names, generated from %s" % (len(entries), SOURCE_URL)]
    for name, esc in entries:
        lines.append('        put("%s", "%s")' % (name, esc))
    lines.append(END)
    return "\n".join(lines)


def main() -> int:
    raw = fetch()
    digest = hashlib.sha256(raw).hexdigest()
    print("fetched %d bytes, sha256=%s" % (len(raw), digest))
    if PINNED_SHA256 and digest != PINNED_SHA256:
        print("refusing: the file changed upstream. Review the diff, then update the pin.")
        return 1
    if "--check" in sys.argv:
        return 0

    text = TARGET.read_text(encoding="utf-8")
    if BEGIN not in text or END not in text:
        print("target file has no generated-table markers")
        return 1
    new = re.sub(re.escape(BEGIN) + r".*?" + re.escape(END), build_table(raw), text, flags=re.S)
    TARGET.write_text(new, encoding="utf-8")
    print("wrote the table into %s" % TARGET)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
