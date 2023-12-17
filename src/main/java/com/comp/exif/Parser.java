package com.comp.exif;

import com.drew.imaging.ImageProcessingException;

import java.io.IOException;
import java.util.Date;

public class Parser {
    public static boolean parseExif(final ImgFileData imgFileData) throws IOException {
        Date imageDate = null;
        try {
            MetaReader metaReader = new MetaReader(imgFileData.getPath().toFile());
            imageDate = metaReader.getOriginalDate();
            imgFileData.setImageData(imageDate, metaReader.getHash());
            return true;
        } catch (ImageProcessingException e) {
            // do nothing
        }
        return false;
    }
}
