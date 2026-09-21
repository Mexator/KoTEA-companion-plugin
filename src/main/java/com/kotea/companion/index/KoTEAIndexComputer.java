package com.kotea.companion.index;

import com.kotea.companion.index.model.FileEntry;
import com.kotea.companion.index.model.KoTEABases;
import com.kotea.companion.index.model.UpdateRecord;
import com.kotea.companion.index.resolve.Supertype;
import com.kotea.companion.index.resolve.SupertypeResolver;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtDeclaration;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry;
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid;
import org.jetbrains.kotlin.psi.psiUtil.KtPsiUtilKt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class KoTEAIndexComputer {

    private KoTEAIndexComputer() {
    }

    /**
     * Full project-wide scan in the current read action: returns every production class implementing
     * {@code Update}, grouped by the file that declares it. The service runs the same walk in batches.
     */
    public static Map<VirtualFile, List<UpdateRecord>> computeAll(Project project) {
        UpdateScan scan = UpdateScan.fromKoTEABases(project);
        while (!scan.isDone()) scan.apply(scan.compute(Long.MAX_VALUE));

        Map<VirtualFile, List<UpdateRecord>> recordsByFile = new HashMap<>();
        for (FileEntry entry : scan.entries().values()) recordsByFile.put(entry.file(), entry.records());
        return recordsByFile;
    }

    /**
     * Everything the index stores about {@code file}, or {@code null} when the file is gone or is not in
     * {@code scope}.
     *
     * @param known records of Updates declared elsewhere, by FQN, for classes that extend them
     */
    @Nullable
    static FileEntry readFile(Project project, VirtualFile file, GlobalSearchScope scope,
                              Function<String, UpdateRecord> known, @Nullable PsiClass updateClass) {
        if (!file.isValid() || file.isDirectory() || !scope.contains(file)) return null;
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (!(psiFile instanceof KtFile ktFile)) return null;
        return new FileEntry(file, computeForFile(ktFile, known, updateClass));
    }

    /**
     * False when no class in {@code file} names any of {@code supertypeNames} in its supertype list. Only the
     * names as written are compared, which the stubs keep, so the file is neither parsed nor resolved.
     */
    static boolean mayDeclareUpdate(Project project, VirtualFile file, Set<String> supertypeNames) {
        PsiFile psiFile = file.isValid() && !file.isDirectory() ? PsiManager.getInstance(project).findFile(file) : null;
        return !(psiFile instanceof KtFile ktFile) || namesAnySupertype(ktFile.getDeclarations(), supertypeNames);
    }

    private static boolean namesAnySupertype(List<KtDeclaration> declarations, Set<String> supertypeNames) {
        for (KtDeclaration declaration : declarations) {
            if (!(declaration instanceof KtClassOrObject classOrObject)) continue;
            for (String name : KtPsiUtilKt.getSuperNames(classOrObject)) {
                if (supertypeNames.contains(name)) return true;
            }
            if (namesAnySupertype(classOrObject.getDeclarations(), supertypeNames)) return true;
        }
        return false;
    }

    /**
     * The record for {@code classOrObject}, or {@code null} if none of its supertypes is KoTEA's {@code Update},
     * {@code DslUpdate} or a class in {@code known}. It is built from the parent's record.
     */
    @Nullable
    static UpdateRecord computeForClass(KtClassOrObject classOrObject, SupertypeResolver resolver,
                                        Function<String, UpdateRecord> known) {
        for (KtSuperTypeListEntry entry : classOrObject.getSuperTypeListEntries()) {
            if (!(resolver.resolve(entry) instanceof Supertype.ClassType type)) continue;
            UpdateRecord parent = KoTEABases.recordOf(type.fqn());
            if (parent == null) parent = known.apply(type.fqn());
            if (parent != null) return parent.subclass(fqnOf(classOrObject), type.arguments()::binding);
        }
        return null;
    }

    @Nullable
    private static String fqnOf(KtClassOrObject classOrObject) {
        FqName fqName = classOrObject.getFqName();
        return fqName != null ? fqName.asString() : null;
    }

    /**
     * Classes of one file may extend each other in any order, so records are built in rounds until no more
     * are found. Classes declared here are never looked up in {@code known}, which may be out of date for them.
     */
    private static List<UpdateRecord> computeForFile(KtFile ktFile, Function<String, UpdateRecord> known,
                                                     @Nullable PsiClass updateClass) {
        if (updateClass == null) return List.of();
        List<KtClassOrObject> pending = new ArrayList<>();
        Set<String> declared = new HashSet<>();
        ktFile.accept(new KtTreeVisitorVoid() {
            @Override
            public void visitClassOrObject(@NotNull KtClassOrObject classOrObject) {
                super.visitClassOrObject(classOrObject);
                if (classOrObject.getSuperTypeListEntries().isEmpty()) return;
                pending.add(classOrObject);
                String fqn = fqnOf(classOrObject);
                if (fqn != null) declared.add(fqn);
            }
        });

        SupertypeResolver resolver = new SupertypeResolver(ktFile.getProject());
        Map<String, UpdateRecord> inFile = new HashMap<>();
        Function<String, UpdateRecord> lookup = fqn -> declared.contains(fqn) ? inFile.get(fqn) : known.apply(fqn);
        List<UpdateRecord> records = new ArrayList<>();
        boolean found = true;
        while (found) {
            found = false;
            for (Iterator<KtClassOrObject> it = pending.iterator(); it.hasNext(); ) {
                UpdateRecord record = computeForClass(it.next(), resolver, lookup);
                if (record == null) continue;
                it.remove();
                records.add(record);
                if (record.fqn() != null) inFile.put(record.fqn(), record);
                found = true;
            }
        }
        return records;
    }

    /**
     * Builds {@link KoTEARootsIndex} out of provided {@link UpdateRecord}s. Only Feature Updates contribute: a
     * record that another record lists as an ancestor is a shared base.
     */
    public static KoTEARootsIndex derive(Collection<List<UpdateRecord>> recordsByFile) {
        List<UpdateRecord> all = new ArrayList<>();
        for (List<UpdateRecord> records : recordsByFile) all.addAll(records);

        Set<String> updateAncestorFqns = new HashSet<>();
        for (UpdateRecord record : all) updateAncestorFqns.addAll(record.updateAncestorFqns());

        Set<String> eventRootFqns = new HashSet<>();
        Set<String> commandRootFqns = new HashSet<>();
        Set<String> newsRootFqns = new HashSet<>();
        for (UpdateRecord record : all) {
            boolean isFeatureUpdate = record.fqn() == null || !updateAncestorFqns.contains(record.fqn());
            if (!isFeatureUpdate) continue;

            if (record.eventRootFqn() != null) eventRootFqns.add(record.eventRootFqn());
            if (record.commandRootFqn() != null) commandRootFqns.add(record.commandRootFqn());
            if (record.newsRootFqn() != null) newsRootFqns.add(record.newsRootFqn());
        }
        return new KoTEARootsIndex(eventRootFqns, commandRootFqns, newsRootFqns);
    }

    @Nullable
    static PsiClass findUpdateClass(Project project) {
        GlobalSearchScope allScope = GlobalSearchScope.allScope(project);
        return JavaPsiFacade.getInstance(project).findClass(KoTEABases.UPDATE_FQN, allScope);
    }
}
