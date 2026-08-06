package com.kotea.companion.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.asJava.LightClassUtilsKt;
import org.jetbrains.kotlin.asJava.classes.KtLightClass;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtReferenceExpression;
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry;
import org.jetbrains.kotlin.psi.KtTypeElement;
import org.jetbrains.kotlin.psi.KtTypeProjection;
import org.jetbrains.kotlin.psi.KtTypeReference;
import org.jetbrains.kotlin.psi.KtUserType;

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
            PsiClass eventClass = resolveClassArg(substitutor.substitute(params[1]));
            PsiClass commandClass = resolveClassArg(substitutor.substitute(params[2]));

            // kotlinc cannot encode a JVM generic signature that instantiates a type parameter with
            // `Nothing` (it has no compiled class), so it drops the leaf's whole generic supertype
            // signature and the light-class substitutor above sees a raw, argument-less type. That
            // happens e.g. for `Update<State, Event, Command, Nothing>` when a feature has no News.
            // Fall back to reading the type arguments straight out of Kotlin source, which has no
            // such limitation.
            if (eventClass == null || commandClass == null) {
                PsiClass[] fromSource = resolveEventAndCommandFromKotlinSource(leaf, updateClass);
                if (eventClass == null) eventClass = fromSource[0];
                if (commandClass == null) commandClass = fromSource[1];
            }

            if (eventClass != null) rootEvents.add(eventClass);
            if (commandClass != null) rootCommands.add(commandClass);
        }

        return new KoTEAIndex(rootEvents, rootCommands);
    }

    @Nullable
    private static PsiClass resolveClassArg(@Nullable PsiType type) {
        return type instanceof PsiClassType classType ? classType.resolve() : null;
    }

    /**
     * Reads the Event/Command type arguments off the Kotlin super type call that instantiates
     * {@code updateClass} (directly or via an intermediate base like {@code DslUpdate}), e.g. the
     * {@code OffersEvent}/{@code OffersCommand} in {@code OffersUpdate : DslUpdate<OffersState,
     * OffersEvent, OffersCommand, Nothing>()}. Assumes that intermediate base declares its own type
     * parameters in the same {@code <State, Event, Command, News>} order as {@code updateClass} —
     * true for {@code DslUpdate}, the only base KoTEA ships.
     */
    private static PsiClass[] resolveEventAndCommandFromKotlinSource(PsiClass leaf, PsiClass updateClass) {
        PsiClass[] result = new PsiClass[2];
        if (!(leaf instanceof KtLightClass lightClass)) return result;
        KtClassOrObject ktClass = lightClass.getKotlinOrigin();
        if (ktClass == null) return result;

        for (KtSuperTypeListEntry entry : ktClass.getSuperTypeListEntries()) {
            KtTypeReference typeRef = entry.getTypeReference();
            KtTypeElement typeElement = typeRef != null ? typeRef.getTypeElement() : null;
            if (!(typeElement instanceof KtUserType userType)) continue;

            PsiClass superClass = resolveUserTypeClass(userType);
            if (superClass == null || !InheritanceUtil.isInheritorOrSelf(superClass, updateClass, true)) continue;

            List<KtTypeProjection> typeArgs = userType.getTypeArguments();
            if (typeArgs.size() < 3) continue;

            result[0] = resolveTypeArgumentClass(typeArgs.get(1));
            result[1] = resolveTypeArgumentClass(typeArgs.get(2));
            return result;
        }
        return result;
    }

    @Nullable
    private static PsiClass resolveTypeArgumentClass(KtTypeProjection projection) {
        KtTypeReference typeRef = projection.getTypeReference();
        KtTypeElement typeElement = typeRef != null ? typeRef.getTypeElement() : null;
        return typeElement instanceof KtUserType userType ? resolveUserTypeClass(userType) : null;
    }

    @Nullable
    private static PsiClass resolveUserTypeClass(KtUserType userType) {
        KtReferenceExpression ref = userType.getReferenceExpression();
        PsiReference psiRef = ref != null ? ref.getReference() : null;
        PsiElement resolved = psiRef != null ? psiRef.resolve() : null;
        if (resolved == null) return null;

        PsiElement nav = resolved.getNavigationElement();
        if (nav instanceof KtClassOrObject cls) return LightClassUtilsKt.toLightClass(cls);
        return resolved instanceof PsiClass psiClass ? psiClass : null;
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
