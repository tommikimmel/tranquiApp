package com.tranqui.eventos.jwt;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Las claves y los tokens que llegan mal formados o débiles se rechazan con un mensaje claro, nunca se aceptan. */
class ClavesYTokensBordesTest {

    private static KeyPair par(int bits) throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(bits);
        return g.generateKeyPair();
    }

    private static String pem(String tipo, byte[] bytes) {
        return "-----BEGIN " + tipo + "-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(bytes)
                + "\n-----END " + tipo + "-----\n";
    }

    @Test
    void clavesRsa_enPemOBase64() throws Exception {
        KeyPair p = par(2048);
        assertThat(ClavesRsa.leerClavePublica(pem("PUBLIC KEY", p.getPublic().getEncoded())).getModulus().bitLength()).isEqualTo(2048);
        assertThat(ClavesRsa.leerClavePrivada(Base64.getEncoder().encodeToString(p.getPrivate().getEncoded()))).isNotNull();
    }

    @Test
    void clavesRsa_vaciasInvalidasOCortas() throws Exception {
        assertThatThrownBy(() -> ClavesRsa.leerClavePublica(" ")).hasMessageContaining("nula ni vacía");
        assertThatThrownBy(() -> ClavesRsa.leerClavePrivada(null)).hasMessageContaining("nula ni vacía");
        assertThatThrownBy(() -> ClavesRsa.leerClavePublica("no-es-base64!")).hasMessageContaining("formato inválido");
        assertThatThrownBy(() -> ClavesRsa.leerClavePrivada("AAAA")).hasMessageContaining("formato inválido");
        KeyPair corta = par(1024);
        assertThatThrownBy(() -> ClavesRsa.leerClavePublica(Base64.getEncoder().encodeToString(corta.getPublic().getEncoded())))
                .hasMessageContaining("al menos");
        assertThatThrownBy(() -> ClavesRsa.leerClavePrivada(Base64.getEncoder().encodeToString(corta.getPrivate().getEncoded())))
                .hasMessageContaining("al menos");
        KeyPair ec = KeyPairGenerator.getInstance("EC").generateKeyPair();
        assertThatThrownBy(() -> ClavesRsa.leerClavePublica(Base64.getEncoder().encodeToString(ec.getPublic().getEncoded())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void serviceJwt_clavesYServiciosInvalidos() {
        assertThatThrownBy(() -> new ServiceJwt(" ")).isInstanceOf(IllegalArgumentException.class);
        ServiceJwt corta = new ServiceJwt("clave-corta", null);
        assertThatThrownBy(() -> corta.emitirToken(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(corta.validarYObtenerServicio(corta.emitirToken(" pagos "))).isEqualTo("pagos");
        assertThatThrownBy(() -> corta.parseClaims(" ")).isInstanceOf(JwtException.class);
        assertThat(corta.esValido("basura")).isFalse();
        assertThat(new ServiceJwt("una-clave-de-servicio-de-32-bytes!!", Duration.ofMinutes(1)).esValido(corta.emitirToken("pagos"))).isFalse();
    }

    @Test
    void serviceJwt_sinSubject_seRechaza() {
        String clave = "una-clave-de-servicio-de-32-bytes!!";
        String sinSubject = Jwts.builder().claim("rol", "SERVICE").setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(clave.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256).compact();
        assertThatThrownBy(() -> new ServiceJwt(clave).validarYObtenerServicio(sinSubject)).isInstanceOf(JwtException.class);
    }

    // ---------------------------------------------------------------- verificador de usuario

    private static final String SECRETO = "un-secreto-de-al-menos-32-bytes-para-test-12345";

    private static String hs256(io.jsonwebtoken.JwtBuilder b) {
        return b.signWith(Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256).compact();
    }

    private static io.jsonwebtoken.JwtBuilder base(String email, String rol, Object uid) {
        io.jsonwebtoken.JwtBuilder b = Jwts.builder().setSubject(email).claim(JwtUsuario.CLAIM_ROL, rol)
                .setExpiration(new Date(System.currentTimeMillis() + 60_000));
        return uid != null ? b.claim(JwtUsuario.CLAIM_UID, uid) : b;
    }

    @Test
    void verificador_configuracionInvalida() throws Exception {
        assertThatThrownBy(() -> new VerificadorJwtUsuario((java.security.interfaces.RSAPublicKey) null, "corto", true)).hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new VerificadorJwtUsuario((java.security.interfaces.RSAPublicKey) null, SECRETO, false)).hasMessageContaining("ninguna clave");
        assertThatThrownBy(() -> new VerificadorJwtUsuario((java.security.interfaces.RSAPublicKey) null, " ", true)).hasMessageContaining("ninguna clave");
    }

    @Test
    void verificador_hs256_reglasDelUidYDeLosClaims() {
        VerificadorJwtUsuario v = new VerificadorJwtUsuario((java.security.interfaces.RSAPublicKey) null, SECRETO, true);
        assertThat(v.validar(hs256(base("ana@t.com", "PACIENTE", "12")))).hasValueSatisfying(u -> assertThat(u.uid()).isEqualTo(12L));
        assertThat(v.validar(hs256(base("ana@t.com", "PACIENTE", null)))).isPresent();
        assertThat(v.validar(hs256(base("ana@t.com", "PACIENTE", "doce")))).isEmpty();
        assertThat(v.validar(hs256(base("ana@t.com", "PACIENTE", 0)))).isEmpty();
        assertThat(v.validar(hs256(base("ana@t.com", "PACIENTE", true)))).isEmpty();
        assertThat(v.validar(hs256(base("ana@t.com", "PACIENTE", " ")))).isEmpty();
        assertThat(v.validar(hs256(base("no-es-mail", "PACIENTE", 1)))).isEmpty();
        assertThat(v.validar(hs256(base("a@b@c.com", "PACIENTE", 1)))).isEmpty();
        assertThat(v.validar(hs256(base("ana@t.com", "ROOT", 1)))).isEmpty();
        assertThat(v.validar(hs256(base("ana@t.com", null, 1)))).isEmpty();
        assertThat(v.validar(hs256(Jwts.builder().setSubject("ana@t.com").claim(JwtUsuario.CLAIM_ROL, "PACIENTE")))).isEmpty();
        assertThat(v.validar(" ")).isEmpty();
        assertThat(v.validar(null)).isEmpty();
    }

    @Test
    void verificador_rs256_exigeEmisorYUidYRechazaOtrosAlgoritmos() throws Exception {
        KeyPair p = par(2048);
        VerificadorJwtUsuario soloRs = new VerificadorJwtUsuario((java.security.interfaces.RSAPublicKey) p.getPublic(), null, false);
        String sinEmisor = base("ana@t.com", "PACIENTE", 5).signWith(p.getPrivate(), SignatureAlgorithm.RS256).compact();
        String sinUid = base("ana@t.com", "PACIENTE", null).setIssuer(JwtUsuario.EMISOR).signWith(p.getPrivate(), SignatureAlgorithm.RS256).compact();
        String bueno = base("ana@t.com", "PACIENTE", 5).setIssuer(JwtUsuario.EMISOR).signWith(p.getPrivate(), SignatureAlgorithm.RS256).compact();
        assertThat(soloRs.validar(sinEmisor)).isEmpty();
        assertThat(soloRs.validar(sinUid)).isEmpty();
        assertThat(soloRs.validar(bueno)).isPresent();
        assertThat(soloRs.validar(hs256(base("ana@t.com", "PACIENTE", 1)))).isEmpty();

        String hs512 = base("ana@t.com", "PACIENTE", 1).signWith(Keys.hmacShaKeyFor(new byte[64]), SignatureAlgorithm.HS512).compact();
        assertThat(soloRs.validar(hs512)).isEmpty();
        VerificadorJwtUsuario soloHs = new VerificadorJwtUsuario((java.security.interfaces.RSAPublicKey) null, SECRETO, true);
        assertThat(soloHs.validar(bueno)).isEmpty();
    }
}
