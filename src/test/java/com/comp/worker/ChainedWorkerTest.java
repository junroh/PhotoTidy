package com.comp.worker;

import com.comp.phototidy.ProcessingResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class ChainedWorkerTest {

    @Test
    void testRequestStopWithSingle() throws ExecutionException, InterruptedException, TimeoutException {
        final ExecutorService exec = Executors.newSingleThreadExecutor();
        final MockedWorker mockedWorker = new MockedWorker("dummy1", null);
        CompletableFuture<ProcessingResult> f = run(mockedWorker, exec);
        Thread.sleep(500);
        mockedWorker.requestToComplete();
        final ProcessingResult result = f.get(2, TimeUnit.SECONDS);
        Assertions.assertEquals(result.getHandledFileCount(), 0);
        Assertions.assertTrue(result.getRunningTime() > 0);
        exec.shutdownNow();
    }

    @Test
    void testRequestStopWithMultiples() throws InterruptedException {
        final int numWorkers = 20;
        final ExecutorService exec = Executors.newFixedThreadPool(numWorkers);
        final List<CompletableFuture<ProcessingResult>> l = new LinkedList<>();
        MockedWorker nextWorker = null;
        for (int i = 0; i < numWorkers; i++) {
            nextWorker = new MockedWorker("dummy" + (numWorkers - i), nextWorker);
            l.add(run(nextWorker , exec));
        }
        nextWorker.requestToComplete();
        CompletableFuture<Void> f = CompletableFuture.allOf(l.toArray(new CompletableFuture<?>[0]));
        Assertions.assertDoesNotThrow(() -> f.get(3, TimeUnit.SECONDS));
        exec.shutdownNow();
    }

    private CompletableFuture<ProcessingResult> run(ChainedWorker worker, ExecutorService exec) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return worker.run();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, exec);
    }

    private static class MockedWorker extends ChainedWorker {
        protected MockedWorker(final String name, ChainedWorker nextWorker) {
            super(name, nextWorker, 1, null);
        }
    }
}
