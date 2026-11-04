package com.kotea.companion.domain.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.kotea.companion.domain.KoTEAMarkerResolver;
import com.kotea.companion.domain.KoTEAMarkerResolver.Marker;
import com.kotea.companion.fixtures.KoTEAFixtureTestCase;

import static com.kotea.companion.domain.KoTEAMarkerResolver.Marker.TO_EMISSION;
import static com.kotea.companion.domain.KoTEAMarkerResolver.Marker.TO_PROCESSING;

public class KoTEAMarkerResolverImplPsiTest extends KoTEAFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        openFixtureProject("roleHeuristics");
    }

    public void testReferenceWithRole_leadsToOppositeRole() {
        assertEquals(TO_PROCESSING, markerAt("rh/DecoratorUpdate.kt", "news(RhNews.Toast)", "Toast"));
        assertEquals(TO_EMISSION, markerAt("rh/DecoratorUpdate.kt", "is RhEvent.Clicked", "Clicked"));
    }

    public void testEventParameterOfUpdateHelper_leadsToEmission() {
        assertEquals(TO_EMISSION, markerAt("rh/DecoratorUpdate.kt", "event: RhEvent.Clicked", "Clicked"));
        assertNull(markerAt("rh/DecoratorUpdate.kt", "event: RhEvent.Clicked", "RhEvent"));
    }

    public void testNewsAndCommandParametersOfUpdateHelpers_haveNoMarker() {
        assertNull(markerAt("rh/DecoratorUpdate.kt", "news: RhNews.Toast", "Toast"));
        assertNull(markerAt("rh/DecoratorUpdate.kt", "command: RhCommand.Load", "Load"));
    }

    public void testEventParameterOutsideUpdate_hasNoMarker() {
        assertNull(markerAt("rh/RhScreen.kt", "clicked: RhEvent.Clicked", "Clicked"));
    }

    private Marker markerAt(String path, String fragment, String name) {
        PsiFile file = myFixture.configureFromTempProjectFile(path);
        return ReadAction.compute(() -> {
            int fragmentOffset = file.getText().indexOf(fragment);
            assertTrue("no '" + fragment + "' in " + path, fragmentOffset >= 0);
            PsiElement leaf = file.findElementAt(fragmentOffset + fragment.indexOf(name));
            return KoTEAMarkerResolver.getInstance(getProject()).markerFor(leaf);
        });
    }
}
