package com.kotea.companion.index;

import com.kotea.companion.index.background.BatchReader;
import com.kotea.companion.index.model.FileEntry;
import com.kotea.companion.index.model.KoTEABases;
import com.kotea.companion.index.model.UpdateRecord;
import com.kotea.companion.index.resolve.Supertype;
import com.kotea.companion.index.resolve.SupertypeResolver;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.kotea.companion.util.ScopeBuilder;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.stubindex.KotlinSuperClassIndex;
import org.jetbrains.kotlin.lexer.KtTokens;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Breadth-first walk down the Update hierarchy over the Kotlin super-class stub index, split into
 * {@link BatchReader} batches. Everything the walk has learned is keyed by file path and FQN, never by PSI,
 * so a batch that a write action cancels loses only its own work.
 * <p>
 * A class found by the walk is kept when one of its supertype entries names the base, after typealias
 * expansion; names are read from imports (see {@link SupertypeResolver}). Its record is built from the record of
 * the base, which the walk always finds first. The project-open scan starts from KoTEA's {@code Update} and {@code DslUpdate}; the update cycle
 * starts it from a class that just became an Update.
 */
final class UpdateScan {

    private final Project project;
    private final GlobalSearchScope scope;

    private final Deque<String> bases = new ArrayDeque<>();
    private final Set<String> queuedBases = new HashSet<>();
    private final Set<String> checked = new HashSet<>();
    private final Set<String> found = new HashSet<>();
    private final Map<String, FileEntry> entries = new HashMap<>();
    private final Map<String, UpdateRecord> knownRecords;

    private int candidatesChecked;
    private boolean updateClassMissing;

    private UpdateScan(Project project, Collection<String> seeds, Map<String, UpdateRecord> knownRecords) {
        this.project = project;
        this.scope = ScopeBuilder.getUpdateSourcesScope(project);
        this.knownRecords = new HashMap<>(knownRecords);
        for (String seed : seeds) {
            if (queuedBases.add(seed)) bases.add(seed);
        }
    }

    /** The project-open scan. */
    static UpdateScan fromKoTEABases(Project project) {
        return new UpdateScan(project, List.of(KoTEABases.UPDATE_FQN, KoTEABases.DSL_UPDATE_FQN),
                Map.of());
    }

    /** @param knownRecords records of the index, by FQN; they include the records of {@code baseFqns} */
    static UpdateScan fromClasses(Project project, Collection<String> baseFqns, Map<String, UpdateRecord> knownRecords) {
        return new UpdateScan(project, baseFqns, knownRecords);
    }

    /** Files with Update records found so far, by path. */
    Map<String, FileEntry> entries() {
        return entries;
    }

    int candidatesChecked() {
        return candidatesChecked;
    }

    /** True when KoTEA's {@code Update} was not on the classpath, so the scan could find nothing. */
    boolean updateClassMissing() {
        return updateClassMissing;
    }

    boolean isDone() {
        return bases.isEmpty();
    }

    static final class Batch {
        final Set<String> finishedBases = new HashSet<>();
        final Set<String> newBases = new LinkedHashSet<>();
        final Set<String> checked = new HashSet<>();
        final Set<String> found = new HashSet<>();
        final Map<String, VirtualFile> files = new HashMap<>();
        final Map<String, List<UpdateRecord>> records = new HashMap<>();
        final Map<String, UpdateRecord> recordsByFqn = new HashMap<>();
        boolean updateClassMissing;
    }

    /** Runs inside a read action and changes no state. */
    Batch compute(long deadlineNanos) {
        Batch batch = new Batch();
        if (KoTEAIndexComputer.findUpdateClass(project) == null) {
            batch.updateClassMissing = true;
            batch.finishedBases.addAll(bases);
        } else {
            walk(batch, deadlineNanos);
        }
        return batch;
    }

    private void walk(Batch batch, long deadlineNanos) {
        Deque<String> queue = new ArrayDeque<>(bases);
        SupertypeResolver resolver = new SupertypeResolver(project);
        Function<String, UpdateRecord> known = fqn -> {
            UpdateRecord record = batch.recordsByFqn.get(fqn);
            return record != null ? record : knownRecords.get(fqn);
        };
        int checkedInBatch = 0;
        while (!queue.isEmpty()) {
            String base = queue.peek();
            for (KtClassOrObject candidate : ScanTimings.time(ScanTimings.Step.LOOKUP, () -> candidatesOf(base))) {
                ProgressManager.checkCanceled();
                String key = candidateKey(candidate);
                String checkKey = base + "|" + key;
                if (key == null || checked.contains(checkKey) || !batch.checked.add(checkKey)) continue;
                if (checkedInBatch++ > 0 && System.nanoTime() > deadlineNanos) {
                    batch.checked.remove(checkKey);
                    return;
                }
                long candidateStart = System.nanoTime();
                if (!ScanTimings.time(ScanTimings.Step.SUPERTYPE, () -> extendsBase(resolver, candidate, base))) continue;

                String fqn = fqnOf(candidate);
                if (fqn != null && !isFinal(candidate) && !queuedBases.contains(fqn) && batch.newBases.add(fqn)) {
                    queue.add(fqn);
                }
                if (found.contains(key) || !batch.found.add(key)) continue;
                UpdateRecord record = IndexLog.orNullOnError(IndexLog.mask(fqn), () -> ScanTimings.time(
                        ScanTimings.Step.RECORD, () -> KoTEAIndexComputer.computeForClass(candidate, resolver, known)));
                ScanTimings.candidateChecked(fqn, System.nanoTime() - candidateStart);
                if (record == null) continue;
                if (record.fqn() != null) batch.recordsByFqn.put(record.fqn(), record);
                VirtualFile file = candidate.getContainingFile().getVirtualFile();
                batch.files.put(file.getPath(), file);
                batch.records.computeIfAbsent(file.getPath(), p -> new ArrayList<>()).add(record);
            }
            batch.finishedBases.add(queue.poll());
        }
    }

    void apply(Batch batch) {
        updateClassMissing |= batch.updateClassMissing;
        bases.removeAll(batch.finishedBases);
        for (String base : batch.newBases) {
            if (queuedBases.add(base) && !batch.finishedBases.contains(base)) bases.add(base);
        }
        checked.addAll(batch.checked);
        found.addAll(batch.found);
        candidatesChecked += batch.checked.size();
        knownRecords.putAll(batch.recordsByFqn);

        for (Map.Entry<String, List<UpdateRecord>> e : batch.records.entrySet()) {
            FileEntry old = entries.get(e.getKey());
            List<UpdateRecord> records = new ArrayList<>(old != null ? old.records() : List.of());
            records.addAll(e.getValue());
            entries.put(e.getKey(), new FileEntry(batch.files.get(e.getKey()), records));
        }
    }

    /** Short description of a finished batch for the log. */
    String describe(Batch batch) {
        int records = batch.records.values().stream().mapToInt(List::size).sum();
        return batch.checked.size() + " candidates checked, " + records + " Updates found, "
                + bases.size() + " bases queued";
    }

    private List<KtClassOrObject> candidatesOf(String baseFqn) {
        List<KtClassOrObject> candidates = new ArrayList<>();
        for (String name : SupertypeResolver.namesFor(project, scope, baseFqn)) {
            candidates.addAll(StubIndex.getElements(
                    KotlinSuperClassIndex.Helper.getIndexKey(), name, project, scope, KtClassOrObject.class));
        }
        return candidates;
    }

    private static boolean extendsBase(SupertypeResolver resolver, KtClassOrObject candidate, String baseFqn) {
        for (KtSuperTypeListEntry entry : candidate.getSuperTypeListEntries()) {
            if (resolver.resolve(entry) instanceof Supertype.ClassType type && baseFqn.equals(type.fqn())) return true;
        }
        return false;
    }

    @Nullable
    private static String fqnOf(@Nullable KtClassOrObject classOrObject) {
        FqName fqName = classOrObject != null ? classOrObject.getFqName() : null;
        return fqName != null ? fqName.asString() : null;
    }

    /** Stable across read actions: file path plus FQN, or plus offset for a class without one. */
    @Nullable
    private static String candidateKey(KtClassOrObject candidate) {
        VirtualFile file = candidate.getContainingFile().getVirtualFile();
        if (file == null) return null;
        String fqn = fqnOf(candidate);
        return file.getPath() + "#" + (fqn != null ? fqn : "@" + candidate.getTextOffset());
    }

    private static boolean isFinal(KtClassOrObject candidate) {
        if (!(candidate instanceof KtClass ktClass)) return true;
        return !ktClass.isInterface()
                && !ktClass.hasModifier(KtTokens.OPEN_KEYWORD)
                && !ktClass.hasModifier(KtTokens.ABSTRACT_KEYWORD)
                && !ktClass.hasModifier(KtTokens.SEALED_KEYWORD);
    }
}
