package com.kotea.companion.index;

import com.intellij.psi.PsiClass;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

/**
 * Provides an ability to check if a given PsiClass
 * <br>
 * - Is a Command (via {@link KoTEARootsIndex#isCommand})
 * <br>
 * - Is an Event (via {@link KoTEARootsIndex#isEvent})
 */
public final class KoTEARootsIndex {

    public static final KoTEARootsIndex EMPTY = new KoTEARootsIndex(Set.of(), Set.of());

    private final Set<String> eventRootFqns;
    private final Set<String> commandRootFqns;

    public KoTEARootsIndex(Set<String> eventRootFqns, Set<String> commandRootFqns) {
        this.eventRootFqns = Set.copyOf(eventRootFqns);
        this.commandRootFqns = Set.copyOf(commandRootFqns);
    }

    public boolean isEvent(@Nullable PsiClass candidate) {
        return isDescendantOfAny(candidate, eventRootFqns);
    }

    public boolean isCommand(@Nullable PsiClass candidate) {
        return isDescendantOfAny(candidate, commandRootFqns);
    }

    private static boolean isDescendantOfAny(@Nullable PsiClass candidate, Set<String> rootFqns) {
        if (candidate == null || rootFqns.isEmpty()) return false;
        // processSupers returns false as soon as the processor does, i.e. as soon as a matching
        // root is hit - so a hit short-circuits the walk instead of visiting the whole chain.
        return !InheritanceUtil.processSupers(candidate, true,
                psiClass -> !rootFqns.contains(psiClass.getQualifiedName()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KoTEARootsIndex other)) return false;
        return eventRootFqns.equals(other.eventRootFqns) && commandRootFqns.equals(other.commandRootFqns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventRootFqns, commandRootFqns);
    }

    @Override
    public String toString() {
        return "KoTEARootsIndex{events=" + eventRootFqns + ", commands=" + commandRootFqns + "}";
    }
}
