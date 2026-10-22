package com.tranqui.app.util;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/**
 * Cifra los tokens de Mercado Pago y de Google que se guardan en la base.
 *
 * <p>Formato actual ({@code v2:}): AES-256-GCM con IV aleatorio y la clave derivada (SHA-256) de
 * {@code ENCRYPTION_KEY}. Hasta la v1.1.0 los tokens se cifraban con AES/ECB y una clave escrita en el
 * código (el {@code .env} nunca llegaba a la aplicación), pública porque el repo es público: esos
 * valores sin prefijo se siguen pudiendo leer con {@link #decrypt} y {@code RecifradoTokensLegados}
 * los vuelve a cifrar al arrancar.
 */
@Component
public class EncryptionUtil {

    static final String PREFIJO_V2 = "v2:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String CLAVE_LEGADA = "masterdecryptionkey32charspart12";
    // Valores que están en el repo (público): no pueden ser la clave de verdad.
    private static final Set<String> CLAVES_PUBLICAS = Set.of(CLAVE_LEGADA,
            "cambiar-por-una-clave-aleatoria-de-al-menos-32-bytes");

    @Value("${security.encryption.key:}")
    private String secretKey;

    private final SecureRandom random = new SecureRandom();

    @PostConstruct
    public void validarClave() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("Falta la clave de cifrado: configurar la variable ENCRYPTION_KEY.");
        }
        if (CLAVES_PUBLICAS.contains(secretKey)) {
            throw new IllegalStateException("ENCRYPTION_KEY tiene un valor publicado en el repositorio: generar una "
                    + "nueva con `openssl rand -base64 48`.");
        }
        if (secretKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("ENCRYPTION_KEY tiene que tener al menos 32 bytes.");
        }
    }

    public String encrypt(String value) throws Exception {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, claveActual(), new GCMParameterSpec(TAG_BITS, iv));
        byte[] cifrado = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] salida = ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array();
        return PREFIJO_V2 + Base64.getEncoder().encodeToString(salida);
    }

    public String decrypt(String encryptedValue) throws Exception {
        if (esLegado(encryptedValue)) {
            return descifrarLegado(encryptedValue);
        }
        byte[] entrada = Base64.getDecoder().decode(encryptedValue.substring(PREFIJO_V2.length()));
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, claveActual(), new GCMParameterSpec(TAG_BITS, entrada, 0, IV_BYTES));
        byte[] claro = cipher.doFinal(entrada, IV_BYTES, entrada.length - IV_BYTES);
        return new String(claro, StandardCharsets.UTF_8);
    }

    /** {@code true} si el valor está en el formato de antes de la v2 (cifrado con la clave pública). */
    public static boolean esLegado(String encryptedValue) {
        return encryptedValue != null && !encryptedValue.startsWith(PREFIJO_V2);
    }

    /** La clave con la que se firmaban el state del OAuth y los canales de Google hasta la v1.1.0. */
    public static String claveLegada() {
        return CLAVE_LEGADA;
    }

    private SecretKeySpec claveActual() throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(secretKey.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(hash, "AES");
    }

    private static String descifrarLegado(String encryptedValue) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(CLAVE_LEGADA.getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedValue));
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }
}
