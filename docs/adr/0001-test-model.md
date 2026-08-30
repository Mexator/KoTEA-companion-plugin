# Test model for the plugin

Currently, the repository has no automated tests at all. The plugin structure is subject to change. 
For example, UAST may be removed entirely, because it seems to be slower than working with Kotlin PSI directly.

We want a suite that does not have to be rewritten when the implementation changes. 

All tests will belong to one of three layers:
- Pure unit test layer. The good candidate is `KoTEAIndexComputer.derive` (the Feature-Update filter and Event/Command Root union, with 
  no PSI or `Project`), 
- Deliberately thin Kotlin-PSI layer that asserts only on computed values (`UpdateRecord`s, Root FQN sets, `isEvent`/`isCommand`
  booleans) and never on UAST or PSI node types
- A feature layer (`myFixture.findAllGutters()` and the navigation actions) that is the regression contract for
  what a user actually sees. 
 
For all layers KoTEA is supplied to fixtures as the real `ru.tinkoff.kotea:core` artifact resolved from Maven, not as 
stub sources copied into the repo.

## Status

Accepted.

## Considered options

- **Vendored stub sources** for `Update` / `DslUpdate` / `CommandsFlowHandler` in test
  resources — rejected: it puts copies of a third party's API in our VCS and lets the stubs
  drift from the real signatures, for no benefit over depending on the published artifact.
- **Testing primarily through the UAST/PSI walking code** (asserting on `UElement` /
  `PsiClass` structure) — rejected: it is exactly the code most likely to be replaced, so
  those tests would be thrown away with it.

## Consequences

- Fixture setup depends on Maven resolution of `ru.tinkoff.kotea:core`
