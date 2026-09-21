package com.kotea.companion.index;

import com.kotea.companion.index.model.FileEntry;
import com.kotea.companion.index.model.KoTEABases;
import com.kotea.companion.index.model.UpdateRecord;
import com.kotea.companion.index.resolve.SupertypeResolver;
import com.kotea.companion.util.ScopeBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads changed files in batches, keeping what each file gave by path. A file that is gone, outside production
 * sources or skipped maps to {@code null}. A file already read is not read again after a write cancels a later
 * batch.
 */
final class ChangedFileReads {

    record Batch(Map<String, FileEntry> entries, int skipped) {
    }

    private final Project project;
    private final List<VirtualFile> pending;
    private final Map<String, UpdateRecord> knownRecords;
    private final Set<String> indexedPaths;
    @Nullable
    private final Set<String> knownUpdateNames;
    private final Map<String, FileEntry> entries = new LinkedHashMap<>();
    private int done;
    private int skipped;

    /**
     * @param knownRecords     records of the index, by FQN
     * @param indexedPaths     files that have records now
     * @param knownUpdateNames when not null, skip files that are not indexed and whose classes name none of these,
     *                         {@code Update}, {@code DslUpdate} or their aliases in their supertype lists; such
     *                         files cannot declare an Update, except through an alias of an indexed Update
     */
    ChangedFileReads(Project project, Collection<VirtualFile> files, Map<String, UpdateRecord> knownRecords,
                     Set<String> indexedPaths, @Nullable Set<String> knownUpdateNames) {
        this.project = project;
        this.pending = new ArrayList<>(onePerPath(files));
        this.knownRecords = knownRecords;
        this.indexedPaths = indexedPaths;
        this.knownUpdateNames = knownUpdateNames;
    }

    /** What each file gave, by path, in reading order. */
    Map<String, FileEntry> entries() {
        return entries;
    }

    int fileCount() {
        return pending.size();
    }

    int skipped() {
        return skipped;
    }

    boolean isDone() {
        return done >= pending.size();
    }

    /** Runs inside a read action and changes no state. */
    Batch compute(long deadlineNanos) {
        GlobalSearchScope scope = ScopeBuilder.getUpdateSourcesScope(project);
        PsiClass updateClass = KoTEAIndexComputer.findUpdateClass(project);
        Set<String> supertypeNames = null;
        if (knownUpdateNames != null) {
            supertypeNames = new HashSet<>(knownUpdateNames);
            supertypeNames.addAll(SupertypeResolver.namesFor(project, scope, KoTEABases.UPDATE_FQN));
            supertypeNames.addAll(SupertypeResolver.namesFor(project, scope, KoTEABases.DSL_UPDATE_FQN));
        }
        Map<String, FileEntry> batch = new LinkedHashMap<>();
        int skippedInBatch = 0;
        for (int i = done; i < pending.size(); i++) {
            if (i > done && System.nanoTime() > deadlineNanos) break;
            VirtualFile file = pending.get(i);
            if (supertypeNames != null && !indexedPaths.contains(file.getPath())
                    && !KoTEAIndexComputer.mayDeclareUpdate(project, file, supertypeNames)) {
                batch.put(file.getPath(), null);
                skippedInBatch++;
                continue;
            }
            batch.put(file.getPath(), IndexLog.orNullOnError("a file",
                    () -> KoTEAIndexComputer.readFile(project, file, scope, knownRecords::get, updateClass)));
        }
        return new Batch(batch, skippedInBatch);
    }

    void apply(Batch batch) {
        done += batch.entries().size();
        skipped += batch.skipped();
        entries.putAll(batch.entries());
    }

    /** A file deleted and created again leaves two objects for one path; keep the live one. */
    private static Collection<VirtualFile> onePerPath(Collection<VirtualFile> files) {
        Map<String, VirtualFile> byPath = new HashMap<>();
        for (VirtualFile file : files) {
            byPath.merge(file.getPath(), file, (a, b) -> a.isValid() ? a : b);
        }
        return byPath.values();
    }
}
