package com.kotea.companion.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.kotea.companion.domain.KoTEARoleResolver.Role;
import com.kotea.companion.navigation.NavigationRunner;
import com.kotea.companion.util.NavigableElement;
import com.kotea.companion.util.NavigableElementResolver;
import org.jetbrains.annotations.NotNull;

public abstract class BaseGoToAction extends AnAction {

    private final Role role;

    protected BaseGoToAction(@NotNull Role role) {
        this.role = role;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(isAvailable(e));
    }

    static boolean isAvailable(@NotNull AnActionEvent e) {
        PsiElement element = e.getData(CommonDataKeys.PSI_ELEMENT);
        return element != null
                && ReadAction.compute(() -> NavigableElementResolver.resolve(element) != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        PsiElement element = e.getData(CommonDataKeys.PSI_ELEMENT);
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null || element == null) return;

        NavigableElement target = NavigableElementResolver.resolve(element);
        if (target == null) return;

        NavigationRunner.navigate(target.anchor(), role, element, false, NavigationRunner.inEditor(editor));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
