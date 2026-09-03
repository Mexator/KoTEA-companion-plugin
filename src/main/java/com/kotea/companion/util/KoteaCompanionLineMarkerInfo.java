package com.kotea.companion.util;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.MarkupEditorFilter;
import com.intellij.openapi.editor.markup.MarkupEditorFilterFactory;
import com.intellij.openapi.util.NotNullFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.Collection;

/**
 * Subclass to suppress gutter icons inside diff viewers
 */
public class KoteaCompanionLineMarkerInfo<T extends PsiElement> extends RelatedItemLineMarkerInfo<T> {

    public KoteaCompanionLineMarkerInfo(@NotNull T element,
                                        @NotNull TextRange range,
                                        Icon icon,
                                        @Nullable Function<? super T, String> tooltipProvider,
                                        @Nullable GutterIconNavigationHandler<T> navHandler,
                                        @NotNull GutterIconRenderer.Alignment alignment,
                                        @NotNull NotNullFactory<? extends Collection<? extends GotoRelatedItem>> targets) {
        super(element, range, icon, tooltipProvider, navHandler, alignment, targets);
    }

    @Override
    public @NotNull MarkupEditorFilter getEditorFilter() {
        return MarkupEditorFilterFactory.createIsNotDiffFilter();
    }
}
