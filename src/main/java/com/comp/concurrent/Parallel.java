package com.comp.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bounded parallel map over a batch.
 * <p>
 * A fixed pool of worker threads claims input indices from a shared cursor, so concurrency is
 * capped at {@code threads} regardless of batch size. This suits the mixed I/O + CPU work of image
 * hashing: raise {@code threads} above the core count to hide read latency on SSD/cloud storage,
 * keep it near the core count when decoding is the bottleneck. Workers are joined before returning,
 * so no threads leak.
 * <p>
 * {@link #map} takes a fully-materialized list; {@link #mapStreaming} instead pulls items from a
 * producer as they are discovered, overlapping production (e.g. a directory walk) with mapping.
 * <p>
 * Concurrency lives only here; callers just invoke these methods. Per-item failures are the
 * mapper's responsibility to absorb — an exception thrown here is fatal: remaining items are
 * skipped and the failure is rethrown.
 */
public final class Parallel {

    private Parallel() { }

    @FunctionalInterface
    public interface ThrowingMapper<I, O> {
        O apply(I input) throws Exception;
    }

    /** Receives produced items; may block for backpressure. */
    @FunctionalInterface
    public interface Sink<I> {
        void accept(I item) throws InterruptedException;
    }

    /** Feeds items to a sink until exhausted. */
    @FunctionalInterface
    public interface Producer<I> {
        void produce(Sink<I> sink) throws Exception;
    }

    /** Applies {@code mapper} to every input using at most {@code threads} workers, dropping nulls. */
    @SuppressWarnings("unchecked")
    public static <I, O> List<O> map(final List<I> inputs, final int threads,
                                      final ThrowingMapper<I, O> mapper) throws InterruptedException {
        final int size = inputs.size();
        if (size == 0) {
            return List.of();
        }
        final int workers = Math.max(1, Math.min(threads, size));
        final Object[] out = new Object[size];
        final AtomicInteger cursor = new AtomicInteger(0);
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        final ExecutorService exec = Executors.newFixedThreadPool(workers);
        try {
            final List<Future<?>> futures = new ArrayList<>(workers);
            for (int w = 0; w < workers; w++) {
                futures.add(exec.submit(() -> {
                    int i;
                    while (failure.get() == null && (i = cursor.getAndIncrement()) < size) {
                        try {
                            out[i] = mapper.apply(inputs.get(i));
                        } catch (Exception e) {
                            failure.compareAndSet(null, e);
                            return;
                        }
                    }
                }));
            }
            for (final Future<?> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    failure.compareAndSet(null, e.getCause());
                }
            }
        } finally {
            exec.shutdownNow();
        }

        if (failure.get() != null) {
            throw new RuntimeException("Parallel batch failed", failure.get());
        }

        final List<O> results = new ArrayList<>(size);
        for (final Object o : out) {
            if (o != null) {
                results.add((O) o);
            }
        }
        return results;
    }

    /**
     * Maps items pulled from {@code producer} as they are produced, so production overlaps mapping.
     * The producer runs on its own thread and feeds a bounded queue (backpressure caps memory);
     * {@code threads} workers drain it concurrently. Results are returned in completion order.
     */
    public static <I, O> List<O> mapStreaming(final Producer<I> producer, final int threads,
                                              final ThrowingMapper<I, O> mapper) throws InterruptedException {
        final int workers = Math.max(1, threads);
        final BlockingQueue<I> queue = new ArrayBlockingQueue<>(Math.max(64, workers * 4));
        final ConcurrentLinkedQueue<O> results = new ConcurrentLinkedQueue<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AtomicBoolean producing = new AtomicBoolean(true);

        final ExecutorService exec = Executors.newFixedThreadPool(workers + 1);
        try {
            exec.submit(() -> {
                try {
                    producer.produce(item -> {
                        if (failure.get() != null) {
                            throw new InterruptedException("aborted");
                        }
                        queue.put(item);
                    });
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                } finally {
                    producing.set(false);
                }
            });

            final List<Future<?>> consumers = new ArrayList<>(workers);
            for (int w = 0; w < workers; w++) {
                consumers.add(exec.submit(() -> {
                    try {
                        while (failure.get() == null) {
                            final I item = queue.poll(50, TimeUnit.MILLISECONDS);
                            if (item == null) {
                                if (!producing.get() && queue.isEmpty()) {
                                    break;
                                }
                                continue;
                            }
                            try {
                                O out = mapper.apply(item);
                                if (out != null) {
                                    results.add(out);
                                }
                            } catch (Exception e) {
                                failure.compareAndSet(null, e);
                                return;
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }
            for (final Future<?> f : consumers) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    failure.compareAndSet(null, e.getCause());
                }
            }
        } finally {
            exec.shutdownNow();
        }

        if (failure.get() != null) {
            throw new RuntimeException("Streaming batch failed", failure.get());
        }
        return new ArrayList<>(results);
    }
}
