package com.plugin;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CommandProcessingSearcher {

    public static List<PsiElement> findProcessing(@NotNull UClass uClass, GlobalSearchScope scope) {
        Map<GlobalSearchScope, List<PsiElement>> scopeMap = CachedValuesManager.getCachedValue(uClass.getJavaPsi(),
                () -> {
                    Map<GlobalSearchScope, List<PsiElement>> map = new ConcurrentHashMap<>();
                    return CachedValueProvider.Result.create(map, PsiModificationTracker.MODIFICATION_COUNT);
                });


        return scopeMap.computeIfAbsent(scope, s -> search(uClass, s));
    }

    private static List<PsiElement> search(@NotNull UClass uClass, GlobalSearchScope scope) {
        List<PsiElement> targets = new ArrayList<>();
//        if (!isCommand(uClass)) return targets;
        PsiClass classCommand = uClass.getJavaPsi();

        ReferencesSearch.search(classCommand, scope)
                .forEach(usage -> {
                    PsiElement element = usage.getElement();

                    UElement uElement = UastContextKt.toUElement(element, UElement.class);
                    if (uElement == null) return false;

                    UClass handlerClass = UastUtils.getParentOfType(uElement, UClass.class);
                    if (handlerClass != null && isCommandsHandler(handlerClass, classCommand)) {
                        PsiClass psiHandler = handlerClass.getJavaPsi();

                        PsiMethod[] methods = psiHandler.findMethodsByName("handle", false);

                        if (methods.length != 0) targets.add(element);
                    }

                    return true;
                });

        return targets;
    }
    private static boolean isCommandsHandler(UClass uClass, PsiClass pClass) {
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

    private static boolean isCommand(UClass uClass) {
        List<UTypeReferenceExpression> supers = uClass.getUastSuperTypes();

        if (!supers.isEmpty()) {

            UTypeReferenceExpression firstParent = supers.getFirst();
            String parentName = firstParent.getType().getPresentableText();

            return parentName.contains("Command");
        }
        return false;
    }
}
