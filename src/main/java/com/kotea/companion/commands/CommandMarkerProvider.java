package com.kotea.companion.commands;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.PsiTargetNavigator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.ui.awt.RelativePoint;
import com.kotea.companion.domain.KoTEARoleResolver;
import com.kotea.companion.util.ContextPresentationProvider;
import com.kotea.companion.util.KoteaCompanionLineMarkerInfo;
import com.kotea.companion.util.PerfLog;
import com.kotea.companion.util.PluginIcons;
import com.kotea.companion.util.ScopeBuilder;
import com.kotea.companion.util.SearchLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.*;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;

public class CommandMarkerProvider extends RelatedItemLineMarkerProvider {

    private static final Logger LOG = Logger.getInstance(CommandMarkerProvider.class);

    @Override
    public void collectSlowLineMarkers(@NotNull List<? extends PsiElement> elements,
                                        @NotNull Collection<? super LineMarkerInfo<?>> result) {
        long start = PerfLog.start();
        super.collectSlowLineMarkers(elements, result);
        PerfLog.warnIfSlow(LOG, "CommandMarkerProvider marker collection over " + elements.size()
                + " elements", start, 100);
    }

    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element,
                                            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        long start = PerfLog.start();

        addClassMarker(element, result);
        long afterClass = System.nanoTime();
        addReferenceMarker(element, result);
        long afterReference = System.nanoTime();

        PerfLog.warnIfSlow(LOG, "CommandMarkerProvider marker collection for " + element + " is slow; " +
                "class: " + (afterClass - start) / 1_000_000 + " ms, " +
                "reference: " + (afterReference - afterClass) / 1_000_000 + " ms",
                start, 10);
    }

    private void addClassMarker(PsiElement element, Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (!(element instanceof KtClassOrObject command) || !CommandUtil.isNavigableCommandClass(command)) return;

        PsiElement identifier = command.getNameIdentifier();
        if (identifier == null) return;

        result.add(getMarker(identifier, command, PluginIcons.EMISSION, " Go to Emission", CommandEmissionSearcher::findEmission));
        result.add(getMarker(identifier, command, PluginIcons.PROCESSING, "Go to Processing", CommandProcessingSearcher::findProcessing));
    }

    private void addReferenceMarker(PsiElement element, Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (!(element instanceof LeafPsiElement) || !(element.getParent() instanceof KtNameReferenceExpression)) return;

        KtClassOrObject command = CommandUtil.tryResolveToClass(element);
        if (command == null) return;

        KoTEARoleResolver.Role role = KoTEARoleResolver.getInstance(element.getProject()).roleOf(element);
        if (role == KoTEARoleResolver.Role.PROCESSING) {
            result.add(getMarker(element, command, PluginIcons.EMISSION, "Emission", CommandEmissionSearcher::findEmission));
        } else if (role == KoTEARoleResolver.Role.EMISSION) {
            result.add(getMarker(element, command, PluginIcons.PROCESSING, "Processing", CommandProcessingSearcher::findProcessing));
        }
    }

    private RelatedItemLineMarkerInfo<PsiElement> getMarker(PsiElement element, KtClassOrObject targetCommand, Icon icon, String title,
                                                            BiFunction<KtClassOrObject, GlobalSearchScope, List<PsiElement>> searchFunc) {
        GutterIconNavigationHandler<PsiElement> navHandler = (mouseEvent, elt) -> {
            Editor editor = FileEditorManager.getInstance(elt.getProject()).getSelectedTextEditor();
            if (editor == null) return;

            String className = targetCommand.getFqName() != null
                    ? targetCommand.getFqName().asString() : targetCommand.getName();
            String lockKey = className + ":" + title;

            if (!SearchLock.tryLock(lockKey)) {
                showBalloon(mouseEvent, "Search already in progress", MessageType.INFO);
                return;
            }

            ProgressManager.getInstance().run(new Task.Backgroundable(elt.getProject(), title, true) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setIndeterminate(true);
                    try {
                        indicator.setText("Searching in module...");
                        long moduleSearchStart = PerfLog.start();
                        List<PsiElement> targets = ReadAction.compute(() ->
                                searchFunc.apply(targetCommand, ScopeBuilder.getModuleScope(element)));
                        PerfLog.logElapsed(LOG, "CommandMarkerProvider module-scope " + title + " search",
                                moduleSearchStart);

                        String scope = "module";
                        if ((targets == null || targets.isEmpty()) && !indicator.isCanceled()) {
                            indicator.setText("Searching in project...");
                            long projectSearchStart = PerfLog.start();
                            targets = ReadAction.compute(() ->
                                    searchFunc.apply(targetCommand, ScopeBuilder.getProductionScope(element)));
                            PerfLog.logElapsed(LOG, "CommandMarkerProvider project-scope " + title + " search",
                                    projectSearchStart);
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

        return new KoteaCompanionLineMarkerInfo<>(
                element,
                element.getTextRange(),
                icon,
                elt -> title,
                navHandler,
                GutterIconRenderer.Alignment.CENTER,
                List::of
        );
    }

    private void showResults(List<PsiElement> targets, String title, String scope, MouseEvent mouseEvent, PsiElement elt) {
        if (targets.isEmpty()) {
            showBalloon(mouseEvent, "No " + title.trim().toLowerCase() + " usages found", MessageType.INFO);
            return;
        }
        if (targets.size() == 1) {
            ((Navigatable) targets.getFirst()).navigate(true);
        } else {
            new PsiTargetNavigator<>(targets)
                    .presentationProvider(ContextPresentationProvider::getPresentation)
                    .createPopup(elt.getProject(), title.trim() + " — " + scope)
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
