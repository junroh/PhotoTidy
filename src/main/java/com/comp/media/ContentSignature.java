package com.comp.media;

/**
 * A cheap 64-bit content fingerprint used to group exact copies without a full cryptographic hash.
 * <p>
 * Mixes the file size with the head and tail of the content (FNV-1a). This is a heuristic, not a
 * collision-proof digest — but the deduplicator only ever <b>moves</b> duplicates aside for review,
 * so a rare false match merely files a distinct photo into {@code duplicates/} rather than losing it.
 */
public final class ContentSignature {

    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final int SAMPLE = 64 * 1024;

    private ContentSignature() { }

    /** Non-zero fingerprint of the given content (0 is reserved for "unknown"). */
    public static long of(byte[] bytes) {
        long h = FNV_OFFSET;
        h = mix(h, bytes.length);

        int headEnd = Math.min(SAMPLE, bytes.length);
        for (int i = 0; i < headEnd; i++) {
            h = (h ^ (bytes[i] & 0xff)) * FNV_PRIME;
        }
        for (int i = Math.max(headEnd, bytes.length - SAMPLE); i < bytes.length; i++) {
            h = (h ^ (bytes[i] & 0xff)) * FNV_PRIME;
        }
        return (h == 0) ? 1 : h;
    }

    private static long mix(long h, long value) {
        for (int shift = 0; shift < 64; shift += 8) {
            h = (h ^ ((value >>> shift) & 0xff)) * FNV_PRIME;
        }
        return h;
    }
}
