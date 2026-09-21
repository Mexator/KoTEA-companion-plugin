# index.resolve

Answers one question: "which class does this supertype entry name, and with which type arguments?" It
keeps Kotlin PSI and stub indexes away from the rest of the index.

It avoids Kotlin analysis, which was 87% of scan time: names are read from the file's imports and package,
and typealiases are expanded by reading them in their own file. Analysis runs only when those rules can't
decide, and each time it does, a line is logged.

| File | Role |
|---|---|
| `SupertypeResolver` | The only entry point. `resolve(entry)` returns a `Supertype`; `namesFor(base)` gives the names a subclass can write. |
| `Supertype` | `ClassType(fqn, arguments)` or `Unknown`. |
| `TypeArguments` | The arguments of a `ClassType`, read only when asked for, as a `RoleBinding`. |
| `NameLookupChain` | Kotlin's lookup order, one rule each: nested class, explicit import, same package, star import, `kotlin.*`. |
| `NameLookup` | A rule's answer: `Found(fqn)` or `Unsure(reason)`. |
| `TypeIndexes` | "Does this FQN exist?" and "is it a typealias?", from the stub indexes. |
| `AnalysisFallback` | The slow path: resolving a name by Kotlin analysis. |

## Known limits

- Kotlin's rules are subtler than the import rules in a few cases, such as a nested class of a supertype
  hiding an imported name. Those names are rare as Roots.
- Editing a typealias does not update the files that use it (see ADR-0005, Consequences).
- A typealias that can't be expanded gives `Unknown`: the class is not treated as an Update. This happens
  when the alias names something that isn't a class, when aliases nest more than 8 deep, or when the class
  it names can't be found. Each case logs an "Unresolved typealias" line.
