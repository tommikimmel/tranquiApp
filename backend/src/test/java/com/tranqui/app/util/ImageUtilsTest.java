package com.tranqui.app.util;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class ImageUtilsTest {

    private String makeDataUri(int width, int height, String format) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        String mime = format.equals("jpg") ? "image/jpeg" : "image/" + format;
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }

    @Test
    void decodedByteSize_matchesActualDecodedLength() {
        String base64 = Base64.getEncoder().encodeToString("hello world".getBytes());
        String dataUri = "data:image/png;base64," + base64;
        assertEquals("hello world".getBytes().length, ImageUtils.decodedByteSize(dataUri));
    }

    @Test
    void decodedByteSize_handlesPadding() {
        String base64 = Base64.getEncoder().encodeToString("ab".getBytes()); // needs padding
        String dataUri = "data:image/png;base64," + base64;
        assertEquals(2, ImageUtils.decodedByteSize(dataUri));
    }

    @Test
    void decodedByteSize_nonDataUri_returnsStringLength() {
        assertEquals(11, ImageUtils.decodedByteSize("hello world"));
    }

    @Test
    void decodedByteSize_null_returnsZero() {
        assertEquals(0, ImageUtils.decodedByteSize(null));
    }

    @Test
    void resizeIfNeeded_returnsUnchanged_whenAlreadyUnderLimit() throws Exception {
        String small = makeDataUri(10, 10, "jpg");
        String result = ImageUtils.resizeIfNeeded(small, 640, 3L * 1024 * 1024);
        assertEquals(small, result);
    }

    @Test
    void resizeIfNeeded_shrinksLargeImage_belowMaxDimension() throws Exception {
        // A big flat-color bitmap decodes far larger in-memory than its encoded size, but a
        // large TYPE_INT_RGB PNG is still enough to comfortably exceed a tiny maxBytes threshold.
        String large = makeDataUri(2000, 1500, "png");
        long originalSize = ImageUtils.decodedByteSize(large);

        String result = ImageUtils.resizeIfNeeded(large, 640, 1024); // force resize with a tiny cap

        assertNotEquals(large, result, "expected the oversized image to be re-encoded");
        assertTrue(result.startsWith("data:image/jpeg;base64,"));

        String base64 = result.substring(result.indexOf(",") + 1);
        BufferedImage resized = ImageIO.read(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(base64)));
        assertNotNull(resized);
        assertTrue(Math.max(resized.getWidth(), resized.getHeight()) <= 640);
        assertTrue(ImageUtils.decodedByteSize(result) < originalSize);
    }

    @Test
    void resizeIfNeeded_returnsOriginal_whenNotARecognizableDataUri() {
        String notAnImage = "not-a-data-uri-" + "x".repeat(5_000_000);
        String result = ImageUtils.resizeIfNeeded(notAnImage, 640, 1024);
        assertEquals(notAnImage, result);
    }

    @Test
    void resizeIfNeeded_null_returnsNull() {
        assertNull(ImageUtils.resizeIfNeeded(null, 640, 1024));
    }
}
