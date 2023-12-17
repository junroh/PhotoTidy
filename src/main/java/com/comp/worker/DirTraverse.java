package com.comp.worker;

import com.comp.exif.ImageFileInfo;
import com.comp.exif.ImgFileData;
import com.comp.phototidy.ProcessingResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

@NotNullByDefault
public class DirTraverse extends ChainedWorker {

    private static final Logger logger = LogManager.getLogger(DirTraverse.class);
    private final Visitor fileVisitor;
    private final String basePath;

    public DirTraverse(final String basePath, final List<String> exts,
                       final ChainedWorker nextChainedWorker) {
        super("Directory Traverser", nextChainedWorker, 0, logger);
        this.fileVisitor = new Visitor(exts, nextChainedWorker, processingResult);
        this.basePath = basePath;
    }

    @Override
    protected void doRun() throws IOException {
        Files.walkFileTree(Paths.get(basePath), fileVisitor);
        nextWorker.requestToComplete();
    }

    private static class Visitor extends SimpleFileVisitor<Path> {
        private final ChainedWorker nextChainedWorker;
        private final ProcessingResult processingResult;
        private final PathMatcher matcher;

        public Visitor(final List<String> exts,
                       final ChainedWorker nextChainedWorker,
                       final ProcessingResult processingResult) {
            final StringBuilder sb = new StringBuilder("glob:*.{");
            for (final String ext : exts) {
                sb.append(ext);
                sb.append(",");
            }
            sb.deleteCharAt(sb.length() - 1);
            sb.append("}");
            this.matcher = FileSystems.getDefault().getPathMatcher(sb.toString());
            this.nextChainedWorker = nextChainedWorker;
            this.processingResult = processingResult;
        }

        @Override
        public FileVisitResult visitFile(final Path path, final BasicFileAttributes attrs) {
            if (attrs.isDirectory()) {
                return FileVisitResult.CONTINUE;
            }
            try {
                if (matcher.matches(path.getFileName())) {
                    final ImageFileInfo imgFile = new ImageFileInfo(path, attrs);
                    nextChainedWorker.putBlock(imgFile);
                    processingResult.incHandledFile();
                } else {
                    processingResult.incUnHandledFile();
                    processingResult.addUnHandledFile(path.toString());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return FileVisitResult.TERMINATE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(final Path file, final IOException exc) throws IOException {
            processingResult.incFailedFile();
            logger.error(String.format("Failed to access %s due to %s", file, exc));
            throw exc;
        }
    }
}
