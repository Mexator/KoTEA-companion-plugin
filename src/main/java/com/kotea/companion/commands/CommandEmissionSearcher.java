package com.kotea.companion.commands;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.kotea.companion.util.CachingSearcher;
import com.kotea.companion.util.KoTEAElementKind;
import com.kotea.companion.util.PerfLog;
import com.kotea.companion.util.RoleResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.*;

import java.util.ArrayList;
import java.util.List;

public class CommandEmissionSearcher extends CachingSearcher<UClass> {

    private static final Logger LOG = Logger.getInstance(CommandEmissionSearcher.class);
    private static final CommandEmissionSearcher INSTANCE = new CommandEmissionSearcher();

    private CommandEmissionSearcher() {
    }

    public static List<PsiElement> findEmission(@NotNull UClass uClass, GlobalSearchScope scope) {
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
        PsiClass psiClass = uClass.getJavaPsi();

        ReferencesSearch.search(psiClass, scope).forEach(usage -> {
            PsiElement element = usage.getElement();
            UElement uElement = UastContextKt.toUElement(element, UElement.class);
            if (uElement == null) return true;
            if (RoleResolver.roleOf(element, KoTEAElementKind.COMMAND) != RoleResolver.Role.EMISSION) return true;

            if (UastUtils.getParentOfType(uElement, UImportStatement.class) != null) return true;
            if (UastUtils.getParentOfType(uElement, UTypeReferenceExpression.class) != null ||
                    UastUtils.getParentOfType(uElement, UAnnotation.class) != null) return true;

            UCallExpression call = UastUtils.getParentOfType(uElement, UCallExpression.class, false);
            if (call != null) {
                targets.add(element);
                return true;
            }

            if (uElement instanceof USimpleNameReferenceExpression || uElement instanceof UQualifiedReferenceExpression) {
                UElement parent = uElement.getUastParent();
                if (parent instanceof UVariable variable) {
                    if (!uElement.equals(variable.getTypeReference())) {
                        targets.add(element);
                    }
                }
            }
            return true;
        });

        PerfLog.logSearch(LOG, "CommandEmissionSearcher", uClass.getName(), targets.size(), start);
        return targets;
    }
}
