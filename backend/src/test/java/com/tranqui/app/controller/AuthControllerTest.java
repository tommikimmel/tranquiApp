package com.tranqui.app.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.GoogleLoginDto;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.GoogleAuthService;
import com.tranqui.app.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

// Real @SpringBootTest + MockMvc: exercises AuthController through the actual HTTP layer, real
// UsuarioRepository/AccountService/PasswordEncoder against H2, with only GoogleAuthService and
// JwtService mocked out (JwtService is stubbed per-test to fake an authenticated session where
// needed — see authCookieFor). All endpoints here live under /api/auth/, which is in
// SubscriptionAccessFilter.BYPASS_PREFIXES, so the subscription paywall never applies and no
// SubscriptionService mock is required.
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.transaction.annotation.Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private GoogleAuthService googleAuthService;

    @MockBean
    private JwtService jwtService;

    private static int counter = 0;

    private Usuario crearUsuario(String emailPrefix, boolean emailVerificado) {
        Usuario u = Usuario.builder()
                .nombre("Test")
                .apellido("User")
                .email(emailPrefix + (counter++) + "@mail.com")
                .password(passwordEncoder.encode("Secret123"))
                .rol(Rol.PACIENTE)
                .emailVerificado(emailVerificado)
                .build();
        return usuarioRepository.save(u);
    }

    // Fakes an authenticated session for `usuario`. JwtService is a @MockBean here (not the real
    // bean), so JwtAuthenticationFilter's own token-extraction helpers
    // (extractTokenFromCookies/extractTokenFromHeader) also return null unless stubbed — it's
    // not enough to just stub validateToken/extractEmail and attach a cookie, the filter would
    // never even see a token to validate.
    private Cookie authCookieFor(Usuario usuario) {
        String fakeToken = "fake-jwt-" + usuario.getId();
        when(jwtService.extractTokenFromCookies(any())).thenReturn(fakeToken);
        when(jwtService.validateToken(fakeToken)).thenReturn(true);
        when(jwtService.extractEmail(fakeToken)).thenReturn(usuario.getEmail());
        return new Cookie("SESSION-TOKEN", fakeToken);
    }

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

    // ── register ─────────────────────────────────────────────────────

    @Test
    void register_withValidPacienteData_shouldPersistAndRequireVerification() throws Exception {
        Map<String, Object> body = Map.of(
                "email", "nuevo.paciente" + (counter++) + "@mail.com",
                "password", "Secret123",
                "rol", "PACIENTE",
                "nombre", "Ana",
                "aceptaTerminos", true
        );

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresVerification").value(true));

        Usuario saved = usuarioRepository.findByEmail((String) body.get("email")).orElseThrow();
        assertFalseVerified(saved);
    }

    private void assertFalseVerified(Usuario u) {
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.FALSE, u.getEmailVerificado());
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.TRUE, u.getVerificadoAdmin());
    }

    @Test
    void register_withoutAcceptingTerms_shouldReturnBadRequest() throws Exception {
        Map<String, Object> body = Map.of(
                "email", "sinterminos" + (counter++) + "@mail.com",
                "password", "Secret123",
                "rol", "PACIENTE",
                "nombre", "Ana"
        );

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ── verify-email ─────────────────────────────────────────────────

    @Test
    void verifyEmail_withCorrectCode_shouldMarkVerified() throws Exception {
        Usuario u = Usuario.builder()
                .nombre("Ana").email("verificar" + (counter++) + "@mail.com")
                .password(passwordEncoder.encode("Secret123")).rol(Rol.PACIENTE)
                .emailVerificado(false)
                .codigoVerificacion("123456")
                .codigoVerificacionExpiresAt(LocalDateTime.now().plusMinutes(15))
                .build();
        usuarioRepository.save(u);

        Map<String, String> body = Map.of("email", u.getEmail(), "codigo", "123456");

        mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        Usuario updated = usuarioRepository.findByEmail(u.getEmail()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.TRUE, updated.getEmailVerificado());
    }

    @Test
    void verifyEmail_withWrongCode_shouldReturnBadRequest() throws Exception {
        Usuario u = Usuario.builder()
                .nombre("Ana").email("verificarmal" + (counter++) + "@mail.com")
                .password(passwordEncoder.encode("Secret123")).rol(Rol.PACIENTE)
                .emailVerificado(false)
                .codigoVerificacion("123456")
                .codigoVerificacionExpiresAt(LocalDateTime.now().plusMinutes(15))
                .build();
        usuarioRepository.save(u);

        Map<String, String> body = Map.of("email", u.getEmail(), "codigo", "000000");

        mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ── resend-code ──────────────────────────────────────────────────

    @Test
    void resendCode_forUnverifiedUser_shouldReturnOk() throws Exception {
        Usuario u = Usuario.builder()
                .nombre("Ana").email("reenviar" + (counter++) + "@mail.com")
                .password(passwordEncoder.encode("Secret123")).rol(Rol.PACIENTE)
                .emailVerificado(false)
                .build();
        usuarioRepository.save(u);

        Map<String, String> body = Map.of("email", u.getEmail());

        mockMvc.perform(post("/api/auth/resend-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    void resendCode_forUnknownEmail_shouldReturnNotFound() throws Exception {
        Map<String, String> body = Map.of("email", "no-existe" + (counter++) + "@mail.com");

        mockMvc.perform(post("/api/auth/resend-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    // ── forgot-password / reset-password ────────────────────────────

    @Test
    void forgotPassword_forExistingUser_shouldSetResetCodeAndReturnGenericMessage() throws Exception {
        Usuario u = crearUsuario("olvide", true);

        Map<String, String> body = Map.of("email", u.getEmail());

        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        Usuario updated = usuarioRepository.findByEmail(u.getEmail()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotNull(updated.getResetPasswordCode());
    }

    @Test
    void forgotPassword_forUnknownEmail_shouldReturnGenericMessage() throws Exception {
        Map<String, String> body = Map.of("email", "fantasma" + (counter++) + "@mail.com");

        // Deliberately the same 200 + generic message as the existing-user case, to avoid
        // leaking whether an email is registered.
        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    void resetPassword_withValidCode_shouldUpdatePassword() throws Exception {
        Usuario u = crearUsuario("reset", true);
        u.setResetPasswordCode("654321");
        u.setResetPasswordExpiresAt(LocalDateTime.now().plusMinutes(15));
        usuarioRepository.save(u);

        Map<String, String> body = Map.of(
                "email", u.getEmail(), "codigo", "654321", "newPassword", "NuevaClave123"
        );

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        Usuario updated = usuarioRepository.findByEmail(u.getEmail()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches("NuevaClave123", updated.getPassword()));
        org.junit.jupiter.api.Assertions.assertNull(updated.getResetPasswordCode());
    }

    @Test
    void resetPassword_withWrongCode_shouldReturnBadRequest() throws Exception {
        Usuario u = crearUsuario("resetmal", true);
        u.setResetPasswordCode("654321");
        u.setResetPasswordExpiresAt(LocalDateTime.now().plusMinutes(15));
        usuarioRepository.save(u);

        Map<String, String> body = Map.of(
                "email", u.getEmail(), "codigo", "000000", "newPassword", "NuevaClave123"
        );

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ── complete-profile ─────────────────────────────────────────────

    @Test
    void completeProfile_whenAuthenticated_shouldUpdateAndReturnUser() throws Exception {
        Usuario u = crearUsuario("completar", true);
        Cookie cookie = authCookieFor(u);

        Map<String, Object> body = Map.of(
                "nombre", "Ana", "apellido", "Gomez", "sexo", "F",
                "fechaNacimiento", "1990-01-01", "tipoDocumento", "DNI",
                "numeroDocumento", 12345678, "telefono", "1122334455"
        );

        mockMvc.perform(post("/api/auth/complete-profile")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ana"));
    }

    @Test
    void completeProfile_whenUnauthenticated_shouldReturnForbidden() throws Exception {
        // /api/auth/complete-profile isn't in SecurityConfig's permitAll list, so
        // anyRequest().authenticated() rejects this with 403 before it ever reaches
        // AuthController's own `userDetails == null` check (that check only matters for a
        // request that got past Spring Security's filter chain but still somehow carries no
        // principal).
        Map<String, Object> body = Map.of(
                "nombre", "Ana", "apellido", "Gomez", "sexo", "F",
                "fechaNacimiento", "1990-01-01", "tipoDocumento", "DNI",
                "numeroDocumento", 12345678, "telefono", "1122334455"
        );

        mockMvc.perform(post("/api/auth/complete-profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void completeProfile_withMissingRequiredField_shouldReturnBadRequest() throws Exception {
        Usuario u = crearUsuario("completarmal", true);
        Cookie cookie = authCookieFor(u);

        Map<String, Object> body = Map.of(
                "nombre", "Ana"
                // faltan apellido, sexo, fechaNacimiento, tipoDocumento, numeroDocumento, telefono
        );

        mockMvc.perform(post("/api/auth/complete-profile")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ── aceptar-terminos ─────────────────────────────────────────────

    @Test
    void aceptarTerminos_whenAuthenticated_shouldClearRequiereAceptarTerminos() throws Exception {
        Usuario u = crearUsuario("terminos", true);
        Cookie cookie = authCookieFor(u);

        mockMvc.perform(post("/api/auth/aceptar-terminos").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiereAceptarTerminos").value(false));
    }

    @Test
    void aceptarTerminos_whenUnauthenticated_shouldReturnForbidden() throws Exception {
        // Same story as completeProfile_whenUnauthenticated_shouldReturnForbidden: not in
        // permitAll, so Spring Security's anyRequest().authenticated() blocks it with 403.
        mockMvc.perform(post("/api/auth/aceptar-terminos"))
                .andExpect(status().isForbidden());
    }

    // ── mi-cuenta ────────────────────────────────────────────────────

    @Test
    void obtenerMiCuenta_whenAuthenticated_shouldReturnAccountData() throws Exception {
        Usuario u = crearUsuario("micuenta", true);
        Cookie cookie = authCookieFor(u);

        mockMvc.perform(get("/api/auth/mi-cuenta").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(u.getEmail()));
    }

    @Test
    void obtenerMiCuenta_whenUnauthenticated_shouldReturnForbidden() throws Exception {
        // /api/auth/mi-cuenta isn't in permitAll either — same 403-before-the-controller story.
        mockMvc.perform(get("/api/auth/mi-cuenta"))
                .andExpect(status().isForbidden());
    }

    @Test
    void actualizarMiCuenta_whenAuthenticated_shouldUpdateFields() throws Exception {
        Usuario u = crearUsuario("editarcuenta", true);
        Cookie cookie = authCookieFor(u);

        Map<String, Object> body = Map.of("nombre", "Nuevo Nombre", "apellido", "Nuevo Apellido");

        mockMvc.perform(put("/api/auth/mi-cuenta")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Nuevo Nombre"));

        Usuario updated = usuarioRepository.findByEmail(u.getEmail()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Nuevo Nombre", updated.getNombre());
    }

    @Test
    void actualizarPreferenciasNotificacion_shouldUpdateFlags() throws Exception {
        Usuario u = crearUsuario("prefs", true);
        Cookie cookie = authCookieFor(u);

        Map<String, Object> body = Map.of("emailHabilitado", false, "whatsappHabilitado", false);

        mockMvc.perform(put("/api/auth/mi-cuenta/notificaciones")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificacionesEmailHabilitadas").value(false))
                .andExpect(jsonPath("$.notificacionesWhatsappHabilitadas").value(false));
    }

    // ── mi-cuenta/password ───────────────────────────────────────────

    @Test
    void cambiarPassword_withCorrectCurrentPassword_shouldUpdate() throws Exception {
        Usuario u = crearUsuario("cambiarpw", true); // password actual: Secret123
        Cookie cookie = authCookieFor(u);

        Map<String, String> body = Map.of("currentPassword", "Secret123", "newPassword", "NuevaClave456");

        mockMvc.perform(post("/api/auth/mi-cuenta/password")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        Usuario updated = usuarioRepository.findByEmail(u.getEmail()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches("NuevaClave456", updated.getPassword()));
    }

    @Test
    void cambiarPassword_withWrongCurrentPassword_shouldReturnBadRequest() throws Exception {
        Usuario u = crearUsuario("cambiarpwmal", true);
        Cookie cookie = authCookieFor(u);

        Map<String, String> body = Map.of("currentPassword", "Incorrecta1", "newPassword", "NuevaClave456");

        mockMvc.perform(post("/api/auth/mi-cuenta/password")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ── set-new-password ─────────────────────────────────────────────

    @Test
    void setNewPassword_withValidPassword_shouldUpdateAndClearMustChangeFlag() throws Exception {
        Usuario u = crearUsuario("setnuevo", true);
        u.setMustChangePassword(true);
        usuarioRepository.save(u);
        Cookie cookie = authCookieFor(u);

        Map<String, String> body = Map.of("newPassword", "OtraClave789");

        mockMvc.perform(post("/api/auth/set-new-password")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));
    }

    @Test
    void setNewPassword_withWeakPassword_shouldReturnBadRequest() throws Exception {
        Usuario u = crearUsuario("setdebil", true);
        Cookie cookie = authCookieFor(u);

        Map<String, String> body = Map.of("newPassword", "debil");

        mockMvc.perform(post("/api/auth/set-new-password")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ── mi-cuenta/eliminar ───────────────────────────────────────────

    @Test
    void eliminarCuenta_withCorrectPassword_shouldAnonymizeAndClearCookie() throws Exception {
        Usuario u = crearUsuario("eliminar", true); // password: Secret123
        Cookie cookie = authCookieFor(u);

        Map<String, String> body = Map.of("password", "Secret123");

        mockMvc.perform(post("/api/auth/mi-cuenta/eliminar")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));

        Usuario updated = usuarioRepository.findById(u.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(updated.isCuentaEliminada());
    }

    @Test
    void eliminarCuenta_withWrongPassword_shouldReturnBadRequest() throws Exception {
        Usuario u = crearUsuario("eliminarmal", true);
        Cookie cookie = authCookieFor(u);

        Map<String, String> body = Map.of("password", "Incorrecta1");

        mockMvc.perform(post("/api/auth/mi-cuenta/eliminar")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ── logout ───────────────────────────────────────────────────────

    @Test
    void logout_shouldReturnOkAndClearCookie() throws Exception {
        // /api/auth/logout isn't in SecurityConfig's permitAll list either, so it needs a valid
        // session just like mi-cuenta/complete-profile/aceptar-terminos do.
        Usuario u = crearUsuario("logout", true);
        Cookie cookie = authCookieFor(u);

        mockMvc.perform(post("/api/auth/logout").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
    }
}
