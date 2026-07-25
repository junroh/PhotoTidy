package com.comp.app;

import com.comp.cache.CachingHasher;
import com.comp.cache.FileHashCache;
import com.comp.cache.HashCache;
import com.comp.cache.InMemoryHashCache;
import com.comp.cli.Console;
import com.comp.concurrent.Parallel;
import com.comp.dedup.DeduplicationResult;
import com.comp.dedup.Deduplicator;
import com.comp.dedup.MihDeduplicator;
import com.comp.domain.MediaItem;
import com.comp.pipeline.DirectoryScanner;
import com.comp.pipeline.FileMover;
import com.comp.pipeline.MediaHasher;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

/**
 * Runs the pipeline as three stages: {@code scan + hash (overlapped) -> deduplicate (barrier) ->
 * file (serial)}.
 * <p>
 * Deduplication needs every item hashed before it can classify any, so the batch is held in heap
 * regardless (O(N)); scanning is streamed into the parallel hashing stage (via {@link Parallel}) so
 * the two overlap, and a {@link HashCache} lets unchanged files skip hashing on reruns.
 * <p>
 * {@link #scanAndHash}, {@link #getDeduplicator}, {@link #createFileMover} and {@link #openCache}
 * are overridable so tests can drive stages in isolation.
 */
public class Pipeline {

    private final Options opts;
    private final int hashThreads;

    public Pipeline(Options opts) {
        this.opts = opts;
        this.hashThreads = opts.parserCounts > 0
                ? opts.parserCounts
                : Math.max(2, Runtime.getRuntime().availableProcessors());
    }

    public void execute() throws Exception {
        final Stats stats = new Stats();
        printHeader();

        Console.section("Phase 1: Scanning & hashing...");
        final List<MediaItem> items = scanAndHash(stats);
        Console.kv("Scanned", stats.scannedFiles.get());
        Console.kv("From cache", stats.cacheHits.get());

        Console.section("Phase 2: Deduplicating...");
        final DeduplicationResult result = getDeduplicator().deduplicate(items);
        stats.duplicatesFound.set(result.duplicates().size());
        Console.kv("Keepers", result.keepers().size());
        Console.kv("Duplicates", result.duplicates().size());

        Console.section("Phase 3: Filing...");
        final var progress = new Console.ProgressBar(result.total(), "Filing");
        final int moved = createFileMover().move(result, progress::update);
        stats.movedFiles.set(moved);

        printSummary(stats);
    }

    /**
     * Discovers files and hashes them in one overlapped stage: the directory walk runs on its own
     * thread and feeds the hashing workers as files are found. Returns every analyzed item.
     */
    protected List<MediaItem> scanAndHash(final Stats stats) throws Exception {
        final DirectoryScanner scanner = new DirectoryScanner(opts.srcDir, opts.getSupportingExts());
        final MediaHasher hasher = new MediaHasher();
        try (HashCache cache = openCache()) {
            final CachingHasher caching = new CachingHasher(cache, hasher::hash);
            final List<MediaItem> items = Parallel.mapStreaming(scanner::traverse, hashThreads, caching::hash);
            cache.markScanComplete(Paths.get(opts.srcDir));
            stats.scannedFiles.set(items.size());
            stats.cacheHits.set((int) caching.cacheHits());
            return items;
        }
    }

    protected HashCache openCache() throws IOException {
        return opts.useCache ? new FileHashCache(Paths.get(opts.cacheFile)) : new InMemoryHashCache();
    }

    protected Deduplicator getDeduplicator() {
        return new MihDeduplicator();
    }

    protected FileMover createFileMover() {
        return new FileMover(opts);
    }

    private void printHeader() {
        Console.header("Photo Tidy");
        Console.kv("Source", opts.srcDir);
        Console.kv("Dest", opts.dstBaseDir);
        Console.kv("Threads", hashThreads);
        Console.kv("Mode", opts.dryRun ? "DRY RUN" : "LIVE");
    }

    private void printSummary(Stats stats) {
        Console.header("Summary");
        Console.kv("Total Time", stats.getDuration());
        Console.kv("Scanned", stats.scannedFiles.get());
        Console.kv("From cache", stats.cacheHits.get());
        Console.kv("Duplicates", stats.duplicatesFound.get());
        Console.kv("Moved", stats.movedFiles.get());
        Console.separator();
    }
}
