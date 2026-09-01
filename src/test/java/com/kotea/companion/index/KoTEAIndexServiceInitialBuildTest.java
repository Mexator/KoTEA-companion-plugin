package com.kotea.companion.index;

import com.kotea.companion.fixtures.KoTEAFixtureTestCase;

import java.util.Set;

public class KoTEAIndexServiceInitialBuildTest extends KoTEAFixtureTestCase {

    public void testInitialBuild_settlesToTheFixtureRoots() {
        KoTEARootsIndex index = openFixtureProject("featureUpdate");

        assertEquals(
                new KoTEARootsIndex(
                        Set.of("feature.FeatureEvent"),
                        Set.of("feature.FeatureCommand"),
                        Set.of("feature.FeatureNews")),
                index);
    }
}
