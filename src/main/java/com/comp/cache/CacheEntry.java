package com.comp.cache;

import com.comp.domain.MediaItem;
import com.comp.domain.ScannedFile;

import java.util.Date;

/**
 * A cached analysis result for one file, keyed in the cache by path and validated against the
 * file's size and modification time so stale entries are ignored when a file changes.
 */
public record CacheEntry(long fileSize, long lastModified, long exifMillis,
                         long perceptualHash, long contentSignature) {

    static final long NO_EXIF = -1L;

    public static CacheEntry of(ScannedFile file, MediaItem item) {
        long exif = item.getExifDate().map(Date::getTime).orElse(NO_EXIF);
        return new CacheEntry(file.fileSize(), file.lastModified(), exif,
                              item.getPerceptualHash(), item.getContentSignature());
    }

    public boolean matches(ScannedFile file) {
        return fileSize == file.fileSize() && lastModified == file.lastModified();
    }

    public MediaItem toMediaItem(ScannedFile file) {
        Date exif = (exifMillis == NO_EXIF) ? null : new Date(exifMillis);
        return new MediaItem(file.path(), file.fileSize(), file.lastModified(),
                             exif, perceptualHash, contentSignature);
    }
}
