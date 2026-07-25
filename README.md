# PhotoTidy

A small command-line tool that tidies up a messy photo/video collection. It scans a
source directory, reads capture dates from EXIF / container metadata, detects
near-duplicate images, and files everything into dated folders
(e.g. `2020/2020_02/20200203_112244.jpg`).

## How it works

The pipeline runs as three stages, with scanning and hashing overlapped:

```
scan + hash (parallel, overlapped)  →  deduplicate (barrier)  →  file (I/O, serial)
```

1. **Scan + hash** — `DirectoryScanner` walks the source tree (symlinks not followed) on its own
   thread and streams each supported file to a bounded pool of hashing workers as it is found, so
   the walk and the hashing overlap. `MediaHasher` reads each image from disk **once** and decodes
   it **subsampled** (only enough pixels to downscale to 32px — a 48MP photo is never fully
   expanded) to derive its capture date, perceptual (DCT) hash, and a quick content signature, into
   an immutable `MediaItem`. Results are stored in a `HashCache`, so a rerun skips files whose size
   and modification time are unchanged. Per-file failures are absorbed so one bad file never aborts
   the run.
2. **Deduplicate** — `MihDeduplicator` works in two tiers. First, byte-identical copies (same size +
   content signature) are collapsed exactly. Then survivors are grouped by **Multi-Index Hashing**
   over their perceptual hashes: each hash is split into 6 segments so near-duplicates (Hamming
   radius 5) are found by hash lookup rather than tree traversal, and every within-radius pair is
   merged with union-find into true connected components. Each cluster keeps one survivor (largest
   file, then shortest path); the rest are duplicates. This is a barrier (needs the whole batch).
3. **File** — `FileMover` files survivors into the dated layout and routes duplicates into a
   separate `duplicates/` subfolder for review. Runs serially to keep unique-naming race-free.

Videos and unreadable images (no perceptual hash) are never grouped as near-duplicates.

### Which copy is kept

Within a near-duplicate cluster the **keeper** is chosen deterministically:

1. **Largest file size** — the highest-quality/least-recompressed copy wins.
2. **Shortest path** (tie-break) — a stable, deterministic fallback when sizes are equal.

Everything else in the cluster is classified as a **duplicate**. Files with no perceptual hash
(videos, unreadable images) are never near-duplicate-clustered — but exact byte-identical copies
are still collapsed by content signature.

### What happens to the files

Nothing is ever deleted — the tool only **moves or copies**, so a run is reversible by inspection:

- **Keepers** go into the dated layout under the destination root, e.g. `<dest>/2020/2020_02/20200203_112244.jpg`.
- **Duplicates** go into the same dated layout but under a `duplicates/` subfolder
  (`<dest>/duplicates/2020/2020_02/…`), so you can review and delete them yourself.
- `run.mode.filemove=true` **moves** files; `false` **copies** them. `run.mode.drymode=true` only
  reports the plan and touches nothing.

Note there are **two independent notions of "duplicate"**:

- **Near-duplicate** (perceptual) — detected by the Multi-Index Hashing pass above; routed to `duplicates/`.
- **Destination name collision** — two different files that map to the *same* target filename
  (same capture second + extension). This is resolved by `policy.duplicate`:
  `INCREASE` (append `_001`, `_002`, …), `SKIP`, `OVERWRITE`, or `STOP`.

## Build & run

Requires **JDK 21** and Maven.

```bash
mvn package                       # builds an executable shaded jar in target/
java -jar target/PhotoTidy-1.0-SNAPSHOT.jar [config.properties]
```

If no config path is given it defaults to `./src/main/resources/config.properties`.

## Configuration

All behavior is driven by a `.properties` file — see
[`src/main/resources/config.properties`](src/main/resources/config.properties) for the full,
commented list. Key options:

| Key | Meaning |
| --- | --- |
| `dir.source` / `dir.destination` | input tree / output root |
| `run.mode.drymode` | `true` = report only, touch nothing |
| `run.mode.filemove` | `true` = move, `false` = copy |
| `run.parser.count` | hashing threads (`0` = auto = CPU cores). Raise above the core count on SSD/cloud storage to hide read latency. |
| `run.cache.enabled` | persist hashes so unchanged files are skipped on reruns |
| `run.cache.file` | hash cache file; checkpointed while running, compacted only when it changes (deleted files pruned) |
| `policy.noexif` | `SKIP` / `FIXED_DIR` / `MODIFIED_DATE` / `STOP` |
| `policy.duplicate` | name-collision policy: `SKIP` / `INCREASE` / `OVERWRITE` / `STOP` |
| `policy.duplicate.dir` | subfolder for near-duplicate images (default `duplicates`) |
| `format.dir` / `format.file` | `SimpleDateFormat` patterns for folders / filenames |

**Start with `run.mode.drymode=true`** to preview the plan before letting it touch files.

## Performance & sizing

Deduplication is a barrier — every file must be hashed before any can be classified — so the whole
batch is held in heap at once. The figures below are analytical estimates (from object sizes and
algorithmic cost), not measured benchmarks; treat them as planning guidance.

### Memory

The dominant cost is holding every `MediaItem` plus the MIH index in heap. The 64-bit perceptual
hash itself is tiny (8 bytes); the weight is path strings, object overhead, and the index's segment
maps.

| Structure | Per item (approx) | When it lives |
| --- | --- | --- |
| `MediaItem` (path + date + hashes) | ~250 B | whole run |
| MIH index (6 segment maps) | ~250 B | deduplication |
| Hash-cache entry (in memory) | ~180 B | scan + hash only (freed before dedup) |

Peak is roughly **~1 GB of heap per 1 million files** (with overhead). Plan `-Xmx` as
`files / 1M × ~1 GB` — e.g. `-Xmx6g` for ~5M files. A 16 GB machine handles up to ~10M files, which
is the practical single-machine ceiling.

Transient memory: each hashing worker reads one file fully into memory (`worker count × file size`).
Subsampled decoding keeps the decoded bitmap to kilobytes, but a library full of very large files
(cap: 100 MB each) with many threads can spike to `threads × 100 MB`.

### Time

| Scenario | Bound by | Rough cost |
| --- | --- | --- |
| **Cold cache** | disk read + decode (I/O bound) | 3 TB at ~150 MB/s ≈ 5–6 h on HDD; tens of minutes on NVMe |
| **Warm rerun** | directory walk + cache lookups + dedup | minutes — no re-hashing |
| Deduplication | Multi-Index Hashing lookups (`Long.bitCount`) | seconds to minutes; not the bottleneck |
| Filing | sequential I/O (rename is cheap, copy is data-bound) | instant in dry-run |

### Tuning

- **`run.parser.count`** — hashing threads. Default = CPU cores (right when decoding is the
  bottleneck). On SSD/cloud storage where I/O latency dominates, set it well above the core count
  (e.g. 32–64) to hide that latency.
- **`-Xmx`** — size per the memory table above.
- **`run.cache.*`** — the hash cache makes reruns cheap and large runs resumable; the cache file is
  small (~60–120 MB per 1M files) and self-compacting.

### Beyond one machine

Past ~10M files the heap-resident batch is the limit. The natural next step is to keep only the
compact hashes (not full `MediaItem`s) in memory for the dedup pass and distribute hashing across
workers — the `HashCache` / `Deduplicator` seams already isolate those concerns for such an
extension.
