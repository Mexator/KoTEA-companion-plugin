# index

Finds every Feature Update in the project and records the types it binds to Event, Command and News, as written.
The index decides nothing: which of those types are Roots, and which kind a class is, is up to
`com.kotea.companion.domain.KoTEAClassifier`, the only reader of the index outside this package.
The design and its budgets are in `docs/adr/0005-kotea-index-design.md`.

## How it works

1. **Project open.** `UpdateScan` walks down from KoTEA's `Update` and `DslUpdate` through the Kotlin
   super-class index and builds one `UpdateRecord` per Update class.
2. **A file changes.** `ChangeCollector` notes the file. The worker in `KoTEAIndexService` then:
   - reads the changed files again, skipping files that name no Update in a supertype list;
   - asks `UpdateHierarchy` which records changed;
   - reads the files of their descendants again, and walks from classes that just became Updates;
   - repeats until nothing changes, then publishes a new `KoTEARootsIndex`.
3. **Navigation** asks `KoTEAClassifier`, which reads the latest `KoTEARootsIndex`. It never waits for the index.

## Files

| File | Role |
|---|---|
| `KoTEAIndexService` | Entry point. Owns the worker thread, runs the scan and the change cycle, publishes the result. |
| `KoTEARootsIndex` | The result: the types Feature Updates bind to each role. |
| `KoTEAIndexComputer` | Builds records from a file or a class, and `derive`s the bindings from all records. |
| `UpdateScan` | The walk down the Update hierarchy, used on project open and from new Update classes. |
| `ChangedFileReads` | Reads changed files, skipping files that name no Update in a supertype list. |
| `ChangeCollector` | Listens to edits and disk changes and remembers which Kotlin files changed. |
| `IndexLog`, `ScanTimings` | Debug log, with package names shortened so a log can leave the work machine. |

## Rules to keep

- PSI is touched only inside a read action. Nothing that crosses batches holds PSI — only paths and FQNs.
- The EDT never waits for the index.
- At most one scan or cycle runs at a time. A change during a run causes exactly one more run.
