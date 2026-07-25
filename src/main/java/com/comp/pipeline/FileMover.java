package com.comp.pipeline;

import com.comp.app.Options;
import com.comp.dedup.DeduplicationResult;
import com.comp.domain.MediaItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.IntConsumer;

/**
 * Files media into the dated destination layout. Keepers go under the destination root; duplicates
 * go under a {@code duplicates/} subtree so they can be reviewed separately.
 * <p>
 * Moving is done sequentially (single-threaded): filesystem moves on one disk are IO-bound rather
 * than CPU-bound, and serial execution keeps the {@code INCREASE} unique-naming logic race-free.
 */
public class FileMover {

    private static final Logger logger = LogManager.getLogger(FileMover.class);

    private final Options opts;
    private final Locator locator;

    public FileMover(Options opts) {
        this(opts, path -> path.toFile().exists());
    }

    protected FileMover(Options opts, Predicate<Path> fileChecker) {
        this.opts = opts;
        this.locator = new Locator(opts, fileChecker);
    }

    /**
     * Files every keeper and duplicate.
     *
     * @param onProgress invoked with a monotonically increasing count after each item is handled
     * @return number of files actually filed (moved/copied, or counted in dry-run)
     */
    public int move(final DeduplicationResult result, final IntConsumer onProgress) {
        int handled = 0;
        int seen = 0;
        handled += fileAll(result.keepers(), false, onProgress, seen);
        seen += result.keepers().size();
        handled += fileAll(result.duplicates(), true, onProgress, seen);
        return handled;
    }

    private int fileAll(final List<MediaItem> items, final boolean duplicate,
                        final IntConsumer onProgress, final int alreadySeen) {
        int handled = 0;
        int seen = alreadySeen;
        for (final MediaItem item : items) {
            handled += fileOne(item, duplicate);
            onProgress.accept(++seen);
        }
        return handled;
    }

    private int fileOne(final MediaItem item, final boolean duplicate) {
        try {
            final Optional<String> destOpt = locator.resolveDest(item, duplicate);
            if (destOpt.isEmpty()) {
                return 0;
            }
            final Path src = item.getPath();
            final Path dst = Paths.get(destOpt.get());

            if (opts.dryRun) {
                return 1;
            }

            Files.createDirectories(dst.getParent());
            final StandardCopyOption[] copyOpts = (opts.duplicateOpt == Options.DuplicateOpt.OVERWRITE)
                    ? new StandardCopyOption[]{StandardCopyOption.REPLACE_EXISTING}
                    : new StandardCopyOption[]{};

            if (opts.moveFiles) {
                Files.move(src, dst, copyOpts);
            } else {
                Files.copy(src, dst, copyOpts);
            }
            return 1;
        } catch (Exception e) {
            logger.error("Failed to file {}: {}", item, e.toString());
            return 0;
        }
    }

    static class Locator {
        private final Options opts;
        private final Predicate<Path> fileChecker;

        Locator(Options opts, Predicate<Path> fileChecker) {
            this.opts = opts;
            this.fileChecker = fileChecker;
        }

        Optional<String> resolveDest(MediaItem item, boolean duplicate) {
            Date date = item.getEffectiveDate();

            if (!item.hasExifDate()) {
                if (opts.noExifDir == Options.NoExifOpt.STOP) {
                    throw new RuntimeException("Stop: No EXIF " + item.getPath());
                }
                if (opts.noExifDir == Options.NoExifOpt.SKIP) {
                    return Optional.empty();
                }
            }

            String baseDirStr = getBaseDir(date, item, duplicate);
            String nameStr = getBaseName(date, item);
            String extStr = getExt(item.getPath().getFileName().toString());

            // We normalize the input strings to ensure "yyyy/MM" becomes "yyyy\MM" on Windows
            Path target = Paths.get(normalize(baseDirStr), normalize(nameStr) + "." + extStr);

            if (fileChecker.test(target)) {
                return switch (opts.duplicateOpt) {
                    case SKIP -> Optional.empty();
                    case STOP -> throw new RuntimeException("Stop: Duplicate " + target);
                    case OVERWRITE -> Optional.of(target.toString());
                    case INCREASE -> Optional.of(findUnique(target));
                };
            }

            return Optional.of(target.toString());
        }

        private String findUnique(Path originalTarget) {
            Path dir = originalTarget.getParent();
            String fName = originalTarget.getFileName().toString();
            String name = fName.substring(0, fName.lastIndexOf('.'));
            String ext = fName.substring(fName.lastIndexOf('.') + 1);

            int i = 0;
            Path p;
            do {
                p = dir.resolve(String.format("%s_%03d.%s", name, ++i, ext));
            } while (fileChecker.test(p));
            return p.toString();
        }

        private String getBaseDir(Date date, MediaItem item, boolean duplicate) {
            // Duplicates are filed under their own subtree so they can be reviewed/deleted separately.
            String base = duplicate
                    ? Paths.get(opts.dstBaseDir, opts.duplicatesDirName).toString()
                    : opts.dstBaseDir;

            if (!item.hasExifDate() && opts.noExifDir == Options.NoExifOpt.FIXED_DIR) {
                return base + File.separator + opts.noExifDirName;
            }
            return Paths.get(base, opts.dirNameFormatter.format(date)).toString();
        }

        private String getBaseName(Date date, MediaItem item) {
            if (!item.hasExifDate() && opts.noExifDir == Options.NoExifOpt.FIXED_DIR) {
                String n = item.getPath().getFileName().toString();
                int idx = n.lastIndexOf('.');
                return (idx > 0) ? n.substring(0, idx) : n;
            }
            return opts.fileNameFormatter.format(date);
        }

        private String getExt(String name) {
            int i = name.lastIndexOf('.');
            return (i > 0) ? name.substring(i + 1) : "";
        }

        // Ensures that if config says "2020/05" but we are on Windows, it becomes "2020\05"
        private String normalize(String input) {
            if (input == null) {
                return "";
            }
            if (File.separatorChar == '/') {
                return input.replace('\\', '/');
            } else {
                return input.replace('/', '\\');
            }
        }
    }
}
