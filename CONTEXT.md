# KoTEA Companion Plugin

An IDE plugin that navigates between the parts of a KoTEA feature. Its whole job is deciding
which classes are navigable and, for a navigable class, which references count as going one
way (emission) versus the other (processing).

## Language

### The KoTEA hierarchy

**Update interface**:
KoTEA's `ru.tinkoff.kotea.core.Update`, whose four type parameters — `<State, Event, Command,
News>` — declare what a feature's Events and Commands are. Everything the plugin knows about a
project is derived from this declaration. Note that features usually reach it through KoTEA's
`DslUpdate` rather than implementing it directly.

**Feature Update**:
A project's own class implementing the Update interface for a single feature — and, where a
project stacks a shared base of its own beneath KoTEA's, the most-derived such class. Only
Feature Updates have their type arguments read.
_Avoid_: Concrete Update — a non-abstract Update with subclasses is not a Feature Update, so
"concrete" would say the opposite of the rule. "Concrete" means not-abstract everywhere else
in this glossary.

**Event Root**:
The class bound to a Feature Update's `Event` type parameter. Exactly one per feature; it is
the top of that feature's Event hierarchy, not merely an ancestor within it. An intermediate
abstract class partway down the hierarchy is not an Event Root.
_Avoid_: Base Event, event base class, root event class

**Command Root**:
The same, for the `Command` type parameter.
_Avoid_: Base Command, command base class

**Concrete Event**:
A descendant of an Event Root that is neither an interface nor abstract — so Kotlin `object`s,
`data class`es, and `open class`es qualify, while `sealed` and `abstract` intermediates do not.
This is the unit the plugin navigates to and from.
_Avoid_: leaf event, event case, event variant, event class

**Concrete Command**:
The same, for a Command Root.
_Avoid_: leaf command, command case, command class

### Navigation

**Navigable**:
Of a class: the plugin offers gutter icons and keyboard navigation for it. Concrete Events and
Concrete Commands are navigable; Event Roots, Command Roots, and abstract intermediates are
not. Non-navigability is deliberate — a base class would resolve to many targets at once, and
the plugin exists to avoid that ambiguity.

**Emission**:
A site where a Concrete Event or Concrete Command is constructed and dispatched — the place it
comes into being.
_Avoid_: dispatch, usage, reference

**Processing**:
A site where a Concrete Event or Concrete Command is consumed — an Event inside its Feature
Update, a Command inside its CommandsFlowHandler.
_Avoid_: handling, consumption

Emission and Processing are roles, not locations. The plugin currently *approximates* them by
file name (a reference in a file named `*Update*` is treated as Event processing, `*Handler*`
as Command processing, anything else as emission), which is an implementation shortcut and not
what the terms mean.
