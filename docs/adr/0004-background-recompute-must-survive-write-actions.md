# The background recompute must survive write actions

Both the index-based plugin and the `prototype/no-index` branch run their project-wide Update search
inside `ReadAction.nonBlocking(...)`. The platform cancels a non-blocking read action whenever any
write action happens, then restarts it from the beginning once the write is done. A search that takes
tens of seconds on a cold project is therefore restarted by every edit, sync step or file reload that
lands while it runs.

## Status

Accepted. The open questions below are answered in `0005-kotea-index-design.md`.

## Evidence

The old index-based version logged one `FULL_SCAN_SEARCH` line per attempt and one `FULL_SCAN_DONE` per
finished run. Over one long session (`kotea-index-debug.log`):

- 12 full scans finished, after 1 to 13 attempts each. Nine attempts of about 3 s ended as a 164 s run;
  thirteen attempts ended as a 2190 s run.
- A cold search took 30 to 84 s. A warm one took 0.25 to 6 s.
- Single-file edits were folded in by the incremental path in 0 to 5 ms (315 publishes).

The prototype (`kotea.log`, four runs) logs only its last attempt, so its restarts are not directly
visible. It shows the same shape: a cold search of 28 to 52 s, a warm search of 0.17 s plus about 2 s
of record building, and wall time from trigger to publish of 20 s to 4.5 min against a last attempt of
2 to 95 s. The gap is also explained by waiting for smart mode after a Gradle sync; the logs cannot tell
the two causes apart.

Not established: whether appends to an open `idea.log` contribute writes that cancel the read. The
prototype's PSI-change log lines do not record which file changed.

## Considered options

- **A plain blocking read action, never restarted.** Rejected: pending writes wait for it, so typing
  would freeze for as long as a cold search takes, 30 to 50 s or more.
- **Keep the single non-blocking read action.** Rejected: it is what produces the 1 to 13 attempts per
  run above.
- **Cancel nothing, but recompute less.** Necessary regardless, and covered by 0005, but insufficient:
  a cold start and a structure change still need one full search.

## Decision

The recompute is to be structured so that a cancelled attempt loses little work:

- Build the `UpdateRecord`s in small read actions, one class or a small batch at a time, keeping the
  results already computed across restarts.
- Treat the inheritor search as the part that cannot be split, and keep the number of times it is
  restarted low. How is undecided, see below.
- A newer request still never cancels a running computation; at most one run exists at a time and a
  change during a run schedules exactly one more. This already holds in the prototype.
- React to Kotlin-file PSI changes only. Events from other files, such as logs, must not trigger checks.

## What this does not decide

Both points are now settled by `0005-kotea-index-design.md`: the inheritor search is replaced by a
batched walk over the Kotlin super-class index, so it no longer needs to be kept whole, and the index
stays.

- **How to stop the search restarting.** Candidates are running it only when the source tracker moved
  by a structural change (a class header or typealias), warming it once after sync, or storing the
  result of the previous search and reusing it while the tracker is unchanged. Choosing needs per-attempt
  numbers from a real project.
- **Whether the no-index approach replaces the index.** The prototype has the same cold cost as the
  index but pays a full search on every tracker move, where the index paid 0 to 5 ms. That decision
  is settled in 0005.

## Consequences

- Per-attempt logging is required before any tuning: the start of each attempt, whether it was
  cancelled, and how long it waited for smart mode. Without it the next log cannot separate restarts
  from smart-mode waits.
- One measurement round trip to the work machine is needed to compare `idea.log` open and closed.
