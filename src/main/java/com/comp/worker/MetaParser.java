package com.comp.worker;

import com.comp.exif.ImageFileInfo;
import com.comp.exif.MetaReader;
import com.comp.phototidy.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNullByDefault;

@NotNullByDefault
public class MetaParser extends ChainedWorker {

    private static final Logger logger = LogManager.getLogger(MetaParser.class);

    public MetaParser(final Options opts, final ChainedWorker nextChainedWorker) {
        super("Meta Parser", nextChainedWorker, 1024, logger);
    }

    @Override
    protected boolean processing(final Object imgFile) throws Exception {
        if (!(imgFile instanceof ImageFileInfo)) {
            return false;
        }
        final ImageFileInfo imageFileInfo = (ImageFileInfo) imgFile;
        final MetaReader metaReader = new MetaReader(imageFileInfo.getPath().toFile());
        metaReader.doProcess();
        imgFileData.setExifDate(metaReader.getExifDate());
        imgFileData.setImageHash(metaReader.getHash());
        return true;
    }
}
