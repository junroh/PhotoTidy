package com.comp.worker;

import com.comp.exif.ImgFileData;
import com.comp.phototidy.ProcessingResult;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class ChainedWorker {

    private final Logger logger;
    protected final ProcessingResult processingResult;
    protected final ChainedWorker nextWorker;

    private final AtomicBoolean finishIfNoData;
    private final BlockingQueue<ImgFileData> q;
    private final String name;

    private Thread thread;

    protected ChainedWorker(final String name, final ChainedWorker nextWorker,
                            int qSize, Logger logger) {
        this.name = name;
        this.logger = logger;
        this.nextWorker = nextWorker;
        this.processingResult = new ProcessingResult(name);
        this.finishIfNoData = new AtomicBoolean(false);
        if (qSize == 0) {
            this.q = null;
        } else {
            this.q = new ArrayBlockingQueue<>(qSize);
        }
    }

    public ProcessingResult run() throws Exception {
        final long start = System.nanoTime();
        thread = Thread.currentThread();
        doRun();
        if (nextWorker != null) {
            nextWorker.requestToComplete();
        }
        processingResult.setRunningTimeInMilli(
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        return processingResult;
    }

    public void requestToComplete() {
        if (!finishIfNoData.compareAndSet(false, true)) {
            return;
        }
        if (thread != null) {
            thread.interrupt();
        }
    }

    public String name() {
        return name;
    }


    protected void doRun() throws Exception {
        long processingTime = 0;
        while (true) {
            ImgFileData imgFile;
            try {
                imgFile = fetchNextData();
                if (imgFile == null) {
                    break;
                }
                long start = System.currentTimeMillis();
                if (!processing(imgFile)) {
                    processingResult.incUnHandledFile();
                    processingResult.addUnHandledFile(imgFile.getPath().toString());
                } else {
                    processingResult.incHandledFile();
                }
                processingTime += TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                if (nextWorker != null) {
                    nextWorker.putBlock(imgFile);
                }
            } catch (InterruptedException ignore) {
                // thread.interrupt() is not required here. Interrupt flag should be consumed here
                // If this is not an expected interruption, finish this worker
                if(!finishIfNoData.get()) {
                    break;
                }
            } catch (Exception ex) {
                logger.error("Failed to process file due to " + ex);
                processingResult.incFailedFile();
            }
        }
        processingResult.setProcessingTimeInMilli(processingTime);
    }

    protected void putBlock(ImgFileData data) throws InterruptedException {
        if (q == null) {
            return;
        }
        q.put(data);
    }

    protected boolean processing(ImgFileData imgFileData) throws Exception {
        return false;
    }

    private ImgFileData fetchNextData() throws InterruptedException {
        if (q == null) {
            return null;
        }
        if (q.isEmpty() && finishIfNoData.get()) {
            return null;
        }
        return q.take();
    }

}
