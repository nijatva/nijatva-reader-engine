# Nijatva Reader Engine

An HTML + CSS reader renderer written in Kotlin for Android. It parses and lays out
reading material — articles, documentation, help pages, saved HTML — for low-memory
phones, offline.

It is **not a browser**. No JavaScript, no network, no `WebView`, no platform renderer of
any kind, in the engine, the adapter or the demo.

- `engine-core` — pure Kotlin/JVM: tokenizer, tree builder, CSS, cascade, layout, line
  breaking, hit testing, search, coverage. It imports no `android.*`, and a Gradle check
  fails the build if it ever does.
- `engine-android` — the adapter: Canvas renderer, text metrics and shaping, local image
  decode, accessibility bridge.
- `app-demo` — a harness that opens a local HTML file and shows the result together with
  its `CoverageReport`.

## Status

M0 (foundation) only. The tokenizer arrives in M1. Nothing renders yet, and the demo says
so rather than pretending.

## Build

```bash
./gradlew :engine-core:check
./gradlew :app-demo:assembleDebug
```

Licence: Apache-2.0. See `THIRD_PARTY_NOTICES.md` for everything bundled.
