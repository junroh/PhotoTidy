package com.comp.phototidy;

import com.comp.worker.DirTraverse;
import com.comp.worker.FileMover;
import com.comp.worker.MetaParser;
import com.comp.utility.PrintOut;
import com.comp.worker.ChainedWorker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PhotoTidy {
    private static final Logger logger = LogManager.getLogger(PhotoTidy.class);

    public static void main(String[] args) {
        try {
            // todo: what if target and source are same. As of now, it is not a consideration.
            String propertyFile = args[0];
            if (propertyFile == null) {
                propertyFile = "./config.properties";
            }
            final Options opts = Options.newOptionsFromFile(propertyFile);
            final TidyUp tidyUp = new TidyUp(opts);
            tidyUp.doMove();
            tidyUp.showDetail();
        } catch (InterruptedException e) {
            logger.warn("Service was interrupted", e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            logger.error("Failed to execute due to", e);
        } catch (Exception e) {
            logger.error("Unhandled exception happened", e);
        }
    }

    public static class TidyUp {
        private final Options opts;
        protected final Set<CompletableFuture<ProcessingResult>> runningJobs;

        public TidyUp(Options opts) {
            this.opts = opts;
            runningJobs = ConcurrentHashMap.newKeySet();
        }

        public void showDetail() {
            PrintOut.format("TTTT");
            /*
            if (opts.showUnHandles) {
                List<String> unhandles = dirTraverser.getUnHandleList();
                if (unhandles.size() > 0) {
                    System.out.println("UnHandled file list...");
                    for (String f : unhandles) {
                        System.out.printf("%s\n", f);
                    }
                }
            }
            if (opts.showMovedList) {
                List<ResultData> done = fileMover.getDoneList();
                if (done.size() > 0) {
                    System.out.println("Moved file list...");
                    for (ResultData dup : done) {
                        System.out.printf("%s -> %s\n", dup.src, dup.dst);
                    }
                }
            }
            if (opts.showDupLists) {
                List<ResultData> dups = fileMover.getDupList();
                if (dups.size() > 0) {
                    System.out.println("Duplication list...");
                    for (ResultData dup : dups) {
                        System.out.printf("%s -> %s\n", dup.src, dup.dst);
                    }
                }
            }

             */
        }

        public void doMove() throws InterruptedException, ExecutionException {
            final long start = System.currentTimeMillis();
            PrintOut.format("%s (Source) -> %s (Destination)\n", opts.srcDir, opts.dstBaseDir);
            final FileMover fileMover = new FileMover(opts);
            final MetaParser metaParser = new MetaParser(opts, fileMover);
            final DirTraverse dirTraverse = new DirTraverse(opts.srcDir,
                                                            opts.getSupportingExts(),
                                                            metaParser);
            final ExecutorService exec = Executors.newFixedThreadPool(3);
            runAsyncWorker(exec, fileMover);
            runAsyncWorker(exec, metaParser);
            runAsyncWorker(exec, dirTraverse);
            waitingComplete(exec);
            PrintOut.format("Process completed. %.2fsec\n", (System.currentTimeMillis() - start) / 1000.);
        }

        protected void waitingComplete(final ExecutorService exec) {
            if (runningJobs.size() != 0) {
                try {
                    CompletableFuture.allOf(runningJobs.toArray(new CompletableFuture<?>[0]))
                                     .get();
                } catch (ExecutionException | InterruptedException e) {
                    PrintOut.format("Exception happened during tidying photos up with %s\n", e);
                }
            }
            exec.shutdownNow();
        }

        protected void runAsyncWorker(final ExecutorService exec, final ChainedWorker chainedWorker) {
            final CompletableFuture<ProcessingResult> r =
                    CompletableFuture.supplyAsync(() -> {
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
                    PrintOut.format("%s completed %s\n", chainedWorker.name(), ret);
                }
            });
        }
    }
}
