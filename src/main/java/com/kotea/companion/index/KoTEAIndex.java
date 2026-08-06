package com.kotea.companion.index;

import com.intellij.psi.PsiClass;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public final class KoTEAIndex {

    public static final KoTEAIndex EMPTY = new KoTEAIndex(Set.of(), Set.of());

    private final Set<PsiClass> rootEventClasses;
    private final Set<PsiClass> rootCommandClasses;

    public KoTEAIndex(Set<PsiClass> rootEventClasses, Set<PsiClass> rootCommandClasses) {
        this.rootEventClasses = Set.copyOf(rootEventClasses);
        this.rootCommandClasses = Set.copyOf(rootCommandClasses);
    }

    public boolean isEvent(@Nullable PsiClass candidate) {
        return isDescendantOfAny(candidate, rootEventClasses);
    }

    public boolean isCommand(@Nullable PsiClass candidate) {
        return isDescendantOfAny(candidate, rootCommandClasses);
    }

    private static boolean isDescendantOfAny(@Nullable PsiClass candidate, Set<PsiClass> roots) {
        if (candidate == null) return false;
        for (PsiClass root : roots) {
            if (InheritanceUtil.isInheritorOrSelf(candidate, root, true)) return true;
        }
        return false;
    }
}
