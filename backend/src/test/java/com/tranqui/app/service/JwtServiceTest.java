package com.tranqui.app.service;

import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.eventos.jwt.UsuarioToken;
import com.tranqui.eventos.jwt.VerificadorJwtUsuario;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private JwtService jwtService;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", "mysecuresecretkeythatmustbelongenoughfor256bitsHS256");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3600000L); // 1 hour
        jwtService.validateSecret();
    }

    @Test
    void whenGenerateToken_incluyeClaimUid() {
        Usuario usuario = Usuario.builder()
                .id(99L)
                .nombre("Juan Test")
                .email("juan@test.com")
                .rol(Rol.PACIENTE)
                .build();

        String token = jwtService.generateToken(usuario);

        assertNotNull(token);
        assertTrue(jwtService.validateToken(token));
        assertEquals("juan@test.com", jwtService.extractEmail(token));

        VerificadorJwtUsuario verificador = new VerificadorJwtUsuario((String) null, "mysecuresecretkeythatmustbelongenoughfor256bitsHS256", true);
        UsuarioToken ut = verificador.validar(token).orElseThrow();
        assertEquals(99L, ut.uid());
        assertEquals("PACIENTE", ut.rol());
        assertEquals("Juan Test", ut.nombre());
        assertEquals("juan@test.com", ut.email());
    }

    @Test
    void tokenRs256DeIdentidad_esAceptadoPorMonolito() throws Exception {
        java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        java.security.KeyPair kp = kpg.generateKeyPair();
        String pubBase64 = java.util.Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
        String privBase64 = java.util.Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());

        com.tranqui.eventos.jwt.EmisorJwtUsuario emisor = new com.tranqui.eventos.jwt.EmisorJwtUsuario(
                privBase64, java.time.Duration.ofDays(7)
        );
        String tokenRs256 = emisor.emitir("usuario.identidad@tranqui.com", "PSIQUIATRA", "Dr. Identidad", 77L);

        // Monolito con JWT_PUBLIC_KEY configurada
        JwtService serviceConRsa = new JwtService();
        ReflectionTestUtils.setField(serviceConRsa, "secretKey", "mysecuresecretkeythatmustbelongenoughfor256bitsHS256");
        ReflectionTestUtils.setField(serviceConRsa, "publicKey", pubBase64);
        ReflectionTestUtils.setField(serviceConRsa, "jwtExpiration", 3600000L);
        serviceConRsa.validateSecret();

        assertTrue(serviceConRsa.validateToken(tokenRs256));
        assertEquals("usuario.identidad@tranqui.com", serviceConRsa.extractEmail(tokenRs256));
    }

    @Test
    void whenGenerateToken_thenShouldBeValid() {
        Usuario usuario = Usuario.builder()
                .nombre("Juan Test")
                .email("juan@test.com")
                .rol(Rol.PACIENTE)
                .build();

        String token = jwtService.generateToken(usuario);

        assertNotNull(token);
        assertTrue(jwtService.validateToken(token));
        assertEquals("juan@test.com", jwtService.extractEmail(token));

        VerificadorJwtUsuario verificador = new VerificadorJwtUsuario((String) null, "mysecuresecretkeythatmustbelongenoughfor256bitsHS256", true);
        UsuarioToken ut = verificador.validar(token).orElseThrow();
        assertEquals("PACIENTE", ut.rol());
        assertEquals("Juan Test", ut.nombre());
    }

    @Test
    void whenInvalidToken_thenValidateShouldReturnFalse() {
        assertFalse(jwtService.validateToken("invalid.token.string"));
    }

    @Test
    void whenTokenExpirationIsInThePast_thenValidateShouldReturnFalse() {
        // Force generateToken to issue an already-expired token (negative TTL) instead of
        // waiting out a real 1h token — validateToken must reject it even though the signature
        // itself is genuine (it's a boundary on isTokenExpired, not on signature verification,
        // which whenInvalidToken_thenValidateShouldReturnFalse above already covers).
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", -120000L);
        Usuario usuario = Usuario.builder()
                .nombre("Vencido Test")
                .email("vencido@test.com")
                .rol(Rol.PACIENTE)
                .build();

        String expiredToken = jwtService.generateToken(usuario);

        assertFalse(jwtService.validateToken(expiredToken));
    }

    @Test
    void whenExtractFromCookies_thenShouldFindToken() {
        Cookie[] cookies = new Cookie[]{
                new Cookie("OTHER-COOKIE", "value"),
                new Cookie("SESSION-TOKEN", "jwt-cookie-token")
        };
        when(request.getCookies()).thenReturn(cookies);

        String token = jwtService.extractTokenFromCookies(request);

        assertEquals("jwt-cookie-token", token);
    }

    @Test
    void whenExtractFromCookiesAndNoCookiesExist_thenShouldReturnNull() {
        when(request.getCookies()).thenReturn(null);
        assertNull(jwtService.extractTokenFromCookies(request));
    }

    @Test
    void whenExtractFromHeader_thenShouldFindToken() {
        when(request.getHeader("Authorization")).thenReturn("Bearer jwt-header-token");

        String token = jwtService.extractTokenFromHeader(request);

        assertEquals("jwt-header-token", token);
    }

    @Test
    void whenExtractFromHeaderWithoutBearer_thenShouldReturnNull() {
        when(request.getHeader("Authorization")).thenReturn("OtherToken value");

        String token = jwtService.extractTokenFromHeader(request);

        assertNull(token);
    }

    @Test
    void whenSecretIsMissingOrTooShort_thenValidateSecretThrowsIllegalStateException() {
        JwtService serviceWithoutSecret = new JwtService();
        ReflectionTestUtils.setField(serviceWithoutSecret, "secretKey", "");
        assertThrows(IllegalStateException.class, serviceWithoutSecret::validateSecret);

        ReflectionTestUtils.setField(serviceWithoutSecret, "secretKey", "short-secret");
        assertThrows(IllegalStateException.class, serviceWithoutSecret::validateSecret);

        ReflectionTestUtils.setField(serviceWithoutSecret, "secretKey", null);
        assertThrows(IllegalStateException.class, serviceWithoutSecret::validateSecret);
    }
}
