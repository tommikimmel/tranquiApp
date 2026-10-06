package com.tranqui.app.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.GoogleLoginDto;
import com.tranqui.app.model.dto.LoginRequestDto;
import com.tranqui.app.model.dto.RegisterRequestDto;
import com.tranqui.app.repository.PlanRepository;
import com.tranqui.app.repository.SubscriptionRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.GoogleAuthService;
import com.tranqui.app.service.JwtService;
import com.tranqui.app.service.ResendEmailService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
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
    @Mock private PlanRepository planRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private JwtService jwtService;
    @Mock private org.springframework.core.env.Environment env;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ResendEmailService resendEmailService;
    @Mock private com.tranqui.app.service.AccountService accountService;

    @InjectMocks
    private AuthController authController;

    private void setDev(boolean dev) {
        when(env.getActiveProfiles()).thenReturn(dev ? new String[]{"dev"} : new String[]{});
    }

    // ── register ─────────────────────────────────────────────────────

    @Test
    void register_shouldRejectWhenEmailAlreadyExists() {
        // Needs a password that passes the complexity check first (>=8 chars, upper, lower,
        // digit) — otherwise register() returns 400 at that earlier validation and never reaches
        // the findByEmail(...) stub below, which is exactly what made this test fail with
        // UnnecessaryStubbingException before this fix.
        when(usuarioRepository.findByEmail("ya@existe.com")).thenReturn(Optional.of(Usuario.builder().build()));

        RegisterRequestDto dto = RegisterRequestDto.builder().email("ya@existe.com").password("Secret123").build();
        ResponseEntity<?> response = authController.register(dto);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(usuarioRepository, never()).save(any());
    }

    // Defense in depth against the race where two concurrent /register requests for the same
    // email both pass the findByEmail check before either commits: findByEmail here returns
    // empty (this request "won" the race check), but save() still throws because the DB's UNIQUE
    // constraint caught the actual duplicate insert. register() must catch that and respond the
    // same way as the normal duplicate-email path instead of letting a 500 leak through.
    @Test
    void register_shouldReturnBadRequestWhenSaveThrowsDataIntegrityViolationOnEmailRace() {
        when(usuarioRepository.findByEmail("carrera@mail.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(usuarioRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        RegisterRequestDto dto = RegisterRequestDto.builder()
                .email("carrera@mail.com").password("Secret123").rol(Rol.PACIENTE).nombre("Ana")
                .aceptaTerminos(true).build();

        ResponseEntity<?> response = authController.register(dto);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("El email ya está registrado", response.getBody());
    }

    @Test
    void register_shouldSaveWithVerificadoAdminTrueForPaciente() {
        when(usuarioRepository.findByEmail("nuevo@mail.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Secret123")).thenReturn("encoded-secret");
        when(usuarioRepository.save(any())).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        RegisterRequestDto dto = RegisterRequestDto.builder()
                .email("nuevo@mail.com").password("Secret123").rol(Rol.PACIENTE).nombre("Ana")
                .aceptaTerminos(true).build();

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
        // Subscription-init block for PSIQUIATRA calls planRepository.findByCode(...) — the
        // Mockito default (Optional.empty()) already makes register() skip creating a
        // Subscription, so no explicit stub is required here, but this documents that path.
        when(planRepository.findByCode(any())).thenReturn(Optional.empty());

        RegisterRequestDto dto = RegisterRequestDto.builder()
                .email("doc@mail.com").password("Secret123").rol(Rol.PSIQUIATRA).nombre("Dr X")
                .aceptaTerminos(true).build();

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
        // login() rejects unverified emails with 403 (Rol != ADMIN) — the user under test must
        // be verified for the happy-path cookie assertions below to be reachable at all.
        Usuario usuario = Usuario.builder().id(1L).nombre("Ana").email("ana@mail.com").rol(Rol.PACIENTE).password("hashed").emailVerificado(true).build();
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
        Usuario usuario = Usuario.builder().id(1L).nombre("Ana").email("ana@mail.com").rol(Rol.PACIENTE).password("hashed").emailVerificado(true).build();
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

    // ── Email Verification & Password Reset ──────────────────────────

    @Test
    void verifyEmail_branches() {
        com.tranqui.app.model.dto.VerifyEmailDto dto = new com.tranqui.app.model.dto.VerifyEmailDto();
        dto.setEmail("user@mail.com");
        dto.setCodigo("123456");

        // Not found
        when(usuarioRepository.findByEmail("user@mail.com")).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, authController.verifyEmail(dto).getStatusCode());

        Usuario u = Usuario.builder().email("user@mail.com").codigoVerificacion("123456").build();
        when(usuarioRepository.findByEmail("user@mail.com")).thenReturn(Optional.of(u));

        // Already verified
        u.setEmailVerificado(true);
        assertEquals(HttpStatus.OK, authController.verifyEmail(dto).getStatusCode());

        // Wrong code
        u.setEmailVerificado(false);
        u.setCodigoVerificacion("999999");
        assertEquals(HttpStatus.BAD_REQUEST, authController.verifyEmail(dto).getStatusCode());

        // Expired
        u.setCodigoVerificacion("123456");
        u.setCodigoVerificacionExpiresAt(java.time.LocalDateTime.now().minusMinutes(1));
        assertEquals(HttpStatus.BAD_REQUEST, authController.verifyEmail(dto).getStatusCode());

        // Success
        u.setCodigoVerificacionExpiresAt(java.time.LocalDateTime.now().plusMinutes(10));
        assertEquals(HttpStatus.OK, authController.verifyEmail(dto).getStatusCode());
        assertTrue(u.getEmailVerificado());
    }

    @Test
    void resendCode_branches() {
        com.tranqui.app.model.dto.VerifyEmailDto dto = new com.tranqui.app.model.dto.VerifyEmailDto();
        dto.setEmail("user@mail.com");

        when(usuarioRepository.findByEmail("user@mail.com")).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, authController.resendCode(dto).getStatusCode());

        Usuario u = Usuario.builder().email("user@mail.com").emailVerificado(true).build();
        when(usuarioRepository.findByEmail("user@mail.com")).thenReturn(Optional.of(u));
        assertEquals(HttpStatus.OK, authController.resendCode(dto).getStatusCode());

        u.setEmailVerificado(false);
        assertEquals(HttpStatus.OK, authController.resendCode(dto).getStatusCode());
        verify(resendEmailService).enviarCodigoVerificacion(eq("user@mail.com"), any(), anyString());
    }

    @Test
    void forgotPassword_and_resetPassword() {
        com.tranqui.app.model.dto.ForgotPasswordDto fpDto = new com.tranqui.app.model.dto.ForgotPasswordDto();
        fpDto.setEmail("user@mail.com");

        // Not found returns 200 anyway for enumeration protection
        when(usuarioRepository.findByEmail("user@mail.com")).thenReturn(Optional.empty());
        assertEquals(HttpStatus.OK, authController.forgotPassword(fpDto).getStatusCode());

        Usuario u = Usuario.builder().email("user@mail.com").build();
        when(usuarioRepository.findByEmail("user@mail.com")).thenReturn(Optional.of(u));
        assertEquals(HttpStatus.OK, authController.forgotPassword(fpDto).getStatusCode());

        // Reset password
        com.tranqui.app.model.dto.ResetPasswordDto rpDto = new com.tranqui.app.model.dto.ResetPasswordDto();
        rpDto.setEmail("user@mail.com");
        rpDto.setCodigo("123456");
        rpDto.setNewPassword("weak");
        assertEquals(HttpStatus.BAD_REQUEST, authController.resetPassword(rpDto).getStatusCode());

        rpDto.setNewPassword("StrongPass1");
        u.setResetPasswordCode("123456");
        u.setResetPasswordExpiresAt(java.time.LocalDateTime.now().plusMinutes(10));
        when(passwordEncoder.encode("StrongPass1")).thenReturn("hashedPass");
        assertEquals(HttpStatus.OK, authController.resetPassword(rpDto).getStatusCode());
    }

    // ── Complete Profile, Terms, Account Settings ────────────────────

    @Test
    void completeProfile_branches() {
        User principal = new User("user@mail.com", "x", Collections.emptyList());
        com.tranqui.app.model.dto.CompleteProfileDto dto = com.tranqui.app.model.dto.CompleteProfileDto.builder()
                .nombre("Juan")
                .apellido("Perez")
                .sexo("M")
                .fechaNacimiento(java.time.LocalDate.of(1990, 1, 1))
                .tipoDocumento("DNI")
                .numeroDocumento(12345678)
                .telefono("1122334455")
                .build();

        // Unauthorized
        assertEquals(HttpStatus.UNAUTHORIZED, authController.completeProfile(dto, null).getStatusCode());

        // Bad request on missing fields
        com.tranqui.app.model.dto.CompleteProfileDto emptyDto = com.tranqui.app.model.dto.CompleteProfileDto.builder().build();
        assertEquals(HttpStatus.BAD_REQUEST, authController.completeProfile(emptyDto, principal).getStatusCode());

        // Success
        Usuario u = Usuario.builder().email("user@mail.com").build();
        when(usuarioRepository.findByEmail("user@mail.com")).thenReturn(Optional.of(u));
        ResponseEntity<?> resp = authController.completeProfile(dto, principal);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(u.isPerfilCompleto());
    }

    @Test
    void aceptarTerminos_and_miCuentaEndpoints() {
        User principal = new User("user@mail.com", "x", Collections.emptyList());
        Usuario u = Usuario.builder().email("user@mail.com").build();

        // Aceptar terminos
        assertEquals(HttpStatus.UNAUTHORIZED, authController.aceptarTerminos(null).getStatusCode());
        setDev(false);
        when(usuarioRepository.findByEmail("user@mail.com")).thenReturn(Optional.of(u));
        assertEquals(HttpStatus.OK, authController.aceptarTerminos(principal).getStatusCode());

        // Mi cuenta
        assertEquals(HttpStatus.UNAUTHORIZED, authController.obtenerMiCuenta(null).getStatusCode());
        assertEquals(HttpStatus.OK, authController.obtenerMiCuenta(principal).getStatusCode());

        // Actualizar mi cuenta
        com.tranqui.app.model.dto.MiCuentaDto cDto = new com.tranqui.app.model.dto.MiCuentaDto();
        assertEquals(HttpStatus.UNAUTHORIZED, authController.actualizarMiCuenta(cDto, null).getStatusCode());
        assertEquals(HttpStatus.OK, authController.actualizarMiCuenta(cDto, principal).getStatusCode());

        // Actualizar notificaciones
        com.tranqui.app.model.dto.PreferenciasNotificacionDto nDto = new com.tranqui.app.model.dto.PreferenciasNotificacionDto();
        assertEquals(HttpStatus.UNAUTHORIZED, authController.actualizarPreferenciasNotificacion(nDto, null).getStatusCode());
        assertEquals(HttpStatus.OK, authController.actualizarPreferenciasNotificacion(nDto, principal).getStatusCode());

        // Cambiar password
        com.tranqui.app.model.dto.CambiarPasswordDto pDto = new com.tranqui.app.model.dto.CambiarPasswordDto();
        pDto.setCurrentPassword("old");
        pDto.setNewPassword("new");
        assertEquals(HttpStatus.UNAUTHORIZED, authController.cambiarPassword(pDto, null).getStatusCode());
        assertEquals(HttpStatus.OK, authController.cambiarPassword(pDto, principal).getStatusCode());

        doThrow(new IllegalArgumentException("Error pass")).when(accountService).cambiarPassword(any(), any(), any());
        assertEquals(HttpStatus.BAD_REQUEST, authController.cambiarPassword(pDto, principal).getStatusCode());

        // Set new password
        com.tranqui.app.model.dto.SetNewPasswordDto npDto = new com.tranqui.app.model.dto.SetNewPasswordDto();
        npDto.setNewPassword("ValidPass1");
        assertEquals(HttpStatus.UNAUTHORIZED, authController.setNewPassword(npDto, null).getStatusCode());
        assertEquals(HttpStatus.OK, authController.setNewPassword(npDto, principal).getStatusCode());

        // Eliminar cuenta
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertEquals(HttpStatus.UNAUTHORIZED, authController.eliminarCuenta(null, null, res).getStatusCode());
        assertEquals(HttpStatus.OK, authController.eliminarCuenta(null, principal, res).getStatusCode());
    }
}
