# Class role is decided by the nearest Root

The plugin classifies a concrete class as a Concrete Event or Concrete Command by checking
inheritance from an Event Root or Command Root. Legacy pre-KoTEA hierarchies have News Root
that extends Command Root, so some Concrete News may also implement a Command Root and is
wrongly given Command gutter icons and misfiring navigation (issue #3). 

A concrete class's role is now decided by its **Nearest Root**: of the Event Root, Command
Root, and News Root among its supertypes, the one at the shortest inheritance distance
(superclass or interface). Mere descent from a Root no longer classifies a class when a
different-kinded Root sits nearer. To support this the index now reads all three of
`ru.tinkoff.kotea.core.Update`'s `Event`, `Command`, and `News` type parameters.

## Status

Accepted.

## Tie-breaking

- Roots of different kinds at equal distance resolve by fixed priority
  **News > Command > Event**.
- A class whose Nearest Root serves more than one role (for example a News Root in one feature
  and a Command Root in another) has no single role and is not Navigable. A debug log should
  be written to help diagnose this.
- A class with no Root among its supertypes is not Navigable.

## Considered options

- **Remove News descendants from the existing Command-Roots** Rejected: it
  fixes only issue #3's exact shape. The News plumbing will later allow us to implement real News navigation actions 
  later.

## Consequences

- `UpdateRecord`, `KoTEAIndexComputer.derive`, and `KoTEARootsIndex` all gain a News
  dimension; the `KoTEARootsIndex` membership check changes from "any ancestor is a Root" to a
  distance-aware walk.
- No behavior changes for features whose Event, Command, and News hierarchies are independent
  — which is every current test fixture.
- Equal-distance ambiguity (a class directly under two Roots via interfaces) is possible in
  principle; it is resolved by the fixed priority, or treated as roleless when one Root
  carries multiple bindings. This is documented as a known limitation, not designed around.
- Sets up the planned News navigation actions, which can consume `isNews` and Concrete News
  directly.
