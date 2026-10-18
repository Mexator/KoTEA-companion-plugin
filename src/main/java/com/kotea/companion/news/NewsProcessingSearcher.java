package com.kotea.companion.news;

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

public class NewsProcessingSearcher extends CachingSearcher<KtClassOrObject> {

    private static final Logger LOG = Logger.getInstance(NewsProcessingSearcher.class);
    private static final NewsProcessingSearcher INSTANCE = new NewsProcessingSearcher();

    private NewsProcessingSearcher() {
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
        PerfLog.logSearch(LOG, "NewsProcessingSearcher", target.getName(), targets.size(), start);
        return targets;
    }
}
