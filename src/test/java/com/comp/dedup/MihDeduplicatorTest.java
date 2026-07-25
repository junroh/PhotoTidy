package com.comp.dedup;

import com.comp.domain.MediaItem;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;

class MihDeduplicatorTest {

    /** size drives keeper choice; phash drives near-dup clustering; signature drives exact grouping. */
    private static MediaItem item(String name, long size, long phash, long signature) {
        return new MediaItem(Paths.get(name), size, 1L, null, phash, signature);
    }

    private final MihDeduplicator dedup = new MihDeduplicator();

    @Test
    void testExactCopiesCollapseBySignature() {
        // Same (size, signature) => certain copies regardless of phash; tie-break keeps shortest path.
        MediaItem a = item("a.jpg", 100, 0x11L, 0xABCD);
        MediaItem b = item("bb.jpg", 100, 0x22L, 0xABCD);

        DeduplicationResult r = dedup.deduplicate(List.of(a, b));

        Assertions.assertEquals(1, r.keepers().size());
        Assertions.assertEquals(1, r.duplicates().size());
        Assertions.assertEquals("a.jpg", r.keepers().getFirst().getPath().getFileName().toString());
    }

    @Test
    void testNearDuplicatesClusterWithinRadius() {
        // distance(0x20, 0x3F) = 5 (<= radius) => one cluster, largest kept.
        MediaItem a = item("a.jpg", 500, 0x20L, 1);
        MediaItem b = item("b.jpg", 100, 0x3FL, 2);

        DeduplicationResult r = dedup.deduplicate(List.of(a, b));

        Assertions.assertEquals(1, r.keepers().size());
        Assertions.assertEquals(1, r.duplicates().size());
        Assertions.assertEquals("a.jpg", r.keepers().getFirst().getPath().getFileName().toString());
    }

    @Test
    void testDistinctImagesAllKept() {
        // distance(0x0001, 0xFFFF) = 15 (> radius) => no clustering.
        MediaItem a = item("a.jpg", 100, 0x0001L, 1);
        MediaItem b = item("b.jpg", 100, 0xFFFFL, 2);

        DeduplicationResult r = dedup.deduplicate(List.of(a, b));

        Assertions.assertEquals(2, r.keepers().size());
        Assertions.assertTrue(r.duplicates().isEmpty());
    }

    @Test
    void testTransitiveClusteringViaUnionFind() {
        // A~B (d=5) and A~C (d=5) but B~C (d=10, > radius): union-find still merges all three.
        MediaItem a = item("a.jpg", 500, 0x400L, 1);   // bit 10
        MediaItem b = item("b.jpg", 300, 0x41FL, 2);   // bits 0-4,10  -> d(A)=5, d(C)=10
        MediaItem c = item("c.jpg", 100, 0x7E0L, 3);   // bits 5-10    -> d(A)=5, d(B)=10

        DeduplicationResult r = dedup.deduplicate(List.of(a, b, c));

        Assertions.assertEquals(1, r.keepers().size(), "connected component collapses to one keeper");
        Assertions.assertEquals(2, r.duplicates().size());
        Assertions.assertEquals("a.jpg", r.keepers().getFirst().getPath().getFileName().toString());
    }

    @Test
    void testZeroHashNeverDeduplicated() {
        // phash 0 and signature 0 (e.g. videos): always kept, never grouped.
        MediaItem v1 = item("v1.mp4", 100, 0, 0);
        MediaItem v2 = item("v2.mp4", 100, 0, 0);

        DeduplicationResult r = dedup.deduplicate(List.of(v1, v2));

        Assertions.assertEquals(2, r.keepers().size());
        Assertions.assertTrue(r.duplicates().isEmpty());
    }

    @Test
    void testEveryItemClassifiedExactlyOnce() {
        MediaItem exactKeep = item("x1.jpg", 400, 0x01L, 0x99);
        MediaItem exactDup = item("x2.jpg", 400, 0x01L, 0x99); // byte-identical copy of x1
        MediaItem near = item("n.jpg", 100, 0x03L, 7);         // d(0x01)=1 -> near-dup of the exact keeper
        MediaItem lone = item("lone.jpg", 100, 0xFFFFL, 8);

        DeduplicationResult r = dedup.deduplicate(List.of(exactKeep, exactDup, near, lone));

        Assertions.assertEquals(4, r.total(), "keepers + duplicates == input count");
        Assertions.assertEquals(2, r.keepers().size(), "one from the exact+near cluster, plus lone");
        Assertions.assertEquals(2, r.duplicates().size());
    }

    @Test
    void testEmptyInput() {
        Assertions.assertEquals(0, dedup.deduplicate(List.of()).total());
    }
}
