package com.kotea.companion.commands;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiFile;
import com.kotea.companion.fixtures.KoTEAFixtureTestCase;

/**
 * Tests for {@link CommandUtil#isNavigableCommandClass}
 */
public class CommandClassificationTest extends KoTEAFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        openFixtureProject("featureCoverage");
    }

    public void testOnlyConcreteCommandsAreNavigable() {
        PsiFile file = myFixture.configureFromTempProjectFile("cov/Commands.kt");

        ReadAction.run(() -> {
            assertFalse("sealed interface Command Root is not Navigable",
                    CommandUtil.isNavigableCommandClass(findKtClass(file, "FeatureCommand")));
            assertFalse("sealed / abstract Command base is not Navigable",
                    CommandUtil.isNavigableCommandClass(findKtClass(file, "BaseCommand")));
            assertFalse("plain interface below the Root is not Navigable",
                    CommandUtil.isNavigableCommandClass(findKtClass(file, "AsyncCommand")));

            assertTrue("Concrete Command (data class) is Navigable",
                    CommandUtil.isNavigableCommandClass(findKtClass(file, "LoadItems")));
            assertTrue("Concrete Command (data object) is Navigable",
                    CommandUtil.isNavigableCommandClass(findKtClass(file, "Refresh")));
        });
    }
}
