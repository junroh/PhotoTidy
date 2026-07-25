package com.comp.pipeline;

import com.comp.domain.MediaItem;
import com.comp.app.Options;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.TimeZone;

class FileMoverTest {

    @Test
    void testNoExifModifiedDate() throws Exception {
        Options opts = createOptions();
        opts.noExifDir = Options.NoExifOpt.MODIFIED_DATE;
        opts.dirNameFormatter = new SimpleDateFormat("yyyy/yyyy_MM");

        Date modTime = opts.fileNameFormatter.parse("20200203_112244");
        MediaItem item = createItem("dummy.jpg", null, modTime.getTime());

        FileMover.Locator locator = new FileMover.Locator(opts, p -> false);
        Optional<String> res = locator.resolveDest(item, false);

        Assertions.assertTrue(res.isPresent());
        assertPath("targetDir/2020/2020_02/20200203_112244.jpg", res.get());
    }

    @Test
    void testNoExifStop() {
        Options opts = createOptions();
        opts.noExifDir = Options.NoExifOpt.STOP;
        MediaItem item = createItem("dummy.jpg", null, System.currentTimeMillis());

        FileMover.Locator locator = new FileMover.Locator(opts, p -> false);
        Assertions.assertThrows(RuntimeException.class, () -> locator.resolveDest(item, false));
    }

    @Test
    void testNoExifSkip() {
        Options opts = createOptions();
        opts.noExifDir = Options.NoExifOpt.SKIP;
        MediaItem item = createItem("dummy.jpg", null, System.currentTimeMillis());

        FileMover.Locator locator = new FileMover.Locator(opts, p -> false);
        Assertions.assertTrue(locator.resolveDest(item, false).isEmpty());
    }

    @Test
    void testNoExifFixedTarget() {
        Options opts = createOptions();
        opts.noExifDirName = "noExifDir";
        opts.noExifDir = Options.NoExifOpt.FIXED_DIR;
        MediaItem item = createItem("img_123.jpg", null, System.currentTimeMillis());

        FileMover.Locator locator = new FileMover.Locator(opts, p -> false);
        Optional<String> res = locator.resolveDest(item, false);

        Assertions.assertTrue(res.isPresent());
        assertPath("targetDir/noExifDir/img_123.jpg", res.get());
    }

    @Test
    void testExifDate() throws Exception {
        Options opts = createOptions();
        opts.dirNameFormatter = new SimpleDateFormat("yyyy/yyyy_MM");

        Date exif = opts.fileNameFormatter.parse("20200203_112255");
        MediaItem item = createItem("dummy.jpg", exif, System.currentTimeMillis());

        FileMover.Locator locator = new FileMover.Locator(opts, p -> false);
        Optional<String> res = locator.resolveDest(item, false);
        assertPath("targetDir/2020/2020_02/20200203_112255.jpg", res.get());
    }

    @Test
    void testDuplicateRoutedToDuplicatesFolder() throws Exception {
        Options opts = createOptions();
        opts.dirNameFormatter = new SimpleDateFormat("yyyy/yyyy_MM");

        Date exif = opts.fileNameFormatter.parse("20200203_112255");
        MediaItem item = createItem("dummy.jpg", exif, System.currentTimeMillis());

        FileMover.Locator locator = new FileMover.Locator(opts, p -> false);
        // duplicate=true must land under the duplicates subtree
        Optional<String> res = locator.resolveDest(item, true);
        assertPath("targetDir/duplicates/2020/2020_02/20200203_112255.jpg", res.get());
    }

    @Test
    void testDuplicateIncrease() throws Exception {
        Options opts = createOptions();
        opts.dirNameFormatter = new SimpleDateFormat("yyyy/yyyy_MM");
        opts.duplicateOpt = Options.DuplicateOpt.INCREASE;

        Date exif = opts.fileNameFormatter.parse("20200203_112255");
        MediaItem item = createItem("dummy.jpg", exif, System.currentTimeMillis());

        String existingFile = convert("targetDir/2020/2020_02/20200203_112255.jpg");
        FileMover.Locator locator = new FileMover.Locator(opts, p -> p.toString().equals(existingFile));

        Optional<String> res = locator.resolveDest(item, false);
        assertPath("targetDir/2020/2020_02/20200203_112255_001.jpg", res.get());
    }

    @Test
    void testDuplicateStop() throws Exception {
        Options opts = createOptions();
        opts.duplicateOpt = Options.DuplicateOpt.STOP;

        Date exif = opts.fileNameFormatter.parse("20200203_112255");
        MediaItem item = createItem("dummy.jpg", exif, System.currentTimeMillis());

        FileMover.Locator locator = new FileMover.Locator(opts, p -> true);
        Assertions.assertThrows(RuntimeException.class, () -> locator.resolveDest(item, false));
    }

    @Test
    void testDuplicateOverwrite() throws Exception {
        Options opts = createOptions();
        opts.dirNameFormatter = new SimpleDateFormat("yyyy/yyyy_MM");
        opts.duplicateOpt = Options.DuplicateOpt.OVERWRITE;

        Date exif = opts.fileNameFormatter.parse("20200203_112255");
        MediaItem item = createItem("dummy.jpg", exif, System.currentTimeMillis());

        FileMover.Locator locator = new FileMover.Locator(opts, p -> true);
        Optional<String> res = locator.resolveDest(item, false);
        assertPath("targetDir/2020/2020_02/20200203_112255.jpg", res.get());
    }

    // --- Helpers ---
    private Options createOptions() {
        Options opts = new Options();
        opts.dstBaseDir = "targetDir";
        opts.fileNameFormatter = new SimpleDateFormat("yyyyMMdd_HHmmss");
        opts.fileNameFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        return opts;
    }

    private void assertPath(String expectedWithForwardSlash, String actual) {
        Assertions.assertEquals(convert(expectedWithForwardSlash), actual);
    }

    private String convert(String path) {
        return path.replace("/", File.separator);
    }

    private MediaItem createItem(String name, Date exifDate, long lastModified) {
        return new MediaItem(Paths.get(name), 1024, lastModified, exifDate, 0, 0);
    }
}
