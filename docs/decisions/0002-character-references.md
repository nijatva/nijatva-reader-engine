# ADR 0002 — Character references

- Status: accepted
- Date: 2026-11-24
- Milestone: M1

## Context

The specification defines 2,231 named character references, and the milestone requires the
table to be generated, versioned and tested rather than typed by hand. The authoritative
source is `https://html.spec.whatwg.org/entities.json` (WHATWG, CC BY 4.0).

## Decision

The **mechanism** is complete: longest-match scanning, the semicolon rules, the
"ambiguous ampersand" rule inside attributes, numeric references with the Windows-1252
remapping of `0x80`–`0x9F`, and rejection of null, surrogate and out-of-range code points.

The **table** shipped in `CharacterReferences.kt` is a documented subset of roughly 120
names — the ones that appear in real articles — because the specification host was not
reachable from the build environment when this file was written:

```
html.spec.whatwg.org/entities.json      HTTP 502 (proxy refused)
raw.githubusercontent.com/whatwg/html   HTTP 404 (no such path)
```

Guessing the remaining 2,100 names from memory would be worse than shipping a small table
honestly, so that is what this does.

`tools/generate-character-references/` contains the generator. It downloads the file,
checks it against the pinned sha256, and writes the full table into
`CharacterReferences.kt` between markers. Running it with network access is the whole of
the remaining work, and it is recorded in `docs/known-gaps.md`.

## Consequences

- Names outside the subset are **not** decoded; they stay as literal text. That is a
  visible, countable gap, not silent corruption.
- `CharacterReferences.knownNameCount` is asserted to be at least 100, so the table cannot
  quietly shrink.
- The generator's output is checked in, so the build itself never needs network access.
