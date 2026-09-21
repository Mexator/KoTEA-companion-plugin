package com.kotea.companion.index.resolve;

import com.kotea.companion.index.IndexLog;
import com.kotea.companion.index.model.RoleBinding;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.stubindex.KotlinTypeAliasByExpansionShortNameIndex;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtNullableType;
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry;
import org.jetbrains.kotlin.psi.KtTypeAlias;
import org.jetbrains.kotlin.psi.KtTypeElement;
import org.jetbrains.kotlin.psi.KtTypeParameter;
import org.jetbrains.kotlin.psi.KtTypeProjection;
import org.jetbrains.kotlin.psi.KtTypeReference;
import org.jetbrains.kotlin.psi.KtUserType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tells which class a supertype entry names, following typealiases, mostly without Kotlin analysis: names are
 * read from the file's imports and package ({@link NameLookupChain}), and a name those cannot settle is resolved
 * by analysis ({@link AnalysisFallback}). A typealias that cannot be expanded is logged and gives
 * {@link Supertype.Unknown}.
 * <p>
 * A typealias is expanded in its own file. Where its expansion uses one of its own type parameters, the argument
 * given to the alias takes its place: {@code typealias A<E> = DslUpdate<S, E, C, N>} used as {@code A<FooEvent>}
 * passes {@code FooEvent} as {@code DslUpdate}'s second argument.
 */
public final class SupertypeResolver {

    private static final int MAX_ALIAS_DEPTH = 8;

    private final TypeIndexes indexes;
    private final NameLookupChain names;

    public SupertypeResolver(Project project) {
        this.indexes = new TypeIndexes(project);
        this.names = new NameLookupChain(indexes);
    }

    public Supertype resolve(KtSuperTypeListEntry entry) {
        KtUserType type = userType(entry.getTypeReference());
        if (type == null) return Supertype.UNKNOWN;
        return resolve(type, type.getTypeArguments(), PsiTreeUtil.getParentOfType(entry, KtClassOrObject.class), 0);
    }

    /**
     * Names a subclass of {@code baseFqn} can write in its supertype list: the short name and the typealiases that
     * expand to it.
     */
    public static Set<String> namesFor(Project project, GlobalSearchScope scope, String baseFqn) {
        Set<String> names = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        String shortName = baseFqn.substring(baseFqn.lastIndexOf('.') + 1);
        names.add(shortName);
        pending.add(shortName);
        while (!pending.isEmpty()) {
            String name = pending.poll();
            for (KtTypeAlias alias : StubIndex.getElements(
                    KotlinTypeAliasByExpansionShortNameIndex.Helper.getIndexKey(), name, project, scope, KtTypeAlias.class)) {
                String aliasName = alias.getName();
                if (aliasName != null && names.add(aliasName)) pending.add(aliasName);
            }
        }
        return names;
    }

    /** See {@link TypeArguments#binding}. */
    RoleBinding binding(KtTypeProjection projection, @Nullable KtClassOrObject owner) {
        KtUserType type = userType(projection.getTypeReference());
        if (type == null) return RoleBinding.NONE;
        if (owner != null && PsiTreeUtil.isAncestor(owner, projection, true)) {
            int index = typeParameterIndex(type, owner.getTypeParameters());
            if (index >= 0) return new RoleBinding.FromTypeParameter(index);
        }
        return resolve(type, type.getTypeArguments(), null, 0) instanceof Supertype.ClassType classType
                ? RoleBinding.root(classType.fqn()) : RoleBinding.NONE;
    }

    private Supertype resolve(KtUserType type, List<KtTypeProjection> arguments, @Nullable KtClassOrObject owner,
                              int depth) {
        String fqn = fqnOf(type);
        if (fqn == null) return Supertype.UNKNOWN;
        KtTypeAlias alias = indexes.typealias(fqn);
        return alias != null ? expand(alias, fqn, arguments, owner, depth)
                : new Supertype.ClassType(fqn, new TypeArguments(this, arguments, owner));
    }

    @Nullable
    private String fqnOf(KtUserType type) {
        String name = type.getReferencedName();
        if (name == null || !(type.getContainingFile() instanceof KtFile file)) return null;

        if (type.getQualifier() != null) {
            List<String> path = new ArrayList<>();
            for (KtUserType part = type; part != null; part = part.getQualifier()) path.addFirst(part.getReferencedName());
            String rest = String.join(".", path.subList(1, path.size()));
            return names.lookup(type, file, path.getFirst()) instanceof NameLookup.Found head
                    ? head.fqn() + "." + rest : String.join(".", path);
        }
        return switch (names.lookup(type, file, name)) {
            case NameLookup.Found found -> found.fqn();
            case NameLookup.Unsure unsure -> AnalysisFallback.fqnOf(type, file, unsure.reason());
        };
    }

    private Supertype expand(KtTypeAlias alias, String aliasFqn, List<KtTypeProjection> arguments,
                             @Nullable KtClassOrObject owner, int depth) {
        if (depth >= MAX_ALIAS_DEPTH) return unresolvedAlias(aliasFqn, owner, "more than " + MAX_ALIAS_DEPTH + " nested typealiases");
        KtUserType expansion = userType(alias.getTypeReference());
        if (expansion == null) return unresolvedAlias(aliasFqn, owner, "it does not expand to a class");

        List<KtTypeParameter> parameters = alias.getTypeParameters();
        List<KtTypeProjection> substituted = new ArrayList<>();
        for (KtTypeProjection projection : expansion.getTypeArguments()) {
            KtUserType type = userType(projection.getTypeReference());
            int index = type != null ? typeParameterIndex(type, parameters) : -1;
            substituted.add(index >= 0 && index < arguments.size() ? arguments.get(index) : projection);
        }
        Supertype target = resolve(expansion, substituted, owner, depth + 1);
        return target instanceof Supertype.Unknown
                ? unresolvedAlias(aliasFqn, owner, "the class it expands to was not found") : target;
    }

    private static Supertype unresolvedAlias(String aliasFqn, @Nullable KtClassOrObject owner, String reason) {
        IndexLog.debug(() -> "Unresolved typealias " + IndexLog.mask(aliasFqn) + " in "
                + (owner != null ? IndexLog.mask(fqnOf(owner)) : "a type argument") + ": " + reason
                + "; not treated as an Update");
        return Supertype.UNKNOWN;
    }

    @Nullable
    private static String fqnOf(KtClassOrObject classOrObject) {
        FqName fqName = classOrObject.getFqName();
        return fqName != null ? fqName.asString() : null;
    }

    private static int typeParameterIndex(KtUserType type, List<KtTypeParameter> parameters) {
        if (type.getQualifier() != null || !type.getTypeArguments().isEmpty()) return -1;
        for (int i = 0; i < parameters.size(); i++) {
            if (parameters.get(i).getName() != null && parameters.get(i).getName().equals(type.getReferencedName())) {
                return i;
            }
        }
        return -1;
    }

    @Nullable
    private static KtUserType userType(@Nullable KtTypeReference reference) {
        KtTypeElement element = reference != null ? reference.getTypeElement() : null;
        while (element instanceof KtNullableType nullable) element = nullable.getInnerType();
        return element instanceof KtUserType userType ? userType : null;
    }
}
