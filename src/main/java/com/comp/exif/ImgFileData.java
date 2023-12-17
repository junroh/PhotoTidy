package com.comp.exif;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class ImgFileData {
    private static Date noExifDate;

    private final Path path;
    private final Date creationDate;
    private Date modifiedDate;
    private Date exifImageDate;
    private byte[] hash;
    private String destPath;

    static {
        final SimpleDateFormat fileNameFormatter = new SimpleDateFormat("yyyyMMdd_HHmmss");
        fileNameFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            noExifDate = fileNameFormatter.parse("19900101_000000");
        } catch (ParseException e) {
            noExifDate = null;
        }
    }

    public ImgFileData(Path path, BasicFileAttributes attrs) {
        this.path = path;
        this.modifiedDate = new Date(attrs.lastModifiedTime().toMillis());
        this.creationDate = new Date(attrs.creationTime().toMillis());
    }

    public void setImageData(final Date imageDate, byte[] hash) {
        this.hash = hash;
        // set image meta date if it is valid
        if (imageDate != null && noExifDate != null && imageDate.after(noExifDate)) {
            exifImageDate = imageDate;
            return;
        }
        // set file modified date with creation date if it is invalid
        if (modifiedDate.before(noExifDate)) {
            modifiedDate = creationDate;
        }
    }

    // return null if there is no image date
    public Date getExifImageDate() {
        return exifImageDate;
    }

    public Path getPath() {
        return path;
    }

    public Date getFileModifiedDate() {
        return modifiedDate;
    }

    public byte[] getHash() {
        return hash;
    }

    public void setDestPath(final String destPath) {
        this.destPath = destPath;
    }

    public String getDestPath() {
        return destPath;
    }
}
