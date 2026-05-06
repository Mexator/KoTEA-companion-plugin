package com.plugin;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.codeInsight.navigation.PsiTargetNavigator;
import com.intellij.codeInsight.navigation.impl.PsiTargetPresentationRenderer;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.ui.popup.JBPopupFactory;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BaseAction extends AnAction {

    protected List<PsiElement> findTargets(PsiElement targetClass, GlobalSearchScope scope) {
        return null;
    }

    protected PsiElement findTargetClass(PsiElement element) {return null;}

    protected String getTitle() {
        return " ";
    }

    protected String getOperation() {
        return "";
    }
    protected void createLog(String project) {}

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        PsiElement element = e.getData(CommonDataKeys.PSI_ELEMENT);
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null || element == null) return;

        PsiElement targetClass = findTargetClass(element);

        if (targetClass == null) return;

        GlobalSearchScope scope = ScopeBuilder.getProductionScope(element);
        executeSearch(editor, targetClass, scope);
    }

    private void executeSearch(Editor e, PsiElement targetClass, GlobalSearchScope scope) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<PsiElement> targets = ReadAction.compute(() -> findTargets(targetClass, scope));

            ApplicationManager.getApplication().invokeLater(() -> {
                createLog(targetClass.getProject().getName());

                if (targets.isEmpty()) {
                    String message = "No " + getOperation().toLowerCase() + " usages found.";
                    HintManager.getInstance().showInformationHint(e, message);
                    return;
                }

                if (targets.size() == 1) {
                    ((Navigatable) targets.getFirst()).navigate(true);
                }
                else {
                    new PsiTargetNavigator<>(targets)
                            .presentationProvider(ContextPresentationProvider::getPresentation)
                            .createPopup(e.getProject(), getTitle())
                            .showInBestPositionFor(e);
                }
            });
        });
    }


    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
