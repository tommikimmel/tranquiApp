package com.tranqui.app.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionUtilTest {

    private EncryptionUtil encryptionUtil;

    @BeforeEach
    void setUp() {
        encryptionUtil = conClave("una-clave-de-prueba-de-al-menos-32-bytes");
    }

    private static EncryptionUtil conClave(String clave) {
        EncryptionUtil util = new EncryptionUtil();
        ReflectionTestUtils.setField(util, "secretKey", clave);
        return util;
    }

    /** Cómo cifraba EncryptionUtil hasta la v1.1.0 (AES/ECB con la clave pública). */
    private static String cifrarComoAntes(String valor) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(
                "masterdecryptionkey32charspart12".getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        return Base64.getEncoder().encodeToString(cipher.doFinal(valor.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void whenEncryptAndDecrypt_shouldReturnOriginalValue() throws Exception {
        String original = "test-access-token-12345";
        String encrypted = encryptionUtil.encrypt(original);
        assertTrue(encrypted.startsWith("v2:"));
        assertNotEquals(original, encrypted);

        assertEquals(original, encryptionUtil.decrypt(encrypted));
    }

    @Test
    void cadaCifradoUsaUnIvDistinto() throws Exception {
        assertNotEquals(encryptionUtil.encrypt("mismo-token"), encryptionUtil.encrypt("mismo-token"));
    }

    @Test
    void conOtraClaveNoDescifra() throws Exception {
        String encrypted = encryptionUtil.encrypt("token");

        assertThrows(Exception.class, () -> conClave("otra-clave-de-prueba-de-al-menos-32-bytes").decrypt(encrypted));
    }

    @Test
    void leeLosValoresCifradosAntesDeLaV2() throws Exception {
        String viejo = cifrarComoAntes("APP_USR-token-de-mercado-pago");

        assertTrue(EncryptionUtil.esLegado(viejo));
        assertEquals("APP_USR-token-de-mercado-pago", encryptionUtil.decrypt(viejo));
    }

    @Test
    void sinClave_noArranca() {
        assertThrows(IllegalStateException.class, conClave("")::validarClave);
    }

    @Test
    void conUnaClavePublicadaEnElRepo_noArranca() {
        assertThrows(IllegalStateException.class, conClave("masterdecryptionkey32charspart12")::validarClave);
        assertThrows(IllegalStateException.class,
                conClave("cambiar-por-una-clave-aleatoria-de-al-menos-32-bytes")::validarClave);
    }

    @Test
    void conUnaClaveCorta_noArranca() {
        assertThrows(IllegalStateException.class, conClave("corta")::validarClave);
    }

    @Test
    void conUnaClaveValida_arranca() {
        assertDoesNotThrow(encryptionUtil::validarClave);
    }
}
