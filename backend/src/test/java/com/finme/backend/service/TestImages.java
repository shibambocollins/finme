package com.finme.backend.service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Real image bytes for tests.
 * <p>
 * The receipt fixtures used to be {@code new byte[]{1, 2, 3}} with a "image/jpeg" content type -
 * which passed for as long as nothing looked past the header. Adding signature validation broke
 * every one of them, correctly: they were never images, and the tests had been asserting on a
 * path the real pipeline would now reject. Generating a genuine JPEG keeps the fixtures honest
 * about what they claim to be.
 */
final class TestImages {

    private TestImages() {
    }

    static byte[] jpeg() {
        return encode("jpg");
    }

    static byte[] png() {
        return encode("png");
    }

    private static byte[] encode(String format) {
        try {
            BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
