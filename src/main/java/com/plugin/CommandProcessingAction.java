package com.plugin;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.uast.*;

import java.util.List;
import java.util.Map;

public class CommandProcessingAction extends BaseAction {
    @Override
    protected List<PsiElement> findTargets(PsiElement targetClass, GlobalSearchScope scope) {
        UClass uClass = UastContextKt.toUElement(targetClass, UClass.class);
        if (uClass == null || !isCommand(uClass.getJavaPsi())) return List.of();

        return CommandProcessingSearcher.findProcessing(uClass, scope);
    }

    @Override
    protected String getTitle() {
        return "Command Processing";
    }

    @Override
    protected String getOperation() {
        return "Processing";
    }

    @Override
    protected void createLog(String project) {
        AnalyticsService.log("hot-key", Map.of(
                "type", "Command",
                "feature", "Go to Processing",
                "project", project
        ));
    }

    @Override
    protected PsiElement findTargetClass(PsiElement element) {
        UElement uElement = UastContextKt.toUElement(element);
        UClass uClass = null;

        if (uElement instanceof UMethod method && method.isConstructor()) {
            uClass = UastUtils.getParentOfType(uElement, UClass.class);
        }
        if (uClass == null) {
            uClass = UastUtils.getParentOfType(uElement, UClass.class, false);
        }

        return uClass != null ? uClass.getSourcePsi() : null;
    }

    private boolean isCommand(PsiClass psiClass) {
        for (PsiClass superClass : psiClass.getSupers()) {
            String name = superClass.getName();

            if (name != null && name.contains("Command") && !name.contains("CommandsFlowHandler")) return true;
        }

        return false;
    }
}
