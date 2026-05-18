package com.plugin;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.PsiTargetNavigator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.*;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;

public class EventLineMarkerProvider extends RelatedItemLineMarkerProvider {

    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element,
                                            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {

        if (!(element instanceof LeafPsiElement)) return;

        PsiElement parent = element.getParent();
        if (parent instanceof KtNameReferenceExpression) {
            PsiElement grandParent = parent.getParent();
            if (grandParent instanceof KtDotQualifiedExpression && ((KtDotQualifiedExpression) grandParent).getReceiverExpression() == parent)
                return;

            if (grandParent instanceof KtUserType) {
                PsiElement greatGrandParent = grandParent.getParent();
                if (greatGrandParent instanceof KtUserType && ((KtUserType) greatGrandParent).getQualifier() == grandParent)
                    return;
            }
        }

        if (PsiTreeUtil.getParentOfType(element, KtSuperTypeList.class) != null) return;

        KtClassOrObject targetClass = KtEventUtil.tryResolveToClass(element);

        if (targetClass == null || !KtEventUtil.isEventClass(targetClass)) return;

        boolean isDeclaration = element.getParent() == targetClass;
        boolean isInsideUpdateFile = element.getContainingFile().getName().contains("Update");

        if (isDeclaration || isInsideUpdateFile) {
            result.add(createMarker(element, targetClass, PluginIcons.EMISSION, "Emission", EventEmissionSearcher::findEmissions));
        }

        if (!isInsideUpdateFile || isDeclaration) {
            result.add(createMarker(element, targetClass, PluginIcons.PROCESSING, "Processing", EventProcessingSearcher::findProcessing));
        }
    }

    private RelatedItemLineMarkerInfo<PsiElement> createMarker(PsiElement element, KtClassOrObject targetClass, Icon icon,
                                                               String title, BiFunction<KtClassOrObject, GlobalSearchScope, List<PsiElement>> searchFunc) {

        GutterIconNavigationHandler<PsiElement> navHandler = (mouseEvent, elt) -> {
            Editor editor = FileEditorManager.getInstance(elt.getProject()).getSelectedTextEditor();
            if (editor == null) return;

            String fqName = targetClass.getFqName() != null ? targetClass.getFqName().asString() : targetClass.getName();
            String lockKey = fqName + ":" + title;

            if (!SearchLock.tryLock(lockKey)) {
                showBalloon(mouseEvent, "Search already in progress", MessageType.INFO);
                return;
            }

            ProgressManager.getInstance().run(new Task.Backgroundable(elt.getProject(), "Go to " + title, true) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setIndeterminate(true);
                    try {
                        indicator.setText("Searching in module...");
                        List<PsiElement> targets = ReadAction.compute(() ->
                                searchFunc.apply(targetClass, ScopeBuilder.getModuleScope(elt)));

                        String scope = "module";
                        if ((targets == null || targets.isEmpty()) && !indicator.isCanceled()) {
                            indicator.setText("Searching in project...");
                            targets = ReadAction.compute(() ->
                                    searchFunc.apply(targetClass, ScopeBuilder.getProductionScope(elt)));
                            scope = "project";
                        }

                        if (indicator.isCanceled()) return;

                        final List<PsiElement> finalTargets = targets != null ? targets : List.of();
                        final String finalScope = scope;
                        ApplicationManager.getApplication().invokeLater(() -> {
                            showResults(finalTargets, title, finalScope, mouseEvent, elt);
                        });
                    } finally {
                        SearchLock.unlock(lockKey);
                    }
                }
            });
        };

        return new RelatedItemLineMarkerInfo<>(element, element.getTextRange(), icon, elt -> "Go to " + title, navHandler,
                GutterIconRenderer.Alignment.CENTER, () -> List.of());
    }

    private void showResults(List<PsiElement> targets, String title, String scope, MouseEvent mouseEvent, PsiElement elt) {
        if (targets.isEmpty()) {
            showBalloon(mouseEvent, "No " + title.toLowerCase() + " usages found", MessageType.INFO);
            return;
        }
        if (targets.size() == 1) {
            ((Navigatable) targets.getFirst()).navigate(true);
        } else {
            new PsiTargetNavigator<>(targets)
                    .presentationProvider(ContextPresentationProvider::getPresentation)
                    .createPopup(elt.getProject(), "Go to " + title + " — " + scope)
                    .show(new RelativePoint(mouseEvent));
        }
    }

    private void showBalloon(MouseEvent mouseEvent, String message, MessageType type) {
        JBPopupFactory.getInstance()
                .createHtmlTextBalloonBuilder(message, type, null)
                .setFadeoutTime(3000)
                .createBalloon()
                .show(new RelativePoint(mouseEvent), Balloon.Position.atRight);
    }

}