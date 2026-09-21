package com.kotea.companion.index.model;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

/** What the index keeps about one Kotlin file: its Update records. */
public record FileEntry(VirtualFile file, List<UpdateRecord> records) {

    public FileEntry {
        records = List.copyOf(records);
    }

    public boolean isEmpty() {
        return records.isEmpty();
    }
}
