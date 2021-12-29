
import Worker.*;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.*;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class PhotoTidy {
    static {
        ConfigurationBuilder<BuiltConfiguration> builder
                = ConfigurationBuilderFactory.newConfigurationBuilder();

        AppenderComponentBuilder console
                = builder.newAppender("stdout", "Console");
        LayoutComponentBuilder standard
                = builder.newLayout("PatternLayout");
        standard.addAttribute("pattern", "%d [%t/%C] %-5level: %msg%n%throwable");
        console.add(standard);
        builder.add(console);

        RootLoggerComponentBuilder rootLogger
                = builder.newRootLogger(Level.INFO);
        rootLogger.add(builder.newAppenderRef("stdout"));
        builder.add(rootLogger);

        Configurator.initialize(builder.build());
    }

    public static void main(String[] args) {
        try {
            // todo: what if target and source are same. As of now, it is not a consideration.
            Options opts = new Options();
            opts.srcDir = "D:\\New Folder";
            opts.dstBaseDir = "D:\\Photo";
            opts.dryRun = true;
            opts.moveFiles = true;

            opts.showDupLists = false;
            opts.showUnHandles = false;
            opts.showMovedList = false;

            TidyUp tidyUp = new TidyUp(opts);
            tidyUp.doMove();
            tidyUp.showDetail();
        } catch (InterruptedException | ExecutionException e) {
            System.err.printf("Failed to execute due to %s", e);
        }
    }

    public static class TidyUp {
        private static final int waitingTimeoutInSec = 5;
        private final List<String> exts;
        private final Options opts;
        private FileMover fileMover;
        private MetaParser metaParser;
        private DirTraverser dirTraverser;

        public TidyUp(Options opts) {
            this.opts = opts;
            exts = new ArrayList<>();
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

        public void showDetail() {
            if(opts.showUnHandles) {
                List<String> unhandles = dirTraverser.getUnHandleList();
                if(unhandles.size()>0) {
                    System.out.println("UnHandled file list...");
                    for (String f : unhandles) {
                        System.out.printf("%s\n", f);
                    }
                }
            }
            if(opts.showMovedList) {
                List<ResultData> done = fileMover.getDoneList();
                if (done.size() > 0) {
                    System.out.println("Moved file list...");
                    for (ResultData dup : done) {
                        System.out.printf("%s -> %s\n", dup.src, dup.dst);
                    }
                }
            }
            if(opts.showDupLists) {
                List<ResultData> dups = fileMover.getDupList();
                if (dups.size() > 0) {
                    System.out.println("Duplication list...");
                    for (ResultData dup : dups) {
                        System.out.printf("%s -> %s\n", dup.src, dup.dst);
                    }
                }
            }
        }

        public void doMove() throws InterruptedException, ExecutionException {
            long start = System.currentTimeMillis();
            System.out.printf("%s (Source) -> %s (Destination)\n", opts.srcDir, opts.dstBaseDir);
            ExecutorService exec = Executors.newFixedThreadPool(3);
            fileMover = new FileMover(opts);
            metaParser = new MetaParser(fileMover);
            dirTraverser = new DirTraverser(opts.srcDir, metaParser, exts);
            Future<Boolean> f1 = exec.submit(fileMover);
            Future<Boolean> f2 = exec.submit(metaParser);
            Future<Boolean> f3 = exec.submit(dirTraverser);
            f3.get();
            f2.get();
            f1.get();
            exec.shutdown();
            exec.awaitTermination(waitingTimeoutInSec, TimeUnit.SECONDS);
            System.out.printf("Process completed. %.2fsec\n", (System.currentTimeMillis() - start) / 1000.);
        }
    }
}
