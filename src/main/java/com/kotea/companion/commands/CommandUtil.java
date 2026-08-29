package com.kotea.companion.commands;

import com.intellij.openapi.project.DumbService;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.kotea.companion.index.KoTEAIndexService;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

public class CommandUtil {

    public static boolean isNavigableCommand(@Nullable PsiElement element) {
        if (element instanceof PsiClass psiClass) {
            return isNavigableCommandClass(psiClass);
        }
        UClass uClass = UastContextKt.toUElement(element, UClass.class);
        return uClass != null && isNavigableCommandClass(uClass.getJavaPsi());
    }

    private static boolean isNavigableCommandClass(@Nullable PsiClass psiClass) {
        if (psiClass == null) return false;
        if (DumbService.getInstance(psiClass.getProject()).isDumb()) return false;
        return CachedValuesManager.getCachedValue(psiClass, () ->
                CachedValueProvider.Result.create(computeIsNavigableCommandClass(psiClass),
                        PsiModificationTracker.MODIFICATION_COUNT,
                        KoTEAIndexService.getInstance(psiClass.getProject()).getModificationTracker()));
    }

    private static boolean computeIsNavigableCommandClass(PsiClass psiClass) {
        if (psiClass.isInterface() || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) return false;
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
}
