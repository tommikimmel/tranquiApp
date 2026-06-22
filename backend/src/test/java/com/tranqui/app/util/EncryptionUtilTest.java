package com.tranqui.app.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionUtilTest {

    private EncryptionUtil encryptionUtil;

    @BeforeEach
    void setUp() {
        encryptionUtil = new EncryptionUtil();
        ReflectionTestUtils.setField(encryptionUtil, "secretKey", "masterdecryptionkey32charspart12"); // 32 bytes key
    }

    @Test
    void whenEncryptAndDecrypt_shouldReturnOriginalValue() throws Exception {
        String original = "test-access-token-12345";
        String encrypted = encryptionUtil.encrypt(original);
        assertNotNull(encrypted);
        assertNotEquals(original, encrypted);

        String decrypted = encryptionUtil.decrypt(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void whenDecryptWithCorruptKey_shouldThrowException() {
        EncryptionUtil corruptUtil = new EncryptionUtil();
        // Set invalid key size to trigger exception
        ReflectionTestUtils.setField(corruptUtil, "secretKey", "shortkey");

        assertThrows(Exception.class, () -> {
            corruptUtil.decrypt("some-encrypted-string");
        });
    }
}
