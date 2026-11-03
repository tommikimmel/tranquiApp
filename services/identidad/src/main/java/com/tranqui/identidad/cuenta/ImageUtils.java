package com.tranqui.identidad.cuenta;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilidad para validar tamaño de fotos de perfil almacenadas en formato base64 data URI.
 */
public final class ImageUtils {

    private static final Pattern DATA_URI_PATTERN = Pattern.compile("^data:image/[^;]+;base64,(.+)$", Pattern.DOTALL);

    private ImageUtils() {}

    /** Decoded byte size of a base64 data: URI, or the raw string length if it isn't one. */
    public static long decodedByteSize(String dataUriOrString) {
        if (dataUriOrString == null) {
            return 0;
        }
        Matcher m = DATA_URI_PATTERN.matcher(dataUriOrString);
        if (!m.matches()) {
            return dataUriOrString.length();
        }
        String b64 = m.group(1);
        int padding = 0;
        if (b64.endsWith("==")) {
            padding = 2;
        } else if (b64.endsWith("=")) {
            padding = 1;
        }
        return (long) (b64.length() / 4.0 * 3) - padding;
    }
}
