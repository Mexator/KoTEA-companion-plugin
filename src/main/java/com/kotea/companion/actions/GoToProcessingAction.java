package com.kotea.companion.actions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.kotea.companion.commands.CommandProcessingSearcher;
import com.kotea.companion.events.EventProcessingSearcher;
import com.kotea.companion.events.EventUtil;
import com.kotea.companion.util.KoTEAUtil;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UastContextKt;

import java.util.List;

public class GoToProcessingAction extends BaseGoToAction {

    @Override
    protected List<PsiElement> findTargets(PsiElement targetElement, GlobalSearchScope scope) {
        if (!(targetElement instanceof KtClassOrObject targetClass)) return List.of();

        if (EventUtil.isNavigableEventClass(targetClass)) {
            return EventProcessingSearcher.findProcessing(targetClass, scope);
        }

        if (KoTEAUtil.isNavigableCommand(targetClass)) {
            UClass uClass = UastContextKt.toUElement(targetClass, UClass.class);
            return uClass != null ? CommandProcessingSearcher.findProcessing(uClass, scope) : List.of();
        }

        return List.of();
    }

    @Override
    protected String getTitle() {
        return "Processing Sites";
    }

    @Override
    protected String getOperation() {
        return "Processing";
    }
}
