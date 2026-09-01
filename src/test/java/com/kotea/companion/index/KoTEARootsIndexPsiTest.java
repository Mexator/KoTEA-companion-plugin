package com.kotea.companion.index;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.kotea.companion.fixtures.KoTEAFixtureTestCase;

import java.util.Set;

public class KoTEARootsIndexPsiTest extends KoTEAFixtureTestCase {

    public void testIsEventAndIsCommand_overFixtureHierarchy() {
        myFixture.copyDirectoryToProject("rootsHierarchy", "");

        KoTEARootsIndex index = new KoTEARootsIndex(
                Set.of("roots.FeatureEvent"), Set.of("roots.FeatureCommand"), Set.of());

        ReadAction.run(() -> {
            assertTrue("root is an Event", index.isEvent(findClass("roots.FeatureEvent")));
            assertTrue("concrete leaf is an Event", index.isEvent(findClass("roots.ItemClicked")));
            assertTrue("concrete leaf below an intermediate is an Event",
                    index.isEvent(findClass("roots.BackPressed")));
            assertFalse("unrelated class is not an Event", index.isEvent(findClass("roots.Unrelated")));
            assertFalse("a Command is not an Event", index.isEvent(findClass("roots.LoadItems")));

            assertTrue("root is a Command", index.isCommand(findClass("roots.FeatureCommand")));
            assertTrue("concrete leaf is a Command", index.isCommand(findClass("roots.LoadItems")));
            assertFalse("unrelated class is not a Command", index.isCommand(findClass("roots.Unrelated")));
            assertFalse("an Event is not a Command", index.isCommand(findClass("roots.ItemClicked")));
        });
    }

    public void testNearestRoot_concreteNewsUnderCommandRoot_isNewsNotCommand() {
        myFixture.copyDirectoryToProject("nearestRoot", "");

        KoTEARootsIndex index = new KoTEARootsIndex(
                Set.of("nr.NrEvent"), Set.of("nr.NrCommand"), Set.of("nr.NrNews"));

        ReadAction.run(() -> {
            assertFalse("Concrete News under NewsRoot : CommandRoot is not a Command",
                    index.isCommand(findClass("nr.NrShown")));
            assertTrue("Concrete News is classified by its nearer News Root",
                    index.isNews(findClass("nr.NrShown")));

            assertTrue("Concrete Command directly under the Command Root is unaffected",
                    index.isCommand(findClass("nr.NrLoad")));
            assertFalse("Concrete Command is not a News", index.isNews(findClass("nr.NrLoad")));
        });
    }

    private PsiClass findClass(String fqn) {
        PsiClass psiClass = JavaPsiFacade.getInstance(getProject())
                .findClass(fqn, GlobalSearchScope.allScope(getProject()));
        assertNotNull("fixture class " + fqn + " not found", psiClass);
        return psiClass;
    }
}
