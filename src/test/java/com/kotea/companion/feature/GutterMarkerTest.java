package com.kotea.companion.feature;

import com.intellij.codeInsight.daemon.GutterMark;
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

    private boolean fileHasKoTEAGutter(String relativePath) {
        myFixture.configureFromTempProjectFile(relativePath);
        List<GutterMark> koTEAGutters = ContainerUtil.filter(myFixture.findAllGutters(), KoTEAFixtureTestCase::isKoTEAGutter);
        return !koTEAGutters.isEmpty();
    }
}
