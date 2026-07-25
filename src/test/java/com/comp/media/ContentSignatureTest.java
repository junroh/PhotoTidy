package com.comp.media;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

class ContentSignatureTest {

    @Test
    void testSameBytesSameNonZeroSignature() {
        byte[] a = bytes(4096, 1);
        byte[] b = bytes(4096, 1);
        long sig = ContentSignature.of(a);
        Assertions.assertNotEquals(0, sig);
        Assertions.assertEquals(sig, ContentSignature.of(b));
    }

    @Test
    void testDifferentContentDiffersSignature() {
        Assertions.assertNotEquals(ContentSignature.of(bytes(4096, 1)),
                                   ContentSignature.of(bytes(4096, 2)));
    }

    @Test
    void testSizeIsPartOfSignature() {
        // Same repeating content, different length -> different signature.
        Assertions.assertNotEquals(ContentSignature.of(bytes(1000, 7)),
                                   ContentSignature.of(bytes(2000, 7)));
    }

    @Test
    void testDifferenceOutsideTheSampledWindowMayCollide() {
        // Documents the heuristic: only head+tail are sampled, so a mid-file-only change of the
        // same-length content can collide. Safe because duplicates are moved, never deleted.
        byte[] a = new byte[512 * 1024];
        byte[] b = a.clone();
        b[256 * 1024] = 1; // change only the middle, outside the 64KB head/tail window
        Assertions.assertEquals(ContentSignature.of(a), ContentSignature.of(b));
    }

    private static byte[] bytes(int len, int seed) {
        byte[] b = new byte[len];
        Arrays.fill(b, (byte) seed);
        return b;
    }
}
