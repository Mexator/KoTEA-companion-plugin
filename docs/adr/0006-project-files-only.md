# Operating on project files only

## The issue

There are KoTEA stores that have some of its features unused (e.g, no Commands or News).
Sometimes developers declare such Updates as `Update<..., Nothing>`, the other times it is 
`Update<..., Unit, ...>` or `Update<..., Any, ...>`.

For such Updates `Any` and `Unit` become Roots. Any usage of these classes starts to display gutter
icons and provides navigation actions.

## The solution

From now on, a class should be **declared** in a project to be considered as a Root. Any class declared 
outside the project production sources cannot be Root.

Moreover, we won't index any `Updates` or do any other indexing in external libraries. Except the
KoTEA library itself, of course.

## Status

Accepted.

## Considered options

- **A blacklist of widespread types** (`Any`, `Unit`, `String`, primitives, collections,
  `Uri`, …). Rejected: too many classes to blacklist.
- **A root should be declared in a module that has KoTEA attached**, to allow using the plugin 
  for KoTEAStore's in external libraries. Rejected: It is hard to resolve deps for external modules
  using standard IDE tools.