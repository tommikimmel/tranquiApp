package com.tranqui.chat.seguridad;

import com.tranqui.eventos.jwt.EmisorJwtUsuario;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * Utilidades de prueba para emitir tokens RS256 e históricos HS256 en tests de chat.
 */
public final class JwtTestHelper {

    public static final String CLAVE_JWT = "clave-de-test-de-al-menos-32-bytes-ok";

    public static final KeyPair PAR_CLAVES;
    public static final KeyPair PAR_CLAVES_OTRA;
    public static final String CLAVE_PUBLICA_BASE64;
    public static final EmisorJwtUsuario EMISOR;
    public static final EmisorJwtUsuario EMISOR_OTRA;

    static {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            PAR_CLAVES = kpg.generateKeyPair();
            PAR_CLAVES_OTRA = kpg.generateKeyPair();
            CLAVE_PUBLICA_BASE64 = Base64.getEncoder().encodeToString(PAR_CLAVES.getPublic().getEncoded());
            EMISOR = new EmisorJwtUsuario((RSAPrivateKey) PAR_CLAVES.getPrivate(), Duration.ofDays(7));
            EMISOR_OTRA = new EmisorJwtUsuario((RSAPrivateKey) PAR_CLAVES_OTRA.getPrivate(), Duration.ofDays(7));
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private JwtTestHelper() {}

    public static void registrarPropiedades(DynamicPropertyRegistry registry) {
        registry.add("jwt.public-key", () -> CLAVE_PUBLICA_BASE64);
        registry.add("jwt.secret", () -> CLAVE_JWT);
    }

    public static String tokenRs256(String email, String rol) {
        return tokenRs256(email, rol, 1L);
    }

    public static String tokenRs256(String email, String rol, Long uid) {
        return EMISOR.emitir(email, rol, email, uid != null ? uid : 1L);
    }

    public static String tokenRs256OtraClave(String email, String rol) {
        return EMISOR_OTRA.emitir(email, rol, email, 1L);
    }

    public static String tokenHs256(String email, String rol) {
        return Jwts.builder()
                .setClaims(Map.of("rol", rol))
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(CLAVE_JWT.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }
}
