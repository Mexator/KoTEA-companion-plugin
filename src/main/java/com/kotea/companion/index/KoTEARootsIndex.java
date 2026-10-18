package com.kotea.companion.index;

import com.kotea.companion.domain.KoTEAClassifier;

import java.util.Set;

/**
 * The types that Feature Updates bind to their Event, Command and News roles.
 * Don't use for domain logic - Use {@link KoTEAClassifier}.
 */
public record KoTEARootsIndex(Set<String> eventRootFqns, Set<String> commandRootFqns, Set<String> newsRootFqns) {

    public static final KoTEARootsIndex EMPTY = new KoTEARootsIndex(Set.of(), Set.of(), Set.of());

    public KoTEARootsIndex {
        eventRootFqns = Set.copyOf(eventRootFqns);
        commandRootFqns = Set.copyOf(commandRootFqns);
        newsRootFqns = Set.copyOf(newsRootFqns);
    }
}
