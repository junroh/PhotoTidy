package com.comp.dedup;

import com.comp.domain.MediaItem;

import java.util.List;

/**
 * Outcome of deduplication: the files to keep and the near-duplicates to set aside.
 * Both lists together account for every input item exactly once.
 */
public record DeduplicationResult(List<MediaItem> keepers, List<MediaItem> duplicates) {

    public int total() {
        return keepers.size() + duplicates.size();
    }
}
