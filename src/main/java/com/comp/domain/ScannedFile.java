package com.comp.domain;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * A media file discovered during traversal, before any content is read.
 * Carries only cheap filesystem attributes; hashing and metadata extraction happen later.
 *
 * @param path         absolute path to the file
 * @param fileSize     size in bytes
 * @param lastModified effective modification epoch-millis (falls back to creation time when the
 *                     OS modification time is implausibly old)
 */
public record ScannedFile(Path path, long fileSize, long lastModified) {

    // Filesystems sometimes report epoch-0 / garbage mtimes; below this we trust creation time.
    private static final long MIN_VALID_EPOCH = 631152000000L; // 1990-01-01

    public static ScannedFile from(Path path, BasicFileAttributes attrs) {
        long modified = attrs.lastModifiedTime().toMillis();
        long effective = (modified < MIN_VALID_EPOCH) ? attrs.creationTime().toMillis() : modified;
        return new ScannedFile(path, attrs.size(), effective);
    }
}
