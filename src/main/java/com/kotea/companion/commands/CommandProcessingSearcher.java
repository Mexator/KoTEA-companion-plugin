package com.kotea.companion.commands;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.kotea.companion.util.KoTEAElementKind;
import com.kotea.companion.util.PerfLog;
import com.kotea.companion.util.RoleResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CommandProcessingSearcher {

    private static final Logger LOG = Logger.getInstance(CommandProcessingSearcher.class);

    public static List<PsiElement> findProcessing(@NotNull UClass uClass, GlobalSearchScope scope) {
        Map<GlobalSearchScope, List<PsiElement>> scopeMap = CachedValuesManager.getCachedValue(uClass.getJavaPsi(),
                () -> {
                    Map<GlobalSearchScope, List<PsiElement>> map = new ConcurrentHashMap<>();
                    return CachedValueProvider.Result.create(map, PsiModificationTracker.MODIFICATION_COUNT);
                });


        return scopeMap.computeIfAbsent(scope, s -> search(uClass, s));
    }

    private static List<PsiElement> search(@NotNull UClass uClass, GlobalSearchScope scope) {
        long start = PerfLog.start();
        List<PsiElement> targets = new ArrayList<>();
        PsiClass classCommand = uClass.getJavaPsi();

        ReferencesSearch.search(classCommand, scope).forEach(usage -> {
            PsiElement element = usage.getElement();
            UElement uElement = UastContextKt.toUElement(element, UElement.class);
            if (uElement == null) return true;
            if (RoleResolver.roleOf(element, KoTEAElementKind.COMMAND) != RoleResolver.Role.PROCESSING) return true;

            UClass handlerClass = UastUtils.getParentOfType(uElement, UClass.class);
            if (handlerClass != null && CommandUtil.isCommandsHandler(handlerClass, classCommand)) {
                PsiClass psiHandler = handlerClass.getJavaPsi();
                PsiMethod[] methods = psiHandler.findMethodsByName("handle", false);
                if (methods.length != 0) {
                    targets.add(element);
                }
            }
            return true;
        });

        PerfLog.logSearch(LOG, "CommandProcessingSearcher", uClass.getName(), targets.size(), start);
        return targets;
    }
}
