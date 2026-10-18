package com.kotea.companion.feature;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import com.kotea.companion.commands.CommandEmissionSearcher;
import com.kotea.companion.commands.CommandProcessingSearcher;
import com.kotea.companion.events.EventEmissionSearcher;
import com.kotea.companion.events.EventProcessingSearcher;
import com.kotea.companion.fixtures.KoTEAFixtureTestCase;
import com.kotea.companion.util.PluginIcons;
import com.kotea.companion.util.ScopeBuilder;
import org.jetbrains.kotlin.psi.KtClassOrObject;

import javax.swing.Icon;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class RoleHeuristicsTest extends KoTEAFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        openFixtureProject("roleHeuristics");
    }

    public void testEventCheckedOutsideUpdate_isProcessing() {
        assertEquals(Set.of("Analytics.kt:6"), sites("Reset", EventProcessingSearcher::findProcessing));
        assertEquals(Set.of("DecoratorUpdate.kt:9", "Analytics.kt:7"), sites("Clicked", EventProcessingSearcher::findProcessing));
        assertEquals(Set.of(PluginIcons.EMISSION), guttersAt("rh/Analytics.kt", "is RhEvent.Reset", "Reset"));
    }

    public void testEventConstructedInsideDecoratorUpdate_isEmission() {
        assertEquals(Set.of("DecoratorUpdate.kt:11"), sites("InvalidateCache", EventEmissionSearcher::findEmissions));
        assertEquals(Set.of("RealUpdate.kt:8"), sites("InvalidateCache", EventProcessingSearcher::findProcessing));
        assertEquals(Set.of(PluginIcons.PROCESSING),
                guttersAt("rh/DecoratorUpdate.kt", "RealEvent.InvalidateCache,", "InvalidateCache"));
    }

    public void testEventEmissions_includeObjectsAndCallableReferences() {
        assertEquals(Set.of("RhScreen.kt:8"), sites("Reset", EventEmissionSearcher::findEmissions));
        assertEquals(Set.of("RhScreen.kt:12"), sites("Clicked", EventEmissionSearcher::findEmissions));
        assertEquals(Set.of("RhHandler.kt:11"), sites("Refresh", EventEmissionSearcher::findEmissions));
    }

    public void testTypeOnlyUsages_haveNoGutter() {
        assertEquals(Set.of(), guttersAt("rh/RhScreen.kt", "clicked: RhEvent.Clicked", "Clicked"));
        assertEquals(Set.of(), guttersAt("rh/RhScreen.kt", "RhEvent.Refresh::class", "Refresh"));
    }

    public void testCommandProcessing_staysInsideCommandsFlowHandler() {
        assertEquals(Set.of("RhHandler.kt:11"), sites("Load", CommandProcessingSearcher::findProcessing));
        assertEquals(Set.of("DecoratorUpdate.kt:14", "RealUpdate.kt:8"), sites("Load", CommandEmissionSearcher::findEmission));
        assertEquals(Set.of(PluginIcons.EMISSION), guttersAt("rh/RhHandler.kt", "filterIsInstance<RhCommand.Load>", "Load"));
        assertEquals(Set.of(), guttersAt("rh/Analytics.kt", "is RhCommand.Load", "Load"));
    }

    /** {@code file:line} of each site {@code search} finds for the class {@code name} in Contract.kt. */
    private Set<String> sites(String name, BiFunction<KtClassOrObject, GlobalSearchScope, List<PsiElement>> search) {
        KtClassOrObject target = ktClass("rh/Contract.kt", name);
        GlobalSearchScope scope = ScopeBuilder.getProductionScope(getProject());
        return ReadAction.compute(() -> search.apply(target, scope).stream().map(site -> {
            PsiFile file = site.getContainingFile();
            Document document = file.getViewProvider().getDocument();
            return file.getName() + ":" + (document.getLineNumber(site.getTextRange().getStartOffset()) + 1);
        }).collect(Collectors.toSet()));
    }

    /** KoTEA gutter icons on {@code name} inside the first occurrence of {@code fragment} in {@code path}. */
    private Set<Icon> guttersAt(String path, String fragment, String name) {
        PsiFile file = myFixture.configureFromTempProjectFile(path);
        int fragmentOffset = file.getText().indexOf(fragment);
        assertTrue("no '" + fragment + "' in " + path, fragmentOffset >= 0);
        int offset = fragmentOffset + fragment.indexOf(name);
        List<GutterMark> gutters = ContainerUtil.filter(myFixture.findAllGutters(), KoTEAFixtureTestCase::isKoTEAGutter);
        return gutters.stream()
                .filter(gutter -> markerCovers(gutter, offset))
                .map(GutterMark::getIcon)
                .collect(Collectors.toSet());
    }
}
