package com.tranqui.app.service;

import com.tranqui.app.model.Usuario;
import com.tranqui.eventos.jwt.ClavesRsa;
import com.tranqui.eventos.jwt.UsuarioToken;
import com.tranqui.eventos.jwt.VerificadorJwtUsuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SigningKeyResolverAdapter;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${jwt.secret:}")
    private String secretKey;

    @Value("${jwt.public-key:}")
    private String publicKey;

    @Value("${jwt.expiration:604800000}") // 7 days in ms
    private long jwtExpiration;

    private VerificadorJwtUsuario verificador;

    @PostConstruct
    public void validateSecret() {
        if (secretKey == null || secretKey.trim().isEmpty()) {
            throw new IllegalStateException("JWT secret is not configured. Missing JWT_SECRET environment variable.");
        }
        if (secretKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes (256 bits) long.");
        }
        this.verificador = new VerificadorJwtUsuario(publicKey, secretKey, true);
    }

    private VerificadorJwtUsuario getVerificador() {
        if (this.verificador == null) {
            this.verificador = new VerificadorJwtUsuario(publicKey, secretKey, true);
        }
        return this.verificador;
    }

    private Key getSigningKey() {
        if (secretKey == null || secretKey.trim().isEmpty()) {
            throw new IllegalStateException("JWT secret is not configured. Missing JWT_SECRET environment variable.");
        }
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes (256 bits) long.");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Usuario usuario) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("rol", usuario.getRol().name());
        extraClaims.put("nombre", usuario.getNombre());
        if (usuario.getId() != null) {
            extraClaims.put("uid", usuario.getId());
        }
        return generateToken(extraClaims, usuario.getEmail());
    }

    public String generateToken(Map<String, Object> extraClaims, String email) {
        long ttl = jwtExpiration < 0 ? (jwtExpiration - 35000L) : jwtExpiration;
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(email)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + ttl))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractEmail(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return getVerificador().validar(token).map(UsuarioToken::email).orElse(null);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        if (publicKey != null && !publicKey.trim().isEmpty()) {
            return Jwts.parserBuilder()
                    .setAllowedClockSkewSeconds(30)
                    .setSigningKeyResolver(new SigningKeyResolverAdapter() {
                        @Override
                        public Key resolveSigningKey(JwsHeader header, Claims claims) {
                            String alg = header != null ? header.getAlgorithm() : null;
                            if ("RS256".equals(alg)) {
                                return ClavesRsa.leerClavePublica(publicKey);
                            }
                            return getSigningKey();
                        }
                    })
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        }
        return Jwts.parserBuilder()
                .setAllowedClockSkewSeconds(30)
                .setSigningKeyResolver(new SigningKeyResolverAdapter() {
                    @Override
                    public Key resolveSigningKey(JwsHeader header, Claims claims) {
                        return getSigningKey();
                    }
                })
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            return getVerificador().validar(token).isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    public String extractTokenFromCookies(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("SESSION-TOKEN".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    public String extractTokenFromHeader(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
