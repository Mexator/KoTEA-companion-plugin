package com.kotea.companion.domain.impl;

import com.kotea.companion.domain.KoTEAClassifier;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.kotea.companion.fixtures.KoTEAFixtureTestCase;
import com.kotea.companion.util.KoTEAElementKind;

import java.util.Set;

import static com.kotea.companion.util.KoTEAElementKind.COMMAND;
import static com.kotea.companion.util.KoTEAElementKind.EVENT;
import static com.kotea.companion.util.KoTEAElementKind.NEWS;

public class KoTEAClassifierImplPsiTest extends KoTEAFixtureTestCase {

    public void testKindOf_overFixtureHierarchy() {
        myFixture.copyDirectoryToProject("rootsHierarchy", "");

        KoTEARoots roots = new KoTEARoots(
                Set.of("roots.FeatureEvent"), Set.of("roots.FeatureCommand"), Set.of());

        ReadAction.run(() -> {
            assertEquals("root is an Event", EVENT, kindOf(roots, "roots.FeatureEvent"));
            assertEquals("concrete leaf is an Event", EVENT, kindOf(roots, "roots.ItemClicked"));
            assertEquals("concrete leaf below an intermediate is an Event", EVENT, kindOf(roots, "roots.BackPressed"));
            assertNull("unrelated class has no kind", kindOf(roots, "roots.Unrelated"));

            assertEquals("root is a Command", COMMAND, kindOf(roots, "roots.FeatureCommand"));
            assertEquals("concrete leaf is a Command", COMMAND, kindOf(roots, "roots.LoadItems"));
        });
    }

    public void testNearestRoot_concreteNewsUnderCommandRoot_isNewsNotCommand() {
        myFixture.copyDirectoryToProject("nearestRoot", "");

        KoTEARoots roots = new KoTEARoots(
                Set.of("nr.NrEvent"), Set.of("nr.NrCommand"), Set.of("nr.NrNews"));

        ReadAction.run(() -> {
            assertEquals("Concrete News is classified by its nearer News Root", NEWS, kindOf(roots, "nr.NrShown"));
            assertEquals("Concrete Command directly under the Command Root is unaffected",
                    COMMAND, kindOf(roots, "nr.NrLoad"));
        });
    }

    public void testRoots_typesFromLibrariesAreNotRoots() {
        openFixtureProject("unitRoots");

        assertEquals(new KoTEARoots(Set.of("unitroots.UrEvent"), Set.of(), Set.of()), projectRoots());
    }

    public void testRoots_nothingIsNotARoot() {
        openFixtureProject("newsNothing");

        assertEquals(new KoTEARoots(Set.of("nothingnews.NnEvent"), Set.of("nothingnews.NnCommand"), Set.of()),
                projectRoots());
    }

    private KoTEARoots projectRoots() {
        return ReadAction.compute(() -> ((KoTEAClassifierImpl) KoTEAClassifier.getInstance(getProject())).roots());
    }

    private KoTEAElementKind kindOf(KoTEARoots roots, String fqn) {
        PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass(fqn, GlobalSearchScope.allScope(getProject()));
        assertNotNull("fixture class " + fqn + " not found", psiClass);
        return KoTEAClassifierImpl.kindOf(roots, psiClass);
    }
}
