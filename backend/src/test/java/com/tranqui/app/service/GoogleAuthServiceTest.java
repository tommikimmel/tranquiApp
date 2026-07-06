package com.tranqui.app.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class GoogleAuthServiceTest {

    @Autowired
    private GoogleAuthService googleAuthService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Usuario existingUser;

    @BeforeEach
    void setUp() {
        existingUser = Usuario.builder()
                .nombre("Marta Test")
                .email("marta.test@gmail.com")
                .rol(Rol.PACIENTE)
                .build();
        usuarioRepository.save(existingUser);
    }

    @AfterEach
    void tearDown() {
        usuarioRepository.delete(existingUser);
        usuarioRepository.findByEmail("new.user@gmail.com").ifPresent(usuarioRepository::delete);
    }

    @Test
    void testVerifyGoogleTokenMock() {
        GoogleIdToken.Payload payload = googleAuthService.verifyGoogleToken("mock-token");
        assertNotNull(payload);
        assertEquals("paula@tranqui.com", payload.getEmail());
    }

    @Test
    void testVerifyGoogleTokenMockWithEmail() {
        GoogleIdToken.Payload payload = googleAuthService.verifyGoogleToken("mock-marta.test@gmail.com");
        assertNotNull(payload);
        assertEquals("marta.test@gmail.com", payload.getEmail());
        assertEquals("Marta Test", payload.get("name"));
    }

    @Test
    void testVerifyGoogleTokenMockNewEmail() {
        GoogleIdToken.Payload payload = googleAuthService.verifyGoogleToken("mock-new.user@gmail.com");
        assertNotNull(payload);
        assertEquals("new.user@gmail.com", payload.getEmail());
        assertEquals("new.user", payload.get("name"));
    }

    @Test
    void testGetOrCreateUsuarioExisting() {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail("marta.test@gmail.com");
        payload.set("name", "Marta Test");

        Usuario user = googleAuthService.getOrCreateUsuario(payload);
        assertNotNull(user);
        assertEquals(existingUser.getId(), user.getId());
    }

    @Test
    void testGetOrCreateUsuarioNew() {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail("new.user@gmail.com");

        Usuario user = googleAuthService.getOrCreateUsuario(payload);
        assertNotNull(user);
        assertEquals("new.user@gmail.com", user.getEmail());
        assertEquals("new.user", user.getNombre());
    }
}
