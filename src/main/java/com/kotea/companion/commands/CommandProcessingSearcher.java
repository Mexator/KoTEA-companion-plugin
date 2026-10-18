package com.kotea.companion.commands;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.kotea.companion.domain.KoTEARoleResolver;
import com.kotea.companion.util.CachingSearcher;
import com.kotea.companion.util.PerfLog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtClassOrObject;

import java.util.ArrayList;
import java.util.List;

public class CommandProcessingSearcher extends CachingSearcher<KtClassOrObject> {

    private static final Logger LOG = Logger.getInstance(CommandProcessingSearcher.class);
    private static final CommandProcessingSearcher INSTANCE = new CommandProcessingSearcher();

    private CommandProcessingSearcher() {
    }

    public static List<PsiElement> findProcessing(@NotNull KtClassOrObject target, @NotNull GlobalSearchScope scope) {
        return INSTANCE.findCached(target, scope);
    }

    @Override
    protected PsiElement cacheKeyOf(@NotNull KtClassOrObject target) {
        return target;
    }

    @Override
    protected List<PsiElement> search(@NotNull KtClassOrObject target, @NotNull GlobalSearchScope scope) {
        long start = PerfLog.start();
        KoTEARoleResolver resolver = KoTEARoleResolver.getInstance(target.getProject());
        List<PsiElement> targets = new ArrayList<>();
        for (var ref : ReferencesSearch.search(target, scope, false).findAll()) {
            PsiElement el = ref.getElement();
            if (resolver.roleOf(el) == KoTEARoleResolver.Role.PROCESSING) targets.add(el);
        }
        PerfLog.logSearch(LOG, "CommandProcessingSearcher", target.getName(), targets.size(), start);
        return targets;
    }
}
