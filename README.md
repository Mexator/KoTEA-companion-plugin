# KoTEA Companion Plugin

**KoTEA-companion-plugin** is a native plugin for Android Studio (based on IDEA 2025.3 and higher). The tool is designed to simplify navigation and improve Developer Experience in projects using the KoTEA architectural pattern.

## The Problem

The KoTEA library provides significant advantages when building the presentation layer, but its strictly decoupled architecture makes the code difficult to navigate. Business logic is split across different files:
* The central `Update` entity (similar to a Reducer) processes events and dispatches commands.
* `Event` and `Command` classes are scattered throughout the project.

Built-in IDE tools are insufficient to quickly check where a specific event was sent from or where a specific command is processed.

## Key Features

The plugin creates a contextual bridge between the points of generation (Emission) and execution (Processing) of events and commands.

For each `Event` and `Command` class, 2 smart actions are available:

1.  **Go to emission:** Instantly opens the exact location where the event or command is dispatched (e.g., constructor calls). The search defaults to finding usages only in the `main` sourceSet, safely excluding test files for maximum relevance.
2.  **Go to processing:** Navigates to the point of actual execution. For events, this is the `Update` class (specifically, `when` branches or private functions with a parameter of the required type). For commands, this is the nearest `CommandsFlowHandler` parameterized with the required type.

### Interactive Gutter Icons
The plugin automatically places convenient markers in the editor's gutter:
* **On declarations:** At the declaration site of an `Event` or `Command` both icons.
* **On generation:** At constructor call sites, an icon appears allowing a quick jump to the processing logic (Go to processing).
* **Inside Reducers:** Where an `Event` is used inside the `Update` class, a marker is displayed to quickly jump to the emission point (Go to emission).
* **Inside Handlers:** In the `handle` method of a `CommandsFlowHandler` (specifically on `filterIsInstance` constructs), an icon appears to navigate to the command's invocation points.

*Tip: Hotkeys have also been added for all references to Event or Command types for blazing-fast navigation.*

## Under the Hood

* **Asynchronous Execution:** All heavy project tree searches are executed in a pool of background threads (`executeOnPooledThread`), ensuring the IDE interface no freezes.
* **Smart Parsing (UAST & PSI):** The plugin avoids naive text searching. Instead, the analyzer relies on Abstract Syntax Trees, correctly resolving generics and handling Kotlin-specific features (like `object` singletons that lack constructor calls).
* **Smart Caching:** By utilizing `CachedValuesManager`, navigation results are kept in memory. Repeated transitions happen instantly.
* **Smart Scope:** The search prioritizes the local Gradle module and safely ignores test directories.

## Telemetry & Privacy

The plugin includes anonymous product analytics powered by PostHog to track UX metrics (e.g., the usage ratio between "Emission" and "Processing" actions).
* Only depersonalized interaction events are collected.
* Network requests are handled by a custom lightweight HttpClient.

## Installation

1. Download the latest `.zip` file from the Releases page.
2. Open your IDE (Android Studio or IntelliJ IDEA).
3. Navigate to **Settings / Preferences** > **Plugins**.
4. Click the gear icon (⚙️) at the top and select **Install Plugin from Disk...**
5. Choose the downloaded `.zip` archive and click **OK**.
6. Restart your IDE to activate the plugin.
