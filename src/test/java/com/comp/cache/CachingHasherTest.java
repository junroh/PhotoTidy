package com.comp.cache;

import com.comp.domain.MediaItem;
import com.comp.domain.ScannedFile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

class CachingHasherTest {

    private static ScannedFile file(String path, long size, long mtime) {
        return new ScannedFile(Path.of(path), size, mtime);
    }

    private static MediaItem item(ScannedFile f, long phash) {
        return new MediaItem(f.path(), f.fileSize(), f.lastModified(), null, phash, 0);
    }

    @Test
    void testMissComputesThenHitServesFromCache() {
        AtomicInteger computeCalls = new AtomicInteger();
        ScannedFile f = file("/a.jpg", 10, 20);

        CachingHasher hasher = new CachingHasher(new InMemoryHashCache(), scanned -> {
            computeCalls.incrementAndGet();
            return item(scanned, 0x99L);
        });

        MediaItem first = hasher.hash(f);
        MediaItem second = hasher.hash(f);

        Assertions.assertEquals(1, computeCalls.get(), "second call must be served from cache");
        Assertions.assertEquals(1, hasher.cacheHits(), "exactly one cache hit");
        Assertions.assertEquals(0x99L, first.getPerceptualHash());
        Assertions.assertEquals(0x99L, second.getPerceptualHash());
    }

    @Test
    void testChangedFileRecomputes() {
        AtomicInteger computeCalls = new AtomicInteger();
        CachingHasher hasher = new CachingHasher(new InMemoryHashCache(), scanned -> {
            computeCalls.incrementAndGet();
            return item(scanned, 1L);
        });

        hasher.hash(file("/a.jpg", 10, 20));
        hasher.hash(file("/a.jpg", 10, 21)); // mtime changed -> stale

        Assertions.assertEquals(2, computeCalls.get(), "a changed file must be recomputed");
        Assertions.assertEquals(0, hasher.cacheHits());
    }
}
