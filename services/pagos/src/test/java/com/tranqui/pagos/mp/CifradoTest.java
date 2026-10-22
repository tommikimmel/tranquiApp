package com.tranqui.pagos.mp;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Compatibilidad con el monolito: un token cifrado por EncryptionUtil y un state o token de canal
 * firmado por MercadoPagoOAuthService se leen igual en el servicio, en el formato
 * actual y en el de antes del hotfix de la clave.
 */
class CifradoTest {

    private static final String CLAVE = "clave-de-prueba-de-32-caracteres";
    private static final String CLAVE_LEGADA = "masterdecryptionkey32charspart12";

    private static String hmac(String clave, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(clave.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void leeLoQueCifraElMonolito() throws Exception {
        // Mismo formato que EncryptionUtil del monolito: v2: + base64(IV de 12 bytes + AES-256-GCM).
        byte[] iv = new byte[12];
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                .digest(CLAVE.getBytes(StandardCharsets.UTF_8)), "AES"), new GCMParameterSpec(128, iv));
        byte[] cifrado = cipher.doFinal("APP_USR-token".getBytes(StandardCharsets.UTF_8));
        String delMonolito = "v2:" + Base64.getEncoder().encodeToString(
                ByteBuffer.allocate(12 + cifrado.length).put(iv).put(cifrado).array());

        Cifrado servicio = new Cifrado(CLAVE);

        assertThat(servicio.descifrar(delMonolito)).isEqualTo("APP_USR-token");
        assertThat(servicio.cifrar("APP_USR-token")).startsWith("v2:");
        assertThat(servicio.descifrar(servicio.cifrar("APP_USR-token"))).isEqualTo("APP_USR-token");
    }

    @Test
    void leeLoCifradoAntesDelHotfix() throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(CLAVE_LEGADA.getBytes(StandardCharsets.UTF_8), "AES"));
        String viejo = Base64.getEncoder().encodeToString(cipher.doFinal("APP_USR-viejo".getBytes(StandardCharsets.UTF_8)));

        assertThat(new Cifrado(CLAVE).descifrar(viejo)).isEqualTo("APP_USR-viejo");
    }

    @Test
    void verificaLaFirmaDelMonolitoYRechazaLaAdulterada() throws Exception {
        String delMonolito = "16." + hmac(CLAVE, "16");

        Cifrado cifrado = new Cifrado(CLAVE);

        assertThat(cifrado.verificar(delMonolito)).isEqualTo(16L);
        assertThat(cifrado.firmar(16L)).isEqualTo(delMonolito);
        assertThat(cifrado.verificar("17" + delMonolito.substring(2))).isNull();
        assertThat(cifrado.verificar(null)).isNull();
        assertThat(cifrado.verificar("sin-punto")).isNull();
    }

    @Test
    void aceptaLosCanalesFirmadosConLaClaveAnterior() throws Exception {
        assertThat(new Cifrado(CLAVE).verificar("16." + hmac(CLAVE_LEGADA, "16"))).isEqualTo(16L);
    }

    @Test
    void noArrancaSinClaveNiConUnaPublicada() {
        assertThatThrownBy(() -> new Cifrado("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new Cifrado(CLAVE_LEGADA)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new Cifrado("corta")).isInstanceOf(IllegalStateException.class);
    }
}
