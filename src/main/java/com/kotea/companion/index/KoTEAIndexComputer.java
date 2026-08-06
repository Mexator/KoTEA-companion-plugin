package com.kotea.companion.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.TypeConversionUtil;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class KoTEAIndexComputer {

    // https://opensource.tbank.ru/mobile-tech/KoTEA/-/blob/main/core/src/commonMain/kotlin/ru/tinkoff/kotea/core/Update.kt
    private static final String UPDATE_FQN = "ru.tinkoff.kotea.core.Update";

    private KoTEAIndexComputer() {
    }

    public static KoTEAIndex compute(Project project) {
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        PsiClass updateClass = JavaPsiFacade.getInstance(project).findClass(UPDATE_FQN, scope);
        if (updateClass == null) return KoTEAIndex.EMPTY;

        Set<PsiClass> inheritorSet = new HashSet<>(ClassInheritorsSearch.search(updateClass, scope, true).findAll());
        Set<PsiClass> leaves = leafFilter(inheritorSet);

        Set<PsiClass> rootEvents = new HashSet<>();
        Set<PsiClass> rootCommands = new HashSet<>();

        // Update params: <State, Event, Command, News>
        PsiTypeParameter[] params = updateClass.getTypeParameters();
        if (params.length < 3) return KoTEAIndex.EMPTY;

        for (PsiClass leaf : leaves) {
            PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(updateClass, leaf, PsiSubstitutor.EMPTY);
            PsiType eventType = substitutor.substitute(params[1]);
            PsiType commandType = substitutor.substitute(params[2]);

            if (eventType instanceof PsiClassType eventClassType) {
                PsiClass resolved = eventClassType.resolve();
                if (resolved != null) rootEvents.add(resolved);
            }
            if (commandType instanceof PsiClassType commandClassType) {
                PsiClass resolved = commandClassType.resolve();
                if (resolved != null) rootCommands.add(resolved);
            }
        }

        return new KoTEAIndex(rootEvents, rootCommands);
    }

    private static Set<PsiClass> leafFilter(Set<PsiClass> inheritorSet) {
        Set<PsiClass> notLeaf = new HashSet<>();
        for (PsiClass y : inheritorSet) {
            Set<PsiClass> visited = new HashSet<>();
            Deque<PsiClass> stack = new ArrayDeque<>(List.of(y.getSupers()));
            while (!stack.isEmpty()) {
                PsiClass ancestor = stack.pop();
                if (!visited.add(ancestor)) continue;
                if (inheritorSet.contains(ancestor)) notLeaf.add(ancestor);
                stack.addAll(List.of(ancestor.getSupers()));
            }
        }
        Set<PsiClass> leaves = new HashSet<>(inheritorSet);
        leaves.removeAll(notLeaf);
        return leaves;
    }
}
