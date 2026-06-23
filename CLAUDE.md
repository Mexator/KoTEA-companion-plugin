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
`EventLineMarkerProvider.java` and `CommandLineMarkerProvider.java` register with IntelliJ's `lineMarkerProvider` extension point. They detect KoTEA elements in PSI/UAST and attach clickable gutter icons (emission/processing) that trigger the corresponding action.

### 2. Actions
`EventEmissionAction`, `EventProcessingAction`, `CommandEmissionAction`, `CommandProcessingAction` (all extending `BaseAction`) respond to gutter icon clicks and keyboard shortcuts:
- `Ctrl+Alt+E` → Event Emission
- `Ctrl+Alt+R` → Event Processing
- `Ctrl+Alt+M` → Command Emission
- `Ctrl+Alt+K` → Command Processing

### 3. Searchers
One searcher per navigation direction: `EventEmissionSearcher`, `EventProcessingSearcher`, `CommandEmissionSearcher`, `CommandProcessingSearcher`. These are the heaviest classes — they walk PSI/UAST trees to find usages and return `NavigatablePsiElement` targets.

### Supporting Infrastructure
- **`ScopeBuilder`** — restricts searches to production sources, avoiding false matches in test files.
- **`KtEventUtil`** — handles Kotlin-specific PSI quirks (sealed classes, companion objects) that UAST doesn't fully abstract.
- **`CachedValuesManager`** — caches searcher results; invalidated by file changes automatically.
- **`ContextPresentationProvider`** — controls how navigation results appear in the "Choose Target" popup.

### Plugin Registration
`src/main/resources/META-INF/plugin.xml` wires everything together — extension points for `lineMarkerProvider`, `intentionAction`, and keyboard shortcut `<keymap>` entries. All four actions and both line marker providers must be registered here.

### Threading Model
Heavy PSI searches run inside `ReadAction.nonBlocking(...).inSmartMode(project).submit(executor)` to avoid blocking the EDT. Results are collected and passed back via callbacks. Never call PSI APIs from the EDT without a `ReadAction`.

## Key Technologies
- **IntelliJ Platform SDK** targeting Android Studio 2025.3.1.1
- **UAST** for language-agnostic syntax tree access (works across Java and Kotlin)
- **PSI** for Kotlin-specific analysis where UAST is insufficient
- Java 21, Gradle Kotlin DSL (`build.gradle.kts`)