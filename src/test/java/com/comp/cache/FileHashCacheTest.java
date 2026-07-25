package com.comp.cache;

import com.comp.domain.MediaItem;
import com.comp.domain.ScannedFile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Date;
import java.util.Optional;

class FileHashCacheTest {

    private static ScannedFile file(String path, long size, long mtime) {
        return new ScannedFile(Path.of(path), size, mtime);
    }

    private static MediaItem item(String path, long size, long mtime, Date exif, long phash) {
        return new MediaItem(Path.of(path), size, mtime, exif, phash, 0xF00DL);
    }

    @Test
    void testPersistsAndReloadsAcrossInstances(@TempDir Path dir) throws Exception {
        Path cacheFile = dir.resolve("cache.tsv");
        ScannedFile f = file("/photos/a.jpg", 1000, 5555);

        try (FileHashCache cache = new FileHashCache(cacheFile)) {
            cache.put(f, CacheEntry.of(f, item("/photos/a.jpg", 1000, 5555, new Date(1234000), 0xABCDL)));
        }

        try (FileHashCache reopened = new FileHashCache(cacheFile)) {
            Optional<CacheEntry> hit = reopened.get(f);
            Assertions.assertTrue(hit.isPresent(), "entry should survive a reopen");
            Assertions.assertEquals(0xABCDL, hit.get().perceptualHash());
            Assertions.assertEquals(0xF00DL, hit.get().contentSignature(), "signature must round-trip");
            Assertions.assertEquals(new Date(1234000), hit.get().toMediaItem(f).getExifDate().orElseThrow());
        }
    }

    @Test
    void testStaleWhenSizeOrMtimeChanges(@TempDir Path dir) throws Exception {
        ScannedFile original = file("/photos/a.jpg", 1000, 5555);
        try (FileHashCache cache = new FileHashCache(dir.resolve("cache.tsv"))) {
            cache.put(original, CacheEntry.of(original, item("/photos/a.jpg", 1000, 5555, null, 7L)));

            Assertions.assertTrue(cache.get(file("/photos/a.jpg", 1000, 5555)).isPresent());
            Assertions.assertTrue(cache.get(file("/photos/a.jpg", 2000, 5555)).isEmpty(), "size change invalidates");
            Assertions.assertTrue(cache.get(file("/photos/a.jpg", 1000, 9999)).isEmpty(), "mtime change invalidates");
        }
    }

    @Test
    void testPrunesDeletedFileAfterCompleteScan(@TempDir Path dir) throws Exception {
        Path cacheFile = dir.resolve("cache.tsv");
        ScannedFile kept = file("/photos/kept.jpg", 1, 1);
        ScannedFile deleted = file("/photos/deleted.jpg", 2, 2);

        try (FileHashCache first = new FileHashCache(cacheFile)) {
            first.put(kept, CacheEntry.of(kept, item("/photos/kept.jpg", 1, 1, null, 1L)));
            first.put(deleted, CacheEntry.of(deleted, item("/photos/deleted.jpg", 2, 2, null, 2L)));
        }

        // Second run scans /photos but only sees kept.jpg (deleted.jpg is gone from disk).
        try (FileHashCache second = new FileHashCache(cacheFile)) {
            second.get(kept);
            second.markScanComplete(Path.of("/photos"));
        }

        try (FileHashCache third = new FileHashCache(cacheFile)) {
            Assertions.assertTrue(third.get(kept).isPresent(), "living file kept");
            Assertions.assertTrue(third.get(deleted).isEmpty(), "deleted file pruned after complete scan");
        }
    }

    @Test
    void testUnscannedRootIsNotPruned(@TempDir Path dir) throws Exception {
        Path cacheFile = dir.resolve("cache.tsv");
        ScannedFile other = file("/other/x.jpg", 3, 3);

        try (FileHashCache first = new FileHashCache(cacheFile)) {
            first.put(other, CacheEntry.of(other, item("/other/x.jpg", 3, 3, null, 9L)));
        }
        // Complete scan of a *different* root must not evict entries outside it.
        try (FileHashCache second = new FileHashCache(cacheFile)) {
            second.markScanComplete(Path.of("/photos"));
        }
        try (FileHashCache third = new FileHashCache(cacheFile)) {
            Assertions.assertTrue(third.get(other).isPresent(), "entries outside the scanned root are retained");
        }
    }

    @Test
    void testNoExifRoundTrips(@TempDir Path dir) throws Exception {
        Path cacheFile = dir.resolve("cache.tsv");
        ScannedFile f = file("/photos/noexif.jpg", 42, 99);
        try (FileHashCache cache = new FileHashCache(cacheFile)) {
            cache.put(f, CacheEntry.of(f, item("/photos/noexif.jpg", 42, 99, null, 0)));
        }
        try (FileHashCache reopened = new FileHashCache(cacheFile)) {
            MediaItem restored = reopened.get(f).orElseThrow().toMediaItem(f);
            Assertions.assertTrue(restored.getExifDate().isEmpty(), "absent date must round-trip as absent");
        }
    }
}
