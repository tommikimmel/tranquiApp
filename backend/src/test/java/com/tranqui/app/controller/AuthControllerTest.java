package com.tranqui.app.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.GoogleLoginDto;
import com.tranqui.app.service.GoogleAuthService;
import com.tranqui.app.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GoogleAuthService googleAuthService;

    @MockBean
    private JwtService jwtService;

    @Test
    void loginWithGoogle_whenTokenIsValid_shouldReturnUserAndSetCookie() throws Exception {
        GoogleIdToken.Payload mockPayload = new GoogleIdToken.Payload();
        mockPayload.setEmail("test@gmail.com");
        mockPayload.set("name", "Test User");

        Usuario mockUsuario = Usuario.builder()
                .id(1L)
                .nombre("Test User")
                .email("test@gmail.com")
                .rol(Rol.PACIENTE)
                .build();

        when(googleAuthService.verifyGoogleToken("valid-token")).thenReturn(mockPayload);
        when(googleAuthService.getOrCreateUsuario(mockPayload)).thenReturn(mockUsuario);
        when(jwtService.generateToken(mockUsuario)).thenReturn("mocked-jwt");

        GoogleLoginDto loginDto = new GoogleLoginDto("valid-token");

        mockMvc.perform(post("/api/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Test User"))
                .andExpect(jsonPath("$.email").value("test@gmail.com"))
                .andExpect(jsonPath("$.rol").value("PACIENTE"))
                .andExpect(header().exists("Set-Cookie"));
    }

    @Test
    void loginWithGoogle_whenTokenIsInvalid_shouldReturnUnauthorized() throws Exception {
        when(googleAuthService.verifyGoogleToken("invalid-token")).thenReturn(null);

        GoogleLoginDto loginDto = new GoogleLoginDto("invalid-token");

        mockMvc.perform(post("/api/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isUnauthorized());
    }
}
