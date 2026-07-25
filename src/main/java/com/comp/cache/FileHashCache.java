package com.comp.cache;

import com.comp.domain.ScannedFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

/**
 * Cache persisted as a tab-separated file so runs are resumable.
 *
 * <h2>Writes: periodic checkpoint</h2>
 * Hashing threads only enqueue new entries (lock-free); a single background thread appends them in
 * batches every {@link #CHECKPOINT_MILLIS}. This keeps hashing off the write path and bounds crash
 * loss to roughly one checkpoint interval.
 *
 * <h2>Cleanup: conditional compaction, no stat calls</h2>
 * On {@link #close()} the file is rewritten only if it holds redundant lines (superseded entries or
 * files pruned as deleted); an unchanged rerun writes nothing. Deletions are detected purely in
 * memory: after a {@link #markScanComplete(Path) complete scan} of a root, cached entries under that
 * root that were never looked up are gone from disk and are pruned — no per-file existence checks.
 * <p>
 * Line format: {@code perceptualHash \t fileSize \t lastModified \t exifMillis \t contentSignature
 * \t path} (path last so rare tabs in a path survive the length-limited split).
 */
public class FileHashCache extends AbstractMapHashCache {

    private static final Logger logger = LogManager.getLogger(FileHashCache.class);
    private static final int FIELDS = 6;
    private static final long CHECKPOINT_MILLIS = 2000;

    private final Path file;
    private final BufferedWriter writer;
    private final ConcurrentLinkedQueue<String> pending = new ConcurrentLinkedQueue<>();
    private final Set<String> seen = ConcurrentHashMap.newKeySet();
    private final LongAdder appended = new LongAdder();
    private final Thread checkpointer;

    private volatile boolean running = true;
    private long loadedLines;
    private Path completedRoot;

    public FileHashCache(Path file) throws IOException {
        this.file = file;
        load();
        this.writer = Files.newBufferedWriter(file, CREATE, APPEND);
        this.checkpointer = Thread.ofPlatform().name("hashcache-checkpoint").daemon(true).start(this::checkpointLoop);
    }

    @Override
    public Optional<CacheEntry> get(ScannedFile file) {
        seen.add(key(file));
        return super.get(file);
    }

    @Override
    public void put(ScannedFile file, CacheEntry entry) {
        seen.add(key(file));
        super.put(file, entry);
        pending.add(line(key(file), entry));
        appended.increment();
    }

    @Override
    public void markScanComplete(Path root) {
        this.completedRoot = root;
    }

    @Override
    public void close() throws IOException {
        running = false;
        checkpointer.interrupt();
        join(checkpointer);
        flushPending();
        writer.close();
        compactIfNeeded();
    }

    private void checkpointLoop() {
        try {
            while (running) {
                Thread.sleep(CHECKPOINT_MILLIS);
                flushPending();
            }
        } catch (InterruptedException ignored) {
            // stopping; final flush happens in close()
        }
    }

    private synchronized void flushPending() {
        try {
            for (String line = pending.poll(); line != null; line = pending.poll()) {
                writer.write(line);
                writer.newLine();
            }
            writer.flush();
        } catch (IOException e) {
            logger.error("Failed to checkpoint hash cache: {}", e.toString());
        }
    }

    private void compactIfNeeded() throws IOException {
        int pruned = pruneDeleted();
        long linesInFile = loadedLines + appended.sum();
        if (linesInFile <= entries.size()) {
            return; // no superseded or pruned lines -> file already minimal
        }
        rewrite();
        logger.info("Compacted cache -> {} entries ({} deleted pruned)", entries.size(), pruned);
    }

    private int pruneDeleted() {
        if (completedRoot == null) {
            return 0;
        }
        int pruned = 0;
        for (String key : new ArrayList<>(entries.keySet())) {
            if (!seen.contains(key) && Path.of(key).startsWith(completedRoot)) {
                entries.remove(key);
                pruned++;
            }
        }
        return pruned;
    }

    private void rewrite() throws IOException {
        List<String> lines = new ArrayList<>(entries.size());
        for (Map.Entry<String, CacheEntry> e : entries.entrySet()) {
            lines.add(line(e.getKey(), e.getValue()));
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(tmp, lines, CREATE, TRUNCATE_EXISTING);
        Files.move(tmp, file, REPLACE_EXISTING);
    }

    private void load() throws IOException {
        if (!Files.exists(file)) {
            return;
        }
        for (String line : Files.readAllLines(file)) {
            parse(line).ifPresent(row -> {
                entries.put(row.path(), row.entry());
                loadedLines++;
            });
        }
        logger.info("Loaded {} cached hashes from {}", entries.size(), file);
    }

    private static String line(String path, CacheEntry e) {
        return String.join("\t",
                Long.toString(e.perceptualHash()),
                Long.toString(e.fileSize()),
                Long.toString(e.lastModified()),
                Long.toString(e.exifMillis()),
                Long.toString(e.contentSignature()),
                path);
    }

    private static Optional<Row> parse(String line) {
        String[] f = line.split("\t", FIELDS);
        if (f.length < FIELDS) {
            return Optional.empty();
        }
        try {
            CacheEntry entry = new CacheEntry(
                    Long.parseLong(f[1]), Long.parseLong(f[2]), Long.parseLong(f[3]),
                    Long.parseLong(f[0]), Long.parseLong(f[4]));
            return Optional.of(new Row(f[5], entry));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static void join(Thread t) {
        try {
            t.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record Row(String path, CacheEntry entry) { }
}
