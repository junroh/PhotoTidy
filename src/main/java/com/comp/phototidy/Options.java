package com.comp.phototidy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;

public class Options {

    public boolean dryRun;
    public boolean moveFiles;

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
    
    private final List<String> exts;

    public enum NoExifOpt {
        SKIP,       // skip image file if there is no exif
        FIXED_DIR,  // move it to the dedicate directory
        MODIFIED_DATE,  // use modified date in a file
    }

    public Options() {
        exts = new LinkedList<>();
        initSupportingExts(exts);

        dryRun = true;
        moveFiles = false;
        noExifDir = NoExifOpt.MODIFIED_DATE;
        noExifDirName = "noExif";
        dstDirPattern = "yyyy\\yyyy_MM";
        dstFilePattern = "yyyyMMdd_HHmmss";
        showDupLists = false;
        showUnHandles = false;
        showMovedList = false;

        dirNameFormatter = new SimpleDateFormat(dstDirPattern);
        dirNameFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        fileNameFormatter = new SimpleDateFormat(dstFilePattern);
        fileNameFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    }
    
    private void initSupportingExts(final List<String> exts) {
        exts.add("heic");
        exts.add("heif");
        exts.add("jpg");
        exts.add("jpeg");
        exts.add("png");
        exts.add("mp4");
        exts.add("avi");
        exts.add("3gp");
        exts.add("mov");
        exts.add("wmv");
        exts.add("mts");
    }
    
    public void addSupportingExt(final String ext) {
        exts.add(ext);
    }

    public final List<String> getSupportingExts() {
        return exts;
    }

    public static Options newOptionsFromFile(final String propertyFile) throws IOException {
        try (final InputStream stream = Files.newInputStream(Paths.get(propertyFile))) {
            return newOptionsFromStream(stream);
        }
    }

    public static Options newOptionsFromStream(InputStream stream) throws IOException {
        final Properties prop = new Properties();
        prop.load(stream);
        final Options opts = new Options();
        opts.srcDir = prop.getProperty("dir.source");
        opts.dstBaseDir = prop.getProperty("dir.destination");
        opts.dryRun = Boolean.getBoolean(prop.getProperty("run.mode.drymode", "true").toLowerCase());
        opts.moveFiles = Boolean.getBoolean(prop.getProperty("run.mode.filemove", "false").toLowerCase());
        opts.showDupLists = Boolean.getBoolean(prop.getProperty("show.result.duplicate", "true").toLowerCase());
        opts.showUnHandles = Boolean.getBoolean(prop.getProperty("show.result.unhandled", "true").toLowerCase());
        opts.showMovedList = Boolean.getBoolean(prop.getProperty("show.result.moved", "true").toLowerCase());
        return opts;
    }
}
