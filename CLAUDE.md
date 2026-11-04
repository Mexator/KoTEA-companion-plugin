# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

KoTEA Companion Plugin — a JetBrains IDE plugin for Android Studio that adds smart navigation between components of the **KoTEA** presentation-layer architecture (Events, Commands, Update classes, CommandsFlowHandler).

## Build & Development Commands

```bash
# Build the plugin ZIP
./gradlew buildPlugin

# Run Android Studio with the plugin loaded (sandbox)
./gradlew runIde

# Run tests only
./gradlew test
```

Build output: `build/distributions/*.zip`

## Threading
Never block the EDT on a heavy PSI search, and never touch PSI off the EDT without holding a read lock.

## Testing

`./gradlew test` runs an IntelliJ Platform suite (JUnit 4). Tests belong to one of three
layers per `docs/adr/0001-test-model.md`: a pure `KoTEAIndexComputer.derive` unit layer, a
thin Kotlin-PSI layer that asserts only on computed values (`UpdateRecord`s, Root FQN sets,
`isEvent` / `isCommand`), and a feature layer driving `findAllGutters()` and the navigation
actions. Fixtures are hand-written `.kt` files under `src/test/testData/`, one directory per
scenario; KoTEA itself is supplied by the real `ru.tinkoff.kotea:core` artifact, not vendored
stubs.

## Key Technologies
- **IntelliJ Platform SDK** targeting Android Studio (for a version consult `build.gradle.kts`)

## Codestyle
- Avoid excessive comments, use only when absolutely necessary

## Agent skills

## Viewing platform sources

Prefer reading platform sources with `idea` mcp rather than decompiling or unpacking jars.
