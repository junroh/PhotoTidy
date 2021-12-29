package Worker;

import Exif.ImgFileData;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class DirTraverser extends Worker {

    private static final Logger logger =  Logger.getLogger(DirTraverser.class);
    private final Visitor v;
    private final String basePath;
    private final Worker nextWorker;

    public DirTraverser(String basePath, MetaParser metaParser, List<String> exts) {
        this.v = new Visitor(metaParser, exts);
        this.basePath = basePath;
        this.nextWorker = metaParser;
    }

    @Override
    public Boolean run(Thread curThread) throws Exception {
        boolean rlt = true;
        long start = System.currentTimeMillis();
        logger.debug("Traverser started");
        Path startingDir = Paths.get(basePath);
        try {
            v.curThread = curThread;
            Files.walkFileTree(startingDir, v);
        } catch (IOException e) {
            rlt = false;
            logger.error(String.format("IO Failed on reading files: %s", e));
        }
        System.out.printf("File list up completed. Total %d, Matched %d, Remaining %d. %.2fsec\n",
                v.getTot(), v.getMatched(), v.getTot() - v.getMatched(), (System.currentTimeMillis()-start)/1000.);
        nextWorker.RequestStop();
        return v.getResult() && rlt;
    }

    @Override
    public boolean put(Object data) {
        throw new IllegalStateException("Put is not supported");
    }

    public List<String> getUnHandleList() {
        return v.unHandleList;
    }

    private static class Visitor extends SimpleFileVisitor<Path> {
        private final Worker nextWorker;
        private final PathMatcher matcher;
        private final List<String> unHandleList;
        private int tot;
        private int matched;
        private boolean result;
        private Thread curThread;

        public Visitor(MetaParser nextWorker, List<String> exts) {
            StringBuilder sb = new StringBuilder("glob:*.{");
            for(String ext: exts) {
                sb.append(ext);
                sb.append(",");
            }
            sb.deleteCharAt(sb.length()-1);
            sb.append("}");
            this.matcher = FileSystems.getDefault().getPathMatcher(sb.toString());
            this.nextWorker = nextWorker;
            unHandleList = new LinkedList<>();
            tot = 0;
            matched = 0;
            result = true;
        }

        public boolean getResult() {
            return result;
        }

        public int getMatched() {
            return matched;
        }

        public int getTot() {
            return tot;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if(!attrs.isDirectory()) {
                tot++;
                Path filename = file.getFileName();
                if (matcher.matches(filename)) {
                    Date lastModifiedDate = new Date(attrs.lastModifiedTime().toMillis());
                    Date lastCreatedDate = new Date(attrs.creationTime().toMillis());
                    ImgFileData fileData = new ImgFileData(file, lastModifiedDate, lastCreatedDate);
                    try {
                        while (!nextWorker.put(fileData)) {
                            if (curThread.isInterrupted()) {
                                curThread.interrupt();
                                result = false;
                                return FileVisitResult.TERMINATE;
                            }
                            Thread.sleep(1);
                        }
                        matched++;
                    } catch (IllegalStateException e) {
                        logger.error("Next worker completed already");
                        result = false;
                        return FileVisitResult.TERMINATE;
                    } catch (InterruptedException e) {
                        curThread.interrupt();
                        result = false;
                        return FileVisitResult.TERMINATE;
                    }
                } else {
                    unHandleList.add(file.toString());
                }
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            result = false;
            logger.error(String.format("Failed to access %s due to %s", file.toString(), exc));
            return FileVisitResult.TERMINATE;
        }
    }
}
