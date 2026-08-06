package com.kotea.companion.util;

import com.intellij.psi.*;
import com.kotea.companion.events.EventUtil;
import com.kotea.companion.index.KoTEAIndexService;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.uast.*;

public class KoTEAUtil {

    public static boolean isEvent(@Nullable PsiElement element) {
        if (element instanceof KtClassOrObject ktClass) {
            return EventUtil.isEventClass(ktClass);
        }
        return false;
    }

    public static boolean isCommand(@Nullable PsiElement element) {
        if (element instanceof PsiClass psiClass) {
            return isCommandClass(psiClass);
        }
        UClass uClass = UastContextKt.toUElement(element, UClass.class);
        return uClass != null && isCommandClass(uClass.getJavaPsi());
    }

    private static boolean isCommandClass(@Nullable PsiClass psiClass) {
        if (psiClass == null) return false;
        return KoTEAIndexService.getInstance(psiClass.getProject()).getIndex().isCommand(psiClass);
    }

    public static boolean isCommandsHandler(UClass uClass, PsiClass pClass) {
        for (UTypeReferenceExpression interfaceRef : uClass.getUastSuperTypes()) {
            PsiType type = interfaceRef.getType();

            if (type instanceof PsiClassType classType) {
                PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
                PsiClass iClass = resolveResult.getElement();

                if (iClass != null && "CommandsFlowHandler".equals(iClass.getName())) {
                    PsiType[] params = classType.getParameters();

                    if (params.length > 0) {
                        PsiType firstParam = params[0];

                        PsiElementFactory psiElementFactory = JavaPsiFacade.getElementFactory(pClass.getProject());
                        PsiType commandType = psiElementFactory.createType(pClass);

                        if (firstParam.isAssignableFrom(commandType)) return true;
                    }
                }
            }
        }

        return false;
    }

    @Nullable
    public static PsiElement tryResolveToKoTEAClass(PsiElement element) {
        // Try Event first
        KtClassOrObject eventClass = EventUtil.tryResolveToClass(element);
        if (eventClass != null) return eventClass;

        UElement uElement = UastContextKt.toUElement(element, UElement.class);
        if (uElement == null) return null;

        UClass uClass = null;
        if (uElement instanceof UMethod method && method.isConstructor()) {
            uClass = UastUtils.getParentOfType(uElement, UClass.class);
        }
        if (uClass == null) {
            uClass = UastUtils.getParentOfType(uElement, UClass.class, false);
        }

        if (uClass != null) {
            PsiClass psiClass = uClass.getJavaPsi();
            if (isCommand(psiClass)) {
                return uClass.getSourcePsi();
            }
        }

        return null;
    }
}
