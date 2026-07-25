package com.comp.domain;

import java.nio.file.Path;
import java.util.Date;
import java.util.Optional;

/**
 * An immutable, fully-analyzed media file: filesystem facts plus its capture date, perceptual hash,
 * and a content signature. Created once by the hashing stage and shared freely across threads.
 * Whether an item is a duplicate is decided later by the deduplicator, not stored here.
 */
public final class MediaItem {

    private final Path path;
    private final long fileSize;
    private final long lastModified;
    private final Date exifDate;        // null when no capture date could be read
    private final long perceptualHash;  // 0 when none could be computed (video, unreadable image)
    private final long contentSignature; // 0 when unknown; quick content fingerprint for exact-copy grouping

    public MediaItem(Path path, long fileSize, long lastModified, Date exifDate,
                     long perceptualHash, long contentSignature) {
        this.path = path;
        this.fileSize = fileSize;
        this.lastModified = lastModified;
        this.exifDate = exifDate;
        this.perceptualHash = perceptualHash;
        this.contentSignature = contentSignature;
    }

    public Date getEffectiveDate() {
        return (exifDate != null) ? exifDate : new Date(lastModified);
    }

    public Path getPath() { return path; }

    public long getFileSize() { return fileSize; }

    public long getLastModified() { return lastModified; }

    public Optional<Date> getExifDate() { return Optional.ofNullable(exifDate); }

    public boolean hasExifDate() { return exifDate != null; }

    public long getPerceptualHash() { return perceptualHash; }

    public long getContentSignature() { return contentSignature; }

    @Override
    public String toString() {
        String dateStr = (exifDate != null) ? "[EXIF] " + exifDate : "[OS] " + new Date(lastModified);
        return String.format("MediaItem{path=%s, size=%d, date=%s, pHash=%016x}",
                             path.getFileName(), fileSize, dateStr, perceptualHash);
    }
}
