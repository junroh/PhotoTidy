package com.comp.worker;

import com.comp.exif.ImgFileData;
import com.comp.phototidy.Options;
import com.comp.utility.PrintOut;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

public class FileMover extends ChainedWorker {

    private static final Logger logger = LogManager.getLogger(FileMover.class);
    private static final int qSize = 1024;
    private final Locator locator;
    private final BiFunction<Path, Path, Boolean> fileOp;
    private final Function<String, Boolean> createDirFn;

    public FileMover(final Options opts) {
        super("File mover", null, qSize, logger);
        this.locator = new Locator(opts);
        this.fileOp = getFileOperator(opts);
        this.createDirFn = getCreateDirFn(opts);
    }

    private Function<String, Boolean> getCreateDirFn(final Options opts) {
        if (!opts.dryRun) {
            return this::createDir;
        }
        return (fileName) -> {
            return true;
        };
    }

    private BiFunction<Path, Path, Boolean> getFileOperator(final Options opts) {
        if (!opts.dryRun) {
            if (opts.moveFiles) {
                return (a, b) -> {
                    try {
                        Files.move(a, b);
                    } catch (Exception e) {
                        return false;
                    }
                    return true;
                };
            }
            return (a, b) -> {
                try {
                    Files.copy(a, b);
                } catch (Exception e) {
                    return false;
                }
                return true;
            };
        }
        return (a, b) -> {
            return true;
        };
    }

    protected boolean processing(final Object imgFile) throws Exception {
        final Optional<String> destinationPathOpt = locator.getDestinationPath(imgFile);
        if (destinationPathOpt.isEmpty()) {
            return false;
        }
        final String destinationPath = destinationPathOpt.get();
        PrintOut.format("Move %s to %s                        \r", imgFile.getPath(), destinationPath);
        if (!createDirFn.apply(destinationPath)) {
            throw new IOException("Failed to create directory " + destinationPath);
        }
        // todo: parallelism can be added. But, it wouldn't be helpful much
        final Path orgFile = imgFile.getPath();
        final Path newDstFile = Paths.get(destinationPath);
        logger.debug(() -> String.format("%s to %s", orgFile, newDstFile.toAbsolutePath()));
        if (!fileOp.apply(orgFile, newDstFile)) {
            logger.error(String.format("Failed to operate %s", orgFile));
            return false;
        }
        return true;
    }

    private boolean createDir(final String dst) {
        final int index = dst.lastIndexOf(File.separator);
        if (index >= dst.length()) {
            logger.error("Invalid destination directory {}", dst);
            return false;
        }
        final String dstDir = dst.substring(0, index);
        final Path file = Paths.get(dstDir);
        if (Files.exists(file)) {
            if (Files.isDirectory(file)) {
                return true;
            }
            logger.error("Failed to create dir {} due to that the same name of file exists", dstDir);
            return false;
        }
        try {
            Files.createDirectory(file);
            return true;
        } catch (final Exception e) {
            logger.error("Failed to create dir {} due to {}", dstDir, e);
            return false;
        }
    }

    static class Locator {
        private final Options opts;
        private final Predicate<String> fileExistingFn;

        Locator(final Options opts) {
            this(opts, fileName -> new File(fileName).exists());
        }

        Locator(final Options opts, final Predicate<String> fileExistingFn) {
            this.opts = opts;
            this.fileExistingFn = fileExistingFn;
        }

        Optional<String> getDestinationPath(final ImgFileData imgFile) {
            final String originalFileName = imgFile.getPath().getFileName().toString();
            final int dotPos = originalFileName.lastIndexOf('.');
            if (dotPos < 0) {
                throw new RuntimeException("Invalid file name was found " + originalFileName);
            }
            final String fileExt = originalFileName.substring(dotPos + 1);

            final Optional<Date> imageDateOpt = imgFile.getExifImageDate();
            Date imageDate;
            if (imageDateOpt.isEmpty()) {
                logger.debug(() -> String.format("No exif in file found %s", originalFileName));
                switch (opts.noExifDir) {
                    case MODIFIED_DATE -> imageDate = imgFile.getFileModifiedDate();
                    case FIXED_DIR -> {
                        return Optional.of(opts.dstBaseDir + File.separator + opts.noExifDirName +
                                File.separator + originalFileName);
                    }
                    case SKIP -> {
                        return Optional.empty();
                    }
                    case STOP -> throw new RuntimeException("no-Exif file was found. STOP! " + originalFileName);
                    default -> throw new IllegalArgumentException("unknown configuration for no-exif file "
                            + opts.noExifDir);
                }
            } else {
                imageDate = imageDateOpt.get();
            }
            return handleDuplication(
                    opts.dstBaseDir + (File.separator + opts.dirNameFormatter.format(imageDate)),
                    opts.fileNameFormatter.format(imageDate),
                    fileExt);
        }

        // It is possible that there is the same name in the target
        // You have to decide whether you need to make a progress or not
        private Optional<String> handleDuplication(final String targetDirectory, final String targetName,
                                                   final String ext) {
            switch (opts.duplicateOpt) {
                case INCREASE -> {
                    return Optional.of(getFileNameWithIncreasedIndex(targetDirectory, targetName, ext));
                }
                case SKIP -> {
                    return Optional.empty();
                }
                case STOP -> throw new RuntimeException("Stop processing due to a duplicated file");
            }
            throw new IllegalArgumentException("Unknown configuration for a duplication " + opts.duplicateOpt);
        }

        private String getFileNameWithIncreasedIndex(final String targetDirectory, final String targetName,
                                                     final String ext) {
            final String defaultFileName = targetDirectory + File.separator + targetName;
            String newFileName = String.format("%s.%s", defaultFileName, ext);
            int index = 0;
            do {
                if (!fileExistingFn.test(newFileName)) {
                    return newFileName;
                }
                newFileName = String.format("%s_%03d.%s", defaultFileName, ++index, ext);
            } while (true);
        }
    }
}
