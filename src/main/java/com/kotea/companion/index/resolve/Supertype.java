package com.kotea.companion.index.resolve;

/** What one entry in a class's supertype list names, as far as {@link SupertypeResolver} can tell. */
public sealed interface Supertype {

    Unknown UNKNOWN = new Unknown();

    /** A class, after following typealiases, and the type arguments it receives. */
    record ClassType(String fqn, TypeArguments arguments) implements Supertype {
    }

    /** Not a named type, or a name or typealias that could not be resolved. */
    record Unknown() implements Supertype {
    }
}
