package com.tranqui.app.util;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Profile photos are stored as inline base64 data: URIs (see Usuario.fotoUrl, a TEXT column)
 * rather than uploaded files with a URL — this utility exists to keep that data reasonably
 * sized instead of changing that storage design. Uses javax.imageio (built into the JDK, no
 * extra dependency) to decode/resize/re-encode as JPEG, which also strips any embedded
 * metadata (EXIF, C2PA provenance chains, etc.) that can otherwise dwarf the actual pixel data.
 */
public final class ImageUtils {

    private static final Pattern DATA_URI_PATTERN = Pattern.compile("^data:image/[^;]+;base64,(.+)$", Pattern.DOTALL);

    private ImageUtils() {}

    /** Decoded byte size of a base64 data: URI, or the raw string length if it isn't one. */
    public static long decodedByteSize(String dataUriOrString) {
        if (dataUriOrString == null) return 0;
        Matcher m = DATA_URI_PATTERN.matcher(dataUriOrString);
        if (!m.matches()) {
            return dataUriOrString.length();
        }
        // Base64 encodes 3 bytes as 4 chars; this avoids decoding just to measure size.
        String b64 = m.group(1);
        int padding = 0;
        if (b64.endsWith("==")) padding = 2;
        else if (b64.endsWith("=")) padding = 1;
        return (long) (b64.length() / 4.0 * 3) - padding;
    }

    /**
     * Resizes a base64 image data: URI down to maxDimension (longest side) and re-encodes as
     * JPEG if it's not already comfortably under maxBytes. Returns the input unchanged if it
     * isn't a recognizable image data: URI, is already small enough, or resizing fails for any
     * reason — this is a best-effort normalization, never a hard failure.
     */
    public static String resizeIfNeeded(String dataUri, int maxDimension, long maxBytes) {
        if (dataUri == null) return null;
        if (decodedByteSize(dataUri) <= maxBytes) return dataUri;

        Matcher m = DATA_URI_PATTERN.matcher(dataUri);
        if (!m.matches()) return dataUri;

        try {
            byte[] raw = Base64.getDecoder().decode(m.group(1));
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(raw));
            if (original == null) return dataUri;

            int width = original.getWidth();
            int height = original.getHeight();
            double scale = Math.min(1.0, (double) maxDimension / Math.max(width, height));
            int targetWidth = Math.max(1, (int) Math.round(width * scale));
            int targetHeight = Math.max(1, (int) Math.round(height * scale));

            BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Image scaledInstance = original.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
            resized.getGraphics().drawImage(scaledInstance, 0, 0, java.awt.Color.WHITE, null);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(resized, "jpg", out);
            String base64 = Base64.getEncoder().encodeToString(out.toByteArray());
            String normalized = "data:image/jpeg;base64," + base64;

            // Only use the resized version if it's actually smaller — otherwise keep the original.
            return normalized.length() < dataUri.length() ? normalized : dataUri;
        } catch (IOException | IllegalArgumentException e) {
            return dataUri;
        }
    }
}
