package com.comp.worker;

import com.comp.exif.ImgFileData;
import com.comp.phototidy.Options;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Optional;
import java.util.TimeZone;

class FileMoverTest {
    private final static SimpleDateFormat fileNameFormatter = new SimpleDateFormat("yyyyMMdd_HHmmss");

    @BeforeAll
    public static void setUp() {
        fileNameFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public void test() throws Exception {
        final Options opts = new Options();
        final FileMover fm = new FileMover(opts);
        final ImgFileData imgFile = new ImgFileData(Paths.get("dummy"),
                fileNameFormatter.parse("20200203_112233"),
                fileNameFormatter.parse("20200203_112233"));
        fm.processing(imgFile);
    }

    private static String convertFileSeparator(final String input) {
        return input.replace("/", File.separator);
    }

    @Test
    public void testNoExifModifiedDate() throws Exception {
        final Options opts = new Options();
        opts.dstBaseDir = "targetDir";
        opts.noExifDir = Options.NoExifOpt.MODIFIED_DATE;
        final ImgFileData imgFile = new ImgFileData(
                Paths.get("dummy.jpeg"),
                opts.fileNameFormatter.parse("20200203_112233"),
                opts.fileNameFormatter.parse("20200203_112244"));

        final FileMover.Locator locator = new FileMover.Locator(opts, (v) -> false);
        final Optional<String> ret = locator.getDestinationPath(imgFile);
        Assertions.assertTrue(ret.isPresent());
        Assertions.assertEquals(convertFileSeparator("targetDir/2020/2020_02/20200203_112244.jpeg"),
                ret.get());
    }

    @Test
    public void testNoExifStop() throws Exception {
        final Options opts = new Options();
        opts.dstBaseDir = "targetDir";
        opts.noExifDir = Options.NoExifOpt.STOP;
        final ImgFileData imgFile = new ImgFileData(
                Paths.get("dummy.jpeg"),
                opts.fileNameFormatter.parse("20200203_112233"),
                opts.fileNameFormatter.parse("20200203_112244"));

        final FileMover.Locator locator = new FileMover.Locator(opts, (v) -> false);
        Assertions.assertThrows(RuntimeException.class, () -> locator.getDestinationPath(imgFile));
    }

    @Test
    public void testNoExifSkip() throws Exception {
        final Options opts = new Options();
        opts.dstBaseDir = "targetDir";
        opts.noExifDirName = "noExifDir";
        opts.noExifDir = Options.NoExifOpt.SKIP;
        final ImgFileData imgFile = new ImgFileData(
                Paths.get("dummy.jpeg"),
                opts.fileNameFormatter.parse("20200203_112233"),
                opts.fileNameFormatter.parse("20200203_112244"));

        final FileMover.Locator locator = new FileMover.Locator(opts, (v) -> false);
        final Optional<String> ret = locator.getDestinationPath(imgFile);
        Assertions.assertTrue(ret.isEmpty());
    }

    @Test
    public void testNoExifFixedTarget() throws Exception {
        final Options opts = new Options();
        opts.dstBaseDir = "targetDir";
        opts.noExifDirName = "noExifDir";
        opts.noExifDir = Options.NoExifOpt.FIXED_DIR;
        final ImgFileData imgFile = new ImgFileData(
                Paths.get("dummy.jpeg"),
                opts.fileNameFormatter.parse("20200203_112233"),
                opts.fileNameFormatter.parse("20200203_112244"));

        final FileMover.Locator locator = new FileMover.Locator(opts, (v) -> false);
        final Optional<String> ret = locator.getDestinationPath(imgFile);
        Assertions.assertTrue(ret.isPresent());
        Assertions.assertEquals(convertFileSeparator("targetDir/noExifDir/dummy.jpeg"),
                ret.get());
    }

    @Test
    public void testExifDate() throws Exception {
        final Options opts = new Options();
        opts.dstBaseDir = "targetDir";
        final ImgFileData imgFile = new ImgFileData(
                Paths.get("dummy.jpeg"),
                opts.fileNameFormatter.parse("20200203_112233"),
                opts.fileNameFormatter.parse("20200203_112244"));
        imgFile.setExifDate(opts.fileNameFormatter.parse("20200203_112255"));

        final FileMover.Locator locator = new FileMover.Locator(opts, (v) -> false);
        final Optional<String> ret = locator.getDestinationPath(imgFile);
        Assertions.assertTrue(ret.isPresent());
        Assertions.assertEquals(convertFileSeparator("targetDir/2020/2020_02/20200203_112255.jpeg"),
                ret.get());
    }

    @Test
    public void testExifDateIncrease() throws Exception {
        final Options opts = new Options();
        opts.dstBaseDir = "targetDir";
        opts.duplicateOpt = Options.DuplicateOpt.INCREASE;
        final ImgFileData imgFile = new ImgFileData(
                Paths.get("dummy.jpeg"),
                opts.fileNameFormatter.parse("20200203_112233"),
                opts.fileNameFormatter.parse("20200203_112244"));
        imgFile.setExifDate(opts.fileNameFormatter.parse("20200203_112255"));

        final FileMover.Locator locator = new FileMover.Locator(opts, (fileName) ->
                fileName.equals(convertFileSeparator("targetDir/2020/2020_02/20200203_112255.jpeg")));
        final Optional<String> ret = locator.getDestinationPath(imgFile);
        Assertions.assertTrue(ret.isPresent());
        Assertions.assertEquals(convertFileSeparator("targetDir/2020/2020_02/20200203_112255_001.jpeg"),
                ret.get());
    }
}