package com.kotea.companion.index.model;

import org.jetbrains.annotations.Nullable;

import java.util.function.IntFunction;

/**
 * How an Update class fills one role (Event, Command or News): with a Root, or by leaving the role to its
 * subclasses through one of its own type parameters, as a generic shared base does.
 */
public sealed interface RoleBinding {

    None NONE = new None();

    String NOTHING_FQN = "kotlin.Nothing";

    record Root(String fqn) implements RoleBinding {
    }

    /** Subclasses bind the role through the type argument they pass at {@code index}. */
    record FromTypeParameter(int index) implements RoleBinding {
    }

    /** No Root: the role is bound to {@code Nothing}, or its type argument could not be read. */
    record None() implements RoleBinding {
    }

    /** {@code Nothing} binds no Root: a feature that emits no News binds it to {@code News}. */
    static RoleBinding root(@Nullable String fqn) {
        return fqn == null || NOTHING_FQN.equals(fqn) ? NONE : new Root(fqn);
    }

    /** This binding as seen by a subclass that passes {@code arguments} to the class that holds it. */
    default RoleBinding inSubclass(IntFunction<RoleBinding> arguments) {
        return this instanceof FromTypeParameter parameter ? arguments.apply(parameter.index()) : this;
    }

    @Nullable
    default String rootFqn() {
        return this instanceof Root root ? root.fqn() : null;
    }
}
