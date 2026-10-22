package com.tranqui.eventos.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceJwtTest {

    private static final String CLAVE_SECRETA = "super-secret-service-internal-key-32chars!";
    private ServiceJwt serviceJwt;

    @BeforeEach
    void setUp() {
        serviceJwt = new ServiceJwt(CLAVE_SECRETA);
    }

    @Test
    void jwtValido_emiteYValidaCorrectamente() {
        String token = serviceJwt.emitirToken("turnos");
        assertThat(token).isNotBlank();

        assertThat(serviceJwt.esValido(token)).isTrue();
        String servicio = serviceJwt.validarYObtenerServicio(token);
        assertThat(servicio).isEqualTo("turnos");
    }

    @Test
    void jwtExpirado_lanzaExpiredJwtException() {
        // Emitir un token con expiración en el pasado
        Instant haceDiezMinutos = Instant.now().minus(Duration.ofMinutes(10));
        String tokenVencido = serviceJwt.emitirToken("pagos", haceDiezMinutos);

        assertThat(serviceJwt.esValido(tokenVencido)).isFalse();
        assertThatThrownBy(() -> serviceJwt.validarYObtenerServicio(tokenVencido))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void jwtFirmaInvalida_lanzaSignatureException() {
        String claveDiferente = "otra-clave-completamente-distinta-32chars!";
        ServiceJwt otroJwt = new ServiceJwt(claveDiferente);

        String tokenFirmadoConOtraClave = otroJwt.emitirToken("identidad");

        assertThat(serviceJwt.esValido(tokenFirmadoConOtraClave)).isFalse();
        assertThatThrownBy(() -> serviceJwt.validarYObtenerServicio(tokenFirmadoConOtraClave))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void jwtRolIncorrecto_lanzaJwtException() {
        // Generar un token con la clave correcta pero rol diferente a SERVICE (e.g. USER)
        byte[] keyBytes = CLAVE_SECRETA.getBytes(StandardCharsets.UTF_8);
        String tokenConRolUsuario = Jwts.builder()
                .setSubject("turnos")
                .claim(ServiceJwt.CLAIM_ROL, "USER")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 300_000))
                .signWith(Keys.hmacShaKeyFor(keyBytes), SignatureAlgorithm.HS256)
                .compact();

        assertThat(serviceJwt.esValido(tokenConRolUsuario)).isFalse();
        assertThatThrownBy(() -> serviceJwt.validarYObtenerServicio(tokenConRolUsuario))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("Rol inválido en ServiceJwt");
    }
}
