package com.plugin;

import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.kotlin.psi.KtClassOrObject;

import java.util.List;

public class EventEmissionAction extends BaseAction {

    @Override
    protected PsiElement findTargetClass(PsiElement element) {
        return KtEventUtil.tryResolveToClass(element);
    }

    @Override
    protected List<PsiElement> findTargets(PsiElement targetClass, GlobalSearchScope scope) {
        if (targetClass instanceof KtClassOrObject ktClass) {
            return EventEmissionSearcher.findEmissions(ktClass, scope);
        }
        return List.of();
    }

    @Override
    protected String getTitle() {
        return "Event Emissions";
    }

    @Override
    protected String getOperation() {
        return "Event Emission";
    }
}