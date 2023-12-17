package com.comp.worker;

import com.comp.exif.ImgFileData;
import com.comp.exif.MetaReader;
import com.comp.phototidy.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Date;

public class MetaParser extends ChainedWorker {

    private static final Logger logger = LogManager.getLogger(MetaParser.class);
    private final Options opts;

    public MetaParser(final Options opts, final ChainedWorker nextChainedWorker,) {
        super("Meta Parser", nextChainedWorker, 1024, logger);
        this.opts = opts;
    }

    @Override
    protected boolean processing(final ImgFileData imgFileData) throws Exception {
        if (imgFileData == null) {
            return false;
        }
        final MetaReader metaReader = new MetaReader(imgFileData.getPath().toFile());
        final Date imageDate = metaReader.getOriginalDate();
        imgFileData.setImageData(imageDate, metaReader.getHash());
        setDestinationPath(imgFileData);
        return true;
    }

    private void setDestinationPath(final ImgFileData imgFile) {
        final String orgFileName = imgFile.getPath().getFileName().toString();
        final int index = orgFileName.lastIndexOf('.');
        if (index < 0) {
            throw new RuntimeException("Invalid file name was found" + orgFileName);
        }
        String dstBaseDir = opts.dstBaseDir;
        final String fileExt = orgFileName.substring(index + 1);
        Date imageDate = imgFile.getExifImageDate();
        if (imageDate == null) {
            logger.debug(String.format("No exif in file found %s", orgFileName));
            noExifCnt++;
            noExifList.add(orgFileName);
            switch (opts.noExifDir) {
                case FIXED_DIR:
                    imgFile.setDestPath(dstBaseDir += (File.separator + opts.noExifDirName));
                    return;
                case MODIFIED_DATE:
                    imageDate = imgFile.getFileModifiedDate();
                    break;
                default:
                    return;
            }
        }
        dstBaseDir += (File.separator + opts.dirNameFormatter.format(imageDate));
        final String newPath = opts.fileNameFormatter.format(imageDate) + "." + fileExt;
        imgFile.setDestPath(dstBaseDir + File.separator + newPath);
    }
}
