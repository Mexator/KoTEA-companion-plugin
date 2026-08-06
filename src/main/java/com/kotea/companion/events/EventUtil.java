package com.kotea.companion.events;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.kotea.companion.index.KoTEAIndexService;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.asJava.LightClassUtilsKt;
import org.jetbrains.kotlin.psi.*;

public class EventUtil {

    public static boolean isEventClass(KtClassOrObject ktClass) {
        return CachedValuesManager.getCachedValue(ktClass, () ->
                CachedValueProvider.Result.create(computeIsEventClass(ktClass), PsiModificationTracker.MODIFICATION_COUNT));
    }

    private static boolean computeIsEventClass(KtClassOrObject ktClass) {
        PsiClass lightClass = LightClassUtilsKt.toLightClass(ktClass);
        if (lightClass == null) return false;
        return KoTEAIndexService.getInstance(ktClass.getProject()).getIndex().isEvent(lightClass);
    }

    @Nullable
    public static KtClassOrObject tryResolveToClass(PsiElement element) {
        if (element == null) return null;

        KtClassOrObject result = null;

        PsiElement source = element.getNavigationElement();

        if (source instanceof KtClassOrObject cls) {
            result = cls;
        } else if (source instanceof KtConstructor<?> constructor) {
            result = constructor.getContainingClassOrObject();
        } else {
            if (PsiTreeUtil.getParentOfType(source, KtImportDirective.class) != null) return null;

            PsiElement parent = source.getParent();
            if (parent instanceof KtClassOrObject cls && source == cls.getNameIdentifier()) {
                result = cls;
            } else {
                KtReferenceExpression ref = null;
                if (source instanceof KtReferenceExpression) {
                    ref = (KtReferenceExpression) source;
                } else if (parent instanceof KtReferenceExpression) {
                    ref = (KtReferenceExpression) parent;
                }

                if (ref != null) {
                    PsiReference reference = ref.getReference();
                    PsiElement resolved = reference != null ? reference.resolve() : null;

                    if (resolved != null) {
                        PsiElement resolvedSource = resolved.getNavigationElement();

                        if (resolvedSource instanceof KtClassOrObject cls) {
                            result = cls;
                        } else if (resolvedSource instanceof KtConstructor<?> constructor) {
                            result = constructor.getContainingClassOrObject();
                        }
                    }
                }
            }
        }
        if (result != null && isEventClass(result)) {
            return result;
        }
        return null;
    }
}
