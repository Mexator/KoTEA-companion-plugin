package com.kotea.companion.navigation;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.kotea.companion.domain.KoTEARoleResolver;
import com.kotea.companion.domain.KoTEARoleResolver.Role;
import com.kotea.companion.util.KoteaCompanionLineMarkerInfo;
import com.kotea.companion.util.NavigableElement;
import com.kotea.companion.util.NavigableElementResolver;
import com.kotea.companion.util.PerfLog;
import com.kotea.companion.util.PluginIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtNameReferenceExpression;

import java.util.Collection;
import java.util.List;

/**
 * Puts both markers on the declaration of a navigable KoTEA class, and on each reference to it the marker that leads
 * to the opposite role.
 */
public class KoTEAMarkerProvider extends RelatedItemLineMarkerProvider {

    private static final Logger LOG = Logger.getInstance(KoTEAMarkerProvider.class);

    @Override
    public void collectSlowLineMarkers(@NotNull List<? extends PsiElement> elements,
                                       @NotNull Collection<? super LineMarkerInfo<?>> result) {
        long start = PerfLog.start();
        super.collectSlowLineMarkers(elements, result);
        PerfLog.warnIfSlow(LOG, "KoTEAMarkerProvider marker collection over " + elements.size()
                + " elements", start, 100);
    }

    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element,
                                            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (!(element instanceof LeafPsiElement)) return;
        long start = PerfLog.start();

        PsiElement parent = element.getParent();
        if (parent instanceof KtClassOrObject declaration && declaration.getNameIdentifier() == element) {
            if (NavigableElementResolver.resolve(declaration) != null) {
                result.add(marker(element, declaration, Role.EMISSION));
                result.add(marker(element, declaration, Role.PROCESSING));
            }
        } else if (parent instanceof KtNameReferenceExpression) {
            Role role = KoTEARoleResolver.getInstance(element.getProject()).roleOf(element);
            NavigableElement target = role != null ? NavigableElementResolver.resolve(element) : null;
            if (target != null) {
                result.add(marker(element, target.anchor(), role == Role.EMISSION ? Role.PROCESSING : Role.EMISSION));
            }
        }

        PerfLog.warnIfSlow(LOG, "KoTEAMarkerProvider marker collection for element " + element + " is slow", start, 10);
    }

    private static RelatedItemLineMarkerInfo<PsiElement> marker(PsiElement anchor, KtClassOrObject target, Role role) {
        String title = NavigationRunner.title(role);
        GutterIconNavigationHandler<PsiElement> navHandler = (mouseEvent, elt) ->
                NavigationRunner.navigate(target, role, elt, true, NavigationRunner.atMouse(mouseEvent));
        return new KoteaCompanionLineMarkerInfo<>(anchor, anchor.getTextRange(),
                role == Role.EMISSION ? PluginIcons.EMISSION : PluginIcons.PROCESSING,
                elt -> title, navHandler, GutterIconRenderer.Alignment.CENTER, List::of);
    }
}
