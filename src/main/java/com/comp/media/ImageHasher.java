package com.comp.media;

import java.awt.Graphics2D;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.ByteArrayInputStream;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Perceptual (pHash) fingerprint: shrink to 32x32 grayscale, take the low-frequency DCT
 * coefficients, and emit a 64-bit hash of which coefficients exceed the average. Visually similar
 * images yield hashes a small Hamming distance apart.
 */
public final class ImageHasher {

    private static final int SIZE = 32;
    private static final int LOW_FREQ = 8;

    // Precomputed DCT cosine terms so the transform avoids millions of Math.cos calls.
    private static final double[] COEFF = new double[SIZE];
    private static final double[][] COS = new double[SIZE][SIZE];
    static {
        for (int i = 0; i < SIZE; i++) {
            COEFF[i] = (i == 0) ? 1.0 / Math.sqrt(2) : 1.0;
            for (int j = 0; j < SIZE; j++) {
                COS[i][j] = Math.cos(((2 * i + 1) / (2.0 * SIZE)) * j * Math.PI);
            }
        }
    }

    private ImageHasher() { }

    /**
     * Decodes image bytes and hashes them; returns 0 if the bytes are not a readable image.
     * <p>
     * The image is decoded <b>subsampled</b> — only roughly every Nth pixel is read so the decoded
     * bitmap is just large enough to downscale to {@value #SIZE}px. A 48MP photo is never fully
     * expanded in memory to produce a 64-bit hash.
     */
    public static long fromBytes(byte[] bytes) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (iis == null) {
                return 0;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return 0;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int step = Math.max(1, Math.min(reader.getWidth(0), reader.getHeight(0)) / SIZE);
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(step, step, 0, 0);
                return hash(reader.read(0, param));
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return 0;
        }
    }

    /** Returns the perceptual hash of an image, or 0 if the image is null. */
    public static long hash(BufferedImage image) {
        if (image == null) {
            return 0;
        }
        double[][] pixels = toGrayscale32(image);
        double[][] dct = dct(pixels);
        double average = lowFreqAverage(dct);

        long hash = 0;
        for (int x = 0; x < LOW_FREQ; x++) {
            for (int y = 0; y < LOW_FREQ; y++) {
                if ((x != 0 || y != 0) && dct[x][y] > average) {
                    hash |= (1L << (x + y * LOW_FREQ));
                }
            }
        }
        return hash;
    }

    public static int hammingDistance(long a, long b) {
        return Long.bitCount(a ^ b);
    }

    private static double[][] toGrayscale32(BufferedImage image) {
        BufferedImage resized = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resized.createGraphics();
        g.drawImage(image, 0, 0, SIZE, SIZE, null);
        g.dispose();
        resized = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null)
                .filter(resized, null);

        double[][] values = new double[SIZE][SIZE];
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                values[x][y] = resized.getRGB(x, y) & 0xFF;
            }
        }
        return values;
    }

    private static double[][] dct(double[][] f) {
        double[][] result = new double[SIZE][SIZE];
        for (int u = 0; u < SIZE; u++) {
            for (int v = 0; v < SIZE; v++) {
                double sum = 0.0;
                for (int i = 0; i < SIZE; i++) {
                    for (int j = 0; j < SIZE; j++) {
                        sum += f[i][j] * COS[i][u] * COS[j][v];
                    }
                }
                result[u][v] = 0.25 * COEFF[u] * COEFF[v] * sum;
            }
        }
        return result;
    }

    /** Average of the low-frequency block excluding the DC term at (0,0). */
    private static double lowFreqAverage(double[][] dct) {
        double total = 0;
        for (int x = 0; x < LOW_FREQ; x++) {
            for (int y = 0; y < LOW_FREQ; y++) {
                if (x != 0 || y != 0) {
                    total += dct[x][y];
                }
            }
        }
        return total / (LOW_FREQ * LOW_FREQ - 1);
    }
}
