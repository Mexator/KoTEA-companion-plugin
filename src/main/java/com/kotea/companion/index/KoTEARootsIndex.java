package com.kotea.companion.index;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Provides an ability to check if a given PsiClass
 * <br>
 * - Is a Command (via {@link KoTEARootsIndex#isCommand})
 * <br>
 * - Is an Event (via {@link KoTEARootsIndex#isEvent})
 * <br>
 * - Is a News (via {@link KoTEARootsIndex#isNews})
 * <br>
 * Each class may only have a single role.
 */
public final class KoTEARootsIndex {

    private static final Logger LOG = Logger.getInstance(KoTEARootsIndex.class);

    public static final KoTEARootsIndex EMPTY = new KoTEARootsIndex(Set.of(), Set.of(), Set.of());

    /** Fixed priority for Roots of different kinds at equal distance: News &gt; Command &gt; Event. */
    private enum Role {NEWS, COMMAND, EVENT}

    private final Set<String> eventRootFqns;
    private final Set<String> commandRootFqns;
    private final Set<String> newsRootFqns;

    public KoTEARootsIndex(Set<String> eventRootFqns, Set<String> commandRootFqns, Set<String> newsRootFqns) {
        this.eventRootFqns = Set.copyOf(eventRootFqns);
        this.commandRootFqns = Set.copyOf(commandRootFqns);
        this.newsRootFqns = Set.copyOf(newsRootFqns);
    }

    public boolean isEvent(@Nullable PsiClass candidate) {
        return nearestRootRole(candidate) == Role.EVENT;
    }

    public boolean isCommand(@Nullable PsiClass candidate) {
        return nearestRootRole(candidate) == Role.COMMAND;
    }

    public boolean isNews(@Nullable PsiClass candidate) {
        return nearestRootRole(candidate) == Role.NEWS;
    }

    /**
     * Breadth-first over {@code candidate} and its supertypes; the first inheritance distance at which
     * any supertype is a Root decides the role. Ties between different-kinded Roots resolve by
     * {@link Role} priority; a Root that itself carries more than one role leaves the class roleless.
     */
    @Nullable
    private Role nearestRootRole(@Nullable PsiClass candidate) {
        if (candidate == null) return null;
        if (eventRootFqns.isEmpty() && commandRootFqns.isEmpty() && newsRootFqns.isEmpty()) return null;

        Set<PsiClass> visited = new HashSet<>();
        List<PsiClass> frontier = new ArrayList<>();
        frontier.add(candidate);
        visited.add(candidate);

        while (!frontier.isEmpty()) {
            Map<PsiClass, Set<Role>> rootsAtThisDistance = new HashMap<>();
            List<PsiClass> next = new ArrayList<>();
            for (PsiClass psiClass : frontier) {
                Set<Role> roles = rolesOf(psiClass);
                if (!roles.isEmpty()) rootsAtThisDistance.put(psiClass, roles);
                for (PsiClass superClass : psiClass.getSupers()) {
                    if (visited.add(superClass)) next.add(superClass);
                }
            }
            if (!rootsAtThisDistance.isEmpty()) return resolveRole(candidate, rootsAtThisDistance);
            frontier = next;
        }
        return null;
    }

    private Set<Role> rolesOf(PsiClass psiClass) {
        String fqn = psiClass.getQualifiedName();
        if (fqn == null) return Set.of();
        EnumSet<Role> roles = EnumSet.noneOf(Role.class);
        if (newsRootFqns.contains(fqn)) roles.add(Role.NEWS);
        if (commandRootFqns.contains(fqn)) roles.add(Role.COMMAND);
        if (eventRootFqns.contains(fqn)) roles.add(Role.EVENT);
        return roles;
    }

    @Nullable
    private Role resolveRole(PsiClass candidate, Map<PsiClass, Set<Role>> rootsAtThisDistance) {
        for (Map.Entry<PsiClass, Set<Role>> entry : rootsAtThisDistance.entrySet()) {
            if (entry.getValue().size() > 1) {
                String message = "KoTEA: " + candidate.getQualifiedName() + " reaches multi-role Root "
                        + entry.getKey().getQualifiedName() + " " + entry.getValue() + "; treating as non-navigable";
                LOG.debug(message);
                IndexDebugLog.log("MULTI_ROLE_CONFLICT " + message);
                return null;
            }
        }
        EnumSet<Role> roles = EnumSet.noneOf(Role.class);
        rootsAtThisDistance.values().forEach(roles::addAll);
        // EnumSet iterates in Role declaration order, which is the News > Command > Event priority.
        return roles.iterator().next();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KoTEARootsIndex other)) return false;
        return eventRootFqns.equals(other.eventRootFqns)
                && commandRootFqns.equals(other.commandRootFqns)
                && newsRootFqns.equals(other.newsRootFqns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventRootFqns, commandRootFqns, newsRootFqns);
    }

    @Override
    public String toString() {
        return "KoTEARootsIndex{events=" + eventRootFqns + ", commands=" + commandRootFqns
                + ", news=" + newsRootFqns + "}";
    }
}
