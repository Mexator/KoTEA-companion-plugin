package com.kotea.companion.index.model;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Every Update record the index holds, by file. A record is built from its parent's record, so when one record
 * changes, the records of its descendants may be out of date: {@link #apply} says which FQNs changed, and
 * {@link #filesDescendingFrom} which files to read again because of them. Used only on the index worker.
 */
public final class UpdateHierarchy {

    /**
     * @param fqns  records that were added, changed or removed
     * @param added records that did not exist before; nobody knows yet which classes extend them
     */
    public record HierarchyChanged(Set<String> fqns, Set<String> added) {
    }

    private final Map<String, FileEntry> entriesByPath = new HashMap<>();
    @Nullable
    private Map<String, UpdateRecord> recordsByFqn;

    public void replaceAll(Map<String, FileEntry> entries) {
        entriesByPath.clear();
        entriesByPath.putAll(entries);
        recordsByFqn = null;
    }

    /** @param updates new entries by path; {@code null} or an empty entry removes the file */
    public HierarchyChanged apply(Map<String, FileEntry> updates) {
        Map<String, UpdateRecord> before = recordsByFqn();
        for (Map.Entry<String, FileEntry> update : updates.entrySet()) {
            if (update.getValue() == null || update.getValue().isEmpty()) entriesByPath.remove(update.getKey());
            else entriesByPath.put(update.getKey(), update.getValue());
        }
        recordsByFqn = null;
        Map<String, UpdateRecord> after = recordsByFqn();

        Set<String> changed = new HashSet<>(before.keySet());
        for (FileEntry entry : updates.values()) {
            if (entry != null) entry.records().forEach(r -> changed.add(r.fqn()));
        }
        changed.removeIf(fqn -> fqn == null || Objects.equals(before.get(fqn), after.get(fqn)));

        Set<String> added = new HashSet<>(changed);
        added.removeAll(before.keySet());
        return new HierarchyChanged(changed, added);
    }

    /** Removals, in the form {@link #apply} takes, for the indexed files at or under {@code paths}. */
    public Map<String, FileEntry> removalsUnder(Set<String> paths) {
        Map<String, FileEntry> removals = new LinkedHashMap<>();
        for (String path : entriesByPath.keySet()) {
            for (String removed : paths) {
                if (path.equals(removed) || path.startsWith(removed + "/")) removals.put(path, null);
            }
        }
        return removals;
    }

    /** Files with a record whose ancestors include any of {@code fqns}. */
    public Set<VirtualFile> filesDescendingFrom(Set<String> fqns) {
        Set<VirtualFile> files = new HashSet<>();
        for (FileEntry entry : entriesByPath.values()) {
            for (UpdateRecord record : entry.records()) {
                if (!Collections.disjoint(record.updateAncestorFqns(), fqns)) files.add(entry.file());
            }
        }
        return files;
    }

    /** Named records by FQN. Not changed by later calls: {@link #apply} builds a new map. */
    public Map<String, UpdateRecord> recordsByFqn() {
        if (recordsByFqn == null) {
            Map<String, UpdateRecord> byFqn = new HashMap<>();
            for (FileEntry entry : entriesByPath.values()) {
                for (UpdateRecord record : entry.records()) {
                    if (record.fqn() != null) byFqn.put(record.fqn(), record);
                }
            }
            recordsByFqn = Collections.unmodifiableMap(byFqn);
        }
        return recordsByFqn;
    }

    public Set<String> paths() {
        return Set.copyOf(entriesByPath.keySet());
    }

    /** Short names of the named records, which a subclass would write in its supertype list. */
    public Set<String> shortNames() {
        Set<String> names = new HashSet<>();
        for (String fqn : recordsByFqn().keySet()) names.add(fqn.substring(fqn.lastIndexOf('.') + 1));
        return names;
    }

    public List<List<UpdateRecord>> recordsByFile() {
        List<List<UpdateRecord>> records = new ArrayList<>();
        for (FileEntry entry : entriesByPath.values()) records.add(entry.records());
        return records;
    }

    public int fileCount() {
        return entriesByPath.size();
    }

    public int recordCount() {
        return entriesByPath.values().stream().mapToInt(e -> e.records().size()).sum();
    }
}
