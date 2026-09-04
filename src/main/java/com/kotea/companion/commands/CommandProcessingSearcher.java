package com.kotea.companion.commands;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.kotea.companion.util.CachingSearcher;
import com.kotea.companion.util.KoTEAElementKind;
import com.kotea.companion.util.PerfLog;
import com.kotea.companion.util.RoleResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.*;

import java.util.*;

public class CommandProcessingSearcher extends CachingSearcher<UClass> {

    private static final Logger LOG = Logger.getInstance(CommandProcessingSearcher.class);
    private static final CommandProcessingSearcher INSTANCE = new CommandProcessingSearcher();

    private CommandProcessingSearcher() {
    }

    public static List<PsiElement> findProcessing(@NotNull UClass uClass, GlobalSearchScope scope) {
        return INSTANCE.findCached(uClass, scope);
    }

    @Override
    protected PsiElement cacheKeyOf(@NotNull UClass target) {
        return target.getJavaPsi();
    }

    @Override
    protected List<PsiElement> search(@NotNull UClass uClass, @NotNull GlobalSearchScope scope) {
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
