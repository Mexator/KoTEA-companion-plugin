# KoTEA index design

The index maps each Feature Update to its Event, Command and News Roots. It is built once when the
project opens, and after that it only changes when a Kotlin file in production sources changes.
Most edits touch no Update header, so most of them should cost almost nothing. A full scan happens
only when the project opens.

This answers the open questions in
`0004-background-recompute-must-survive-write-actions.md`. It builds on the index from release
1.3.0, not on the `prototype/no-index` branch, which stays only as a timing baseline.

## Status

Accepted.

## What is stored

- One `UpdateRecord` per class that reaches `ru.tinkoff.kotea.core.Update`, keyed by file.
  Shared bases get a record too. Each record keeps the FQNs of its Update ancestors, and `derive`
  keeps working as it does today.
- It is kept in memory only. Nothing is saved to disk.

A record may depend on other files, through supertypes and type arguments. An earlier draft wanted to forbid
that. This design keeps it, and handles the other files through the ancestor sets described
below.

## What wakes the index

- A VFS bulk listener, for everything that changes on disk: checkout, pull, rebase, create, delete,
  move. This includes files that were never loaded.
- A PSI tree change listener, for typing that is not yet saved. Events with no file are ignored,
  because the VFS listener covers them.
- Both listeners only add `.kt` files to a dirty set. The check "production source, not test, not
  generated" runs later, on the background thread.
- Created or deleted directories are expanded into their files.
- Gradle sync, module-root changes and builds trigger nothing. Updates and Roots never live in
  generated code, and a new module's files arrive through the VFS listener like any other files.
- One exception: while KoTEA's `Update` is not on the classpath, a module-root change starts a full
  scan. On the first open of a fresh clone, the project-open scan can run before Gradle sync adds
  KoTEA, and find nothing; the root change at the end of sync then starts the scan again. Once KoTEA
  is found, root changes do nothing.

## How each change is applied

One background worker drains the dirty set. At most one cycle runs at a time, and a change that
arrives during a cycle schedules exactly one more. Each cycle:

1. **Re-read each dirty file.** A file that was deleted or has left production sources has no
   records now. A file with no records yet is read only if one of its classes names `Update`,
   `DslUpdate`, one of their aliases, or an indexed Update in its supertype list. These names come
   from the stubs, so the check needs no parsing or resolution. Most files changed by a branch
   switch fail it and cost almost nothing.
2. **Compare old and new records.** A record counts as changed when any of its five fields differ.
3. **Changed or removed class X: re-read its descendants.** These are the files whose records list
   X among their ancestors. This takes no search.
4. **New class X (no record before, one now):** the index cannot know who extends X, so it runs the
   scan walk below, starting from X alone. For a new feature this finds nothing within
   milliseconds.
5. Repeat steps 2–4 for the files that were re-read, until nothing changes. Then publish.

| Change | What runs | Budget |
|---|---|---|
| Edit an Update body; add a Concrete Event/Command/News | Re-read the file; record unchanged | ~1 s |
| New feature | Re-read; walk from the new class | 5–10 s |
| Change an Update header | Re-read; re-read its descendants | 5–10 s |
| Rename or move an Update or Root | The edited files are re-read; the old class counts as removed | 5–10 s |
| Delete a feature or folder; file leaves production | Records dropped; descendants re-read | 5–10 s |
| Shared base change | Its descendants re-read, in batches | 1–3 min |
| Typealias edited in its own file | Nothing for the files that use it (see Consequences) | — |
| Branch switch, pull, rebase | The rows above, file by file | 15 s – 1 min |
| Gradle sync, build, root changes | Nothing, unless KoTEA is not on the classpath yet: then a full scan | immediate |
| Project open | Full scan | ~2 min |

The budgets come from the maintainer. Being out of date for a short while is acceptable, as long
as the index fixes itself without anyone doing anything.

## The scan survives write actions

A write action cancels a read action that is running, and the read action then restarts from the
beginning. No read-action API avoids this, so the only lever is how much work each read action
holds. The scan is the plugin's own breadth-first walk over the Kotlin super-class stub index:

- The walk starts from `Update` and `DslUpdate`, found by FQN. No other library bases are in scope.
  The walk also adds the names of typealiases that expand to each base.
- It checks candidates in batches of about 200–300 ms. Each batch is its own cancellable read
  action in smart mode.
- The queue, the candidates already checked and the records found so far are kept outside the
  read action, keyed by file and FQN. A write loses at most one batch.
- Files that change during the scan are added to the dirty set, and they are handled by the
  per-file cycle once the scan is done.

The project-open scan and the walk from a new class (step 4) use the same code. Re-reading
descendants in step 3 is split into batches in the same way.

## Records are built without Kotlin analysis

A record is built from one supertype entry and the record of the parent it names. KoTEA's `Update`
and `DslUpdate` count as parents with Event, Command and News at type arguments 2–4. A shared base's
record stores which of its own type parameters its subclasses bind to each role, and the child
reads its type arguments at those positions. Ancestors are the parent plus the parent's ancestors.
The walk always records a parent before its children. When a file is read, the parent comes from
the index, or from the same file.

Type names are turned into FQNs from the file's imports, its package, and the Kotlin class and
typealias FQN indexes, in Kotlin's order: explicit imports, same package, star imports, then
`kotlin.*`. A typealias is expanded the same way: its right-hand side is read in the alias's own
file, and where it uses one of the alias's type parameters, the argument given to the alias takes
its place. This covers the common per-feature alias
(`typealias FooUpdate = Update<FooState, FooEvent, FooCommand, FooNews>`). A class nested in an
enclosing class wins over imports and is taken directly. A name that a class nested in the class
itself could shadow, or that two star imports both provide, is resolved by Kotlin analysis
instead, and this is logged. A class whose supertype alias cannot be expanded at all — it names
something that is not a class, aliases nest more than 8 deep, or the class it names cannot be found —
is not treated as an Update, and this is logged too.

Kotlin analysis was 87% of the scan's work: the first resolve in a module is slow, up to 14 s for
one class on the work project. Reading imports avoids it for nearly all classes.

## Diagnostics

The index logs at debug level, so logging is off by default. It is turned on through the IDE's
debug log settings. There is no separate log file. Class names in the log have their packages
masked to initials: `com.example.SomeEvent` becomes `c.e.SomeEvent`. The maintainer checks the
design by hand on the work project, one working day, noting the time whenever a gutter icon looks
wrong. The exact log lines and the checklist belong to the implementation spec.

## Considered options

- **Keep index inputs file-local.** Rejected: the cross-file cases are rare, and the stored
  ancestor sets already find the affected files, so full rescans can go without re-establishing
  resolution at query time.
- **No index: search on demand (`prototype/no-index`).** Rejected: it has the same cold cost as
  the index, and it pays a full search every time the tracker moves, while the index pays
  0–5 ms.
- **Full rescan on a Gradle sync or a module-root change.** Rejected: in the field log these
  triggers caused all the long rebuilds, and they almost never change an Update.
- **Full rescan when a shared base changes.** Rejected: its descendants are known from the index,
  so re-reading only them fits inside the budget.
- **Saving the index to disk.** Rejected: the cold start fits its 2 min budget without it.
- **A "Verify index" action.** Rejected as not needed for a single manual check.
- **Track typealiases: fingerprint every alias, and on a change re-read the files that mention
  its name.** Built and then removed. The fingerprint covered every alias in production code, and
  the word search for a common alias name matched thousands of unrelated files. On the work
  project, branch switches re-read ~5,500 files and took 10–12 min, to change 2 records.

## Consequences

- `computeIncrementalPatch` loses its `MEMBERSHIP_CHANGED` and `NON_EMPTY_FILE_DELETED` fallbacks
  to a full scan. The `ModuleRootListener` subscription stays only for the case where KoTEA is not
  on the classpath yet, and no Gradle sync listener is added.
- The four gaps in `main`'s listeners get closed: directory events, files leaving production,
  generated sources, and the scope check on the EDT.
- `ClassInheritorsSearch` is no longer used for the scan. That also stops walking test and library
  hierarchies and building light classes.
- A Java class between `DslUpdate` and a Feature Update would be missed. Update classes are
  Kotlin-only in practice, so this is accepted.
- Typealiases are not tracked. Reading a file still resolves the aliases it uses, and the scan
  still finds Updates declared through an alias. But editing an alias does not re-read the files
  that use it: they stay stale until they change themselves or the project is reopened. The work
  project has two relevant aliases that almost never change, so this is accepted.
- A new class that extends an indexed Update through an alias of that Update is not found by the
  supertype-name check in step 1. This is the same kind of alias case as above, and is accepted.
- Since a child's record is built from its parent's record, a file read earlier in a cycle is
  read again when a parent changes. A shared base whose type-parameter order changes now ripples
  to its descendants, because the order is part of its record.
- The import rules could pick the wrong class where Kotlin's own rules are subtler, such as a
  nested class of a supertype that shadows an imported name. Such names are rare as Roots.
- A class whose supertype alias cannot be expanded gets no gutter icons. Earlier drafts fell back
  to light classes here; that was removed as a second way of resolving that almost never applies.
  The "Unresolved typealias" log line shows when it happens.

## Evidence

- `kotea-index-debug.log` (old index, one working day): 12 full rebuilds, all caused by root
  changes except for two project opens and one membership fallback. One run needed 13 attempts
  and took 36 min during a terminal build. Per-file updates took 0–5 ms.
- `kotea.log` (no-index prototype, ~935 Feature Updates): a cold search took 28–52 s, a warm one
  0.17 s plus about 2 s of record building.
- `filtered_companion.log` (this design, two and a half working days): the project-open scan
  found the same 938 Updates both times. Three branch switches took 10–12 min each because of
  typealias tracking, which led to removing it.
- Scan step timings (`1.3.1-incremental-profiling`, work project): of ~100 s of scan work,
  resolving supertypes took 89 s; light classes, the inheritance check and ancestors together took
  3 s, and roots from generics 8.5 s.
- Research on the `research/change-signals` and `research/restartable-full-scan` branches, with
  the platform sources cited there.
