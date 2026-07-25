package com.comp.cache;

import com.comp.domain.ScannedFile;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Shared map-backed lookup and staleness validation for {@link HashCache} implementations. */
abstract class AbstractMapHashCache implements HashCache {

    protected final Map<String, CacheEntry> entries = new ConcurrentHashMap<>();

    @Override
    public Optional<CacheEntry> get(ScannedFile file) {
        CacheEntry entry = entries.get(key(file));
        return (entry != null && entry.matches(file)) ? Optional.of(entry) : Optional.empty();
    }

    @Override
    public void put(ScannedFile file, CacheEntry entry) {
        entries.put(key(file), entry);
    }

    protected static String key(ScannedFile file) {
        return file.path().toString();
    }
}
