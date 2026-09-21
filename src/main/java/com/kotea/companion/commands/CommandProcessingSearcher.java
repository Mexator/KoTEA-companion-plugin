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
        List<PsiElement> targets = new ArrayList<>();
        for (var ref : ReferencesSearch.search(target, scope, false).findAll()) {
            PsiElement el = ref.getElement();
            if (RoleResolver.roleOf(el, KoTEAElementKind.COMMAND) != RoleResolver.Role.PROCESSING) continue;
            if (PsiTreeUtil.getParentOfType(el, KDoc.class) != null) continue;

            KtClassOrObject handler = PsiTreeUtil.getParentOfType(el, KtClassOrObject.class);
            if (handler != null && CommandUtil.isCommandsHandler(handler, target)
                    && CommandUtil.hasHandleFunction(handler)) {
                targets.add(el);
            }
        }
        PerfLog.logSearch(LOG, "CommandProcessingSearcher", target.getName(), targets.size(), start);
        return targets;
    }
}
