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
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.awt.RelativePoint;
import com.kotea.companion.util.ContextPresentationProvider;
import com.kotea.companion.util.PerfLog;
import com.kotea.companion.util.PluginIcons;
import com.kotea.companion.util.ScopeBuilder;
import com.kotea.companion.util.SearchLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.*;

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
        long classAdded = PerfLog.start();
        addConstructorCallMarker(element, result);
        long constructorAdded = PerfLog.start();
        addObjectMarker(element, result);
        long objectAdded = PerfLog.start();
        addInHandle(element, result);
        PerfLog.warnIfSlow(LOG, "CommandMarkerProvider marker collection for " + element + "is slow; " +
                "class: " + (classAdded - start) + "ms, " +
                "constructor: " + (constructorAdded - classAdded)/1_000_000 + "ms, " +
                "object: " + (objectAdded - constructorAdded)/1_000_000 + "ms, " +
                "inHandle: " + (PerfLog.start() - objectAdded)/1_000_000 + "ms",
                start, 10);
    }

    private void addClassMarker(PsiElement element, Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        UClass uCommand = UastContextKt.toUElement(element, UClass.class);

        if (uCommand == null || uCommand.getName() == null) return;

        PsiClass psiClass = uCommand.getJavaPsi();

        if (CommandUtil.isNavigableCommand(psiClass)) {

            PsiElement identifier = psiClass.getNameIdentifier();
            if (identifier == null) return;

           RelatedItemLineMarkerInfo<PsiElement> emissionMarker = getMarker(identifier, psiClass, PluginIcons.EMISSION, " Go to Emission", CommandEmissionSearcher::findEmission);
           result.add(emissionMarker);

           RelatedItemLineMarkerInfo<PsiElement> processingMarker = getMarker(identifier, psiClass, PluginIcons.PROCESSING, "Go to Processing", CommandProcessingSearcher::findProcessing);
           result.add(processingMarker);
        }
    }

    private void addConstructorCallMarker(PsiElement element, Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        UElement uElement = UastContextKt.toUElement(element, UElement.class);
        if (!(uElement instanceof UCallExpression callExpression)) return;
        PsiMethod constructor = callExpression.resolve();
        if (constructor != null && constructor.isConstructor()) {
            PsiClass constructedClass = constructor.getContainingClass();
            if (constructedClass != null && CommandUtil.isNavigableCommand(constructedClass)) {
                UClass uCommand = UastContextKt.toUElement(constructedClass, UClass.class);
                if (uCommand != null) {
                    RelatedItemLineMarkerInfo<PsiElement> marker = getMarker(element, constructedClass, PluginIcons.PROCESSING, "Processing", CommandProcessingSearcher::findProcessing);
                    result.add(marker);
                }
            }
        }
    }

    private void addObjectMarker(PsiElement element, Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        UElement uElement = UastContextKt.toUElement(element, UElement.class);
        if (uElement instanceof USimpleNameReferenceExpression ref) {
            UElement parent = uElement.getUastParent();
            if (parent == null) return;

            UCallExpression call = UastUtils.getParentOfType(uElement, UCallExpression.class);
            if (call != null) {
                if (call.getValueArguments().contains(uElement) || call.getValueArguments().contains(parent)) {
                    PsiElement res = ref.resolve();
                    if (res instanceof PsiClass psiClass && CommandUtil.isNavigableCommand(psiClass)) {
                        RelatedItemLineMarkerInfo<PsiElement> marker = getMarker(element, psiClass, PluginIcons.PROCESSING, "Processing", CommandProcessingSearcher::findProcessing);
                        result.add(marker);
                    }
                }
            }
        }
    }

    private void addInHandle(PsiElement element, Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        UElement uElement = UastContextKt.toUElement(element, UElement.class);

        if (!(uElement instanceof USimpleNameReferenceExpression ref)) return;

        PsiElement resolved = ref.resolve();
        if (!(resolved instanceof PsiClass targetCommand) || targetCommand.isInterface()) return;

        UClass uCommand = UastUtils.getParentOfType(ref, UClass.class);
        if (uCommand == null) return;

        if (!CommandUtil.isCommandsHandler(uCommand, targetCommand)) return;

        RelatedItemLineMarkerInfo<PsiElement> marker = getMarker(element, targetCommand, PluginIcons.EMISSION, "Emission", CommandEmissionSearcher::findEmission);

        result.add(marker);
    }

    private RelatedItemLineMarkerInfo<PsiElement> getMarker(PsiElement element, PsiClass targetCommand, Icon icon, String title,
                                                            BiFunction<UClass, GlobalSearchScope, List<PsiElement>> searchFunc) {
        GutterIconNavigationHandler<PsiElement> navHandler = (mouseEvent, elt) -> {
            Editor editor = FileEditorManager.getInstance(elt.getProject()).getSelectedTextEditor();
            if (editor == null) return;

            String className = targetCommand.getQualifiedName() != null
                    ? targetCommand.getQualifiedName() : targetCommand.getName();
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
                        UClass uClass = UastContextKt.toUElement(targetCommand, UClass.class);
                        long moduleSearchStart = PerfLog.start();
                        List<PsiElement> targets = ReadAction.compute(() ->
                                searchFunc.apply(uClass, ScopeBuilder.getModuleScope(element)));
                        PerfLog.logElapsed(LOG, "CommandMarkerProvider module-scope " + title + " search",
                                moduleSearchStart);

                        String scope = "module";
                        if ((targets == null || targets.isEmpty()) && !indicator.isCanceled()) {
                            indicator.setText("Searching in project...");
                            long projectSearchStart = PerfLog.start();
                            targets = ReadAction.compute(() ->
                                    searchFunc.apply(uClass, ScopeBuilder.getProductionScope(element)));
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

        return new RelatedItemLineMarkerInfo<>(
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
