package com.comp.dedup;

import com.comp.domain.MediaItem;

import java.util.List;

/**
 * Partitions a batch of analyzed media into keepers and near-duplicates.
 * Implementations receive the full batch (deduplication is inherently a barrier — every item
 * must be seen before any can be classified) and return a complete partition of it.
 */
public interface Deduplicator {

    DeduplicationResult deduplicate(List<MediaItem> items);
}
