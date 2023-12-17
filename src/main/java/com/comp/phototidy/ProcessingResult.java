package com.comp.phototidy;

import java.util.LinkedList;
import java.util.List;

// This is not thread safe!
public class ProcessingResult {
    private final String name;
    private final List<String> unHandledFiles;
    private final List<String> handledFiles;
    private int handledFileCount;
    private int unHandledFileCount;
    private int failedFileCount;
    private long runningTimeInMilli;   // include a waiting time
    private long processingTimeInMilli; //without a waiting time

    public ProcessingResult(final String name) {
        this.name = name;
        unHandledFiles = new LinkedList<>();
        handledFiles = new LinkedList<>();
    }

    public void addHandledFile(String file) {
        handledFiles.add(file);
    }

    public void incHandledFile() {
        handledFileCount++;
    }

    public void addUnHandledFile(String file) {
        unHandledFiles.add(file);
    }

    public void incUnHandledFile() {
        unHandledFileCount++;
    }

    public void incFailedFile() {
        failedFileCount++;
    }

    public void setRunningTimeInMilli(long timeInMilli) {
        runningTimeInMilli = timeInMilli;
    }

    public void setProcessingTimeInMilli(long timeInMilli) {
        processingTimeInMilli = timeInMilli;
    }

    public long getRunningTime() {
        return runningTimeInMilli;
    }

    public int getHandledFileCount() {
        return handledFileCount;
    }

    @Override
    public String toString() {
        return name +
                " processed " + handledFileCount +
                " unProcessed " + unHandledFileCount +
                " failed " + failedFileCount +
                " Processing Time " + processingTimeInMilli +
                " Total running Time " + runningTimeInMilli;
    }
}
