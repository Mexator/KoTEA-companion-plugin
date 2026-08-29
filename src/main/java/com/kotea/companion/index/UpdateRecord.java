package com.kotea.companion.index;

import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * One project class found to implement {@code Update}, recorded as strings so it can outlive the
 * PSI edit that produced it. {@code fqn} is null for an anonymous {@code object : Update<...> {}}
 * declaration, which has no qualified name and is therefore always a Feature Update.
 */
public record UpdateRecord(
        @Nullable String fqn,
        Set<String> updateAncestorFqns,
        @Nullable String eventRootFqn,
        @Nullable String commandRootFqn) {

    public UpdateRecord {
        updateAncestorFqns = Set.copyOf(updateAncestorFqns);
    }
}
