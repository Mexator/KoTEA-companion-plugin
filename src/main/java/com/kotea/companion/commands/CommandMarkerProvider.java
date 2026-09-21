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
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.awt.RelativePoint;
import com.kotea.companion.util.ContextPresentationProvider;
import com.kotea.companion.util.KoteaCompanionLineMarkerInfo;
import com.kotea.companion.util.PerfLog;
import com.kotea.companion.util.PluginIcons;
import com.kotea.companion.util.ScopeBuilder;
import com.kotea.companion.util.SearchLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
        addConstructorCallMarker(element, result);
        long afterConstructor = System.nanoTime();
        addObjectMarker(element, result);
        long afterObject = System.nanoTime();
        addInHandle(element, result);
        long afterInHandle = System.nanoTime();

        PerfLog.warnIfSlow(LOG, "CommandMarkerProvider marker collection for " + element + " is slow; " +
                "class: " + (afterClass - start) / 1_000_000 + " ms, " +
                "constructor: " + (afterConstructor - afterClass) / 1_000_000 + " ms, " +
                "object: " + (afterObject - afterConstructor) / 1_000_000 + " ms, " +
                "inHandle: " + (afterInHandle - afterObject) / 1_000_000 + " ms",
                start, 10);
    }

    private void addClassMarker(PsiElement element, Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (!(element instanceof KtClassOrObject command) || !CommandUtil.isNavigableCommandClass(command)) return;

        PsiElement identifier = command.getNameIdentifier();
        if (identifier == null) return;

        result.add(getMarker(identifier, command, PluginIcons.EMISSION, " Go to Emission", CommandEmissionSearcher::findEmission));
        result.add(getMarker(identifier, command, PluginIcons.PROCESSING, "Go to Processing", CommandProcessingSearcher::findProcessing));
    }

    private void addConstructorCallMarker(PsiElement element, Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (!(element instanceof KtCallExpression call)) return;
        if (!(call.getCalleeExpression() instanceof KtReferenceExpression callee)) return;

        PsiElement resolved = resolve(callee);
        KtClassOrObject constructedClass = resolved instanceof KtConstructor<?> constructor
                ? constructor.getContainingClassOrObject()
                : resolved instanceof KtClass ktClass ? ktClass : null;
        if (constructedClass != null && CommandUtil.isNavigableCommandClass(constructedClass)) {
            result.add(getMarker(element, constructedClass, PluginIcons.PROCESSING, "Processing", CommandProcessingSearcher::findProcessing));
        }
    }

    private void addObjectMarker(PsiElement element, Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (!(element instanceof KtNameReferenceExpression ref) || !isCallArgument(ref)) return;

        if (resolve(ref) instanceof KtClassOrObject command && CommandUtil.isNavigableCommandClass(command)) {
            result.add(getMarker(element, command, PluginIcons.PROCESSING, "Processing", CommandProcessingSearcher::findProcessing));
        }
    }

    private void addInHandle(PsiElement element, Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (!(element instanceof KtNameReferenceExpression ref)) return;

        KtClassOrObject handler = PsiTreeUtil.getParentOfType(ref, KtClassOrObject.class);
        if (handler == null) return;

        if (!(resolve(ref) instanceof KtClassOrObject targetCommand)) return;
        if (targetCommand instanceof KtClass ktClass && ktClass.isInterface()) return;

        if (!CommandUtil.isCommandsHandler(handler, targetCommand)) return;

        result.add(getMarker(element, targetCommand, PluginIcons.EMISSION, "Emission", CommandEmissionSearcher::findEmission));
    }

    /** {@code ref} is a call argument by itself, or as either side of a qualified expression. */
    private static boolean isCallArgument(KtNameReferenceExpression ref) {
        PsiElement parent = ref.getParent();
        if (parent instanceof KtQualifiedExpression) parent = parent.getParent();
        return parent instanceof KtValueArgument
                && parent.getParent() instanceof KtValueArgumentList list
                && list.getParent() instanceof KtCallExpression;
    }

    @Nullable
    private static PsiElement resolve(KtReferenceExpression ref) {
        PsiReference reference = ref.getReference();
        PsiElement resolved = reference != null ? reference.resolve() : null;
        return resolved != null ? resolved.getNavigationElement() : null;
    }

    private RelatedItemLineMarkerInfo<PsiElement> getMarker(PsiElement element, KtClassOrObject targetCommand, Icon icon, String title,
                                                            BiFunction<KtClassOrObject, GlobalSearchScope, List<PsiElement>> searchFunc) {
        PsiElement anchor = leafOf(element);

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
                                searchFunc.apply(targetCommand, ScopeBuilder.getModuleScope(anchor)));
                        PerfLog.logElapsed(LOG, "CommandMarkerProvider module-scope " + title + " search",
                                moduleSearchStart);

                        String scope = "module";
                        if ((targets == null || targets.isEmpty()) && !indicator.isCanceled()) {
                            indicator.setText("Searching in project...");
                            long projectSearchStart = PerfLog.start();
                            targets = ReadAction.compute(() ->
                                    searchFunc.apply(targetCommand, ScopeBuilder.getProductionScope(anchor)));
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
                anchor,
                anchor.getTextRange(),
                icon,
                elt -> title,
                navHandler,
                GutterIconRenderer.Alignment.CENTER,
                List::of
        );
    }

    /** First leaf under {@code element} (or {@code element} itself if it has no children). */
    private static PsiElement leafOf(PsiElement element) {
        PsiElement leaf = element;
        while (leaf.getFirstChild() != null) {
            leaf = leaf.getFirstChild();
        }
        return leaf;
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
