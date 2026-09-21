# index.background

Runs the index's long read-only work in the background without blocking writes. Knows nothing about
KoTEA itself. Log lines go to the index's debug log.

A write action cancels any read action that is running, and the platform then starts it again from the
beginning. Long work in one read action can therefore restart many times and never finish
(`docs/adr/0004-background-recompute-must-survive-write-actions.md`). So the work can be split into short
batches of about 250 ms, each in its own read action. A write cancels only the batch that is running; that
batch is retried, and the batches already done are kept.

Batching is off by default and is turned on by the `kotea.index.batched.reads` Registry key, read once when
the project opens. When off, each read gets no deadline, so the whole work runs as one batch.

| File | Role |
|---|---|
| `BatchReader` | Runs one batch per `read(deadline -> …)` in smart mode, retries it after a write, and adds up timing `Stats`. Logs cancelled batches. |

## Using it

The caller owns the loop:

```java
while (!work.isDone()) work.apply(reader.read(work::compute));
```

- `compute(deadline)` runs inside the read action. It must not change any state, because it may be
  cancelled and run again. It stops at the deadline and returns what it found.
- `apply` runs outside the read action and stores what `compute` found.
- Keep only paths, names and plain data between batches, never PSI: PSI can become invalid after a write.
