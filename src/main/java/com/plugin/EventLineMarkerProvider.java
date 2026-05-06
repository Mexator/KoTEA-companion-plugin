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
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

        GlobalSearchScope scope = ScopeBuilder.getProductionScope(element);

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

            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                List<PsiElement> targets = ReadAction.compute(() -> {
                    List<PsiElement> res = searchFunc.apply(targetClass, ScopeBuilder.getModuleScope(elt));
                    if (res == null || res.isEmpty()) {
                        res = searchFunc.apply(targetClass, ScopeBuilder.getProductionScope(elt));
                    }
                    return res;
                });
                ApplicationManager.getApplication().invokeLater(() -> {
                    createLog("Go to " + title, element.getProject().getName());

                    if (targets.isEmpty()) {
                        JBPopupFactory.getInstance()
                                .createHtmlTextBalloonBuilder("Not found for " + title, MessageType.INFO, null)
                                .setFadeoutTime(3000) // Исчезнет через 3 секунды
                                .createBalloon()
                                .show(new RelativePoint(mouseEvent), Balloon.Position.atRight);
                        return;
                    }

                    if (targets.size() == 1) {
                        ((Navigatable) targets.getFirst()).navigate(true);
                    } else {
                        new PsiTargetNavigator<>(targets)
                                .presentationProvider(ContextPresentationProvider::getPresentation)
                                .createPopup(elt.getProject(), "Go to " + title)
                                .show(new RelativePoint(mouseEvent));

                    }
                });
            });
        };

        return new RelatedItemLineMarkerInfo<>(element, element.getTextRange(), icon, elt -> "Go to " + title, navHandler,
                GutterIconRenderer.Alignment.CENTER, () -> List.of());
    }

    private void createLog(String title, String project) {
        AnalyticsService.log("gutter-icon", Map.of(
                "type", "Event",
                "feature", title,
                "project", project
        ));
    }
}