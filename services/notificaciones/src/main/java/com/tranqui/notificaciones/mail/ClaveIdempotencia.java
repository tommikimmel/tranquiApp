package com.tranqui.notificaciones.mail;

import java.util.function.Supplier;

/**
 * Clave de idempotencia del mail que se está mandando (sección 6.2: efectos externos con el
 * {@code eventId} como clave del proveedor). La fija el handler alrededor de la llamada a la
 * plantilla, así las plantillas mudadas del monolito no cambian de firma.
 */
public final class ClaveIdempotencia {

    private static final ThreadLocal<String> ACTUAL = new ThreadLocal<>();

    private ClaveIdempotencia() {}

    public static void con(String clave, Runnable envio) {
        String anterior = ACTUAL.get();
        ACTUAL.set(clave);
        try {
            envio.run();
        } finally {
            if (anterior == null) {
                ACTUAL.remove();
            } else {
                ACTUAL.set(anterior);
            }
        }
    }

    public static <T> T con(String clave, Supplier<T> envio) {
        String anterior = ACTUAL.get();
        ACTUAL.set(clave);
        try {
            return envio.get();
        } finally {
            if (anterior == null) {
                ACTUAL.remove();
            } else {
                ACTUAL.set(anterior);
            }
        }
    }

    static String actual() {
        return ACTUAL.get();
    }
}
