package com.comp.pipeline;

import com.comp.concurrent.Parallel;
import com.comp.domain.ScannedFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Walks a source directory and emits every supported media file as a {@link ScannedFile}.
 * Symlinks are not followed (avoids cycles and double-counting). This stage touches only cheap
 * filesystem metadata; content is read later by the hashing stage.
 */
public class DirectoryScanner {

    private static final Logger logger = LogManager.getLogger(DirectoryScanner.class);

    private final Path sourcePath;
    private final Set<String> supportingExts;

    public DirectoryScanner(String sourcePath, Set<String> supportingExts) {
        this(Paths.get(sourcePath), supportingExts);
    }

    public DirectoryScanner(Path sourcePath, Set<String> supportingExts) {
        this.sourcePath = sourcePath;
        this.supportingExts = supportingExts;
    }

    /** Emits each supported file to the sink as it is discovered (used to overlap with hashing). */
    public void traverse(Parallel.Sink<ScannedFile> sink) throws IOException {
        if (!Files.exists(sourcePath)) {
            throw new IOException("Source directory does not exist: " + sourcePath);
        }
        Files.walkFileTree(sourcePath, new SimpleFileVisitor<>() {
            @Override
            public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) {
                if (isSupported(file)) {
                    try {
                        sink.accept(ScannedFile.from(file, attrs));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return FileVisitResult.TERMINATE;
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public @NotNull FileVisitResult visitFileFailed(@NotNull Path file, @NotNull IOException exc) {
                logger.warn("Failed to access file: {}", file);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** Collects all supported files into a list (convenience over {@link #traverse}). */
    public List<ScannedFile> scan() throws IOException {
        List<ScannedFile> found = new ArrayList<>();
        traverse(found::add);
        return found;
    }

    private boolean isSupported(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            String ext = name.substring(dot + 1).toLowerCase();
            return supportingExts.contains(ext);
        }
        return false;
    }
}
