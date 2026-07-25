package com.comp.pipeline;

import com.comp.media.CaptureDateReader;
import com.comp.media.ContentSignature;
import com.comp.media.ImageHasher;
import com.comp.domain.MediaItem;
import com.comp.domain.ScannedFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Set;

/**
 * Turns a {@link ScannedFile} into a fully-analyzed {@link MediaItem} (capture date, perceptual
 * hash, content signature). Images are read from disk exactly once and all values are derived from
 * the same bytes. Videos and oversized files get their date only. Per-file failures are absorbed so
 * one bad file never aborts the batch — the file still gets filed by modification date.
 */
public class MediaHasher {

    private static final Logger logger = LogManager.getLogger(MediaHasher.class);

    private static final Set<String> VIDEO_EXTS = Set.of("mp4", "avi", "3gp", "mov", "wmv", "mts");
    private static final long MAX_DECODE_BYTES = 100L * 1024 * 1024;

    public MediaItem hash(final ScannedFile file) {
        final Path path = file.path();
        Date date = null;
        long perceptualHash = 0;
        long signature = 0;

        try {
            if (isDecodable(file)) {
                final byte[] bytes = Files.readAllBytes(path);
                date = CaptureDateReader.readDate(bytes).orElse(null);
                perceptualHash = ImageHasher.fromBytes(bytes);
                signature = ContentSignature.of(bytes);
            } else {
                try (InputStream in = Files.newInputStream(path)) {
                    date = CaptureDateReader.readDate(in).orElse(null);
                }
            }
        } catch (Exception e) {
            logger.warn("Analysis failed for {}: {}", path, e.toString());
        }

        return new MediaItem(path, file.fileSize(), file.lastModified(), date, perceptualHash, signature);
    }

    private boolean isDecodable(ScannedFile file) {
        return file.fileSize() <= MAX_DECODE_BYTES && !isVideo(file.path());
    }

    private boolean isVideo(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 && VIDEO_EXTS.contains(name.substring(dot + 1).toLowerCase());
    }
}
