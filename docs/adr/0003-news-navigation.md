# News gets Event-shaped detection with inverted file-name polarity

Issue #2 asked for the same navigation actions and icons on News as already exist for Events
and Commands. Command's detection is structural: `CommandUtil.isCommandsHandler` verifies the
enclosing class actually implements `CommandsFlowHandler<ThisCommand>` before offering a
Processing marker. News has no equivalent interface.

A News item is *constructed* inside the Feature Update (returned via `Next.news`, the same way
a Command is) and *processed* anywhere — no fixed naming convention. 
So for News, a `*Update*`-named file is Emission, and everything else defaults to Processing.

## Status

Accepted.

## Considered options

- **Command-style structural verification for News Processing** (e.g. checking the enclosing
  lambda's parameter type). Rejected: there is no interface like `CommandsFlowHandler` to
  anchor the check on, so this would add real complexity for uncertain payoff, and Event's
  file-heuristic-only model already sets the precedent that this plugin does not require
  structural proof for every role.

## Consequences

- `KoTEAElementKind` gains `NEWS`; `RoleResolver.roleOfFileName` now switches per-kind instead
  of sharing one "processing token" helper, since News's polarity differs from Event's and
  Command's.
- `NewsUtil` was rewritten from its original UAST/`PsiClass`-based shape (mirroring
  `CommandUtil`) to a `KtClassOrObject`-based shape mirroring `EventUtil`, including a
  `tryResolveToClass` — `NewsMarkerProvider`, `NewsEmissionSearcher`, and
  `NewsProcessingSearcher` are near-identical copies of their Event counterparts.
- Same false-positive risk as Event's heuristic already carries: any non-`*Update*` reference
  to a News class counts as Processing, even if it isn't actually inside a `newsCollector`
  lambda (e.g. a stray reference in a mapper or test helper). This is inherited, not new.
