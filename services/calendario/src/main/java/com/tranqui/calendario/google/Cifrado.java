package com.tranqui.calendario.google;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;

/**
 * Cifrado de los tokens de Google y firma HMAC del {@code state} del OAuth y del token de los canales
 * de webhook. Hace exactamente lo mismo que EncryptionUtil y las firmas del monolito (decisión 3 de la
 * spec de la Fase 3), así los tokens y los canales sirven en los dos.
 *
 * <p>Formato actual ({@code v2:}): AES-256-GCM con IV aleatorio y la clave derivada (SHA-256) de
 * {@code ENCRYPTION_KEY}. Lo cifrado antes del hotfix de la clave (AES/ECB con una clave pública,
 * porque el monolito nunca recibía {@code ENCRYPTION_KEY}) se sigue leyendo, y las firmas hechas con
 * esa clave valen hasta que la renovación diaria vuelve a registrar los canales.
 */
@Component
public class Cifrado {

    private static final String PREFIJO_V2 = "v2:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String CLAVE_LEGADA = "masterdecryptionkey32charspart12";
    private static final Set<String> CLAVES_PUBLICAS = Set.of(CLAVE_LEGADA,
            "cambiar-por-una-clave-aleatoria-de-al-menos-32-bytes");

    private final String clave;
    private final SecureRandom random = new SecureRandom();

    public Cifrado(@Value("${security.encryption.key:}") String clave) {
        if (clave == null || clave.isBlank()) {
            throw new IllegalStateException("Falta la clave de cifrado: configurar la variable ENCRYPTION_KEY.");
        }
        if (CLAVES_PUBLICAS.contains(clave)) {
            throw new IllegalStateException("ENCRYPTION_KEY tiene un valor publicado en el repositorio.");
        }
        if (clave.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("ENCRYPTION_KEY tiene que tener al menos 32 bytes.");
        }
        this.clave = clave;
    }

    public String cifrar(String valor) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, claveAes(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(valor.getBytes(StandardCharsets.UTF_8));
            return PREFIJO_V2 + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array());
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cifrar el token de Google", e);
        }
    }

    public String descifrar(String cifrado) {
        try {
            if (!cifrado.startsWith(PREFIJO_V2)) {
                Cipher cipher = Cipher.getInstance("AES");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(CLAVE_LEGADA.getBytes(StandardCharsets.UTF_8), "AES"));
                return new String(cipher.doFinal(Base64.getDecoder().decode(cifrado)), StandardCharsets.UTF_8);
            }
            byte[] entrada = Base64.getDecoder().decode(cifrado.substring(PREFIJO_V2.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, claveAes(), new GCMParameterSpec(TAG_BITS, entrada, 0, IV_BYTES));
            return new String(cipher.doFinal(entrada, IV_BYTES, entrada.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo descifrar el token de Google", e);
        }
    }

    /** {@code <profesionalId>.<hmac>}, como el state del OAuth y el token de canal del monolito. */
    public String firmar(Long profesionalId) {
        String payload = String.valueOf(profesionalId);
        return payload + "." + hmac(payload, clave);
    }

    /** El id del profesional si la firma es válida (con la clave actual o la anterior); si no, {@code null}. */
    public Long verificar(String firmado) {
        if (firmado == null || !firmado.contains(".")) {
            return null;
        }
        int separador = firmado.lastIndexOf('.');
        String payload = firmado.substring(0, separador);
        String firma = firmado.substring(separador + 1);
        if (!igualesEnTiempoConstante(hmac(payload, clave), firma)
                && !igualesEnTiempoConstante(hmac(payload, CLAVE_LEGADA), firma)) {
            return null;
        }
        try {
            return Long.parseLong(payload);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private SecretKeySpec claveAes() throws Exception {
        return new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(clave.getBytes(StandardCharsets.UTF_8)), "AES");
    }

    private static String hmac(String payload, String clave) {
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
