package com.tranqui.eventos.jwt;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Utilidad para decodificar claves RSA (pública X.509 y privada PKCS#8) desde formato base64
 * de una línea o bloques PEM con o sin encabezados. Exige un tamaño mínimo de 2048 bits.
 */
public final class ClavesRsa {

    private static final int LONGITUD_MINIMA_BITS = 2048;

    private ClavesRsa() {}

    /**
     * Lee una clave pública RSA en formato X.509 desde texto base64 o PEM.
     */
    public static RSAPublicKey leerClavePublica(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            throw new IllegalStateException("La clave pública RSA no puede ser nula ni vacía");
        }
        try {
            byte[] bytes = decodificarBase64(texto);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PublicKey key = kf.generatePublic(new X509EncodedKeySpec(bytes));
            if (!(key instanceof RSAPublicKey rsaKey)) {
                throw new IllegalStateException("La clave decodificada no es una clave RSA válida");
            }
            validarLongitud(rsaKey);
            return rsaKey;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Error al decodificar la clave pública RSA: formato inválido", e);
        }
    }

    /**
     * Lee una clave privada RSA en formato PKCS#8 desde texto base64 o PEM.
     */
    public static RSAPrivateKey leerClavePrivada(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            throw new IllegalStateException("La clave privada RSA no puede ser nula ni vacía");
        }
        try {
            byte[] bytes = decodificarBase64(texto);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey key = kf.generatePrivate(new PKCS8EncodedKeySpec(bytes));
            if (!(key instanceof RSAPrivateKey rsaKey)) {
                throw new IllegalStateException("La clave decodificada no es una clave RSA válida");
            }
            validarLongitud(rsaKey);
            return rsaKey;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Error al decodificar la clave privada RSA: formato inválido", e);
        }
    }

    private static void validarLongitud(RSAKey rsaKey) {
        int bits = rsaKey.getModulus().bitLength();
        if (bits < LONGITUD_MINIMA_BITS) {
            throw new IllegalStateException(
                    "La clave RSA debe tener al menos " + LONGITUD_MINIMA_BITS + " bits (tiene " + bits + ")"
            );
        }
    }

    private static byte[] decodificarBase64(String texto) {
        String limpio = texto
                .replaceAll("-----BEGIN [A-Z0-9 ]+-----", "")
                .replaceAll("-----END [A-Z0-9 ]+-----", "")
                .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(limpio);
    }
}
