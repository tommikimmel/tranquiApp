package com.tranqui.eventos.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SigningKeyResolverAdapter;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.interfaces.RSAPublicKey;
import java.util.Optional;

/**
 * Verificador único de JWT de usuario (RS256 para identidad, HS256 durante la transición).
 * Valida los claims obligatorios (exp con tolerancia de 30s, sub con formato de mail, rol válido).
 * RS256 además exige iss=tranqui-identidad y uid positivo.
 * HS256 acepta uid ausente; si viene, exige que sea positivo.
 */
public class VerificadorJwtUsuario {

    private final RSAPublicKey clavePublica;
    private final SecretKey claveHs256;
    private final boolean aceptarHs256;
    private final JwtParser parser;

    public VerificadorJwtUsuario(String clavePublicaTexto, String secretoHs256, boolean aceptarHs256) {
        RSAPublicKey pub = null;
        if (clavePublicaTexto != null && !clavePublicaTexto.trim().isEmpty()) {
            pub = ClavesRsa.leerClavePublica(clavePublicaTexto);
        }

        SecretKey hmac = null;
        if (secretoHs256 != null && !secretoHs256.trim().isEmpty()) {
            byte[] bytes = secretoHs256.trim().getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) {
                throw new IllegalStateException("El secreto HS256 debe tener al menos 32 bytes.");
            }
            hmac = Keys.hmacShaKeyFor(bytes);
        }

        boolean hmacUsable = aceptarHs256 && hmac != null;
        if (pub == null && !hmacUsable) {
            throw new IllegalStateException("No hay ninguna clave de verificación usable configurada.");
        }

        this.clavePublica = pub;
        this.claveHs256 = hmac;
        this.aceptarHs256 = aceptarHs256;

        this.parser = Jwts.parserBuilder()
                .setAllowedClockSkewSeconds(30)
                .setSigningKeyResolver(new SigningKeyResolverAdapter() {
                    @Override
                    public Key resolveSigningKey(JwsHeader header, Claims claims) {
                        return resolverClave(header);
                    }

                    @Override
                    public Key resolveSigningKey(JwsHeader header, String plaintext) {
                        return resolverClave(header);
                    }

                    private Key resolverClave(JwsHeader header) {
                        String alg = header != null ? header.getAlgorithm() : null;
                        if ("RS256".equals(alg)) {
                            if (clavePublica != null) {
                                return clavePublica;
                            }
                            throw new JwtException("RS256 no configurado o no aceptado");
                        }
                        if ("HS256".equals(alg)) {
                            if (aceptarHs256 && claveHs256 != null) {
                                return claveHs256;
                            }
                            throw new JwtException("HS256 no configurado o no aceptado");
                        }
                        throw new JwtException("Algoritmo no permitido: " + alg);
                    }
                })
                .build();
    }

    public VerificadorJwtUsuario(RSAPublicKey clavePublica, String secretoHs256, boolean aceptarHs256) {
        SecretKey hmac = null;
        if (secretoHs256 != null && !secretoHs256.trim().isEmpty()) {
            byte[] bytes = secretoHs256.trim().getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) {
                throw new IllegalStateException("El secreto HS256 debe tener al menos 32 bytes.");
            }
            hmac = Keys.hmacShaKeyFor(bytes);
        }

        boolean hmacUsable = aceptarHs256 && hmac != null;
        if (clavePublica == null && !hmacUsable) {
            throw new IllegalStateException("No hay ninguna clave de verificación usable configurada.");
        }

        this.clavePublica = clavePublica;
        this.claveHs256 = hmac;
        this.aceptarHs256 = aceptarHs256;

        this.parser = Jwts.parserBuilder()
                .setAllowedClockSkewSeconds(30)
                .setSigningKeyResolver(new SigningKeyResolverAdapter() {
                    @Override
                    public Key resolveSigningKey(JwsHeader header, Claims claims) {
                        return resolverClave(header);
                    }

                    @Override
                    public Key resolveSigningKey(JwsHeader header, String plaintext) {
                        return resolverClave(header);
                    }

                    private Key resolverClave(JwsHeader header) {
                        String alg = header != null ? header.getAlgorithm() : null;
                        if ("RS256".equals(alg)) {
                            if (clavePublica != null) {
                                return clavePublica;
                            }
                            throw new JwtException("RS256 no configurado o no aceptado");
                        }
                        if ("HS256".equals(alg)) {
                            if (aceptarHs256 && claveHs256 != null) {
                                return claveHs256;
                            }
                            throw new JwtException("HS256 no configurado o no aceptado");
                        }
                        throw new JwtException("Algoritmo no permitido: " + alg);
                    }
                })
                .build();
    }

    /**
     * Valida el token y devuelve el UsuarioToken parseado. Ante cualquier error retorna Optional.empty()
     * sin loguear el token.
     */
    public Optional<UsuarioToken> validar(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Jws<Claims> jws = parser.parseClaimsJws(token.trim());
            Claims claims = jws.getBody();
            JwsHeader<?> header = jws.getHeader();
            String alg = header.getAlgorithm();

            if (claims.getExpiration() == null) {
                return Optional.empty();
            }

            String email = claims.getSubject();
            if (email == null || email.isBlank() || !esFormatoMail(email)) {
                return Optional.empty();
            }

            String rol = claims.get(JwtUsuario.CLAIM_ROL, String.class);
            if (rol == null || !JwtUsuario.esRolValido(rol)) {
                return Optional.empty();
            }

            Long uid = extraerUid(claims.get(JwtUsuario.CLAIM_UID));

            if ("RS256".equals(alg)) {
                if (!JwtUsuario.EMISOR.equals(claims.getIssuer())) {
                    return Optional.empty();
                }
                if (uid == null || uid <= 0) {
                    return Optional.empty();
                }
            } else if ("HS256".equals(alg)) {
                if (uid != null && uid <= 0) {
                    return Optional.empty();
                }
            } else {
                return Optional.empty();
            }

            String nombre = claims.get(JwtUsuario.CLAIM_NOMBRE, String.class);
            return Optional.of(new UsuarioToken(email.trim().toLowerCase(), rol.trim().toUpperCase(), uid, nombre));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static boolean esFormatoMail(String s) {
        int arroba = s.indexOf('@');
        return arroba > 0 && arroba == s.lastIndexOf('@') && arroba < s.length() - 1 && !s.contains(" ");
    }

    private static Long extraerUid(Object valor) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof Number n) {
            return n.longValue();
        }
        if (valor instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                return -1L;
            }
        }
        return -1L;
    }
}
