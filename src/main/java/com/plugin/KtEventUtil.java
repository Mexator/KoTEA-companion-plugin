package com.plugin;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.lexer.KtTokens;
import org.jetbrains.kotlin.psi.*;

public class KtEventUtil {

    public static boolean isEventClass(KtClassOrObject ktClass) {
        return CachedValuesManager.getCachedValue(ktClass, () ->
                CachedValueProvider.Result.create(computeIsEventClass(ktClass), PsiModificationTracker.MODIFICATION_COUNT));
    }

    private static boolean computeIsEventClass(KtClassOrObject ktClass) {
        String name = ktClass.getName();
        if (ktClass instanceof KtClass cls) {
            if (cls.isInterface() || cls.hasModifier(KtTokens.ABSTRACT_KEYWORD) || cls.hasModifier(KtTokens.SEALED_KEYWORD))
                return false;
        }
        if (name == null || name.endsWith("Update") || name.endsWith("Handler")) return false;
        if (name.endsWith("Event")) return true;

        if (isInEventSuperTypeHierarchy(ktClass)) return true;

        KtClassOrObject ancestor = PsiTreeUtil.getParentOfType(ktClass, KtClassOrObject.class);
        while (ancestor != null) {
            if (ancestor.getName() != null && ancestor.getName().endsWith("Event")) return true;
            ancestor = PsiTreeUtil.getParentOfType(ancestor, KtClassOrObject.class);
        }
        return false;
    }

    // Separate cache key from isEventClass so both can live on the same KtClassOrObject.
    private static boolean isInEventSuperTypeHierarchy(KtClassOrObject ktClass) {
        return CachedValuesManager.getCachedValue(ktClass, () ->
                CachedValueProvider.Result.create(computeIsInEventSuperTypeHierarchy(ktClass), PsiModificationTracker.MODIFICATION_COUNT));
    }

    private static boolean computeIsInEventSuperTypeHierarchy(KtClassOrObject ktClass) {
        KtSuperTypeList superTypeList = ktClass.getSuperTypeList();
        if (superTypeList == null) return false;
        for (KtSuperTypeListEntry entry : superTypeList.getEntries()) {
            KtTypeReference typeRef = entry.getTypeReference();
            if (typeRef == null || !(typeRef.getTypeElement() instanceof KtUserType userType)) continue;
            String baseName = userType.getReferencedName();
            if (baseName != null && baseName.endsWith("Event")) return true;
            KtReferenceExpression ref = userType.getReferenceExpression();
            if (ref != null) {
                PsiReference psiRef = ref.getReference();
                PsiElement resolved = psiRef != null ? psiRef.resolve() : null;
                PsiElement nav = resolved != null ? resolved.getNavigationElement() : null;
                if (nav instanceof KtClassOrObject superClass && isInEventSuperTypeHierarchy(superClass)) return true;
            }
        }
        return false;
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