package com.tranqui.app.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class MercadoPagoWebhookValidatorTest {

    private MercadoPagoWebhookValidator buildValidator(boolean enabled, String secret) {
        MercadoPagoWebhookValidator validator = new MercadoPagoWebhookValidator();
        ReflectionTestUtils.setField(validator, "isEnabled", enabled);
        ReflectionTestUtils.setField(validator, "webhookSecret", secret);
        return validator;
    }

    private String hmac(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    // ── Legacy/dev behavior (mercadopago.enabled=false) ─────────────────

    @Test
    void whenDisabled_shouldRejectNullOrExplicitlyInvalidSignatures() {
        MercadoPagoWebhookValidator validator = buildValidator(false, "");

        assertFalse(validator.isValid(null, null, null));
        assertFalse(validator.isValid("invalid-signature", null, null));
        assertFalse(validator.isValid("this-is-fake", null, null));
    }

    @Test
    void whenDisabled_shouldAcceptAnyOtherNonNullSignature() {
        MercadoPagoWebhookValidator validator = buildValidator(false, "");

        assertTrue(validator.isValid("valid-signature-12345", null, null));
        assertTrue(validator.isValid("test-signature", null, null));
    }

    // ── Real validation (mercadopago.enabled=true) ──────────────────────

    @Test
    void whenEnabled_shouldAcceptACorrectlySignedRequest() throws Exception {
        String secret = "whsec_test_secret";
        MercadoPagoWebhookValidator validator = buildValidator(true, secret);

        String ts = "1704908010";
        String dataId = "123456";
        String requestId = "req-abc-1";
        String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";";
        String v1 = hmac(manifest, secret);
        String xSignature = "ts=" + ts + ",v1=" + v1;

        assertTrue(validator.isValid(xSignature, requestId, dataId));
    }

    @Test
    void whenEnabled_shouldRejectWhenSignatureDoesNotMatch() throws Exception {
        MercadoPagoWebhookValidator validator = buildValidator(true, "whsec_test_secret");

        String xSignature = "ts=1704908010,v1=deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef";

        assertFalse(validator.isValid(xSignature, "req-abc-1", "123456"));
    }

    @Test
    void whenEnabled_shouldRejectWhenDataIdIsTamperedAfterSigning() throws Exception {
        String secret = "whsec_test_secret";
        MercadoPagoWebhookValidator validator = buildValidator(true, secret);

        String ts = "1704908010";
        String requestId = "req-abc-1";
        String manifest = "id:123456;request-id:" + requestId + ";ts:" + ts + ";";
        String v1 = hmac(manifest, secret);
        String xSignature = "ts=" + ts + ",v1=" + v1;

        // Signature was computed for data.id=123456, but the request claims a different id
        assertFalse(validator.isValid(xSignature, requestId, "999999"));
    }

    @Test
    void whenEnabled_shouldRejectWhenRequiredHeadersAreMissing() {
        MercadoPagoWebhookValidator validator = buildValidator(true, "whsec_test_secret");

        assertFalse(validator.isValid(null, "req-abc-1", "123456"));
        assertFalse(validator.isValid("ts=1,v1=abc", null, "123456"));
        assertFalse(validator.isValid("ts=1,v1=abc", "req-abc-1", null));
    }

    @Test
    void whenEnabled_shouldRejectWhenWebhookSecretIsNotConfigured() {
        MercadoPagoWebhookValidator validator = buildValidator(true, "");

        assertFalse(validator.isValid("ts=1704908010,v1=whatever", "req-abc-1", "123456"));
    }
}
