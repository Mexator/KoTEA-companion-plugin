package com.kotea.companion.actions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.kotea.companion.commands.CommandEmissionSearcher;
import com.kotea.companion.events.EventEmissionSearcher;
import com.kotea.companion.news.NewsEmissionSearcher;
import com.kotea.companion.util.NavigableElement;
import org.jetbrains.kotlin.psi.KtClassOrObject;

import java.util.List;

public class GoToEmissionAction extends BaseGoToAction {

    @Override
    protected List<PsiElement> findTargets(NavigableElement target, GlobalSearchScope scope) {
        KtClassOrObject anchor = target.anchor();
        return switch (target.kind()) {
            case EVENT -> EventEmissionSearcher.findEmissions(anchor, scope);
            case NEWS -> NewsEmissionSearcher.findEmissions(anchor, scope);
            case COMMAND -> CommandEmissionSearcher.findEmission(anchor, scope);
        };
    }

    @Override
    protected String getTitle() {
        return "Emission Sites";
    }

    @Override
    protected String getOperation() {
        return "Go to emission sites";
    }
}
