package com.kotea.companion.events;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiFile;
import com.kotea.companion.fixtures.KoTEAFixtureTestCase;

/**
 * Tests for {@link EventUtil#isNavigableEventClass}
 */
public class EventClassificationTest extends KoTEAFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        openFixtureProject("featureCoverage");
    }

    public void testOnlyConcreteEventsAreNavigable() {
        PsiFile file = myFixture.configureFromTempProjectFile("cov/Events.kt");

        ReadAction.run(() -> {
            assertFalse("sealed interface Event Root is not Navigable",
                    EventUtil.isNavigableEventClass(findKtClass(file, "FeatureEvent")));
            assertFalse("abstract intermediate is not Navigable",
                    EventUtil.isNavigableEventClass(findKtClass(file, "NavigationEvent")));
            assertFalse("plain interface below the Root is not Navigable",
                    EventUtil.isNavigableEventClass(findKtClass(file, "AnalyticsEvent")));

            assertTrue("Concrete Event (data class) is Navigable",
                    EventUtil.isNavigableEventClass(findKtClass(file, "ItemClicked")));
            assertTrue("Concrete Event (data object) is Navigable",
                    EventUtil.isNavigableEventClass(findKtClass(file, "BackPressed")));
        });
    }
}
