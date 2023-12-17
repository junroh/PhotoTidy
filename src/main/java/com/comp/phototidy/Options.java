package com.comp.phototidy;

import org.jetbrains.annotations.NotNull;

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

    private final List<String> supportExts;
    public boolean dryRun;
    public boolean moveFiles;
    public String srcDir;
    public String dstBaseDir;
    public String dstDirPattern;
    public NoExifOpt noExifDir;
    public DuplicateOpt duplicateOpt;
    public String noExifDirName;
    public String dstFilePattern;
    public SimpleDateFormat dirNameFormatter;
    public SimpleDateFormat fileNameFormatter;
    public boolean showMovedList;
    public boolean showDupLists;
    public boolean showUnHandles;

    public Options() {
        supportExts = new LinkedList<>();
        initSupportingExts(supportExts);
        initDefaultOpts();
    }

    @NotNull
    public static Options newOptionsFromFile(final String propertyFile) throws IOException {
        try (final InputStream stream = Files.newInputStream(Paths.get(propertyFile))) {
            return newOptionsFromStream(stream);
        }
    }

    @NotNull
    public static Options newOptionsFromStream(final InputStream stream) throws IOException {
        final Properties prop = new Properties();
        prop.load(stream);
        final Options opts = new Options();
        opts.srcDir = prop.getProperty("dir.source");
        opts.dstBaseDir = prop.getProperty("dir.destination");
        opts.dryRun = Boolean.parseBoolean(prop.getProperty("run.mode.drymode", "true").toLowerCase());
        opts.moveFiles = Boolean.parseBoolean(prop.getProperty("run.mode.filemove", "false").toLowerCase());
        opts.showDupLists = Boolean.parseBoolean(prop.getProperty("show.result.duplicate", "true").toLowerCase());
        opts.showUnHandles = Boolean.parseBoolean(prop.getProperty("show.result.unhandled", "true").toLowerCase());
        opts.showMovedList = Boolean.parseBoolean(prop.getProperty("show.result.moved", "true").toLowerCase());
        return opts;
    }

    private void initSupportingExts(@NotNull final List<String> exts) {
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

    private void initDefaultOpts() {
        dryRun = true;
        moveFiles = false;
        noExifDir = NoExifOpt.MODIFIED_DATE;
        noExifDirName = "noExif";
        dstDirPattern = "yyyy\\yyyy_MM";
        dstFilePattern = "yyyyMMdd_HHmmss";
        duplicateOpt = DuplicateOpt.INCREASE;
        showDupLists = false;
        showUnHandles = false;
        showMovedList = false;

        dirNameFormatter = new SimpleDateFormat(dstDirPattern);
        dirNameFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        fileNameFormatter = new SimpleDateFormat(dstFilePattern);
        fileNameFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public void addSupportingExt(final String ext) {
        supportExts.add(ext);
    }

    public final List<String> getSupportingExts() {
        return supportExts;
    }

    public enum NoExifOpt {
        SKIP,           // skip image file if there is no exif
        FIXED_DIR,      // move it to the dedicate directory
        MODIFIED_DATE,  // use modified date in a file
        STOP            // stop processing
    }

    public enum DuplicateOpt {
        SKIP,       // skip
        INCREASE,   // move with the different name
        STOP        // stop processing
    }
}
