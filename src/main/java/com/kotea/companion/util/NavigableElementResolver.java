package com.kotea.companion.util;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.kotea.companion.domain.KoTEAClassifier;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.asJava.LightClassUtilsKt;
import org.jetbrains.kotlin.psi.KtClassOrObject;

/**
 * Resolves a PSI element to the {@link NavigableElement} (if any).
 * Answers the question: "is a PSI element references a KoTEA class and what is its kind?"
 */
public final class NavigableElementResolver {

    private NavigableElementResolver() {
    }

    @Nullable
    public static NavigableElement resolve(PsiElement element) {
        KtClassOrObject candidate = ClassReferenceResolver.resolveClassOrObject(element);
        if (candidate == null) return null;
        KoTEAElementKind kind = KoTEAClassifier.getInstance(candidate.getProject()).kindOf(candidate);
        if (kind == null) return null;
        PsiClass lightClass = LightClassUtilsKt.toLightClass(candidate);
        boolean concrete = lightClass != null && !lightClass.isInterface()
                && !lightClass.hasModifierProperty(PsiModifier.ABSTRACT);
        return concrete ? new NavigableElement(kind, candidate) : null;
    }
}
