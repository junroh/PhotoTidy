package com.comp.concurrent;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

class ParallelBatchTest {

    @Test
    void testMapsAllInputs() throws Exception {
        List<Integer> inputs = IntStream.rangeClosed(1, 1000).boxed().toList();

        List<Integer> out = Parallel.map(inputs, 8, x -> x * x);

        Assertions.assertEquals(1000, out.size());
        long sum = out.stream().mapToLong(Integer::longValue).sum();
        long expected = inputs.stream().mapToLong(x -> (long) x * x).sum();
        Assertions.assertEquals(expected, sum, "every input must be mapped exactly once");
    }

    @Test
    void testEmptyInput() throws Exception {
        Assertions.assertTrue(Parallel.map(List.of(), 4, x -> x).isEmpty());
    }

    @Test
    void testNullResultsDropped() throws Exception {
        List<Integer> inputs = List.of(1, 2, 3, 4, 5, 6);
        List<Integer> out = Parallel.map(inputs, 3, x -> (x % 2 == 0) ? x : null);
        Assertions.assertEquals(3, out.size(), "odd inputs mapped to null are dropped");
    }

    @Test
    void testBoundedConcurrency() throws Exception {
        // With 2 workers, no more than 2 tasks may be in-flight simultaneously.
        int workers = 2;
        var inFlight = new java.util.concurrent.atomic.AtomicInteger();
        var maxSeen = new java.util.concurrent.atomic.AtomicInteger();
        List<Integer> inputs = IntStream.rangeClosed(1, 20).boxed().toList();

        Parallel.map(inputs, workers, x -> {
            int cur = inFlight.incrementAndGet();
            maxSeen.accumulateAndGet(cur, Math::max);
            Thread.sleep(5);
            inFlight.decrementAndGet();
            return x;
        });

        Assertions.assertTrue(maxSeen.get() <= workers,
                              "concurrency must stay bounded to " + workers + " (saw " + maxSeen.get() + ")");
    }

    @Test
    void testMapperExceptionPropagates() {
        List<Integer> inputs = List.of(1, 2, 3);
        Assertions.assertThrows(RuntimeException.class,
                () -> Parallel.map(inputs, 2, x -> { throw new IllegalStateException("boom"); }));
    }

    @Test
    void testStreamingMapsEveryProducedItem() throws Exception {
        List<Integer> out = Parallel.<Integer, Integer>mapStreaming(
                sink -> { for (int i = 1; i <= 500; i++) sink.accept(i); },
                8,
                x -> x * 2);

        Assertions.assertEquals(500, out.size());
        long sum = out.stream().mapToLong(Integer::longValue).sum();
        Assertions.assertEquals(500L * 501, sum, "sum of 2*(1..500)");
    }

    @Test
    void testStreamingEmptyProducer() throws Exception {
        Assertions.assertTrue(Parallel.<Integer, Integer>mapStreaming(sink -> { }, 4, x -> x).isEmpty());
    }

    @Test
    void testStreamingMapperExceptionPropagates() {
        Assertions.assertThrows(RuntimeException.class, () -> Parallel.<Integer, Integer>mapStreaming(
                sink -> { for (int i = 0; i < 100; i++) sink.accept(i); },
                4,
                x -> { throw new IllegalStateException("boom"); }));
    }
}
