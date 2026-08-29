package com.kotea.companion.index;

import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Short summary about a specific Update interface implementation. The summary is extracted from classes' Psi.
 * Psi element may be destroyed, but this record is safe to persist.
 *
 * @param fqn fully qualified name of the update class, null for anonymous class
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
