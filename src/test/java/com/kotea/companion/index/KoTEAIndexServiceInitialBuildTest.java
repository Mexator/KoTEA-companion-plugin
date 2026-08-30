package com.kotea.companion.index;

import com.kotea.companion.fixtures.KoTEAFixtureTestCase;

import java.util.Set;

public class KoTEAIndexServiceInitialBuildTest extends KoTEAFixtureTestCase {

    public void testInitialBuild_settlesToTheFixtureRoots() {
        myFixture.copyDirectoryToProject("featureUpdate", "");

        KoTEAIndexService service = KoTEAIndexService.getInstance(getProject());
        service.registerListeners();

        KoTEARootsIndex index = awaitIndex();

        assertEquals(
                new KoTEARootsIndex(Set.of("feature.FeatureEvent"), Set.of("feature.FeatureCommand")),
                index);
    }
}
