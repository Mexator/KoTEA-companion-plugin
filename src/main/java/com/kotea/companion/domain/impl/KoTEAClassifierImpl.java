package com.kotea.companion.domain.impl;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiClass;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.kotea.companion.domain.KoTEAClassifier;
import com.kotea.companion.index.KoTEARootsIndex;
import com.kotea.companion.index.IndexLog;
import com.kotea.companion.index.KoTEAIndexService;
import com.kotea.companion.util.KoTEAElementKind;
import com.kotea.companion.util.ScopeBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.asJava.LightClassUtilsKt;
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex;
import org.jetbrains.kotlin.psi.KtClassOrObject;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class KoTEAClassifierImpl implements KoTEAClassifier {

    static final String NOTHING_FQN = "kotlin.Nothing";

    /** Roots of different kinds at equal distance resolve in this order. */
    private static final List<KoTEAElementKind> PRIORITY =
            List.of(KoTEAElementKind.NEWS, KoTEAElementKind.COMMAND, KoTEAElementKind.EVENT);

    private final Project project;
    private final KoTEAIndexService index;
    private final ModificationTracker[] dependencies;
    private final CachedValue<KoTEARoots> roots;

    public KoTEAClassifierImpl(@NotNull Project project) {
        this.project = project;
        this.index = KoTEAIndexService.getInstance(project);
        this.dependencies = new ModificationTracker[]{index.getModificationTracker(),
                ProjectRootModificationTracker.getInstance(project), PsiModificationTracker.getInstance(project)};
        this.roots = CachedValuesManager.getManager(project).createCachedValue(
                () -> CachedValueProvider.Result.create(computeRoots(), (Object[]) dependencies), false);
    }

    @Nullable
    @Override
    public KoTEAElementKind kindOf(@Nullable KtClassOrObject candidate) {
        if (candidate == null || DumbService.isDumb(project)) return null;
        return CachedValuesManager.getCachedValue(candidate, () -> {
            PsiClass lightClass = LightClassUtilsKt.toLightClass(candidate);
            return CachedValueProvider.Result.create(lightClass != null ? kindOf(roots(), lightClass) : null, (Object[]) dependencies);
        });
    }

    /** Requires smart mode. */
    KoTEARoots roots() {
        return roots.getValue();
    }

    private KoTEARoots computeRoots() {
        KoTEARootsIndex candidates = index.getIndex();
        return new KoTEARoots(rootsAmong(candidates.eventRootFqns()), rootsAmong(candidates.commandRootFqns()),
                rootsAmong(candidates.newsRootFqns()));
    }

    private Set<String> rootsAmong(Set<String> candidates) {
        Set<String> roots = new HashSet<>();
        for (String fqn : candidates) {
            if (isRoot(fqn)) roots.add(fqn);
        }
        return roots;
    }

    private boolean isRoot(String fqn) {
        return !NOTHING_FQN.equals(fqn) && isDeclaredInProject(fqn);
    }

    private boolean isDeclaredInProject(String fqn) {
        return !StubIndex.getElements(KotlinFullClassNameIndex.Helper.getIndexKey(), fqn, project,
                ScopeBuilder.getUpdateSourcesScope(project), KtClassOrObject.class).isEmpty();
    }

    /**
     * Breadth-first over {@code candidate} and its supertypes; see ({@code docs/adr/0002-nearest-root.md})
     */
    @Nullable
    static KoTEAElementKind kindOf(KoTEARoots roots, PsiClass candidate) {
        if (roots.isEmpty()) return null;

        Set<PsiClass> visited = new HashSet<>();
        List<PsiClass> frontier = new ArrayList<>();
        frontier.add(candidate);
        visited.add(candidate);

        while (!frontier.isEmpty()) {
            EnumSet<KoTEAElementKind> kindsAtThisDistance = EnumSet.noneOf(KoTEAElementKind.class);
            List<PsiClass> next = new ArrayList<>();
            for (PsiClass psiClass : frontier) {
                String fqn = psiClass.getQualifiedName();
                Set<KoTEAElementKind> kinds = fqn != null ? roots.kindsOf(fqn) : Set.of();
                if (kinds.size() > 1) {
                    IndexLog.debug(() -> IndexLog.mask(candidate.getQualifiedName()) + " reaches multi-role Root "
                            + IndexLog.mask(fqn) + " " + kinds + "; treating as non-navigable");
                    return null;
                }
                kindsAtThisDistance.addAll(kinds);
                for (PsiClass superClass : psiClass.getSupers()) {
                    if (visited.add(superClass)) next.add(superClass);
                }
            }
            for (KoTEAElementKind kind : PRIORITY) {
                if (kindsAtThisDistance.contains(kind)) return kind;
            }
            frontier = next;
        }
        return null;
    }
}
