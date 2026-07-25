package com.comp.cache;

import com.comp.domain.ScannedFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Stores analysis results across runs so unchanged files are never re-hashed. Implementations must
 * be safe for concurrent {@link #get}/{@link #put} from multiple hashing threads.
 */
public interface HashCache extends AutoCloseable {

    /** A valid (size + mtime matching) cached entry for the file, or empty on miss/staleness. */
    Optional<CacheEntry> get(ScannedFile file);

    void put(ScannedFile file, CacheEntry entry);

    /**
     * Signals that {@code root} was fully scanned this run, so cached entries under it that were
     * never looked up can be treated as deleted and pruned. Only call after a complete scan.
     */
    default void markScanComplete(Path root) { }

    @Override
    default void close() throws IOException { }
}
