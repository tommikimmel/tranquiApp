package com.tranqui.app.service;

import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
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
        assertEquals("PACIENTE", jwtService.extractClaim(token, claims -> claims.get("rol")));
        assertEquals("Juan Test", jwtService.extractClaim(token, claims -> claims.get("nombre")));
    }

    @Test
    void whenInvalidToken_thenValidateShouldReturnFalse() {
        assertFalse(jwtService.validateToken("invalid.token.string"));
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
}
