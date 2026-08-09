package com.kotea.companion.index;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.kotea.companion.util.PerfLog;
import com.kotea.companion.util.ScopeBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.asJava.LightClassUtilsKt;
import org.jetbrains.kotlin.asJava.classes.KtLightClass;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtReferenceExpression;
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry;
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid;
import org.jetbrains.kotlin.psi.KtTypeElement;
import org.jetbrains.kotlin.psi.KtTypeProjection;
import org.jetbrains.kotlin.psi.KtTypeReference;
import org.jetbrains.kotlin.psi.KtUserType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class KoTEAIndexComputer {

    private static final Logger LOG = Logger.getInstance(KoTEAIndexComputer.class);

    // https://opensource.tbank.ru/mobile-tech/KoTEA/-/blob/main/core/src/commonMain/kotlin/ru/tinkoff/kotea/core/Update.kt
    private static final String UPDATE_FQN = "ru.tinkoff.kotea.core.Update";

    private KoTEAIndexComputer() {
    }

    /**
     * Full project-wide scan: every project class implementing {@code Update}, grouped by the file
     * that declares it. Production scope only - {@code JavaClassInheritorsSearcher} traverses with
     * {@code allScope} internally regardless of the requested scope and only *filters* by it, so
     * nothing is missed by passing the narrower one.
     * <p>
     * Also seeds the search from any library-provided intermediate base (e.g. KoTEA's
     * {@code DslUpdate}), found via a one-off library-scope inheritor search and run
     * unconditionally: Kotlin's {@code DirectClassInheritorsSearch} executor looks up literal
     * supertype names in an index restricted to <i>project</i> source files only, so on a project
     * that reaches {@code updateClass} only through such an intermediate base and never references
     * {@code updateClass} directly - the usual case per {@code CONTEXT.md} - a search seeded from
     * {@code updateClass} alone can never take its first hop, since the intermediate base's own
     * declaration lives in the library, not a project source file.
     */
    public static Map<VirtualFile, List<UpdateRecord>> computeAll(Project project) {
        PsiClass updateClass = findUpdateClass(project);
        if (updateClass == null) return Map.of();

        PsiTypeParameter[] params = updateClass.getTypeParameters();
        if (params.length < 3) return Map.of();

        GlobalSearchScope productionScope = ScopeBuilder.getProductionScope(project);

        long searchStart = PerfLog.start();
        Set<PsiClass> hits = new HashSet<>(ClassInheritorsSearch.search(updateClass, productionScope, true).findAll());

        GlobalSearchScope librariesScope = ProjectScope.getLibrariesScope(project);
        for (PsiClass libraryBase : ClassInheritorsSearch.search(updateClass, librariesScope, true).findAll()) {
            hits.addAll(ClassInheritorsSearch.search(libraryBase, productionScope, true).findAll());
        }
        PerfLog.logElapsed(LOG, "KoTEA full ClassInheritorsSearch found " + hits.size() + " candidates", searchStart);

        Map<VirtualFile, List<UpdateRecord>> recordsByFile = new HashMap<>();
        for (PsiClass psiClass : hits) {
            PsiFile containingFile = psiClass.getContainingFile();
            VirtualFile virtualFile = containingFile != null ? containingFile.getVirtualFile() : null;
            if (virtualFile == null) continue;
            recordsByFile.computeIfAbsent(virtualFile, f -> new ArrayList<>())
                    .add(buildRecord(psiClass, updateClass, params));
        }
        return recordsByFile;
    }

    /**
     * The incremental path: re-derives the {@link UpdateRecord}s declared by a single file from its
     * PSI directly - no literal-name matching, so typealiases and aliased imports resolve correctly.
     */
    public static List<UpdateRecord> computeForFile(Project project, VirtualFile file) {
        PsiClass updateClass = findUpdateClass(project);
        if (updateClass == null) return List.of();

        PsiTypeParameter[] params = updateClass.getTypeParameters();
        if (params.length < 3) return List.of();

        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (!(psiFile instanceof KtFile ktFile)) return List.of();

        List<UpdateRecord> records = new ArrayList<>();
        ktFile.accept(new KtTreeVisitorVoid() {
            @Override
            public void visitClassOrObject(@NotNull KtClassOrObject classOrObject) {
                super.visitClassOrObject(classOrObject);
                if (classOrObject.getSuperTypeListEntries().isEmpty()) return;

                PsiClass lightClass = LightClassUtilsKt.toLightClass(classOrObject);
                if (lightClass == null || !InheritanceUtil.isInheritorOrSelf(lightClass, updateClass, true)) return;

                records.add(buildRecord(lightClass, updateClass, params));
            }
        });
        return records;
    }

    /**
     * Pure string work, no PSI: the Feature Update filter (a record's {@code fqn} appears in no
     * other record's {@code updateAncestorFqns} - records with a null {@code fqn} always qualify,
     * since an anonymous {@code object : Update<...> {}} can't be anyone's supertype) plus the union
     * of the surviving records' Event/Command Root FQNs.
     */
    public static KoTEAIndex derive(Collection<List<UpdateRecord>> recordsByFile) {
        List<UpdateRecord> all = new ArrayList<>();
        for (List<UpdateRecord> records : recordsByFile) all.addAll(records);

        Set<String> updateAncestorFqns = new HashSet<>();
        for (UpdateRecord record : all) updateAncestorFqns.addAll(record.updateAncestorFqns());

        Set<String> eventRootFqns = new HashSet<>();
        Set<String> commandRootFqns = new HashSet<>();
        for (UpdateRecord record : all) {
            boolean isFeatureUpdate = record.fqn() == null || !updateAncestorFqns.contains(record.fqn());
            if (!isFeatureUpdate) continue;

            if (record.eventRootFqn() != null) eventRootFqns.add(record.eventRootFqn());
            if (record.commandRootFqn() != null) commandRootFqns.add(record.commandRootFqn());
        }
        return new KoTEAIndex(eventRootFqns, commandRootFqns);
    }

    @Nullable
    private static PsiClass findUpdateClass(Project project) {
        GlobalSearchScope allScope = GlobalSearchScope.allScope(project);
        return JavaPsiFacade.getInstance(project).findClass(UPDATE_FQN, allScope);
    }

    private static UpdateRecord buildRecord(PsiClass psiClass, PsiClass updateClass, PsiTypeParameter[] params) {
        // Update params: <State, Event, Command, News>
        PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(updateClass, psiClass, PsiSubstitutor.EMPTY);
        PsiClass eventClass = resolveClassArg(substitutor.substitute(params[1]));
        PsiClass commandClass = resolveClassArg(substitutor.substitute(params[2]));

        // kotlinc cannot encode a JVM generic signature that instantiates a type parameter with
        // `Nothing` (it has no compiled class), so it drops the class's whole generic supertype
        // signature and the light-class substitutor above sees a raw, argument-less type. That
        // happens e.g. for `Update<State, Event, Command, Nothing>` when a feature has no News.
        // Fall back to reading the type arguments straight out of Kotlin source, which has no
        // such limitation.
        if (eventClass == null || commandClass == null) {
            PsiClass[] fromSource = resolveEventAndCommandFromKotlinSource(psiClass, updateClass);
            if (eventClass == null) eventClass = fromSource[0];
            if (commandClass == null) commandClass = fromSource[1];
        }

        return new UpdateRecord(
                psiClass.getQualifiedName(),
                collectUpdateAncestorFqns(psiClass, updateClass),
                eventClass != null ? eventClass.getQualifiedName() : null,
                commandClass != null ? commandClass.getQualifiedName() : null);
    }

    /**
     * FQNs of {@code psiClass}'s supers (excluding itself and {@code updateClass}) that are
     * themselves {@code Update} implementations - what the Feature Update filter in {@link #derive}
     * uses to tell a most-derived class from an intermediate base.
     */
    private static Set<String> collectUpdateAncestorFqns(PsiClass psiClass, PsiClass updateClass) {
        Set<String> ancestorFqns = new HashSet<>();
        InheritanceUtil.processSupers(psiClass, false, ancestor -> {
            String fqn = ancestor.getQualifiedName();
            if (fqn != null && !UPDATE_FQN.equals(fqn) && InheritanceUtil.isInheritorOrSelf(ancestor, updateClass, true)) {
                ancestorFqns.add(fqn);
            }
            return true;
        });
        return ancestorFqns;
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
            if (!InheritanceUtil.isInheritorOrSelf(superClass, updateClass, true)) continue;

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
}
