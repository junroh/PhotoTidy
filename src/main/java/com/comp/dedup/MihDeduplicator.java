package com.comp.dedup;

import com.comp.domain.MediaItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Near-duplicate detector using Multi-Index Hashing (MIH) with union-find clustering, plus an exact
 * fast-path for byte-identical copies.
 *
 * <h2>Why MIH</h2>
 * For Hamming-radius search over 64-bit perceptual hashes, MIH is exact and near-constant per query:
 * split each hash into {@code RADIUS+1} segments — by the pigeonhole principle two hashes within
 * {@link #RADIUS} bits must match exactly in at least one segment — so candidates are found by hash
 * lookup instead of tree traversal. Scales far better than a BK-tree on uniformly-distributed hashes.
 *
 * <h2>Clustering</h2>
 * Every within-radius pair is unioned (union-find), so clusters are true connected components rather
 * than order-dependent greedy groups. Within each cluster the keeper is the largest file (tie-broken
 * by shortest path); the rest are duplicates.
 *
 * <h2>Tiers</h2>
 * <ol>
 * <li><b>Exact</b>: items sharing a content signature (and size) are certain copies; collapsed first
 * so only one representative per exact group enters the fuzzy pass.</li>
 * <li><b>Near-duplicate</b>: representatives are clustered by MIH over their perceptual hashes.</li>
 * </ol>
 * Items without a perceptual hash (videos, unreadable images) are always kept.
 */
public class MihDeduplicator implements Deduplicator {

    private static final Logger logger = LogManager.getLogger(MihDeduplicator.class);
    private static final int RADIUS = 5;
    private static final int SEGMENTS = RADIUS + 1;

    private static final Comparator<MediaItem> KEEPER_ORDER =
            Comparator.comparingLong(MediaItem::getFileSize).reversed()
                      .thenComparingInt(i -> i.getPath().toString().length());

    private record ExactKey(long size, long signature) { }

    @Override
    public DeduplicationResult deduplicate(final List<MediaItem> items) {
        final List<MediaItem> keepers = new ArrayList<>();
        final List<MediaItem> duplicates = new ArrayList<>();

        final List<MediaItem> representatives = collapseExactCopies(items, duplicates);
        clusterNearDuplicates(representatives, keepers, duplicates);

        logger.debug("Deduplicated {} items -> {} keepers, {} duplicates",
                     items.size(), keepers.size(), duplicates.size());
        return new DeduplicationResult(keepers, duplicates);
    }

    /**
     * Groups byte-identical copies (same content signature and size), keeping one representative per
     * group and pushing the rest to {@code duplicates}. Items without a signature pass through as
     * their own representative.
     */
    private List<MediaItem> collapseExactCopies(List<MediaItem> items, List<MediaItem> duplicates) {
        final Map<ExactKey, List<MediaItem>> groups = new HashMap<>();
        final List<MediaItem> representatives = new ArrayList<>();

        for (MediaItem item : items) {
            if (item.getContentSignature() == 0) {
                representatives.add(item);
            } else {
                groups.computeIfAbsent(new ExactKey(item.getFileSize(), item.getContentSignature()),
                                       k -> new ArrayList<>()).add(item);
            }
        }
        for (List<MediaItem> group : groups.values()) {
            group.sort(KEEPER_ORDER);
            representatives.add(group.getFirst());
            duplicates.addAll(group.subList(1, group.size()));
        }
        return representatives;
    }

    /**
     * Clusters representatives that have a usable perceptual hash by Hamming radius (MIH + union-find),
     * emitting one keeper per cluster. Representatives without a hash are kept as-is.
     */
    private void clusterNearDuplicates(List<MediaItem> reps, List<MediaItem> keepers, List<MediaItem> duplicates) {
        final List<MediaItem> hashable = new ArrayList<>();
        for (MediaItem rep : reps) {
            if (rep.getPerceptualHash() == 0) {
                keepers.add(rep); // no usable hash: always a keeper
            } else {
                hashable.add(rep);
            }
        }
        if (hashable.isEmpty()) {
            return;
        }

        final long[] hashes = new long[hashable.size()];
        for (int i = 0; i < hashes.length; i++) {
            hashes[i] = hashable.get(i).getPerceptualHash();
        }

        final DisjointSet clusters = new DisjointSet(hashable.size());
        unionWithinRadius(hashes, clusters);

        final Map<Integer, List<MediaItem>> components = new HashMap<>();
        for (int i = 0; i < hashable.size(); i++) {
            components.computeIfAbsent(clusters.find(i), k -> new ArrayList<>()).add(hashable.get(i));
        }
        for (List<MediaItem> cluster : components.values()) {
            cluster.sort(KEEPER_ORDER);
            keepers.add(cluster.getFirst());
            duplicates.addAll(cluster.subList(1, cluster.size()));
        }
    }

    /** Builds the multi-index and unions every pair within {@link #RADIUS} Hamming distance. */
    private void unionWithinRadius(long[] hashes, DisjointSet clusters) {
        final List<Map<Long, List<Integer>>> index = buildIndex(hashes);
        for (int i = 0; i < hashes.length; i++) {
            for (int seg = 0; seg < SEGMENTS; seg++) {
                for (int j : index.get(seg).getOrDefault(segment(hashes[i], seg), List.of())) {
                    if (j > i && Long.bitCount(hashes[i] ^ hashes[j]) <= RADIUS) {
                        clusters.union(i, j);
                    }
                }
            }
        }
    }

    private List<Map<Long, List<Integer>>> buildIndex(long[] hashes) {
        final List<Map<Long, List<Integer>>> index = new ArrayList<>(SEGMENTS);
        for (int seg = 0; seg < SEGMENTS; seg++) {
            index.add(new HashMap<>());
        }
        for (int i = 0; i < hashes.length; i++) {
            for (int seg = 0; seg < SEGMENTS; seg++) {
                index.get(seg).computeIfAbsent(segment(hashes[i], seg), k -> new ArrayList<>()).add(i);
            }
        }
        return index;
    }

    /** Value of the {@code seg}-th slice of the 64-bit hash (slices partition all 64 bits). */
    private static long segment(long hash, int seg) {
        int base = 64 / SEGMENTS;
        int rem = 64 % SEGMENTS;
        int start = seg * base + Math.min(seg, rem);
        int len = base + (seg < rem ? 1 : 0);
        return (hash >>> start) & ((1L << len) - 1);
    }

    private static final class DisjointSet {
        private final int[] parent;
        private final int[] rank;

        DisjointSet(int n) {
            parent = new int[n];
            rank = new int[n];
            for (int i = 0; i < n; i++) {
                parent[i] = i;
            }
        }

        int find(int x) {
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]; // path halving
                x = parent[x];
            }
            return x;
        }

        void union(int a, int b) {
            int ra = find(a);
            int rb = find(b);
            if (ra == rb) {
                return;
            }
            if (rank[ra] < rank[rb]) {
                int t = ra; ra = rb; rb = t;
            }
            parent[rb] = ra;
            if (rank[ra] == rank[rb]) {
                rank[ra]++;
            }
        }
    }
}
