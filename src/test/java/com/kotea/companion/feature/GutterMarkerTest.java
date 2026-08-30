package com.kotea.companion.feature;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.kotea.companion.fixtures.KoTEAFixtureTestCase;
import com.kotea.companion.util.PluginIcons;
import org.jetbrains.kotlin.psi.KtClassOrObject;

import javax.swing.Icon;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GutterMarkerTest extends KoTEAFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        openFixtureProject("featureCoverage");
    }

    public void testGuttersOnConcreteEventDeclarationsOnly() {
        assertEquals(Set.of("ItemClicked", "BackPressed"), declarationsWithKoTEAGutter("cov/Events.kt"));
    }

    public void testGuttersOnConcreteCommandDeclarationsOnly() {
        assertEquals(Set.of("LoadItems", "Refresh"), declarationsWithKoTEAGutter("cov/Commands.kt"));
    }

    public void testGuttersOnEmissionAndProcessingSites() {
        assertTrue("Event Emission site in CovViewModel.kt", fileHasKoTEAGutter("cov/CovViewModel.kt"));
        assertTrue("Event Processing + Command Emission sites in CovUpdate.kt",
                fileHasKoTEAGutter("cov/CovUpdate.kt"));
        assertTrue("Command Processing site in CovHandler.kt", fileHasKoTEAGutter("cov/CovHandler.kt"));
    }

    /** Simple names of the classes in {@code relativePath} whose name identifier carries a KoTEA gutter. */
    private Set<String> declarationsWithKoTEAGutter(String relativePath) {
        PsiFile file = myFixture.configureFromTempProjectFile(relativePath);
        List<GutterMark> gutters = findKoTEAGutters();

        Set<String> marked = new HashSet<>();
        for (KtClassOrObject cls : PsiTreeUtil.findChildrenOfType(file, KtClassOrObject.class)) {
            PsiElement nameId = cls.getNameIdentifier();
            if (nameId == null) continue;
            int offset = nameId.getTextRange().getStartOffset();
            if (ContainerUtil.exists(gutters, gutter -> markerCovers(gutter, offset))) {
                marked.add(cls.getName());
            }
        }
        return marked;
    }

    private boolean fileHasKoTEAGutter(String relativePath) {
        myFixture.configureFromTempProjectFile(relativePath);
        return !findKoTEAGutters().isEmpty();
    }

    /** Emission / Processing gutters in the currently configured file. */
    private List<GutterMark> findKoTEAGutters() {
        return ContainerUtil.filter(myFixture.findAllGutters(), GutterMarkerTest::isKoTEAGutter);
    }

    private static boolean isKoTEAGutter(GutterMark gutter) {
        Icon icon = gutter.getIcon();
        return icon == PluginIcons.EMISSION || icon == PluginIcons.PROCESSING;
    }

    /** True if {@code gutter} is a line marker whose anchor element's range contains {@code offset}. */
    private static boolean markerCovers(GutterMark gutter, int offset) {
        if (!(gutter instanceof LineMarkerInfo.LineMarkerGutterIconRenderer<?> renderer)) return false;
        PsiElement element = renderer.getLineMarkerInfo().getElement();
        return element != null && element.getTextRange() != null && element.getTextRange().containsOffset(offset);
    }
}
