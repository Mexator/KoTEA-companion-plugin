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

The IDE run configuration at `.run/Run IDE with Plugin.run.xml` can be used directly from the IDE.

## Architecture

The plugin has three layers that work together for each navigable element type (Event and Command):

### 1. Line Marker Providers (Gutter Icons)
`EventMarkerProvider.java` and `CommandMarkerProvider.java` register with IntelliJ's `lineMarkerProvider` extension point. They detect KoTEA elements in PSI/UAST and attach clickable gutter icons (emission/processing) that trigger the corresponding action.

### 2. Actions
`GoToEmissionAction` and `GoToProcessingAction` (both extending `BaseGoToAction`) handle Events and Commands alike, responding to gutter icon clicks and keyboard shortcuts:
- `Ctrl+Alt+M` → Emission
- `Ctrl+Alt+K` → Processing

### 3. Searchers
One searcher per navigation direction: `EventEmissionSearcher`, `EventProcessingSearcher`, `CommandEmissionSearcher`, `CommandProcessingSearcher`. These are the heaviest classes — they walk PSI/UAST trees to find usages and return `NavigatablePsiElement` targets.

### Supporting Infrastructure
- **`ScopeBuilder`** — restricts searches to production sources, avoiding false matches in test files.
- **`EventUtil`** — resolves a PSI element to the navigable Event `KtClassOrObject` it refers to, if any.
- **`CachedValuesManager`** — caches searcher results; invalidated by file changes automatically.
- **`ContextPresentationProvider`** — controls how navigation results appear in the "Choose Target" popup.

### Plugin Registration
`src/main/resources/META-INF/plugin.xml` wires everything together — the `lineMarkerProvider` extension points and the `<keyboard-shortcut>` entries. Both actions and both line marker providers must be registered here.

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
- **IntelliJ Platform SDK** targeting Android Studio 2025.3.1.1
- **UAST** for language-agnostic syntax tree access (works across Java and Kotlin)
- **PSI** for Kotlin-specific analysis where UAST is insufficient
- Java 21, Gradle Kotlin DSL (`build.gradle.kts`)

## Viewing platform sources

Prefer reading platform sources with `idea` mcp rather than decompiling or unpacking jars.

## Agent skills

### Issue tracker

Issues and specs are tracked as local markdown files under `.scratch/`. See `docs/agents/issue-tracker.md`.

### Domain docs

Single-context layout — `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.