package com.comp.app;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;

public class Options {

    private final Set<String> supportExts;

    public boolean dryRun;
    public boolean moveFiles;
    public int parserCounts;
    public boolean useCache;
    public String cacheFile;

    public String srcDir;
    public String dstBaseDir;
    public String dstDirPattern;
    public NoExifOpt noExifDir;
    public DuplicateOpt duplicateOpt;
    public String noExifDirName;
    public String duplicatesDirName;
    public String dstFilePattern;
    public SimpleDateFormat dirNameFormatter;
    public SimpleDateFormat fileNameFormatter;

    public Options() {
        supportExts = new HashSet<>();
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
        opts.srcDir = prop.getProperty("dir.source", opts.srcDir);
        opts.dstBaseDir = prop.getProperty("dir.destination", opts.dstBaseDir);
        opts.dryRun = parseBool(prop, "run.mode.drymode", opts.dryRun);
        opts.moveFiles = parseBool(prop, "run.mode.filemove", opts.moveFiles);
        opts.parserCounts = parseInt(prop, "run.parser.count", opts.parserCounts);
        opts.useCache = parseBool(prop, "run.cache.enabled", opts.useCache);
        opts.cacheFile = prop.getProperty("run.cache.file", opts.cacheFile);

        opts.noExifDir = parseEnum(prop, "policy.noexif", NoExifOpt.class, opts.noExifDir);
        opts.noExifDirName = prop.getProperty("policy.noexif.dir", opts.noExifDirName);
        opts.duplicateOpt = parseEnum(prop, "policy.duplicate", DuplicateOpt.class, opts.duplicateOpt);
        opts.duplicatesDirName = prop.getProperty("policy.duplicate.dir", opts.duplicatesDirName);

        opts.dstDirPattern = prop.getProperty("format.dir", opts.dstDirPattern);
        opts.dstFilePattern = prop.getProperty("format.file", opts.dstFilePattern);
        opts.rebuildFormatters();
        return opts;
    }

    private static boolean parseBool(final Properties prop, final String key, final boolean fallback) {
        final String v = prop.getProperty(key);
        return (v == null) ? fallback : Boolean.parseBoolean(v.trim().toLowerCase());
    }

    private static int parseInt(final Properties prop, final String key, final int fallback) {
        final String v = prop.getProperty(key);
        try {
            return (v == null || v.isBlank()) ? fallback : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static <E extends Enum<E>> E parseEnum(final Properties prop, final String key,
                                                   final Class<E> type, final E fallback) {
        final String v = prop.getProperty(key);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, v.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private void initSupportingExts(@NotNull final Set<String> exts) {
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
        parserCounts = 0;   // 0 = auto (all available cores)
        dryRun = true;
        moveFiles = false;
        useCache = true;
        cacheFile = ".phototidy-cache.tsv";
        noExifDir = NoExifOpt.MODIFIED_DATE;
        noExifDirName = "noExif";
        duplicatesDirName = "duplicates";
        dstDirPattern = "yyyy\\yyyy_MM";
        dstFilePattern = "yyyyMMdd_HHmmss";
        duplicateOpt = DuplicateOpt.INCREASE;

        rebuildFormatters();
    }

    private void rebuildFormatters() {
        dirNameFormatter = new SimpleDateFormat(dstDirPattern);
        dirNameFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        fileNameFormatter = new SimpleDateFormat(dstFilePattern);
        fileNameFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public final Set<String> getSupportingExts() {
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
        OVERWRITE,
        STOP        // stop processing
    }
}
