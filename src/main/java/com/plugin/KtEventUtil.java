package com.plugin;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.lexer.KtTokens;
import org.jetbrains.kotlin.psi.*;

public class KtEventUtil {

    public static boolean isEventClass(KtClassOrObject ktClass) {
        String name = ktClass.getName();
        if (ktClass instanceof KtClass cls) {
            if (cls.isInterface() ||
                    cls.hasModifier(KtTokens.ABSTRACT_KEYWORD) ||
                    cls.hasModifier(KtTokens.SEALED_KEYWORD)) {
                return false;
            }
        }
        if (name == null || name.endsWith("Update") || name.endsWith("Handler")) return false;
        if (name.endsWith("Event")) return true;

        KtSuperTypeList superTypeList = ktClass.getSuperTypeList();
        if (superTypeList != null) {
            for (KtSuperTypeListEntry entry : superTypeList.getEntries()) {
                KtTypeReference typeRef = entry.getTypeReference();
                if (typeRef != null && typeRef.getTypeElement() instanceof KtUserType userType) {
                    String baseName = userType.getReferencedName();
                    if (baseName != null && baseName.endsWith("Event")) return true;
                }
            }
        }

        KtClassOrObject parent = PsiTreeUtil.getParentOfType(ktClass, KtClassOrObject.class);
        return parent != null && parent.getName() != null && parent.getName().endsWith("Event");
    }

    @Nullable
    public static KtClassOrObject tryResolveToClass(PsiElement element) {
        if (element == null) return null;

        KtClassOrObject result = null;

        PsiElement source = element.getNavigationElement();

        if (source instanceof KtClassOrObject cls) {
            result = cls;
        }
        else if (source instanceof KtConstructor<?> constructor) {
            result = constructor.getContainingClassOrObject();
        }
        else {
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