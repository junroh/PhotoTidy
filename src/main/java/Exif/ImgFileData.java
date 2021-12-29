package Exif;

import com.drew.imaging.ImageProcessingException;

import java.io.IOException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class ImgFileData {
    private static Date noExifDate;

    private final Path path;
    private final Date creationDate;
    private Date modifiedDate;
    private Date imageDate;
    private byte[] hash;

    static {
        SimpleDateFormat fileNameFormatter = new SimpleDateFormat("yyyyMMdd_HHmmss");
        fileNameFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            noExifDate = fileNameFormatter.parse("19900101_000000");
        } catch (ParseException e) {
            noExifDate = null;
        }
    }

    public ImgFileData(Path path, Date modifiedDate, Date creationDate) {
        this.path = path;
        this.modifiedDate = modifiedDate;
        this.creationDate = creationDate;
    }

    public boolean parseExif() throws IOException {
        Date imageDate = null;
        try {
            MetaReader metaReader = new MetaReader(path.toFile());
            imageDate = metaReader.getOriginalDate();
            hash = metaReader.getHash();
        } catch (ImageProcessingException e) {
            // do nothing
        }
        // set image meta date if it is valid
        if (imageDate != null && noExifDate != null && imageDate.after(noExifDate)) {
            this.imageDate = imageDate;
            return true;
        }
        // set file modified date with creation date if it is invalid
        if (modifiedDate.before(noExifDate)) {
            modifiedDate = creationDate;
        }
        return false;
    }

    public Date getImageDate() {
        return imageDate;
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
}
