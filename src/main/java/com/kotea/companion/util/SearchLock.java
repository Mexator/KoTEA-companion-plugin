package com.kotea.companion.util;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SearchLock {
    private static final Set<String> active = ConcurrentHashMap.newKeySet();

    private SearchLock() {}

    public static boolean tryLock(String key) {
        return active.add(key);
    }

    public static void unlock(String key) {
        active.remove(key);
    }
}
