package com.kotea.companion.events;

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

public class EventEmissionSearcher extends CachingSearcher<KtClassOrObject> {

    private static final Logger LOG = Logger.getInstance(EventEmissionSearcher.class);
    private static final EventEmissionSearcher INSTANCE = new EventEmissionSearcher();

    private EventEmissionSearcher() {
    }

    public static List<PsiElement> findEmissions(@NotNull KtClassOrObject target, @NotNull GlobalSearchScope scope) {
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
            if (resolver.roleOf(el) == KoTEARoleResolver.Role.EMISSION) targets.add(el);
        }
        PerfLog.logSearch(LOG, "EventEmissionSearcher", target.getName(), targets.size(), start);
        return targets;
    }
}
