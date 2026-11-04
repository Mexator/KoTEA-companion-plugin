package com.kotea.companion.navigation;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.kotea.companion.domain.KoTEAMarkerResolver;
import com.kotea.companion.domain.KoTEAMarkerResolver.Marker;
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
 * Puts "go to processing" and "go to emission" markers on the appropriate {@code PsiElement}s
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
                result.add(marker(element, declaration, Marker.TO_EMISSION));
                result.add(marker(element, declaration, Marker.TO_PROCESSING));
            }
        } else if (parent instanceof KtNameReferenceExpression) {
            Marker marker = KoTEAMarkerResolver.getInstance(element.getProject()).markerFor(element);
            NavigableElement target = marker != null ? NavigableElementResolver.resolve(element) : null;
            if (target != null) result.add(marker(element, target.anchor(), marker));
        }

        PerfLog.warnIfSlow(LOG, "KoTEAMarkerProvider marker collection for element " + element + " is slow", start, 10);
    }

    private static RelatedItemLineMarkerInfo<PsiElement> marker(
            PsiElement anchor,
            KtClassOrObject target,
            Marker marker
    ) {
        Role targetRole = marker == Marker.TO_EMISSION ? Role.EMISSION : Role.PROCESSING;
        String title = NavigationRunner.title(targetRole);
        GutterIconNavigationHandler<PsiElement> navHandler = (mouseEvent, elt) ->
                NavigationRunner.navigate(target, targetRole, NavigationRunner.atMouse(mouseEvent));
        return new KoteaCompanionLineMarkerInfo<>(anchor, anchor.getTextRange(),
                marker == Marker.TO_EMISSION ? PluginIcons.EMISSION : PluginIcons.PROCESSING,
                elt -> title, navHandler, GutterIconRenderer.Alignment.CENTER, List::of);
    }
}
