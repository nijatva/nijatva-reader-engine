# Integration

## What a host gets

A document string in, a `LayoutResult` and a `CoverageReport` out. The core never draws
and never touches a platform API; the adapter does the drawing.

## The fallback rule

The engine's job on a hard document is to **fail honestly**: return what it could lay out
and a `CoverageReport` that says what it could not. Deciding to render the document with
some other engine is the **host's** decision, made on its own terms, and that policy must
never appear as a code path in this repository.

A host that wants to fall back should read the report and choose for itself — for example,
render with its existing engine when `ignoredNodes` is above a threshold it picked.

## No network, by construction

There is no network stack and no `INTERNET` permission. Images arrive as local bytes
through an injected resolver that denies everything by default.
