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

import java.io.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

public class MetaReader {
    private final Metadata metadata;
    private final FileType fileType;
    private byte[] h;

    public MetaReader(File file) throws ImageProcessingException, IOException {
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // do nothing
        }
        try (FileInputStream inputStream = new FileInputStream(file)) {
            FilterInputStream bufferedInputStream = new BufferedInputStream(inputStream);
            if(digest!=null) {
                bufferedInputStream = new DigestInputStream(bufferedInputStream, digest);
            }
            fileType = FileTypeDetector.detectFileType(bufferedInputStream);
            metadata = ImageMetadataReader.readMetadata(bufferedInputStream, -1L, fileType);
            metadata.addDirectory(new FileTypeDirectory(fileType));
        }
        (new FileSystemMetadataReader()).read(file, metadata);
        h = null;
        if(digest!=null) {
            h = digest.digest();
        }
    }

    public byte[] getHash() {
        return h;
    }

    public Date getOriginalDate() {
        switch(fileType) {
            case Avi:
                AviDirectory aviDir = metadata.getFirstDirectoryOfType(AviDirectory.class);
                if (aviDir == null) {
                    return null;
                }
                return aviDir.getDate(AviDirectory.TAG_DATETIME_ORIGINAL);
            case Mp4:
                Mp4Directory mp4Dir = metadata.getFirstDirectoryOfType(Mp4Directory.class);
                if (mp4Dir == null) {
                    return null;
                }
                return mp4Dir.getDate(Mp4Directory.TAG_CREATION_TIME);
            case QuickTime:
                QuickTimeDirectory movDir = metadata.getFirstDirectoryOfType(QuickTimeDirectory.class);
                if (movDir == null) {
                    return null;
                }
                return movDir.getDate(QuickTimeDirectory.TAG_CREATION_TIME);
            default:
                ExifSubIFDDirectory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
                if (directory == null) {
                    return null;
                }
                return directory.getDateOriginal();
        }
    }
}
