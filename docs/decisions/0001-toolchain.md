# ADR 0001 — Toolchain

- Status: accepted
- Date: 2026-11-24
- Milestone: M0

## Context

The prompt pins major lines and asks for exact patch versions to be verified against the
repositories that serve them before they are written down, then frozen for the project.

## Decision

| Component | Version | Verified against |
|---|---|---|
| JDK | 17 | — (fixed by the prompt) |
| Kotlin | 2.4.21 | Maven Central, `kotlin-gradle-plugin` metadata |
| Android Gradle Plugin | 8.13.2 | Google Maven, `com.android.tools.build:gradle` metadata |
| Gradle | 8.14.8 | services.gradle.org, latest stable on the 8.x line |
| compileSdk / targetSdk | 35 | — (fixed by the prompt) |
| minSdk | 24 | — (fixed by the prompt) |
| androidx.core:core-ktx | 1.19.1 | Google Maven metadata |

Gradle 9.x is the current line but the prompt pins 8.x, so 8.14.8 — the last stable on
that line — is what the wrapper records.

## Consequences

Upgrading any of these is its own commit with a full build and test run, and never in the
middle of a milestone. If a pin turns out not to resolve, the fix is a new ADR, not an
edit in place.
