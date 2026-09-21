package com.kotea.companion.index.model;

import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * What the index knows about one class that reaches KoTEA's {@code Update}. Built from the class's PSI, but
 * holds only names, so it outlives the PSI.
 * <p>
 * A Feature Update binds its roles to Roots. A generic shared base leaves them to its subclasses through its
 * type parameters, and its subclasses' records are built from it with {@link #subclass}.
 *
 * @param fqn                fully qualified name of the class, null for an anonymous class
 * @param updateAncestorFqns the class's ancestors that are Updates themselves, except KoTEA's {@code Update}
 */
public record UpdateRecord(
        @Nullable String fqn,
        Set<String> updateAncestorFqns,
        RoleBinding event,
        RoleBinding command,
        RoleBinding news) {

    public UpdateRecord {
        updateAncestorFqns = Set.copyOf(updateAncestorFqns);
        Objects.requireNonNull(event);
        Objects.requireNonNull(command);
        Objects.requireNonNull(news);
    }

    public static UpdateRecord withRoots(@Nullable String fqn, Set<String> updateAncestorFqns,
                                         @Nullable String eventRootFqn, @Nullable String commandRootFqn,
                                         @Nullable String newsRootFqn) {
        return new UpdateRecord(fqn, updateAncestorFqns,
                RoleBinding.root(eventRootFqn), RoleBinding.root(commandRootFqn), RoleBinding.root(newsRootFqn));
    }

    /**
     * The record of class {@code fqn} that extends this one.
     *
     * @param arguments the type arguments the subclass passes to this class, by position
     */
    public UpdateRecord subclass(@Nullable String fqn, IntFunction<RoleBinding> arguments) {
        Set<String> ancestors = new HashSet<>(updateAncestorFqns);
        if (this.fqn != null && !KoTEABases.UPDATE_FQN.equals(this.fqn)) ancestors.add(this.fqn);
        return new UpdateRecord(fqn, ancestors,
                event.inSubclass(arguments), command.inSubclass(arguments), news.inSubclass(arguments));
    }

    @Nullable
    public String eventRootFqn() {
        return event.rootFqn();
    }

    @Nullable
    public String commandRootFqn() {
        return command.rootFqn();
    }

    @Nullable
    public String newsRootFqn() {
        return news.rootFqn();
    }
}
