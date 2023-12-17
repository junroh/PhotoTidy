package com.comp.exif;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import javax.swing.text.html.Option;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.TimeZone;

@NotNullByDefault
public class ImgFileData {
    private static final Date noExifDate;

    private final Path path;
    private final Date creationDate;
    private final Date modifiedDate;
    private Date exifImageDate;
    private byte[] hash;
    private String destPath;

    static {
        final SimpleDateFormat fileNameFormatter = new SimpleDateFormat("yyyyMMdd_HHmmss");
        fileNameFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));

        try {
            noExifDate = fileNameFormatter.parse("19900101_000000");
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public ImgFileData(final Path path, final Date creationDate, final Date modifiedDate) {
        this.path = path;
        this.creationDate = creationDate;
        // set file modified date to creation date if it is invalid
        if (modifiedDate.before(noExifDate)) {
            this.modifiedDate = creationDate;
        } else {
            this.modifiedDate = modifiedDate;
        }
    }

    public ImgFileData(final Path path, final BasicFileAttributes attrs) {
        this(path, new Date(attrs.creationTime().toMillis()),
                new Date(attrs.lastModifiedTime().toMillis()));
    }

    public void setExifDate(final Date date) {
        this.exifImageDate = date;
    }

    public void setImageHash(final byte[] hash) {
        this.hash = hash;
    }

    public Path getPath() {
        return path;
    }

    public Optional<Date> getExifImageDate() {
        return Optional.of(exifImageDate);
    }

    public Date getFileModifiedDate() {
        return modifiedDate;
    }

    public byte[] getHash() {
        return hash;
    }
}
