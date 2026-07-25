package com.comp.app;

import java.util.concurrent.atomic.AtomicInteger;

public class Stats {
    public final AtomicInteger scannedFiles = new AtomicInteger(0);
    public final AtomicInteger cacheHits = new AtomicInteger(0);
    public final AtomicInteger duplicatesFound = new AtomicInteger(0);
    public final AtomicInteger movedFiles = new AtomicInteger(0);

    private final long startTime = System.currentTimeMillis();

    public String getDuration() {
        return String.format("%.2f sec", (System.currentTimeMillis() - startTime) / 1000.0);
    }
}