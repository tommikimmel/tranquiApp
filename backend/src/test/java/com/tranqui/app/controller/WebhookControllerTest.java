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
}
