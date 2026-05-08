package com.plugin;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class SearchLock {
    private static final Set<String> active = ConcurrentHashMap.newKeySet();

    private SearchLock() {}

    static boolean tryLock(String key) {
        return active.add(key);
    }

    static void unlock(String key) {
        active.remove(key);
    }
}