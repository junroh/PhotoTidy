package com.comp.cache;

import com.comp.domain.MediaItem;
import com.comp.domain.ScannedFile;

import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

/**
 * Wraps a hashing function with a {@link HashCache}: cache hits skip the (expensive) recompute,
 * misses compute and populate the cache. Safe for concurrent use across hashing threads.
 */
public class CachingHasher {

    private final HashCache cache;
    private final Function<ScannedFile, MediaItem> compute;
    private final LongAdder hits = new LongAdder();

    public CachingHasher(HashCache cache, Function<ScannedFile, MediaItem> compute) {
        this.cache = cache;
        this.compute = compute;
    }

    public MediaItem hash(ScannedFile file) {
        return cache.get(file)
                    .map(entry -> {
                        hits.increment();
                        return entry.toMediaItem(file);
                    })
                    .orElseGet(() -> {
                        MediaItem item = compute.apply(file);
                        cache.put(file, CacheEntry.of(file, item));
                        return item;
                    });
    }

    public long cacheHits() {
        return hits.sum();
    }
}
