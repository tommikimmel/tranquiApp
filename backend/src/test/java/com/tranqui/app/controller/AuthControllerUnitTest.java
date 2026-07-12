package com.tranqui.app.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.GoogleLoginDto;
import com.tranqui.app.model.dto.LoginRequestDto;
import com.tranqui.app.model.dto.RegisterRequestDto;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.GoogleAuthService;
import com.tranqui.app.service.JwtService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerUnitTest {

    @Mock private GoogleAuthService googleAuthService;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private JwtService jwtService;
    @Mock private org.springframework.core.env.Environment env;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthController authController;

    private void setDev(boolean dev) {
        when(env.getActiveProfiles()).thenReturn(dev ? new String[]{"dev"} : new String[]{});
    }

    // ── register ─────────────────────────────────────────────────────

    @Test
    void register_shouldRejectWhenEmailAlreadyExists() {
        when(usuarioRepository.findByEmail("ya@existe.com")).thenReturn(Optional.of(Usuario.builder().build()));

        RegisterRequestDto dto = RegisterRequestDto.builder().email("ya@existe.com").build();
        ResponseEntity<?> response = authController.register(dto);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void register_shouldSaveWithVerificadoAdminTrueForPaciente() {
        when(usuarioRepository.findByEmail("nuevo@mail.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret")).thenReturn("encoded-secret");
        when(usuarioRepository.save(any())).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        RegisterRequestDto dto = RegisterRequestDto.builder()
                .email("nuevo@mail.com").password("secret").rol(Rol.PACIENTE).nombre("Ana").build();

        ResponseEntity<?> response = authController.register(dto);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        org.mockito.ArgumentCaptor<Usuario> captor = org.mockito.ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertEquals(Boolean.TRUE, captor.getValue().getVerificadoAdmin());
        assertEquals("encoded-secret", captor.getValue().getPassword());
    }

    @Test
    void register_shouldSaveWithVerificadoAdminNullForPsiquiatra() {
        when(usuarioRepository.findByEmail("doc@mail.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("x");
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RegisterRequestDto dto = RegisterRequestDto.builder()
                .email("doc@mail.com").password("secret").rol(Rol.PSIQUIATRA).nombre("Dr X").build();

        authController.register(dto);

        org.mockito.ArgumentCaptor<Usuario> captor = org.mockito.ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertNull(captor.getValue().getVerificadoAdmin());
    }

    // ── login ────────────────────────────────────────────────────────

    @Test
    void login_shouldRejectWhenUserNotFound() {
        when(usuarioRepository.findByEmail("x@mail.com")).thenReturn(Optional.empty());

        LoginRequestDto dto = new LoginRequestDto(); dto.setEmail("x@mail.com"); dto.setPassword("pw");
        ResponseEntity<?> response = authController.login(dto, new MockHttpServletResponse());

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void login_shouldRejectWhenPasswordIsNull() {
        Usuario usuario = Usuario.builder().email("x@mail.com").password(null).build();
        when(usuarioRepository.findByEmail("x@mail.com")).thenReturn(Optional.of(usuario));

        LoginRequestDto dto = new LoginRequestDto(); dto.setEmail("x@mail.com"); dto.setPassword("pw");
        ResponseEntity<?> response = authController.login(dto, new MockHttpServletResponse());

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void login_shouldRejectWhenPasswordDoesNotMatch() {
        Usuario usuario = Usuario.builder().email("x@mail.com").password("hashed").build();
        when(usuarioRepository.findByEmail("x@mail.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        LoginRequestDto dto = new LoginRequestDto(); dto.setEmail("x@mail.com"); dto.setPassword("wrong");
        ResponseEntity<?> response = authController.login(dto, new MockHttpServletResponse());

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void login_shouldSetSecureCookieInProdWithRealClientId() {
        ReflectionTestUtils.setField(authController, "clientId", "real-client-id");
        setDev(false);
        Usuario usuario = Usuario.builder().id(1L).nombre("Ana").email("ana@mail.com").rol(Rol.PACIENTE).password("hashed").build();
        when(usuarioRepository.findByEmail("ana@mail.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);
        when(jwtService.generateToken(usuario)).thenReturn("jwt-token");

        LoginRequestDto dto = new LoginRequestDto(); dto.setEmail("ana@mail.com"); dto.setPassword("secret");
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        ResponseEntity<?> response = authController.login(dto, httpResponse);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String cookie = httpResponse.getHeader("Set-Cookie");
        assertNotNull(cookie);
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=Strict"));
    }

    @Test
    void login_shouldUseLaxCookieInDevMode() {
        ReflectionTestUtils.setField(authController, "clientId", "real-client-id");
        setDev(true);
        Usuario usuario = Usuario.builder().id(1L).nombre("Ana").email("ana@mail.com").rol(Rol.PACIENTE).password("hashed").build();
        when(usuarioRepository.findByEmail("ana@mail.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);
        when(jwtService.generateToken(usuario)).thenReturn("jwt-token");

        LoginRequestDto dto = new LoginRequestDto(); dto.setEmail("ana@mail.com"); dto.setPassword("secret");
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        authController.login(dto, httpResponse);

        String cookie = httpResponse.getHeader("Set-Cookie");
        assertTrue(cookie.contains("SameSite=Lax"));
        assertFalse(cookie.contains("Secure"));
    }

    // ── loginWithGoogle ──────────────────────────────────────────────

    @Test
    void loginWithGoogle_shouldUseLaxCookieForMockToken() {
        ReflectionTestUtils.setField(authController, "clientId", "real-client-id");
        setDev(false);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail("g@mail.com");
        Usuario usuario = Usuario.builder().id(1L).nombre("G").email("g@mail.com").rol(Rol.PACIENTE).build();

        GoogleLoginDto dto = new GoogleLoginDto("mock-abc");
        when(googleAuthService.verifyGoogleToken("mock-abc")).thenReturn(payload);
        when(googleAuthService.getOrCreateUsuario(payload)).thenReturn(usuario);
        when(jwtService.generateToken(usuario)).thenReturn("jwt-token");

        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        ResponseEntity<?> response = authController.loginWithGoogle(dto, httpResponse);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(httpResponse.getHeader("Set-Cookie").contains("SameSite=Lax"));
    }

    @Test
    void loginWithGoogle_shouldRejectInvalidToken() {
        when(googleAuthService.verifyGoogleToken("bad")).thenReturn(null);

        ResponseEntity<?> response = authController.loginWithGoogle(new GoogleLoginDto("bad"), new MockHttpServletResponse());

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── getMe ────────────────────────────────────────────────────────

    @Test
    void getMe_shouldReturnUnauthorizedWhenPrincipalIsNull() {
        ResponseEntity<?> response = authController.getMe(null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void getMe_shouldThrowWhenUserNotFoundInDb() {
        User principal = new User("ghost@mail.com", "x", Collections.emptyList());
        when(usuarioRepository.findByEmail("ghost@mail.com")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> authController.getMe(principal));
    }

    @Test
    void getMe_shouldReturnUserWhenAuthenticated() {
        Usuario usuario = Usuario.builder().id(1L).nombre("Ana").email("ana@mail.com").rol(Rol.PACIENTE).build();
        User principal = new User("ana@mail.com", "x", Collections.emptyList());
        when(usuarioRepository.findByEmail("ana@mail.com")).thenReturn(Optional.of(usuario));

        ResponseEntity<?> response = authController.getMe(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // ── logout ───────────────────────────────────────────────────────

    @Test
    void logout_shouldClearCookieWithSecureFlagsInProd() {
        ReflectionTestUtils.setField(authController, "clientId", "real-client-id");
        setDev(false);
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        ResponseEntity<?> response = authController.logout(httpResponse);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String cookie = httpResponse.getHeader("Set-Cookie");
        assertTrue(cookie.contains("Max-Age=0"));
        assertTrue(cookie.contains("Secure"));
    }

    @Test
    void logout_shouldUseLaxCookieInDevMode() {
        setDev(true);
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        authController.logout(httpResponse);

        assertTrue(httpResponse.getHeader("Set-Cookie").contains("SameSite=Lax"));
    }
}
