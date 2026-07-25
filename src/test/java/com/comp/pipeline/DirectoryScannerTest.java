package com.comp.pipeline;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.comp.domain.ScannedFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

class DirTraverseTest {

    private FileSystem fs;
    private Path rootDir;

    @BeforeEach
    void setUp() throws IOException {
        fs = Jimfs.newFileSystem(Configuration.unix());
        rootDir = fs.getPath("/virtual-root");
        Files.createDirectory(rootDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        fs.close();
    }

    private void createFile(Path dir, String name) throws IOException {
        Files.createFile(dir.resolve(name));
    }

    private List<String> names(List<ScannedFile> files) {
        return files.stream().map(f -> f.path().getFileName().toString()).toList();
    }

    @Test
    void testTraverseInMemory() throws Exception {
        createFile(rootDir, "photo.jpg");
        createFile(rootDir, "ignored.txt");
        Path sub = Files.createDirectory(rootDir.resolve("vacation"));
        createFile(sub, "video.mp4");

        List<ScannedFile> found = new DirectoryScanner(rootDir, Set.of("jpg", "mp4")).scan();

        Assertions.assertEquals(2, found.size());
        List<String> names = names(found);
        Assertions.assertTrue(names.contains("photo.jpg"));
        Assertions.assertTrue(names.contains("video.mp4"));
        Assertions.assertFalse(names.contains("ignored.txt"));
    }

    @Test
    void testExtensionsAreCaseInsensitive() throws Exception {
        createFile(rootDir, "old_camera.JPG");
        createFile(rootDir, "archive.PnG");
        createFile(rootDir, "standard.jpg");

        List<ScannedFile> found = new DirectoryScanner(rootDir, Set.of("jpg", "png")).scan();

        Assertions.assertEquals(3, found.size(), "Should find all 3 files regardless of case");
    }

    @Test
    void testSymlinksAreNotFollowed() throws Exception {
        Path realFolder = Files.createDirectory(rootDir.resolve("real_folder"));
        createFile(realFolder, "photo.jpg");
        Files.createSymbolicLink(rootDir.resolve("link_folder"), realFolder);

        List<ScannedFile> found = new DirectoryScanner(rootDir, Set.of("jpg")).scan();

        Assertions.assertEquals(1, found.size(),
                                "Should NOT follow symlinks to avoid infinite loops and duplicates");
    }

    @Test
    void testFilesWithNoExtension() throws Exception {
        createFile(rootDir, "README");
        createFile(rootDir, "script.sh");
        createFile(rootDir, ".gitignore");

        List<ScannedFile> found = new DirectoryScanner(rootDir, Set.of("jpg")).scan();

        Assertions.assertTrue(found.isEmpty(), "Should ignore files with no extension or unsupported extensions");
    }

    @Test
    void testMissingSourceDirThrows() {
        Path missing = fs.getPath("/does-not-exist");
        DirectoryScanner traverse = new DirectoryScanner(missing, Set.of("jpg"));
        Assertions.assertThrows(IOException.class, traverse::scan);
    }
}
