package com.comp.worker;

import com.comp.phototidy.ProcessingResult;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class ChainedWorker {

    protected final ProcessingResult processingResult;
    protected final ChainedWorker nextWorker;

    private final Logger logger;
    private final AtomicBoolean finishIfNoData;
    private final AtomicBoolean completed;
    private final BlockingQueue<Object> q;
    private final String name;

    private Thread thread;

    protected ChainedWorker(final String name, final ChainedWorker nextWorker,
                            final int qSize, final Logger logger) {
        if(qSize<=0) {
            throw new IllegalArgumentException("qSize should be larger than 0");
        }
        this.name = name;
        this.logger = logger;
        this.nextWorker = nextWorker;
        this.processingResult = new ProcessingResult(name);
        this.finishIfNoData = new AtomicBoolean(false);
        this.completed = new AtomicBoolean(false);
        this.q = new ArrayBlockingQueue<>(qSize);
    }

    public ProcessingResult run() throws Exception {
        final long start = System.nanoTime();
        thread = Thread.currentThread();
        doRun();
        completed.set(true);
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
            try {
                final Object imgFile = fetchNextData();
                if (imgFile == null) {
                    break;
                }
                final long start = System.nanoTime();
                if (!processing(imgFile)) {
                    processingResult.incUnHandledFile();
                } else {
                    processingResult.incHandledFile();
                }
                processingTime += TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                if (nextWorker != null && !nextWorker.putBlock(imgFile)) {
                    logger.error("The next worker was completed already. Stop here!");
                    break;
                }
            } catch (final InterruptedException ignore) {
                // thread.interrupt() is not required here. Interrupt flag should be consumed here
                // If this is not an expected interruption, finish this worker
                if (!finishIfNoData.get()) {
                    break;
                }
            } catch (final Exception ex) {
                logger.error("Failed to process file due to", ex);
                break;
            }
        }
        processingResult.setProcessingTimeInMilli(processingTime);
    }

    protected boolean putBlock(final Object data) throws InterruptedException {
        if (completed.get()) {
            return false;
        }
        q.put(data);
        return true;
    }

    protected boolean processing(final Object imgFileData) throws Exception {
        return false;
    }

    private Object fetchNextData() throws InterruptedException {
        if (q.isEmpty() && finishIfNoData.get()) {
            return null;
        }
        return q.take();
    }

}
