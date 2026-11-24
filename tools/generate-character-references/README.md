# Character reference generator

The table in `CharacterReferences.kt` between the two markers is generated, never typed.

```bash
python3 tools/generate-character-references/generate.py
```

It downloads `https://html.spec.whatwg.org/entities.json`, prints its sha256, and replaces
the marked block. Pin the digest in `PINNED_SHA256` after the first successful run so a
silent upstream change is refused rather than absorbed.

The data is CC BY 4.0 (WHATWG). See `THIRD_PARTY_NOTICES.md`.

**Status:** the host was unreachable when M1 was written, so the shipped table is a
documented subset. Running this script with network access completes M1's table
requirement. See `docs/decisions/0002-character-references.md`.
