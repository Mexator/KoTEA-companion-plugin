package com.kotea.companion.index.resolve;

/** The FQN a simple name in a file stands for, or why the import rules cannot tell. */
sealed interface NameLookup {

    record Found(String fqn) implements NameLookup {
    }

    record Unsure(String reason) implements NameLookup {
    }
}
