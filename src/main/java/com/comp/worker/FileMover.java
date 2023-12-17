package com.comp.worker;

import com.comp.Exceptions.DuplicateImageException;
import com.comp.exif.ImgFileData;
import com.comp.exif.Parser;
import com.comp.phototidy.Options;
import com.comp.utility.PrintOut;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class FileMover extends ChainedWorker {

    private static final Logger logger = LogManager.getLogger(FileMover.class);
    private final Options opts;

    public FileMover(Options opts) {
        super("File mover", null, 1024, logger);
        this.opts = opts;
    }

    protected boolean processing(final ImgFileData imgFile) throws Exception {
        final String dstPath = imgFile.getDestPath();
        if (dstPath != null) {
            PrintOut.format("Move %s to %s                        \r", imgFile.getPath(), dstPath);
            return doMove(imgFile, dstPath);
        }
        return false;
    }

    private boolean doMove(ImgFileData src, String dst) throws IOException {
        final int index = dst.lastIndexOf(File.separator);
        if (index >= dst.length()) {
            throw new IOException("Invalid destination directory " + dst);
        }
        final String dstDir = dst.substring(0, index);
        if (!createDir(dstDir)) {
            throw new IOException("Failed to create directory " + dstDir);
        }
        try {
            final String dstFileName = dst.substring(index + 1);
            final String newDstName = getDstFileName(src, dstDir, dstFileName);
            // todo: parallelism can be added. But, it wouldn't be helpful much
            moveFile(src.getPath(), newDstName);
            return true;
        } catch (DuplicateImageException e) {
            // do nothing
        }
        return false;
    }

    private boolean createDir(String dstDir) {
        File file = new File(dstDir);
        if (file.exists()) {
            if (file.isDirectory()) {
                return true;
            }
            logger.error("Failed to create dir " + dstDir + " due to that the same name of file exists");
            return false;
        }
        if (opts.dryRun) {
            return true;
        }
        if (file.mkdirs()) {
            return true;
        }
        logger.error("Failed to create dir " + dstDir);
        return false;
    }

    private String getDstFileName(ImgFileData src, String dstDir, String dstName) throws IOException, DuplicateImageException {
        int index = dstName.lastIndexOf('.');
        String baseName = dstName.substring(0, index);
        String baseExt = dstName.substring(index + 1);
        int startIdx = 0;
        while (true) {
            String newFileName;
            if (startIdx == 0) {
                newFileName = dstDir + File.separator + dstName;
            } else {
                newFileName = String.format("%s%s_%03d.%s", dstDir + File.separator, baseName, startIdx, baseExt);
            }
            logger.debug("New name is " + newFileName);
            final File existFile = new File(newFileName);
            if (!existFile.exists()) {
                return newFileName;
            }
            // same original date and same size
            if (isDuplicate(existFile, src)) {
                throw new DuplicateImageException(
                        String.format("%s is duplicated by the name of %s",
                                      src.getPath().toString(), newFileName));
            }
            startIdx++;
        }
    }

    private boolean isDuplicate(File dstFile, ImgFileData srcImage) throws IOException {
        final BasicFileAttributes attr = Files.readAttributes(dstFile.toPath(), BasicFileAttributes.class);
        final ImgFileData tgtImage = new ImgFileData(dstFile.toPath(), attr);
        if (Parser.parseExif(tgtImage)) {
            if (srcImage.getExifImageDate() != null &&
                    tgtImage.getExifImageDate().compareTo(srcImage.getExifImageDate()) == 0) {
                return true;
            }
        }
        if (tgtImage.getFileModifiedDate().compareTo(srcImage.getFileModifiedDate()) != 0) {
            return false;
        }
        final long srcSize = srcImage.getPath().toFile().length();
        final long dstSize = dstFile.length();
        if (srcSize != dstSize) {
            return false;
        }
        return Arrays.equals(tgtImage.getHash(), srcImage.getHash());
    }

    private void moveFile(Path orgFile, String newName) {
        File newDstFile = new File(newName);
        logger.debug(String.format("%s to %s", orgFile, newDstFile.getAbsolutePath()));
        try {
            if (!opts.dryRun) {
                if (opts.moveFiles) {
                    Files.move(orgFile, newDstFile.toPath());
                } else {
                    Files.copy(orgFile, newDstFile.toPath());
                }
            }
            doneList.add(new ResultData(orgFile.toString(), newName));
            processingResult.incHandledFile();
        } catch (IOException e) {
            logger.error(String.format("Failed to move %s due to %s", orgFile, e));
            failedList.add(new ResultData(orgFile.toString(), newName));
            processingResult.incUnHandledFile();
        }
    }
}
