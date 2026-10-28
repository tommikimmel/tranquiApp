package com.tranqui.eventos.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerificadorJwtUsuarioTest {

    private static KeyPair keyPair2048;
    private static KeyPair keyPair2048Otra;
    private static KeyPair keyPair1024;
    private static String clavePublicaBase64;
    private static String clavePrivadaBase64;
    private static String clavePublicaPem;
    private static String clavePrivadaPem;
    private static final String SECRETO_HS256 = "un-secreto-de-al-menos-32-bytes-para-test-12345";

    @BeforeAll
    static void setUpAll() throws Exception {
        KeyPairGenerator kpg2048 = KeyPairGenerator.getInstance("RSA");
        kpg2048.initialize(2048);
        keyPair2048 = kpg2048.generateKeyPair();
        keyPair2048Otra = kpg2048.generateKeyPair();

        KeyPairGenerator kpg1024 = KeyPairGenerator.getInstance("RSA");
        kpg1024.initialize(1024);
        keyPair1024 = kpg1024.generateKeyPair();

        clavePublicaBase64 = Base64.getEncoder().encodeToString(keyPair2048.getPublic().getEncoded());
        clavePrivadaBase64 = Base64.getEncoder().encodeToString(keyPair2048.getPrivate().getEncoded());

        clavePublicaPem = "-----BEGIN PUBLIC KEY-----\n" + clavePublicaBase64 + "\n-----END PUBLIC KEY-----";
        clavePrivadaPem = "-----BEGIN PRIVATE KEY-----\r\n" + clavePrivadaBase64 + "\r\n-----END PRIVATE KEY-----";
    }

    @Test
    void clavesRsa_leeBase64DeUnaLineaYEnPem() {
        assertThat(ClavesRsa.leerClavePublica(clavePublicaBase64)).isNotNull();
        assertThat(ClavesRsa.leerClavePublica(clavePublicaPem)).isNotNull();
        assertThat(ClavesRsa.leerClavePrivada(clavePrivadaBase64)).isNotNull();
        assertThat(ClavesRsa.leerClavePrivada(clavePrivadaPem)).isNotNull();
    }

    @Test
    void clavesRsa_claveDe1024Bits_esRechazadaSinIncluirLaClave() {
        String publica1024 = Base64.getEncoder().encodeToString(keyPair1024.getPublic().getEncoded());
        String privada1024 = Base64.getEncoder().encodeToString(keyPair1024.getPrivate().getEncoded());

        assertThatThrownBy(() -> ClavesRsa.leerClavePublica(publica1024))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2048 bits")
                .hasMessageNotContaining(publica1024);

        assertThatThrownBy(() -> ClavesRsa.leerClavePrivada(privada1024))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2048 bits")
                .hasMessageNotContaining(privada1024);
    }

    @Test
    void rs256_valido_pasaValidacion() {
        EmisorJwtUsuario emisor = new EmisorJwtUsuario(clavePrivadaBase64, Duration.ofDays(7));
        String token = emisor.emitir("paciente@tranqui.com", "PACIENTE", "Juan Perez", 42L);

        VerificadorJwtUsuario verificador = new VerificadorJwtUsuario(clavePublicaBase64, SECRETO_HS256, false);
        Optional<UsuarioToken> res = verificador.validar(token);

        assertThat(res).isPresent();
        UsuarioToken u = res.get();
        assertThat(u.email()).isEqualTo("paciente@tranqui.com");
        assertThat(u.rol()).isEqualTo("PACIENTE");
        assertThat(u.nombre()).isEqualTo("Juan Perez");
        assertThat(u.uid()).isEqualTo(42L);
    }

    @Test
    void hs256_valido_conAceptarHs256True_pasaValidacion() {
        String token = Jwts.builder()
                .setSubject("medico@tranqui.com")
                .claim("rol", "PSIQUIATRA")
                .claim("nombre", "Dra. Gomez")
                .claim("uid", 100L)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(Keys.hmacShaKeyFor(SECRETO_HS256.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();

        VerificadorJwtUsuario verificador = new VerificadorJwtUsuario(clavePublicaBase64, SECRETO_HS256, true);
        Optional<UsuarioToken> res = verificador.validar(token);

        assertThat(res).isPresent();
        UsuarioToken u = res.get();
        assertThat(u.email()).isEqualTo("medico@tranqui.com");
        assertThat(u.rol()).isEqualTo("PSIQUIATRA");
        assertThat(u.nombre()).isEqualTo("Dra. Gomez");
        assertThat(u.uid()).isEqualTo(100L);
    }

    @Test
    void hs256_rechazado_cuandoAceptarHs256EsFalse() {
        String token = Jwts.builder()
                .setSubject("medico@tranqui.com")
                .claim("rol", "PSIQUIATRA")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(Keys.hmacShaKeyFor(SECRETO_HS256.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();

        VerificadorJwtUsuario verificador = new VerificadorJwtUsuario(clavePublicaBase64, SECRETO_HS256, false);
        Optional<UsuarioToken> res = verificador.validar(token);

        assertThat(res).isEmpty();
    }

    @Test
    void tokenConFormatoExactoDeHoyDelMonolito_pasa() {
        // Monolito JwtService hoy: sub, rol, nombre, sin uid, firmado HS256
        String token = Jwts.builder()
                .setSubject("admin@tranqui.com")
                .claim("rol", "ADMIN")
                .claim("nombre", "Administrador")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(Keys.hmacShaKeyFor(SECRETO_HS256.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();

        VerificadorJwtUsuario verificador = new VerificadorJwtUsuario(clavePublicaBase64, SECRETO_HS256, true);
        Optional<UsuarioToken> res = verificador.validar(token);

        assertThat(res).isPresent();
        UsuarioToken u = res.get();
        assertThat(u.email()).isEqualTo("admin@tranqui.com");
        assertThat(u.rol()).isEqualTo("ADMIN");
        assertThat(u.nombre()).isEqualTo("Administrador");
        assertThat(u.uid()).isNull();
    }

    @Test
    void algNone_rechazado() {
        // Construimos token sin firma (alg=none)
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"admin@tranqui.com\",\"rol\":\"ADMIN\",\"exp\":9999999999}".getBytes(StandardCharsets.UTF_8));
        String unsignedToken = header + "." + payload + ".";

        VerificadorJwtUsuario verificador = new VerificadorJwtUsuario(clavePublicaBase64, SECRETO_HS256, true);
        assertThat(verificador.validar(unsignedToken)).isEmpty();
    }

    @Test
    void hs256_firmadoConBytesDeClavePublica_rechazadoPorConfusionDeAlgoritmo() {
        // Intento de confusión de algoritmo: firmar con HS256 usando la clave pública
        byte[] pubBytes = keyPair2048.getPublic().getEncoded();
        String maliciousToken = Jwts.builder()
                .setSubject("attacker@tranqui.com")
                .claim("rol", "ADMIN")
                .claim("uid", 1L)
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(Keys.hmacShaKeyFor(pubBytes), SignatureAlgorithm.HS256)
                .compact();

        VerificadorJwtUsuario verificador = new VerificadorJwtUsuario(clavePublicaBase64, SECRETO_HS256, true);
        assertThat(verificador.validar(maliciousToken)).isEmpty();
    }

    @Test
    void tokenVencido_esRechazado() {
        Date vencido = new Date(System.currentTimeMillis() - 60000); // 60 segundos atrás (supera tolerancia de 30s)
        String token = Jwts.builder()
                .setSubject("paciente@tranqui.com")
                .claim("rol", "PACIENTE")
                .claim("uid", 10L)
                .setIssuer(JwtUsuario.EMISOR)
                .setIssuedAt(new Date(System.currentTimeMillis() - 120000))
                .setExpiration(vencido)
                .signWith(keyPair2048.getPrivate(), SignatureAlgorithm.RS256)
                .compact();

        VerificadorJwtUsuario verificador = new VerificadorJwtUsuario(clavePublicaBase64, SECRETO_HS256, true);
        assertThat(verificador.validar(token)).isEmpty();
    }

    @Test
    void sinSub_oSubSinFormaDeMail_esRechazado() {
        VerificadorJwtUsuario verificador = new VerificadorJwtUsuario(clavePublicaBase64, SECRETO_HS256, true);

        // Sin sub
        String sinSub = Jwts.builder()
                .claim("rol", "PACIENTE")
                .claim("uid", 10L)
                .setIssuer(JwtUsuario.EMISOR)
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(keyPair2048.getPrivate(), SignatureAlgorithm.RS256)
                .compact();
        assertThat(verificador.validar(sinSub)).isEmpty();

        // Sub sin mail
        String subInvalido = Jwts.builder()
                .setSubject("admin")
                .claim("rol", "PACIENTE")
                .claim("uid", 10L)
                .setIssuer(JwtUsuario.EMISOR)
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(keyPair2048.getPrivate(), SignatureAlgorithm.RS256)
                .compact();
        assertThat(verificador.validar(subInvalido)).isEmpty();
    }

    @Test
    void rolInvalido_esRechazado() {
        String token = Jwts.builder()
                .setSubject("user@tranqui.com")
                .claim("rol", "SUPER_ADMIN")
                .claim("uid", 10L)
                .setIssuer(JwtUsuario.EMISOR)
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(keyPair2048.getPrivate(), SignatureAlgorithm.RS256)
                .compact();

        VerificadorJwtUsuario verificador = new VerificadorJwtUsuario(clavePublicaBase64, SECRETO_HS256, true);
        assertThat(verificador.validar(token)).isEmpty();
    }

    @Test
    void rolAdminEnMinusculas_esInvalido() {
        String token = Jwts.builder()
                .setSubject("admin@tranqui.com")
                .claim("rol", "admin")
                .claim("uid", 10L)
                .setIssuer(JwtUsuario.EMISOR)
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(keyPair2048.getPrivate(), SignatureAlgorithm.RS256)
                .compact();

        VerificadorJwtUsuario verificador = new VerificadorJwtUsuario(clavePublicaBase64, SECRETO_HS256, true);
        assertThat(verificador.validar(token)).isEmpty();
    }

    @Test
    void mailConMayusculas_vuelveIgualSinPasarAMinusculas() {
        EmisorJwtUsuario emisor = new EmisorJwtUsuario(clavePrivadaBase64, Duration.ofDays(7));
        String token = emisor.emitir("Usuario.Con.Mayusculas@Tranqui.COM", "PACIENTE", "Juan", 15L);

        VerificadorJwtUsuario verificador = new VerificadorJwtUsuario(clavePublicaBase64, SECRETO_HS256, false);
        Optional<UsuarioToken> res = verificador.validar(token);

        assertThat(res).isPresent();
        assertThat(res.get().email()).isEqualTo("Usuario.Con.Mayusculas@Tranqui.COM");
    }

    @Test
    void rs256_sinIss_oConOtroIss_esRechazado() {
        VerificadorJwtUsuario verificador = new VerificadorJwtUsuario(clavePublicaBase64, SECRETO_HS256, true);

        // Sin iss
        String sinIss = Jwts.builder()
                .setSubject("paciente@tranqui.com")
                .claim("rol", "PACIENTE")
                .claim("uid", 10L)
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(keyPair2048.getPrivate(), SignatureAlgorithm.RS256)
                .compact();
        assertThat(verificador.validar(sinIss)).isEmpty();

        // Con otro iss
        String otroIss = Jwts.builder()
                .setSubject("paciente@tranqui.com")
                .claim("rol", "PACIENTE")
                .claim("uid", 10L)
                .setIssuer("otro-servicio")
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(keyPair2048.getPrivate(), SignatureAlgorithm.RS256)
                .compact();
        assertThat(verificador.validar(otroIss)).isEmpty();
    }

    @Test
    void rs256_sinUid_esRechazado() {
        String sinUid = Jwts.builder()
                .setSubject("paciente@tranqui.com")
                .claim("rol", "PACIENTE")
                .setIssuer(JwtUsuario.EMISOR)
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(keyPair2048.getPrivate(), SignatureAlgorithm.RS256)
                .compact();

        VerificadorJwtUsuario verificador = new VerificadorJwtUsuario(clavePublicaBase64, SECRETO_HS256, true);
        assertThat(verificador.validar(sinUid)).isEmpty();
    }

    @Test
    void firmadoConOtraClaveRsa_esRechazado() {
        String token = Jwts.builder()
                .setSubject("paciente@tranqui.com")
                .claim("rol", "PACIENTE")
                .claim("uid", 10L)
                .setIssuer(JwtUsuario.EMISOR)
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(keyPair2048Otra.getPrivate(), SignatureAlgorithm.RS256)
                .compact();

        VerificadorJwtUsuario verificador = new VerificadorJwtUsuario(clavePublicaBase64, SECRETO_HS256, true);
        assertThat(verificador.validar(token)).isEmpty();
    }
}
