package com.tranqui.calendario.google;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Cifrado de los tokens de Google y firma HMAC del {@code state} del OAuth y del token de los canales
 * de webhook. Es una copia exacta de EncryptionUtil y de las firmas del monolito, con la misma
 * propiedad y el mismo valor de respaldo: los tokens guardados y los canales ya registrados en Google
 * se hicieron con esa clave (decisión 3 de la spec de la Fase 3). Ojo: hoy en producción el monolito
 * no recibe ENCRYPTION_KEY (lee security.encryption.key) y usa el respaldo; corregirlo exige volver a
 * cifrar los tokens y cambiar la clave en los dos a la vez (ver la spec).
 */
@Component
public class Cifrado {

    private static final String ALGORITMO = "AES";

    private final String clave;

    public Cifrado(@Value("${security.encryption.key:masterdecryptionkey32charspart12}") String clave) {
        this.clave = clave;
    }

    public String cifrar(String valor) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(clave.getBytes(StandardCharsets.UTF_8), ALGORITMO));
            return Base64.getEncoder().encodeToString(cipher.doFinal(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cifrar el token de Google", e);
        }
    }

    public String descifrar(String cifrado) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(clave.getBytes(StandardCharsets.UTF_8), ALGORITMO));
            return new String(cipher.doFinal(Base64.getDecoder().decode(cifrado)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo descifrar el token de Google", e);
        }
    }

    /** {@code <profesionalId>.<hmac>}, como el state del OAuth y el token de canal del monolito. */
    public String firmar(Long profesionalId) {
        String payload = String.valueOf(profesionalId);
        return payload + "." + hmac(payload);
    }

    /** El id del profesional si la firma es válida; si no, {@code null}. */
    public Long verificar(String firmado) {
        if (firmado == null || !firmado.contains(".")) {
            return null;
        }
        int separador = firmado.lastIndexOf('.');
        String payload = firmado.substring(0, separador);
        if (!igualesEnTiempoConstante(hmac(payload), firmado.substring(separador + 1))) {
            return null;
        }
        try {
            return Long.parseLong(payload);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clave.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar", e);
        }
    }

    private static boolean igualesEnTiempoConstante(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int resultado = 0;
        for (int i = 0; i < a.length(); i++) {
            resultado |= a.charAt(i) ^ b.charAt(i);
        }
        return resultado == 0;
    }
}
