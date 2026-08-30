package com.kotea.companion.feature;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.kotea.companion.actions.GoToEmissionAction;
import com.kotea.companion.actions.GoToProcessingAction;
import com.kotea.companion.commands.CommandEmissionSearcher;
import com.kotea.companion.commands.CommandProcessingSearcher;
import com.kotea.companion.events.EventEmissionSearcher;
import com.kotea.companion.events.EventProcessingSearcher;
import com.kotea.companion.fixtures.KoTEAFixtureTestCase;
import com.kotea.companion.util.PluginIcons;
import com.kotea.companion.util.ScopeBuilder;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UastContextKt;

import javax.swing.Icon;
import javax.swing.JPanel;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.BiFunction;

public class NavigationTest extends KoTEAFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        openFixtureProject("featureCoverage");
    }

    public void testEventEmission() {
        Site expected = singleSearcherTarget("cov/Events.kt", "ItemClicked",
                EventEmissionSearcher::findEmissions);
        assertEquals("CovViewModel.kt", expected.file());

        assertActionLandsOn(new GoToEmissionAction(), "cov/Events.kt", "ItemClicked", expected);
        assertGutterLandsOn("cov/Events.kt", "ItemClicked", PluginIcons.EMISSION, expected);
    }

    public void testEventProcessing() {
        Site expected = singleSearcherTarget("cov/Events.kt", "ItemClicked",
                EventProcessingSearcher::findProcessing);
        assertEquals("CovUpdate.kt", expected.file());

        assertActionLandsOn(new GoToProcessingAction(), "cov/Events.kt", "ItemClicked", expected);
        assertGutterLandsOn("cov/Events.kt", "ItemClicked", PluginIcons.PROCESSING, expected);
    }

    public void testCommandEmission() {
        Site expected = singleSearcherTarget("cov/Commands.kt", "LoadItems",
                (kt, scope) -> CommandEmissionSearcher.findEmission(toUClass(kt), scope));
        assertEquals("CovUpdate.kt", expected.file());

        assertActionLandsOn(new GoToEmissionAction(), "cov/Commands.kt", "LoadItems", expected);
        assertGutterLandsOn("cov/Commands.kt", "LoadItems", PluginIcons.EMISSION, expected);
    }

    public void testCommandProcessing() {
        Site expected = singleSearcherTarget("cov/Commands.kt", "LoadItems",
                (kt, scope) -> CommandProcessingSearcher.findProcessing(toUClass(kt), scope));
        assertEquals("CovHandler.kt", expected.file());

        assertActionLandsOn(new GoToProcessingAction(), "cov/Commands.kt", "LoadItems", expected);
        assertGutterLandsOn("cov/Commands.kt", "LoadItems", PluginIcons.PROCESSING, expected);
    }

    // --- the searcher boundary: exactly one target ---

    private Site singleSearcherTarget(String declPath, String declName,
                                      BiFunction<KtClassOrObject, GlobalSearchScope, List<PsiElement>> search) {
        KtClassOrObject decl = ktClass(declPath, declName);
        GlobalSearchScope scope = ScopeBuilder.getProductionScope(getProject());
        List<PsiElement> targets = ReadAction.compute(() -> search.apply(decl, scope));
        assertSize(1, targets);
        return ReadAction.compute(() -> siteOf(targets.getFirst()));
    }

    // --- entry point 1: the keyboard action ---

    private void assertActionLandsOn(AnAction action, String declPath, String declName, Site expected) {
        PsiFile file = myFixture.configureFromTempProjectFile(declPath);
        KtClassOrObject decl = findKtClass(file, declName);

        DataContext context = SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, getProject())
                .add(CommonDataKeys.EDITOR, myFixture.getEditor())
                .add(CommonDataKeys.PSI_ELEMENT, decl)
                .build();
        AnActionEvent event = TestActionEvent.createTestEvent(action, context);

        ActionUtil.performAction(action, event);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        assertLandedOn(expected);
    }

    // --- entry point 2: the gutter icon on the declaration ---

    private void assertGutterLandsOn(String declPath, String declName, Icon icon, Site expected) {
        PsiFile file = myFixture.configureFromTempProjectFile(declPath);
        KtClassOrObject decl = findKtClass(file, declName);
        int declOffset = decl.getNameIdentifier().getTextRange().getStartOffset();

        GutterIconNavigationHandler<PsiElement> handler = null;
        PsiElement anchor = null;
        for (GutterMark gutter : myFixture.findAllGutters()) {
            if (gutter.getIcon() != icon) continue;
            if (!(gutter instanceof LineMarkerInfo.LineMarkerGutterIconRenderer<?> renderer)) continue;
            LineMarkerInfo<?> info = renderer.getLineMarkerInfo();
            if (info.getElement() == null || !info.getElement().getTextRange().containsOffset(declOffset)) continue;
            //noinspection unchecked
            handler = (GutterIconNavigationHandler<PsiElement>) info.getNavigationHandler();
            anchor = info.getElement();
            break;
        }
        assertNotNull("no " + iconName(icon) + " gutter on declaration " + declName, handler);

        MouseEvent mouseEvent = new MouseEvent(new JPanel(), MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(), 0, 0, 0, 1, false);
        handler.navigate(mouseEvent, anchor);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        assertLandedOn(expected);
    }

    private void assertLandedOn(Site expected) {
        Editor selected = FileEditorManager.getInstance(getProject()).getSelectedTextEditor();
        assertNotNull("navigation opened no editor", selected);
        String fileName = FileDocumentManager.getInstance().getFile(selected.getDocument()).getName();
        int caret = selected.getCaretModel().getOffset();
        assertEquals("navigated to the wrong file", expected.file(), fileName);
        assertTrue("caret " + caret + " outside the expected target range " + expected,
                caret >= expected.start() && caret <= expected.end());
    }

    // --- helpers ---

    private record Site(String file, int start, int end, String text) {}

    private static Site siteOf(PsiElement element) {
        return new Site(element.getContainingFile().getName(),
                element.getTextRange().getStartOffset(), element.getTextRange().getEndOffset(), element.getText());
    }

    private UClass toUClass(KtClassOrObject ktClass) {
        return UastContextKt.toUElement(ktClass, UClass.class);
    }

    private static String iconName(Icon icon) {
        return icon == PluginIcons.EMISSION ? "Emission" : "Processing";
    }
}
