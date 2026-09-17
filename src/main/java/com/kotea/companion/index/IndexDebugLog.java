package com.kotea.companion.index;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Temporary diagnostic log for what triggers a KoTEA index rebuild and when, written to its
 * own file so it can be read without enabling idea.log Debug Log Settings. Delete once the
 * trigger investigation (long reindex on terminal builds / big rebases) is done.
 */
final class IndexDebugLog {

    private static final Logger LOG = Logger.getInstance(IndexDebugLog.class);
    private static final Path FILE = Path.of(PathManager.getLogPath(), "kotea-index-debug.log");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private IndexDebugLog() {
    }

    static synchronized void log(String message) {
        LOG.info(message);

        String line = LocalDateTime.now().format(TIME_FORMAT) + " [" + Thread.currentThread().getName() + "] "
                + message + System.lineSeparator();
        try {
            Files.writeString(FILE, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOG.warn("Failed to write KoTEA index debug log to " + FILE, e);
        }
    }
}
