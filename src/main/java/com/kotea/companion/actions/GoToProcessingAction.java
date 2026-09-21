package com.kotea.companion.actions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.kotea.companion.commands.CommandProcessingSearcher;
import com.kotea.companion.events.EventProcessingSearcher;
import com.kotea.companion.news.NewsProcessingSearcher;
import com.kotea.companion.util.NavigableElement;
import org.jetbrains.kotlin.psi.KtClassOrObject;

import java.util.List;

public class GoToProcessingAction extends BaseGoToAction {

    @Override
    protected List<PsiElement> findTargets(NavigableElement target, GlobalSearchScope scope) {
        KtClassOrObject anchor = target.anchor();
        return switch (target.kind()) {
            case EVENT -> EventProcessingSearcher.findProcessing(anchor, scope);
            case NEWS -> NewsProcessingSearcher.findProcessing(anchor, scope);
            case COMMAND -> CommandProcessingSearcher.findProcessing(anchor, scope);
        };
    }

    @Override
    protected String getTitle() {
        return "Processing Sites";
    }

    @Override
    protected String getOperation() {
        return "Go to processing sites";
    }
}
