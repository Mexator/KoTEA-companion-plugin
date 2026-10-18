package com.kotea.companion.util;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiFile;
import com.kotea.companion.fixtures.KoTEAFixtureTestCase;
import org.jetbrains.kotlin.psi.KtClassOrObject;

import static com.kotea.companion.util.KoTEAElementKind.COMMAND;
import static com.kotea.companion.util.KoTEAElementKind.EVENT;

/**
 * Tests for {@link NavigableElementResolver#resolve}
 */
public class NavigableElementResolverTest extends KoTEAFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        openFixtureProject("featureCoverage");
    }

    public void testOnlyConcreteEventsAreNavigable() {
        PsiFile file = myFixture.configureFromTempProjectFile("cov/Events.kt");

        ReadAction.run(() -> {
            assertNull("sealed interface Event Root is not Navigable",
                    kindOf(findKtClass(file, "FeatureEvent")));
            assertNull("abstract intermediate is not Navigable",
                    kindOf(findKtClass(file, "NavigationEvent")));
            assertNull("plain interface below the Root is not Navigable",
                    kindOf(findKtClass(file, "AnalyticsEvent")));

            assertEquals("Concrete Event (data class) is Navigable",
                    EVENT, kindOf(findKtClass(file, "ItemClicked")));
            assertEquals("Concrete Event (data object) is Navigable",
                    EVENT, kindOf(findKtClass(file, "BackPressed")));
        });
    }

    public void testOnlyConcreteCommandsAreNavigable() {
        PsiFile file = myFixture.configureFromTempProjectFile("cov/Commands.kt");

        ReadAction.run(() -> {
            assertNull("sealed interface Command Root is not Navigable",
                    kindOf(findKtClass(file, "FeatureCommand")));
            assertNull("sealed / abstract Command base is not Navigable",
                    kindOf(findKtClass(file, "BaseCommand")));
            assertNull("plain interface below the Root is not Navigable",
                    kindOf(findKtClass(file, "AsyncCommand")));

            assertEquals("Concrete Command (data class) is Navigable",
                    COMMAND, kindOf(findKtClass(file, "LoadItems")));
            assertEquals("Concrete Command (data object) is Navigable",
                    COMMAND, kindOf(findKtClass(file, "Refresh")));
        });
    }

    private static KoTEAElementKind kindOf(KtClassOrObject candidate) {
        NavigableElement navigable = NavigableElementResolver.resolve(candidate);
        return navigable != null ? navigable.kind() : null;
    }
}
