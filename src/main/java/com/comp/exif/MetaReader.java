package com.comp.exif;

import com.drew.imaging.FileType;
import com.drew.imaging.FileTypeDetector;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.avi.AviDirectory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.file.FileSystemMetadataReader;
import com.drew.metadata.file.FileTypeDirectory;
import com.drew.metadata.mov.QuickTimeDirectory;
import com.drew.metadata.mp4.Mp4Directory;
import dev.brachtendorf.jimagehash.hashAlgorithms.HashingAlgorithm;
import dev.brachtendorf.jimagehash.hashAlgorithms.PerceptiveHash;
import dev.brachtendorf.jimagehash.matcher.exotic.SingleImageMatcher;
import dev.brachtendorf.jimagehash.matcher.persistent.database.H2DatabaseImageMatcher;

import java.io.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Optional;

public class MetaReader {
    private byte[] hashData;
    private Date dateFromMeta;
    private final MessageDigest digest;
    private final File file;

    public MetaReader(final File file) throws NoSuchAlgorithmException {
        this.digest = MessageDigest.getInstance("SHA-256");
        this.file = file;
    }

    public void doProcess() throws ImageProcessingException, IOException {
        try (final FileInputStream inputStream = new FileInputStream(file)) {
            final FilterInputStream bufferedInputStream =
                    new DigestInputStream(new BufferedInputStream(inputStream), digest);
            final FileType fileType = FileTypeDetector.detectFileType(bufferedInputStream);
            final Metadata metadata = ImageMetadataReader.readMetadata(bufferedInputStream, -1L, fileType);
            metadata.addDirectory(new FileTypeDirectory(fileType));
            (new FileSystemMetadataReader()).read(file, metadata);
            hashData = digest.digest();
            dateFromMeta = getDateFromMeta(fileType, metadata);
        }
    }

    public byte[] getHash() {
        return hashData;
    }

    public Date getExifDate() {
        return dateFromMeta;
    }

    private void check() throws IOException {
        final HashingAlgorithm hasher = new PerceptiveHash(32);
        final var hash = hasher.hash(file);
        H2DatabaseImageMatcher
    }

    private Date getDateFromMeta(final FileType fileType, final Metadata metadata) {
        Date date = null;
        switch(fileType) {
            case Avi:
                final AviDirectory aviDir = metadata.getFirstDirectoryOfType(AviDirectory.class);
                if (aviDir != null) {
                    date = aviDir.getDate(AviDirectory.TAG_DATETIME_ORIGINAL);
                }
            case Mp4:
                Mp4Directory mp4Dir = metadata.getFirstDirectoryOfType(Mp4Directory.class);
                if (mp4Dir != null) {
                    date = mp4Dir.getDate(Mp4Directory.TAG_CREATION_TIME);
                }
            case QuickTime:
                QuickTimeDirectory movDir = metadata.getFirstDirectoryOfType(QuickTimeDirectory.class);
                if (movDir != null) {
                    date = movDir.getDate(QuickTimeDirectory.TAG_CREATION_TIME);
                }
            default:
                ExifSubIFDDirectory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
                if (directory != null) {
                    date = directory.getDateOriginal();
                }
        }
        return date;
    }
}
