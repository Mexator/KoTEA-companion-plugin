package com.kotea.companion.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.psi.*;

/**
 * Shared logic behind {@code EventUtil}, {@code CommandUtil} and {@code NewsUtil}: resolving an
 * arbitrary PSI element back to the {@link KtClassOrObject} it refers to, if any.
 */
public final class KoTEAClassSupport {

    private KoTEAClassSupport() {
    }

    @Nullable
    public static KtClassOrObject resolveClassOrObject(PsiElement element) {
        if (element == null) return null;

        PsiElement source = element.getNavigationElement();

        if (source instanceof KtClassOrObject cls) {
            return cls;
        }
        if (source instanceof KtConstructor<?> constructor) {
            return constructor.getContainingClassOrObject();
        }

        if (PsiTreeUtil.getParentOfType(source, KtImportDirective.class) != null) return null;

        PsiElement parent = source.getParent();
        if (parent instanceof KtClassOrObject cls && source == cls.getNameIdentifier()) {
            return cls;
        }

        KtReferenceExpression ref = null;
        if (source instanceof KtReferenceExpression) {
            ref = (KtReferenceExpression) source;
        } else if (parent instanceof KtReferenceExpression) {
            ref = (KtReferenceExpression) parent;
        }
        if (ref == null) return null;

        PsiReference reference = ref.getReference();
        PsiElement resolved = reference != null ? reference.resolve() : null;
        if (resolved == null) return null;

        PsiElement resolvedSource = resolved.getNavigationElement();
        if (resolvedSource instanceof KtClassOrObject cls) {
            return cls;
        }
        if (resolvedSource instanceof KtConstructor<?> constructor) {
            return constructor.getContainingClassOrObject();
        }
        return null;
    }
}
