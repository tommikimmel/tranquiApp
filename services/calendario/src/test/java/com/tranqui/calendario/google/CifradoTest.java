package com.tranqui.calendario.google;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compatibilidad con el monolito: un token cifrado por EncryptionUtil y un state o token de canal
 * firmado por GoogleCalendarOAuthService/WatchService se leen igual en el servicio.
 */
class CifradoTest {

    private static final String CLAVE = "clave-de-prueba-de-32-caracteres";

    @Test
    void leeLoQueCifraElMonolito() throws Exception {
        // Mismo algoritmo que EncryptionUtil del monolito.
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(CLAVE.getBytes(StandardCharsets.UTF_8), "AES"));
        String delMonolito = Base64.getEncoder().encodeToString(cipher.doFinal("ya29.token".getBytes(StandardCharsets.UTF_8)));

        Cifrado cifrado = new Cifrado(CLAVE);

        assertThat(cifrado.descifrar(delMonolito)).isEqualTo("ya29.token");
        assertThat(cifrado.cifrar("ya29.token")).isEqualTo(delMonolito);
    }

    @Test
    void verificaLaFirmaDelMonolitoYRechazaLaAdulterada() throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(CLAVE.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String delMonolito = "16." + HexFormat.of().formatHex(mac.doFinal("16".getBytes(StandardCharsets.UTF_8)));

        Cifrado cifrado = new Cifrado(CLAVE);

        assertThat(cifrado.verificar(delMonolito)).isEqualTo(16L);
        assertThat(cifrado.firmar(16L)).isEqualTo(delMonolito);
        assertThat(cifrado.verificar("17" + delMonolito.substring(2))).isNull();
        assertThat(cifrado.verificar(null)).isNull();
        assertThat(cifrado.verificar("sin-punto")).isNull();
    }
}
