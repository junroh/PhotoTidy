package Worker;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

public class Options {

    public boolean dryRun;
    public boolean modifiedDateOnly;
    public boolean moveFiles;
    public boolean fileSizeSame;    // todo:

    public String srcDir;

    public String dstBaseDir;
    public String dstDirPattern;
    public SimpleDateFormat dirNameFormatter;

    public NoExifOpt noExifDir;
    public String noExifDirName;

    public String dstFilePattern;
    public SimpleDateFormat fileNameFormatter;

    public boolean showMovedList;
    public boolean showDupLists;
    public boolean showUnHandles;

    public enum NoExifOpt {
        SKIP,
        FIXED_DIR,
        FILE_DATE,
    }

    public Options() {
        dryRun = true;
        modifiedDateOnly = false;
        moveFiles = false;
        noExifDir = NoExifOpt.FILE_DATE;
        noExifDirName = "noExif";
        dstDirPattern = "yyyy\\yyyy_MM";
        dstFilePattern = "yyyyMMdd_HHmmss";
        showDupLists = false;
        showUnHandles = false;
        showMovedList = false;
        fileSizeSame = true;

        dirNameFormatter = new SimpleDateFormat(dstDirPattern);
        dirNameFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        fileNameFormatter = new SimpleDateFormat(dstFilePattern);
        fileNameFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    }
}
