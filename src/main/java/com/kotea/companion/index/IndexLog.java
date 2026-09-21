package com.kotea.companion.index;

import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;

import java.util.Collection;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Debug logging for the index, under the {@code #com.kotea.companion.index} category.
 * Class names are logged with packages masked to initials, so a log can leave the machine it was written on.
 */
public final class IndexLog {

    private static final Logger LOG = Logger.getInstance("#com.kotea.companion.index");
    private static final int MAX_NAMES = 10;

    private IndexLog() {
    }

    public static void debug(Supplier<String> message) {
        if (LOG.isDebugEnabled()) LOG.debug(message.get());
    }

    static void error(String message, Throwable t) {
        LOG.error(message, t);
    }

    /**
     * Runs {@code read}, and turns an unexpected exception into {@code null}, so that one odd class or file
     * cannot stop the whole index. Cancellation still propagates.
     */
    static <T> T orNullOnError(String what, Supplier<T> read) {
        try {
            return read.get();
        } catch (RuntimeException e) {
            if (e instanceof ControlFlowException) throw e;
            LOG.error("KoTEA index could not read " + what, e);
            return null;
        }
    }

    /** {@code com.example.Outer.Inner} becomes {@code c.e.Outer.Inner}. */
    public static String mask(String fqn) {
        if (fqn == null) return "<anonymous>";
        String[] parts = fqn.split("\\.");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            boolean isPackage = i < parts.length - 1 && !part.isEmpty() && Character.isLowerCase(part.charAt(0));
            if (i > 0) result.append('.');
            result.append(isPackage ? part.substring(0, 1) : part);
        }
        return result.toString();
    }

    static String maskAll(Collection<String> fqns) {
        String shown = fqns.stream().limit(MAX_NAMES).map(IndexLog::mask).sorted().collect(Collectors.joining(", "));
        return fqns.size() > MAX_NAMES ? "[" + shown + ", +" + (fqns.size() - MAX_NAMES) + " more]" : "[" + shown + "]";
    }

    public static long ms(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
