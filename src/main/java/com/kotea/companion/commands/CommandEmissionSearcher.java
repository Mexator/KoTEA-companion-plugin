package com.kotea.companion.commands;

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
import org.jetbrains.kotlin.psi.*;

import java.util.ArrayList;
import java.util.List;

public class CommandEmissionSearcher extends CachingSearcher<KtClassOrObject> {

    private static final Logger LOG = Logger.getInstance(CommandEmissionSearcher.class);
    private static final CommandEmissionSearcher INSTANCE = new CommandEmissionSearcher();

    private CommandEmissionSearcher() {
    }

    public static List<PsiElement> findEmission(@NotNull KtClassOrObject target, @NotNull GlobalSearchScope scope) {
        return INSTANCE.findCached(target, scope);
    }

    @Override
    protected PsiElement cacheKeyOf(@NotNull KtClassOrObject target) {
        return target;
    }

    @Override
    protected List<PsiElement> search(@NotNull KtClassOrObject target, @NotNull GlobalSearchScope scope) {
        long start = PerfLog.start();
        List<PsiElement> targets = new ArrayList<>();
        for (var ref : ReferencesSearch.search(target, scope, false).findAll()) {
            PsiElement el = ref.getElement();
            if (RoleResolver.roleOf(el, KoTEAElementKind.COMMAND) != RoleResolver.Role.EMISSION) continue;
            if (PsiTreeUtil.getParentOfType(el, KtImportDirective.class, KtTypeReference.class,
                    KtAnnotationEntry.class, KDoc.class) != null) continue;

            PsiElement parent = el.getParent();
            if (PsiTreeUtil.getParentOfType(el, KtCallExpression.class) != null
                    || parent instanceof KtProperty || parent instanceof KtParameter) {
                targets.add(el);
            }
        }
        PerfLog.logSearch(LOG, "CommandEmissionSearcher", target.getName(), targets.size(), start);
        return targets;
    }
}
