package com.tranqui.eventos.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Emisor del nuevo JWT de usuario en formato RS256 (identidad).
 */
public class EmisorJwtUsuario {

    public static final Duration VIGENCIA_DEFECTO = Duration.ofDays(7);

    private final RSAPrivateKey clavePrivada;
    private final Duration vigencia;

    public EmisorJwtUsuario(String clavePrivadaTexto, Duration vigencia) {
        this(ClavesRsa.leerClavePrivada(clavePrivadaTexto), vigencia);
    }

    public EmisorJwtUsuario(RSAPrivateKey clavePrivada, Duration vigencia) {
        if (clavePrivada == null) {
            throw new IllegalArgumentException("La clave privada no puede ser nula");
        }
        this.clavePrivada = clavePrivada;
        this.vigencia = (vigencia != null && !vigencia.isNegative() && !vigencia.isZero())
                ? vigencia
                : VIGENCIA_DEFECTO;
    }

    /**
     * Emite un token RS256 con sub=email, claims rol, nombre, uid, iss=tranqui-identidad, iat y exp.
     */
    public String emitir(String email, String rol, String nombre, Long uid) {
        return emitir(email, rol, nombre, uid, Instant.now());
    }

    public String emitir(String email, String rol, String nombre, Long uid, Instant instanteEmision) {
        if (email == null || email.trim().isEmpty() || !email.contains("@")) {
            throw new IllegalArgumentException("El email es inválido o no tiene formato de correo: " + email);
        }
        if (!JwtUsuario.esRolValido(rol)) {
            throw new IllegalArgumentException("El rol no es válido: " + rol);
        }
        if (uid == null || uid <= 0) {
            throw new IllegalArgumentException("El uid debe ser un identificador positivo: " + uid);
        }

        Instant ahora = instanteEmision != null ? instanteEmision : Instant.now();
        Instant expiracion = ahora.plus(vigencia);

        return Jwts.builder()
                .setSubject(email.trim().toLowerCase())
                .claim(JwtUsuario.CLAIM_ROL, rol.trim().toUpperCase())
                .claim(JwtUsuario.CLAIM_NOMBRE, nombre != null ? nombre.trim() : null)
                .claim(JwtUsuario.CLAIM_UID, uid)
                .setIssuer(JwtUsuario.EMISOR)
                .setIssuedAt(Date.from(ahora))
                .setExpiration(Date.from(expiracion))
                .signWith(clavePrivada, SignatureAlgorithm.RS256)
                .compact();
    }
}
