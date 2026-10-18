package com.kotea.companion.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.psi.*;

/**
 * Resolves an arbitrary PSI element back to the {@link KtClassOrObject} it refers to, if any.
 */
public final class ClassReferenceResolver {

    private ClassReferenceResolver() {
    }

    @Nullable
    public static KtClassOrObject resolveClassOrObject(@Nullable PsiElement element) {
        if (element == null) return null;

        PsiElement source = element.getNavigationElement();

        // is element inside an import?
        if (PsiTreeUtil.getParentOfType(source, KtImportDirective.class) != null) return null;

        // is element itself class/constructor declaration?
        KtClassOrObject declared = asClassOrObject(source);
        if (declared != null) return declared;

        // is element a reference to a class/constructor?
        PsiElement candidate = source instanceof KtReferenceExpression ? source : source.getParent();
        PsiReference reference = candidate instanceof KtReferenceExpression ref ? ref.getReference() : null;
        PsiElement resolved = reference != null ? reference.resolve() : null;
        return resolved != null ? asClassOrObject(resolved.getNavigationElement()) : null;
    }

    /** {@code element} itself if it is a class or object, or the class a constructor belongs to */
    @Nullable
    private static KtClassOrObject asClassOrObject(PsiElement element) {
        if (element instanceof KtClassOrObject cls) return cls;
        if (element instanceof KtConstructor<?> constructor) return constructor.getContainingClassOrObject();
        return null;
    }
}
