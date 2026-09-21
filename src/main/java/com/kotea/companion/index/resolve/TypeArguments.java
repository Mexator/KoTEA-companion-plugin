package com.kotea.companion.index.resolve;

import com.kotea.companion.index.model.RoleBinding;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtTypeProjection;

import java.util.List;

/**
 * The type arguments a supertype receives, taken from the source and from any typealias expansions on the way.
 * Each is read only when asked for, in its own file.
 */
public final class TypeArguments {

    private final SupertypeResolver resolver;
    private final List<KtTypeProjection> projections;
    @Nullable
    private final KtClassOrObject owner;

    TypeArguments(SupertypeResolver resolver, List<KtTypeProjection> projections, @Nullable KtClassOrObject owner) {
        this.resolver = resolver;
        this.projections = projections;
        this.owner = owner;
    }

    /**
     * The argument at {@code index}: a Root, or the index of the owner's own type parameter it names, or
     * {@link RoleBinding#NONE} when there is no such argument or it cannot be read.
     */
    public RoleBinding binding(int index) {
        return index < projections.size() ? resolver.binding(projections.get(index), owner) : RoleBinding.NONE;
    }
}
