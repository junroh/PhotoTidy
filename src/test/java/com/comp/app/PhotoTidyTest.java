package com.comp.app;

import com.comp.dedup.DeduplicationResult;
import com.comp.dedup.Deduplicator;
import com.comp.domain.MediaItem;
import com.comp.pipeline.FileMover;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

class PhotoTidyTest {

    private static MediaItem item(String name) {
        return new MediaItem(Paths.get(name), 1, 1L, null, 0, 0);
    }

    @Test
    void testFullPipelineFilesKeepersAndDuplicates() throws Exception {
        Options opts = dummyOpts();
        TestablePipeline tidy = new TestablePipeline(opts);

        tidy.result = new DeduplicationResult(
                List.of(item("winner.jpg"), item("unique.jpg")),
                List.of(item("dup.jpg")));

        tidy.execute();

        Assertions.assertTrue(tidy.scanHashRun, "scan+hash stage should run");
        Assertions.assertTrue(tidy.dedupRun, "dedup stage should run");
        Assertions.assertEquals(2, tidy.mover.keepers, "both keepers filed");
        Assertions.assertEquals(1, tidy.mover.duplicates, "duplicate filed separately");
    }

    @Test
    void testEmptyResultFilesNothing() throws Exception {
        TestablePipeline tidy = new TestablePipeline(dummyOpts());
        tidy.result = new DeduplicationResult(List.of(), List.of());

        tidy.execute();

        Assertions.assertTrue(tidy.dedupRun);
        Assertions.assertEquals(0, tidy.mover.keepers);
        Assertions.assertEquals(0, tidy.mover.duplicates);
    }

    @Test
    void testHashedItemsAreWhatGetsDeduplicated() throws Exception {
        TestablePipeline tidy = new TestablePipeline(dummyOpts());
        tidy.hashedItems = List.of(item("a.jpg"), item("b.jpg"));
        tidy.result = new DeduplicationResult(List.of(item("a.jpg")), List.of());

        tidy.execute();

        Assertions.assertEquals(2, tidy.itemsHandedToDedup.size(),
                                "dedup must receive exactly the hashed items");
    }

    private static Options dummyOpts() {
        Options opts = new Options();
        opts.srcDir = "src_dummy";
        opts.dstBaseDir = "dst_dummy";
        opts.dryRun = true;
        return opts;
    }

    static class TestablePipeline extends Pipeline {
        boolean scanHashRun, dedupRun;
        List<MediaItem> hashedItems = List.of(item("x.jpg"));
        List<MediaItem> itemsHandedToDedup = new ArrayList<>();
        DeduplicationResult result = new DeduplicationResult(List.of(), List.of());
        final RecordingMover mover;

        TestablePipeline(Options opts) {
            super(opts);
            this.mover = new RecordingMover(opts);
        }

        @Override
        protected List<MediaItem> scanAndHash(Stats stats) {
            scanHashRun = true;
            stats.scannedFiles.set(hashedItems.size());
            return hashedItems;
        }

        @Override
        protected Deduplicator getDeduplicator() {
            return items -> {
                dedupRun = true;
                itemsHandedToDedup = new ArrayList<>(items);
                return result;
            };
        }

        @Override
        protected FileMover createFileMover() {
            return mover;
        }
    }

    static class RecordingMover extends FileMover {
        int keepers, duplicates;

        RecordingMover(Options opts) {
            super(opts);
        }

        @Override
        public int move(DeduplicationResult result, IntConsumer onProgress) {
            keepers = result.keepers().size();
            duplicates = result.duplicates().size();
            return result.total();
        }
    }
}
