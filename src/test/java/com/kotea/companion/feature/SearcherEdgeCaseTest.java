package com.kotea.companion.feature;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.kotea.companion.commands.CommandEmissionSearcher;
import com.kotea.companion.events.EventEmissionSearcher;
import com.kotea.companion.fixtures.KoTEAFixtureTestCase;
import com.kotea.companion.util.ScopeBuilder;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UastContextKt;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SearcherEdgeCaseTest extends KoTEAFixtureTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        openFixtureProject("searcherEdges");
    }

    public void testEventEmissionResolvesEveryConstructionSite() {
        List<PsiElement> targets = eventEmissions("ItemTapped");

        assertEquals(
                Set.of(
                        "EdgesScreen.kt:14  ItemTapped(id)",
                        "EdgesScreen.kt:15  ItemTapped(id + 1)"),
                locationsOf(targets));
    }

    public void testCommandEmissionResolvesEveryCommandsCall() {
        List<PsiElement> targets = commandEmission("LoadPage");

        assertEquals(
                Set.of(
                        "EdgesUpdate.kt:10  LoadPage(event.id)",
                        "EdgesUpdate.kt:11  LoadPage(event.id + 1)"),
                locationsOf(targets));
    }

    private List<PsiElement> eventEmissions(String declName) {
        KtClassOrObject decl = ktClass("edges/Events.kt", declName);
        GlobalSearchScope scope = ScopeBuilder.getProductionScope(getProject());
        return ReadAction.compute(() -> EventEmissionSearcher.findEmissions(decl, scope));
    }

    private List<PsiElement> commandEmission(String declName) {
        KtClassOrObject decl = ktClass("edges/Commands.kt", declName);
        GlobalSearchScope scope = ScopeBuilder.getProductionScope(getProject());
        return ReadAction.compute(() ->
                CommandEmissionSearcher.findEmission(UastContextKt.toUElement(decl, UClass.class), scope));
    }

    /**
     * {@code file:line  <enclosing call>} for each target
     */
    private static Set<String> locationsOf(List<PsiElement> targets) {
        return ReadAction.compute(() -> targets.stream().map(target -> {
            PsiFile file = target.getContainingFile();
            Document document = file.getViewProvider().getDocument();
            int line = document.getLineNumber(target.getTextRange().getStartOffset()) + 1;
            KtCallExpression call = PsiTreeUtil.getParentOfType(target, KtCallExpression.class);
            return file.getName() + ":" + line + "  " + (call != null ? call.getText() : target.getText());
        }).collect(Collectors.toSet()));
    }
}
