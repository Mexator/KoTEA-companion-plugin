package com.plugin;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.navigation.PsiTargetNavigator;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class BaseAction extends AnAction {

    protected List<PsiElement> findTargets(PsiElement targetClass, GlobalSearchScope scope) {
        return null;
    }

    protected PsiElement findTargetClass(PsiElement element) { return null; }

    protected String getTitle() { return " "; }

    protected String getOperation() { return ""; }

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

    private void executeSearch(Editor editor, PsiElement targetClass, GlobalSearchScope scope) {
        String lockKey = lockKey(targetClass);
        if (!SearchLock.tryLock(lockKey)) {
            HintManager.getInstance().showInformationHint(editor, "Search already in progress");
            return;
        }

        ProgressManager.getInstance().run(new Task.Backgroundable(targetClass.getProject(), getTitle(), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                try {
                    List<PsiElement> targets = ReadAction.compute(() -> findTargets(targetClass, scope));
                    if (indicator.isCanceled()) return;

                    ApplicationManager.getApplication().invokeLater(() -> {
                        createLog(targetClass.getProject().getName());
                        if (targets == null || targets.isEmpty()) {
                            HintManager.getInstance().showInformationHint(editor,
                                    "No " + getOperation().toLowerCase() + " usages found.");
                            return;
                        }
                        if (targets.size() == 1) {
                            ((Navigatable) targets.getFirst()).navigate(true);
                        } else {
                            new PsiTargetNavigator<>(targets)
                                    .presentationProvider(ContextPresentationProvider::getPresentation)
                                    .createPopup(editor.getProject(), getTitle())
                                    .showInBestPositionFor(editor);
                        }
                    });
                } finally {
                    SearchLock.unlock(lockKey);
                }
            }
        });
    }

    private String lockKey(PsiElement targetClass) {
        var file = targetClass.getContainingFile();
        if (file != null && file.getVirtualFile() != null) {
            return file.getVirtualFile().getPath() + ":" + targetClass.getTextOffset() + ":" + getOperation();
        }
        return System.identityHashCode(targetClass) + ":" + getOperation();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}