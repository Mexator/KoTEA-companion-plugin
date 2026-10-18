package com.kotea.companion.domain.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.kotea.companion.domain.KoTEARoleResolver;
import com.kotea.companion.domain.KoTEARoleResolver.Role;
import com.kotea.companion.fixtures.KoTEAFixtureTestCase;

import static com.kotea.companion.domain.KoTEARoleResolver.Role.EMISSION;
import static com.kotea.companion.domain.KoTEARoleResolver.Role.PROCESSING;

public class KoTEARoleResolverImplPsiTest extends KoTEAFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        openFixtureProject("roleHeuristics");
    }

    public void testEventValueUsages_areEmissionsAnywhere() {
        assertEquals(EMISSION, roleAt("rh/DecoratorUpdate.kt", "RealEvent.InvalidateCache,", "InvalidateCache"));
        assertEquals(EMISSION, roleAt("rh/RhScreen.kt", "sink += RhEvent.Reset", "Reset"));
        assertEquals(EMISSION, roleAt("rh/RhScreen.kt", "RhEvent::Clicked", "Clicked"));
        assertEquals(EMISSION, roleAt("rh/RhHandler.kt", "map { RhEvent.Refresh }", "Refresh"));
    }

    public void testEventCheckUsages_areProcessingAnywhere() {
        assertEquals(PROCESSING, roleAt("rh/DecoratorUpdate.kt", "is RhEvent.Clicked", "Clicked"));
        assertEquals(PROCESSING, roleAt("rh/DecoratorUpdate.kt", "RhEvent.Refresh ->", "Refresh"));
        assertEquals(PROCESSING, roleAt("rh/RealUpdate.kt", "event == RealEvent.InvalidateCache", "InvalidateCache"));
        assertEquals(PROCESSING, roleAt("rh/Analytics.kt", "is RhEvent.Reset", "Reset"));
        assertEquals(PROCESSING, roleAt("rh/Analytics.kt", "as? RhEvent.Clicked", "Clicked"));
    }

    public void testOtherUsages_haveNoRole() {
        assertNull(roleAt("rh/RhScreen.kt", "clicked: RhEvent.Clicked", "Clicked"));
        assertNull(roleAt("rh/RhScreen.kt", "RhEvent.Refresh::class", "Refresh"));
        assertNull(roleAt("rh/RhScreen.kt", "sink += RhEvent.Reset", "RhEvent"));
    }

    public void testNewsUsages() {
        assertEquals(EMISSION, roleAt("rh/DecoratorUpdate.kt", "news(RhNews.Toast)", "Toast"));
        assertEquals(PROCESSING, roleAt("rh/RhScreen.kt", "RhNews.Toast ->", "Toast"));
    }

    public void testCommandUsages_areProcessingOnlyInsideCommandsFlowHandler() {
        assertEquals(EMISSION, roleAt("rh/DecoratorUpdate.kt", "RhCommand.Delegate(", "Delegate"));
        assertEquals(EMISSION, roleAt("rh/RealUpdate.kt", "RhCommand.Load(2)", "Load"));
        assertEquals(PROCESSING, roleAt("rh/RhHandler.kt", "filterIsInstance<RhCommand.Load>", "Load"));
        assertNull(roleAt("rh/Analytics.kt", "is RhCommand.Load", "Load"));
    }

    /** The role of the reference to {@code name} inside the first occurrence of {@code fragment} in {@code path}. */
    private Role roleAt(String path, String fragment, String name) {
        PsiFile file = myFixture.configureFromTempProjectFile(path);
        return ReadAction.compute(() -> {
            int fragmentOffset = file.getText().indexOf(fragment);
            assertTrue("no '" + fragment + "' in " + path, fragmentOffset >= 0);
            PsiElement leaf = file.findElementAt(fragmentOffset + fragment.indexOf(name));
            return KoTEARoleResolver.getInstance(getProject()).roleOf(leaf);
        });
    }
}
