package com.kotea.companion.domain.impl;

import com.kotea.companion.util.KoTEAElementKind;

import java.util.EnumSet;
import java.util.Set;

/** The Event, Command and News Roots of the project. */
record KoTEARoots(Set<String> eventFqns, Set<String> commandFqns, Set<String> newsFqns) {

    KoTEARoots {
        eventFqns = Set.copyOf(eventFqns);
        commandFqns = Set.copyOf(commandFqns);
        newsFqns = Set.copyOf(newsFqns);
    }

    boolean isEmpty() {
        return eventFqns.isEmpty() && commandFqns.isEmpty() && newsFqns.isEmpty();
    }

    Set<KoTEAElementKind> kindsOf(String fqn) {
        EnumSet<KoTEAElementKind> kinds = EnumSet.noneOf(KoTEAElementKind.class);
        if (eventFqns.contains(fqn)) kinds.add(KoTEAElementKind.EVENT);
        if (commandFqns.contains(fqn)) kinds.add(KoTEAElementKind.COMMAND);
        if (newsFqns.contains(fqn)) kinds.add(KoTEAElementKind.NEWS);
        return kinds;
    }
}
