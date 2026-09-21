package com.kotea.companion.index;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeAnyChangeAbstractAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records which Kotlin files changed, from unsaved edits and from changes on disk. The listeners only record;
 * all the work happens later, on the index worker.
 */
final class ChangeCollector {

    /**
     * @param files        files to read again, with created directories already expanded into their files
     * @param removedPaths deleted, moved or renamed files and directories, by their old path
     * @param eventCount   how many changes were recorded, for the log
     */
    record Changes(Set<VirtualFile> files, Set<String> removedPaths, int eventCount) {
        boolean isEmpty() {
            return eventCount == 0;
        }
    }

    private final Set<VirtualFile> dirtyFiles = ConcurrentHashMap.newKeySet();
    private final Set<VirtualFile> createdDirectories = ConcurrentHashMap.newKeySet();
    private final Set<String> removedPaths = ConcurrentHashMap.newKeySet();

    void register(Project project, Disposable parent, Runnable onChange) {
        PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeAnyChangeAbstractAdapter() {
            @Override
            protected void onChange(@Nullable PsiFile file) {
                VirtualFile virtualFile = file != null ? file.getVirtualFile() : null;
                if (virtualFile == null || !isKotlin(virtualFile)) return;
                dirtyFiles.add(virtualFile);
                onChange.run();
            }
        }, parent);

        project.getMessageBus().connect(parent).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(@NotNull List<? extends VFileEvent> events) {
                boolean changed = false;
                for (VFileEvent event : events) changed |= record(event);
                if (changed) onChange.run();
            }
        });
    }

    void clear() {
        dirtyFiles.clear();
        createdDirectories.clear();
        removedPaths.clear();
    }

    Changes drain() {
        Set<VirtualFile> files = drain(dirtyFiles);
        Set<VirtualFile> directories = drain(createdDirectories);
        Set<String> removed = drain(removedPaths);
        int eventCount = files.size() + directories.size() + removed.size();
        if (!directories.isEmpty()) files.addAll(ReadAction.compute(() -> kotlinFilesUnder(directories)));
        return new Changes(files, removed, eventCount);
    }

    private boolean record(VFileEvent event) {
        switch (event) {
            case VFileDeleteEvent delete -> {
                if (!delete.getFile().isDirectory() && !isKotlin(delete.getPath())) return false;
                removedPaths.add(delete.getPath());
            }
            case VFileMoveEvent move -> {
                if (!move.getFile().isDirectory() && !isKotlin(move.getFile())) return false;
                removedPaths.add(move.getOldPath());
                recordAdded(move.getFile());
            }
            case VFilePropertyChangeEvent property -> {
                if (!property.isRename()) return false;
                VirtualFile file = property.getFile();
                if (!file.isDirectory() && !isKotlin(file) && !isKotlin(property.getOldPath())) return false;
                removedPaths.add(property.getOldPath());
                recordAdded(property.getFile());
            }
            case VFileCreateEvent create -> {
                return recordAdded(create.getFile());
            }
            case VFileCopyEvent copy -> {
                return recordAdded(copy.findCreatedFile());
            }
            default -> {
                VirtualFile file = event.getFile();
                if (file == null || !isKotlin(file)) return false;
                dirtyFiles.add(file);
            }
        }
        return true;
    }

    private boolean recordAdded(@Nullable VirtualFile file) {
        if (file == null) return false;
        if (file.isDirectory()) {
            createdDirectories.add(file);
        } else if (isKotlin(file)) {
            dirtyFiles.add(file);
        } else {
            return false;
        }
        return true;
    }

    private static boolean isKotlin(VirtualFile file) {
        return isKotlin(file.getName());
    }

    private static boolean isKotlin(String name) {
        return name.endsWith(".kt");
    }

    private static Set<VirtualFile> kotlinFilesUnder(Set<VirtualFile> directories) {
        Set<VirtualFile> files = new HashSet<>();
        for (VirtualFile directory : directories) {
            if (!directory.isValid()) continue;
            VfsUtilCore.visitChildrenRecursively(directory, new VirtualFileVisitor<Void>() {
                @Override
                public boolean visitFile(@NotNull VirtualFile file) {
                    if (!file.isDirectory() && isKotlin(file)) files.add(file);
                    return true;
                }
            });
        }
        return files;
    }

    private static <T> Set<T> drain(Set<T> source) {
        Set<T> drained = new HashSet<>();
        for (T item : source) {
            if (source.remove(item)) drained.add(item);
        }
        return drained;
    }
}
