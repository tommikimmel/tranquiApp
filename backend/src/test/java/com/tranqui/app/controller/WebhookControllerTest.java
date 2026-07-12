package com.tranqui.app.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void whenWebhookHasInvalidSignature_thenBadRequest() throws Exception {
        mockMvc.perform(post("/api/payments/webhook")
                .header("x-signature", "invalid-signature")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": 1234}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void whenWebhookHasValidSignature_thenOk() throws Exception {
        mockMvc.perform(post("/api/payments/webhook")
                .header("x-signature", "valid-signature-12345")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": 1234}"))
                .andExpect(status().isOk());
    }

    @Test
    void whenExternalReferenceIsNumericButTurnoDoesNotExist_thenStillOk() throws Exception {
        // Exercises the "turno" branch (non "doc-" prefix); the handler throws
        // EntityNotFoundException for a non-existent turno, which the controller must swallow.
        mockMvc.perform(post("/api/payments/webhook")
                .header("x-signature", "valid-signature-12345")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"external_reference\": \"999999999\", \"transaction_id\": \"tx-1\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void whenExternalReferenceIsNotNumeric_thenLogsAndStillOk() throws Exception {
        mockMvc.perform(post("/api/payments/webhook")
                .header("x-signature", "valid-signature-12345")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"external_reference\": \"not-a-number\"}"))
                .andExpect(status().isOk());
    }
}
