package com.comp.media;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

class ImageHasherTest {

    private static BufferedImage gradient(int seed) {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                int v = (x * 4 + y * 2 + seed * 30) & 0xFF;
                img.setRGB(x, y, new Color(v, v / 2, (y * 3) & 0xFF).getRGB());
            }
        }
        return img;
    }

    @Test
    void testNullImageHashesToZero() {
        Assertions.assertEquals(0, ImageHasher.hash(null));
    }

    @Test
    void testIdenticalImagesHashEqual() {
        Assertions.assertEquals(ImageHasher.hash(gradient(1)), ImageHasher.hash(gradient(1)));
    }

    @Test
    void testDifferentImagesHashFarApart() {
        long a = ImageHasher.hash(gradient(1));
        long b = ImageHasher.hash(gradient(40));
        Assertions.assertTrue(ImageHasher.hammingDistance(a, b) > 5,
                              "clearly different images should exceed the near-duplicate radius");
    }

    @Test
    void testSlightlyChangedImageStaysClose() {
        BufferedImage base = gradient(1);
        BufferedImage tweaked = gradient(1);
        Graphics2D g = tweaked.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 3, 3); // tiny local change
        g.dispose();

        int d = ImageHasher.hammingDistance(ImageHasher.hash(base), ImageHasher.hash(tweaked));
        Assertions.assertTrue(d <= 5, "a tiny edit should stay within the near-duplicate radius (was " + d + ")");
    }

    @Test
    void testHammingDistance() {
        Assertions.assertEquals(0, ImageHasher.hammingDistance(0xFF, 0xFF));
        Assertions.assertEquals(4, ImageHasher.hammingDistance(0b0000, 0b1111));
    }

    @Test
    void testFromBytesDecodesSubsampledAndIsStable() throws Exception {
        // A large image forces subsampling; two identical encodings must hash equal and non-zero.
        byte[] jpeg = jpegBytes(gradient(3), 800);
        long a = ImageHasher.fromBytes(jpeg);
        long b = ImageHasher.fromBytes(jpeg);

        Assertions.assertNotEquals(0, a, "a decodable image must hash to a non-zero value");
        Assertions.assertEquals(a, b, "same bytes must hash identically");
    }

    @Test
    void testFromBytesRejectsGarbage() {
        Assertions.assertEquals(0, ImageHasher.fromBytes(new byte[]{1, 2, 3, 4}));
    }

    private static byte[] jpegBytes(BufferedImage small, int upscaleTo) throws Exception {
        BufferedImage big = new BufferedImage(upscaleTo, upscaleTo, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = big.createGraphics();
        g.drawImage(small, 0, 0, upscaleTo, upscaleTo, null);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(big, "jpg", out);
        return out.toByteArray();
    }
}
