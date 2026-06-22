package com.tranqui.app.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

    @Mock
    private GoogleIdTokenVerifier verifier;

    @InjectMocks
    private GoogleAuthService googleAuthService;

    @Test
    void shouldReturnPayloadWhenTokenIsValid() throws Exception {
        GoogleIdToken mockToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload mockPayload = new GoogleIdToken.Payload();
        mockPayload.setEmail("paciente@gmail.com");
        mockPayload.set("name", "Juan Pérez");

        when(verifier.verify("valido-google-token")).thenReturn(mockToken);
        when(mockToken.getPayload()).thenReturn(mockPayload);

        GoogleIdToken.Payload payload = googleAuthService.verifyGoogleToken("valido-google-token");
        assertNotNull(payload);
        assertEquals("paciente@gmail.com", payload.getEmail());
    }
}
