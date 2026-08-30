package com.kotea.companion.commands;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.kotea.companion.fixtures.KoTEAFixtureTestCase;

/**
 * Tests for {@link CommandUtil#isNavigableCommand}
 */
public class CommandClassificationTest extends KoTEAFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        openFixtureProject("featureCoverage");
    }

    public void testSealedOrAbstractCommandBaseIsNotNavigable() {
        PsiFile file = myFixture.configureFromTempProjectFile("cov/Commands.kt");
        ReadAction.run(() -> {
            assertNavigable(file, "sealed interface Command Root", "FeatureCommand", false);
            assertNavigable(file, "sealed / abstract Command base", "BaseCommand", false);
            assertNavigable(file, "plain interface below the Root", "AsyncCommand", false);
        });
    }

    public void testConcreteCommandsAreNavigable() {
        PsiFile file = myFixture.configureFromTempProjectFile("cov/Commands.kt");
        ReadAction.run(() -> {
            assertNavigable(file, "Concrete Command (data class)", "LoadItems", true);
            assertNavigable(file, "Concrete Command (data object)", "Refresh", true);
        });
    }

    /** Asserts both entry points ({@code PsiClass} path and UAST path) agree with {@code expected}. */
    private void assertNavigable(PsiFile file, String message, String simpleName, boolean expected) {
        PsiClass psiClass = JavaPsiFacade.getInstance(getProject())
                .findClass("cov." + simpleName, GlobalSearchScope.allScope(getProject()));
        assertNotNull("fixture class cov." + simpleName + " not found", psiClass);
        assertEquals(message + " — via PsiClass", expected, CommandUtil.isNavigableCommand(psiClass));

        assertEquals(message + " — via UAST entry point", expected,
                CommandUtil.isNavigableCommand(findKtClass(file, simpleName)));
    }
}
