# Known gaps

Every unsupported construct the engine meets is counted in `CoverageReport` and listed
here. Nothing is dropped silently.

## M1 — HTML tokenizer

The tokenizer covers the states the supported subset needs: data, tag and attribute
states, comments, doctype, CDATA, RCDATA, RAWTEXT and script data. Known gaps:

- **Character reference table is a subset.** About 120 of the specification's 2,231 names
  decode; the rest stay as literal text. The generator exists and is pinned, but the
  specification host was unreachable when the table was written. See ADR 0002.
- **No tree builder yet.** `nextToken()` returns tokens; nothing assembles a document, so
  there are no implied end tags or insertion modes at this point. That is M2.
- **Foreign content** (SVG, MathML) is not tokenised specially. `<![CDATA[` is treated as
  text, which is only correct outside foreign content.
- **Script data** is text up to `</script`; the double-escaped states are not implemented,
  because the supported subset never executes a script.

## Standing gaps (by design, not by accident)

- No JavaScript, ever. Script content is inert text.
- No network. The demo has no `INTERNET` permission.
- No platform renderer: no `android.webkit.*`, no `WebView`, no GeckoView.
- No flexbox, grid, floats, positioning, transforms, animations, video, audio, forms,
  `iframe`, SVG or canvas rendering.
