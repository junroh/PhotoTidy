package com.comp.phototidy;

import com.comp.worker.ChainedWorker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class PhotoTidyTest {

    @Test
    void testException() {
        final PhotoTidy.TidyUp tidyUp = new PhotoTidy.TidyUp(null);
        final ExecutorService exec = Executors.newFixedThreadPool(3);
        tidyUp.runAsyncWorker(exec, new DummyWorker());
        Assertions.assertEquals(tidyUp.runningJobs.size(), 1);
        tidyUp.runAsyncWorker(exec, new DummyWorker());
        Assertions.assertEquals(tidyUp.runningJobs.size(), 2);
        tidyUp.runAsyncWorker(exec, new ExceptionWorker());
        tidyUp.waitingComplete(exec);
        Assertions.assertEquals(tidyUp.runningJobs.size(), 0);
    }

    private static class ExceptionWorker extends ChainedWorker {
        protected ExceptionWorker() {
            super("exceptionWorker", null, 1, null);
        }

        @Override
        protected void doRun() throws Exception {
            throw new Exception("exceptionWorker");
        }
    }

    private static class DummyWorker extends ChainedWorker {
        protected DummyWorker() {
            super("DummyWorker", null, 1, null);
        }
    }
}
