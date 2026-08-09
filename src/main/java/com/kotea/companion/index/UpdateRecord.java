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

    /**
     * The part of this record that determines Update-hierarchy membership (Feature Update status
     * of this class and any others), as opposed to the Event/Command Root fields, which can change
     * without affecting membership. Used to detect whether a rescanned file requires a full
     * {@code computeAll} instead of an exact patch of {@code recordsByFile}.
     */
    public MembershipKey membershipKey() {
        return new MembershipKey(fqn, updateAncestorFqns);
    }

    public record MembershipKey(@Nullable String fqn, Set<String> updateAncestorFqns) {
    }
}
