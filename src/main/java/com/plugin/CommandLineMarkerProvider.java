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
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import javax.swing.*;
import javax.swing.text.Element;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class CommandLineMarkerProvider extends RelatedItemLineMarkerProvider {

    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element,
                                            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {

        addClassMarker(element, result);
        addConstructorCallMarker(element, result);
        addObjectMarker(element, result);
        addInHandle(element, result);
    }

    private void addClassMarker(PsiElement element, Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        UClass uCommand = UastContextKt.toUElement(element, UClass.class);

        if (uCommand == null || uCommand.getName() == null) return;

        PsiClass psiClass = uCommand.getJavaPsi();

        if (psiClass.isInterface()) return;

        if (isCommand(psiClass)) {

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
            if (constructedClass != null && isCommand(constructedClass)) {
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
                    if (res instanceof PsiClass psiClass && isCommand(psiClass)) {
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

        if (!isCommandsHandler(uCommand, targetCommand)) return;


        RelatedItemLineMarkerInfo<PsiElement> marker = getMarker(element, targetCommand, PluginIcons.EMISSION, "Emission", CommandEmissionSearcher::findEmission);

        result.add(marker);
    }

    private RelatedItemLineMarkerInfo<PsiElement> getMarker(PsiElement element, PsiClass targetCommand, Icon icon, String title ,BiFunction<UClass, GlobalSearchScope, List<PsiElement>> searchFunc) {
        GutterIconNavigationHandler<PsiElement> navHandler = ((mouseEvent, element1) -> {
            Editor e = FileEditorManager.getInstance(element1.getProject()).getSelectedTextEditor();
            if (e != null) {
                RelativePoint point = new RelativePoint(mouseEvent);
                String project = element.getProject().getName();

                ApplicationManager.getApplication().executeOnPooledThread(() -> {
                    List<PsiElement> targets = ReadAction.compute(() -> {
                        UClass uClass = UastContextKt.toUElement(targetCommand, UClass.class);
                        List<PsiElement> result = searchFunc.apply(uClass, ScopeBuilder.getModuleScope(element));

                        if (result.isEmpty()) result = searchFunc.apply(uClass, ScopeBuilder.getProductionScope(element));

                        return result;
                    });

                    ApplicationManager.getApplication().invokeLater(() -> {
                        createLog(title, project);

                        navigation(targets, title, point, mouseEvent, element1);
                    });
                });
            }
        });

        return new RelatedItemLineMarkerInfo<>(
                element,
                element.getTextRange(),
                icon,
                elt -> title,
                navHandler,
                GutterIconRenderer.Alignment.CENTER,
                () -> List.of()
        );
    }

    private void navigation(List<PsiElement> targets, String title, RelativePoint point, MouseEvent mouseEvent, PsiElement elt) {
        if (targets.isEmpty()) {
            JBPopupFactory.getInstance()
                    .createHtmlTextBalloonBuilder("Not found for " + title, MessageType.INFO, null)
                    .setFadeoutTime(3000) // Исчезнет через 3 секунды
                    .createBalloon()
                    .show(point, Balloon.Position.atRight);
            return;
        }

        if (targets.size() == 1) {
            ((Navigatable) targets.getFirst()).navigate(true);
        }
        else {
            new PsiTargetNavigator<>(targets)
                    .presentationProvider(ContextPresentationProvider::getPresentation)
                    .createPopup(elt.getProject(), "Go to " + title)
                    .show(new RelativePoint(mouseEvent));
        }
    }

    private boolean isCommand(PsiClass psiClass) {
        for (PsiClass superClass : psiClass.getSupers()) {
            String name = superClass.getName();

            if (name != null && name.contains("Command") && !name.contains("Handler")) return true;
        }

        return false;
    }

    private boolean isCommandsHandler(UClass handlerClass, PsiClass commandClass) {
        PsiType commandType = JavaPsiFacade.getElementFactory(commandClass.getProject())
                .createType(commandClass);

        for (UTypeReferenceExpression superTypeRef : handlerClass.getUastSuperTypes()) {

            PsiType type = superTypeRef.getType();
            if (!(type instanceof PsiClassType classType)) continue;

            PsiClass resolved = classType.resolve();
            if (resolved == null || !"CommandsFlowHandler".equals(resolved.getName())) continue;

            PsiType[] params = classType.getParameters();
            if (params.length == 0) continue;

            PsiType firstParam = params[0];

            return firstParam.isAssignableFrom(commandType);
        }

        return false;
    }

    private void createLog(String title, String project) {
       AnalyticsService.log("gutter-icon", Map.of(
                "type", "Command",
                "feature", title,
               "project", project
        ));
    }
}