package com.tranqui.eventos.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Gestor de JWT para llamadas internas entre servicios (sección 4.2 del plan de microservicios).
 * Emite y valida tokens de vida corta (5 minutos por defecto) con sub=<servicio> y rol "SERVICE",
 * firmados con una clave HMAC compartida.
 */
public class ServiceJwt {

    public static final String CLAIM_ROL = "rol";
    public static final String ROL_SERVICE = "SERVICE";
    public static final Duration DURACION_DEFECTO = Duration.ofMinutes(5);

    private final Key signingKey;
    private final Duration duracion;

    public ServiceJwt(String claveCompartida) {
        this(claveCompartida, DURACION_DEFECTO);
    }

    public ServiceJwt(String claveCompartida, Duration duracion) {
        if (claveCompartida == null || claveCompartida.trim().isEmpty()) {
            throw new IllegalArgumentException("La clave compartida no puede ser nula ni vacía");
        }
        this.signingKey = derivarClave(claveCompartida.trim());
        this.duracion = (duracion != null) ? duracion : DURACION_DEFECTO;
    }

    private static Key derivarClave(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            try {
                MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                bytes = sha256.digest(bytes);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("Error al obtener algoritmo SHA-256", e);
            }
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    /**
     * Emite un token JWT firmado para el servicio especificado con expiración a 5 minutos.
     */
    public String emitirToken(String servicio) {
        return emitirToken(servicio, Instant.now());
    }

    /**
     * Emite un token especificando el instante de emisión (útil para pruebas y cálculo de expiración).
     */
    public String emitirToken(String servicio, Instant instanteEmision) {
        if (servicio == null || servicio.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del servicio no puede ser nulo ni vacío");
        }
        Instant expiracion = instanteEmision.plus(duracion);
        return Jwts.builder()
                .setSubject(servicio.trim())
                .claim(CLAIM_ROL, ROL_SERVICE)
                .setIssuedAt(Date.from(instanteEmision))
                .setExpiration(Date.from(expiracion))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Valida el token y devuelve el nombre del servicio (subject).
     * Lanza JwtException o subclases (ExpiredJwtException, SignatureException, etc.) si no es válido.
     */
    public String validarYObtenerServicio(String token) {
        Claims claims = parseClaims(token);
        String rol = claims.get(CLAIM_ROL, String.class);
        if (!ROL_SERVICE.equals(rol)) {
            throw new JwtException("Rol inválido en ServiceJwt: se esperaba " + ROL_SERVICE + " pero se recibió " + rol);
        }
        String subject = claims.getSubject();
        if (subject == null || subject.trim().isEmpty()) {
            throw new JwtException("Subject inválido en ServiceJwt: servicio ausente");
        }
        return subject;
    }

    /**
     * Comprueba si el token es válido sin lanzar excepciones.
     */
    public boolean esValido(String token) {
        try {
            validarYObtenerServicio(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Parsea los claims verificando la firma y la fecha de expiración.
     */
    public Claims parseClaims(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new JwtException("El token no puede ser nulo ni vacío");
        }
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token.trim())
                .getBody();
    }
}
