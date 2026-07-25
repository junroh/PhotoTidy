package com.comp.media;

import com.drew.imaging.FileType;
import com.drew.imaging.FileTypeDetector;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.avi.AviDirectory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.mov.QuickTimeDirectory;
import com.drew.metadata.mp4.Mp4Directory;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Optional;

/** Extracts the capture date from an image/video's embedded metadata. */
public final class CaptureDateReader {

    private CaptureDateReader() { }

    public static Optional<Date> readDate(byte[] bytes) throws IOException, ImageProcessingException {
        return readDate(new ByteArrayInputStream(bytes));
    }

    public static Optional<Date> readDate(InputStream source) throws IOException, ImageProcessingException {
        final BufferedInputStream in = new BufferedInputStream(source);
        final FileType fileType = FileTypeDetector.detectFileType(in);
        final Metadata metadata = ImageMetadataReader.readMetadata(in, -1L, fileType);
        return Optional.ofNullable(dateOf(fileType, metadata));
    }

    private static Date dateOf(FileType fileType, Metadata metadata) {
        return switch (fileType) {
            case Avi -> dateFrom(metadata, AviDirectory.class, AviDirectory.TAG_DATETIME_ORIGINAL);
            case Mp4 -> dateFrom(metadata, Mp4Directory.class, Mp4Directory.TAG_CREATION_TIME);
            case QuickTime -> dateFrom(metadata, QuickTimeDirectory.class, QuickTimeDirectory.TAG_CREATION_TIME);
            default -> {
                ExifSubIFDDirectory dir = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
                yield (dir != null) ? dir.getDateOriginal() : null;
            }
        };
    }

    private static <T extends com.drew.metadata.Directory> Date dateFrom(Metadata metadata, Class<T> type, int tag) {
        T dir = metadata.getFirstDirectoryOfType(type);
        return (dir != null) ? dir.getDate(tag) : null;
    }
}
