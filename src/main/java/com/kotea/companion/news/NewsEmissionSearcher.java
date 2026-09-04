package com.kotea.companion.news;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.kotea.companion.util.CachingSearcher;
import com.kotea.companion.util.KoTEAElementKind;
import com.kotea.companion.util.PerfLog;
import com.kotea.companion.util.RoleResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.kdoc.psi.api.KDoc;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtImportDirective;
import org.jetbrains.kotlin.psi.KtTypeReference;

import java.util.ArrayList;
import java.util.List;

public class NewsEmissionSearcher extends CachingSearcher<KtClassOrObject> {

    private static final Logger LOG = Logger.getInstance(NewsEmissionSearcher.class);
    private static final NewsEmissionSearcher INSTANCE = new NewsEmissionSearcher();

    private NewsEmissionSearcher() {
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
        List<PsiElement> emissionPlaces = new ArrayList<>();
        for (var ref : ReferencesSearch.search(target, scope, false).findAll()) {
            PsiElement el = ref.getElement();
            if (PsiTreeUtil.getParentOfType(el, KtImportDirective.class) != null) continue;
            if (PsiTreeUtil.getParentOfType(el, KtTypeReference.class) != null) continue;
            if (PsiTreeUtil.getParentOfType(el, KDoc.class) != null) continue;
            if (RoleResolver.roleOf(el, KoTEAElementKind.NEWS) != RoleResolver.Role.EMISSION) continue;
            emissionPlaces.add(el);
        }
        PerfLog.logSearch(LOG, "NewsEmissionSearcher", target.getName(), emissionPlaces.size(), start);
        return emissionPlaces;
    }
}
