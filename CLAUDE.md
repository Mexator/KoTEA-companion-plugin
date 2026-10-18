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

## Architecture

Navigation is kind-agnostic: the `domain` layer decides what a class is and what a reference does, and one set of
navigation classes serves Events, Commands and News alike.

### 1. Domain
- **`KoTEAClassifier`** — the kind (Event / Command / News) of a class, from the Roots the index collects.
- **`KoTEARoleResolver`** — the role of a reference: Emission (used as a value) or Processing (checked against;
  for Commands only inside a `CommandsFlowHandler` that handles them).

### 2. Navigation (`navigation` package)
- **`KoTEAMarkerProvider`** — the single `lineMarkerProvider`: both gutter icons on a navigable declaration, and on
  each reference the icon leading to the opposite role.
- **`EmissionOrProcessingSearcher`** — finds the references of a class that play a role; results are cached per scope.
- **`NavigationRunner`** — runs a search in the background and shows the result; shared by gutter icons and actions.

### 3. Actions
`GoToEmissionAction` and `GoToProcessingAction` (both extending `BaseGoToAction`, configured with a role) respond to
keyboard shortcuts:
- `Ctrl+Alt+M` → Emission
- `Ctrl+Alt+K` → Processing

### Supporting Infrastructure
- **`ScopeBuilder`** — restricts searches to production sources, avoiding false matches in test files.
- **`NavigableElementResolver`** — resolves a PSI element to the navigable KoTEA class it refers to, if any.
- **`ContextPresentationProvider`** — controls how navigation results appear in the "Choose Target" popup.

### Plugin Registration
`src/main/resources/META-INF/plugin.xml` wires everything together — the line marker provider, the domain services
and the `<keyboard-shortcut>` entries.

### Threading Model
Two invariants: never block the EDT on a heavy PSI search, and never touch PSI off the EDT without holding a read lock. How any given search meets them is an implementation choice — most currently run on a background thread via `ReadAction.nonBlocking(...).inSmartMode(project).submit(executor)` with results handed back through callbacks, but that's the prevailing pattern, not a mandate.

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
