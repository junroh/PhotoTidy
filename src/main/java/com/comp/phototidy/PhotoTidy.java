package com.comp.phototidy;

import com.comp.utility.PrintOut;
import com.comp.worker.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.*;

public class PhotoTidy {
    private static final Logger logger = LogManager.getLogger(PhotoTidy.class);

    public static void main(@NotNull final String[] args) {
        try {
            // todo: what if target and source are same. As of now, it is not a consideration.
            final String propertyFile;
            if (args.length == 0) {
                propertyFile = "./src/main/resources/config.properties";
            } else {
                propertyFile = args[0];
            }
            final Options opts = Options.newOptionsFromFile(propertyFile);
            final TidyUp tidyUp = new TidyUp(opts);
            tidyUp.doMove();
            tidyUp.showDetail();
        } catch (InterruptedException e) {
            logger.warn("Service was interrupted", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Failed to execute due to ", e);
        }
    }

    public static class TidyUp {
        protected final Set<CompletableFuture<ProcessingResult>> runningJobs;
        private final Options opts;
        private final ExecutorService exec;

        public TidyUp(@NotNull final Options opts) {
            this.opts = opts;
            runningJobs = ConcurrentHashMap.newKeySet();
            exec = Executors.newFixedThreadPool(4);

            Runtime.getRuntime().addShutdownHook(cleanUp());
        }

        public void showDetail() {
        }

        public void doMove() throws InterruptedException, ExecutionException {
            final long start = System.currentTimeMillis();
            PrintOut.format("%s (Source) -> %s (Destination)\n", opts.srcDir, opts.dstBaseDir);
            final FileMover fileMover = new FileMover(opts);
            final ImgCompare imgCompare = new ImgCompare(opts, fileMover);
            final MetaParser metaParser = new MetaParser(opts, imgCompare);
            final DirTraverse dirTraverse = new DirTraverse(opts.srcDir, opts.getSupportingExts(), metaParser);
            runAsyncWorker(exec, fileMover);
            runAsyncWorker(exec, imgCompare);
            runAsyncWorker(exec, metaParser);
            runAsyncWorker(exec, dirTraverse);
            waitingComplete(exec);
            PrintOut.format("Process completed. %.2fsec\n", (System.currentTimeMillis() - start) / 1000.);
        }

        protected void waitingComplete(@NotNull final ExecutorService exec) {
            if (!runningJobs.isEmpty()) {
                try {
                    CompletableFuture.allOf(runningJobs.toArray(new CompletableFuture<?>[0]))
                            .get();
                } catch (ExecutionException | InterruptedException e) {
                    PrintOut.format("Exception happened during tidying photos up with %s\n", e);
                }
            }
            exec.shutdownNow();
        }

        protected void runAsyncWorker(@NotNull final ExecutorService exec, @NotNull final ChainedWorker chainedWorker) {
            final CompletableFuture<ProcessingResult> r = CompletableFuture.supplyAsync(
                    () -> {
                        try {
                            return chainedWorker.run();
                        } catch (Exception e) {
                            throw new CompletionException(e);
                        }
                    }, exec);
            runningJobs.add(r);
            r.whenComplete((ret, e) -> {
                runningJobs.remove(r);
                if (e != null) {
                    PrintOut.format("%s completed with an error %s\n", chainedWorker.name(), e);
                    exec.shutdownNow();
                } else {
                    PrintOut.format("%s completed\n%s\n", chainedWorker.name(), ret);
                }
            });
        }

        @NotNull
        private Thread cleanUp() {
            return new Thread(() -> {
                PrintOut.format("Stop request came...\n");
                exec.shutdownNow();
                try {
                    if (exec.awaitTermination(60, TimeUnit.SECONDS)) {
                        PrintOut.format("stop request completed successfully.\n");
                        return;
                    }
                } catch (InterruptedException ignore) {
                    /* Do nothing */
                }
                PrintOut.format("stop request was failed.\n");
            });
        }
    }
}
