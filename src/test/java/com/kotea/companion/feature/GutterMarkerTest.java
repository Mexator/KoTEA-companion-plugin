package com.kotea.companion.feature;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.markup.MarkupEditorFilter;
import com.intellij.util.containers.ContainerUtil;
import com.kotea.companion.fixtures.KoTEAFixtureTestCase;
import com.kotea.companion.util.PluginIcons;

import javax.swing.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GutterMarkerTest extends KoTEAFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        openFixtureProject("featureCoverage");
    }

    public void testGuttersOnConcreteEventDeclarationsOnly() {
        assertEquals(Set.of("ItemClicked", "BackPressed"), koTEAGutterOwners("cov/Events.kt"));
    }

    public void testGuttersOnConcreteCommandDeclarationsOnly() {
        assertEquals(Set.of("LoadItems", "Refresh"), koTEAGutterOwners("cov/Commands.kt"));
    }

    public void testGuttersOnEmissionAndProcessingSites() {
        assertTrue("Event Emission site in CovViewModel.kt", fileHasKoTEAGutter("cov/CovViewModel.kt"));
        assertTrue("Event Processing + Command Emission sites in CovUpdate.kt",
                fileHasKoTEAGutter("cov/CovUpdate.kt"));
        assertTrue("Command Processing site in CovHandler.kt", fileHasKoTEAGutter("cov/CovHandler.kt"));
    }

    public void testConcreteNewsGetsNoCommandGutter_concreteCommandKeepsBoth() {
        openFixtureProject("nearestRoot");
        Map<String, Set<Icon>> gutters = koTEAGuttersByClass("nr/Contract.kt");

        assertEquals(Set.of("NrLoad"), gutters.keySet());
        assertEquals(Set.of(PluginIcons.EMISSION, PluginIcons.PROCESSING), gutters.get("NrLoad"));
    }

    public void testKoTEAGuttersAreHiddenInDiffViewersButNotInNormalEditors() {
        myFixture.configureFromTempProjectFile("cov/Events.kt");
        List<GutterMark> koTEAGutters = ContainerUtil.filter(myFixture.findAllGutters(), KoTEAFixtureTestCase::isKoTEAGutter);
        assertFalse("fixture is expected to carry KoTEA gutters", koTEAGutters.isEmpty());

        EditorFactory editorFactory = EditorFactory.getInstance();
        Editor diffEditor = editorFactory.createViewer(myFixture.getEditor().getDocument(), getProject(), EditorKind.DIFF);
        Editor mainEditor = editorFactory.createViewer(myFixture.getEditor().getDocument(), getProject(), EditorKind.MAIN_EDITOR);

        try {
            for (GutterMark gutter : koTEAGutters) {
                MarkupEditorFilter filter = ((LineMarkerInfo.LineMarkerGutterIconRenderer<?>) gutter).getLineMarkerInfo().getEditorFilter();
                assertFalse("KoTEA gutter must not render in a diff viewer", filter.avaliableIn(diffEditor));
                assertTrue("KoTEA gutter must still render in a normal editor", filter.avaliableIn(mainEditor));
            }
        } finally {
            editorFactory.releaseEditor(diffEditor);
            editorFactory.releaseEditor(mainEditor);
        }
    }

    private boolean fileHasKoTEAGutter(String relativePath) {
        myFixture.configureFromTempProjectFile(relativePath);
        List<GutterMark> koTEAGutters = ContainerUtil.filter(myFixture.findAllGutters(), KoTEAFixtureTestCase::isKoTEAGutter);
        return !koTEAGutters.isEmpty();
    }
}
