package com.kotea.companion.index.model;

import org.jetbrains.annotations.Nullable;

import java.util.function.IntFunction;

/**
 * How an Update class fills one role (Event, Command or News): with a Root, or by leaving the role to its
 * subclasses through one of its own type parameters, as a generic shared base does.
 */
public sealed interface RoleBinding {

    None NONE = new None();

    record Root(String fqn) implements RoleBinding {
    }

    /** Subclasses bind the role through the type argument they pass at {@code index}. */
    record FromTypeParameter(int index) implements RoleBinding {
    }

    /** No Root: the type argument could not be read. */
    record None() implements RoleBinding {
    }

    /** {@code fqn} as written, {@code Nothing} included: which types are real Roots is up to the classifier. */
    static RoleBinding root(@Nullable String fqn) {
        return fqn == null ? NONE : new Root(fqn);
    }

    /** This binding as seen by a subclass that passes {@code arguments} to the class that holds it. */
    default RoleBinding inSubclass(IntFunction<RoleBinding> arguments) {
        return this instanceof FromTypeParameter(int index) ? arguments.apply(index) : this;
    }

    @Nullable
    default String rootFqn() {
        return this instanceof Root(String fqn) ? fqn : null;
    }
}
