package com.tranqui.app.config;

import com.tranqui.app.util.EncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RecifradoTokensLegadosTest {

    private JdbcTemplate jdbc;
    private EncryptionUtil encryptionUtil;
    private RecifradoTokensLegados recifrado;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:recifrado-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE usuario (id BIGINT PRIMARY KEY, mp_access_token_encrypted TEXT, "
                + "mp_refresh_token_encrypted TEXT, google_access_token_encrypted TEXT, "
                + "google_refresh_token_encrypted TEXT)");
        encryptionUtil = new EncryptionUtil();
        ReflectionTestUtils.setField(encryptionUtil, "secretKey", "una-clave-de-prueba-de-al-menos-32-bytes");
        recifrado = new RecifradoTokensLegados(jdbc, new TransactionTemplate(new DataSourceTransactionManager(ds)),
                encryptionUtil);
    }

    private static String cifrarComoAntes(String valor) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(
                "masterdecryptionkey32charspart12".getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        return Base64.getEncoder().encodeToString(cipher.doFinal(valor.getBytes(StandardCharsets.UTF_8)));
    }

    private String columna(long id, String columna) {
        return jdbc.queryForObject("SELECT " + columna + " FROM usuario WHERE id = ?", String.class, id);
    }

    @Test
    void pasaLosTokensViejosALaClaveActualYGuardaElRespaldo() throws Exception {
        String mpViejo = cifrarComoAntes("APP_USR-mp");
        String googleViejo = cifrarComoAntes("ya29.google");
        jdbc.update("INSERT INTO usuario (id, mp_access_token_encrypted, google_refresh_token_encrypted) VALUES (1, ?, ?)",
                mpViejo, googleViejo);

        recifrado.run();

        String mpNuevo = columna(1, "mp_access_token_encrypted");
        assertTrue(mpNuevo.startsWith("v2:"));
        assertEquals("APP_USR-mp", encryptionUtil.decrypt(mpNuevo));
        assertEquals("ya29.google", encryptionUtil.decrypt(columna(1, "google_refresh_token_encrypted")));
        assertNull(columna(1, "mp_refresh_token_encrypted"));
        assertEquals(mpViejo, jdbc.queryForObject("SELECT valor FROM cifrado_legado_respaldo "
                + "WHERE usuario_id = 1 AND columna = 'mp_access_token_encrypted'", String.class));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM cifrado_legado_respaldo", Integer.class));
    }

    @Test
    void esIdempotenteYNoTocaLoQueYaEstaEnV2() throws Exception {
        String actual = encryptionUtil.encrypt("APP_USR-nuevo");
        jdbc.update("INSERT INTO usuario (id, mp_access_token_encrypted) VALUES (1, ?)", actual);
        jdbc.update("INSERT INTO usuario (id, mp_access_token_encrypted) VALUES (2, ?)", cifrarComoAntes("APP_USR-viejo"));

        recifrado.run();
        String despuesDeUna = columna(2, "mp_access_token_encrypted");
        recifrado.run();

        assertEquals(actual, columna(1, "mp_access_token_encrypted"));
        assertEquals(despuesDeUna, columna(2, "mp_access_token_encrypted"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM cifrado_legado_respaldo", Integer.class));
    }

    @Test
    void unValorQueNoSeDescifraQuedaComoEstaba() {
        jdbc.update("INSERT INTO usuario (id, mp_access_token_encrypted) VALUES (1, 'basura')");

        recifrado.run();

        assertEquals("basura", columna(1, "mp_access_token_encrypted"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM cifrado_legado_respaldo", Integer.class));
    }

    @Test
    void borraElRespaldoViejo() {
        recifrado.run();
        jdbc.update("INSERT INTO cifrado_legado_respaldo (usuario_id, columna, valor, respaldado_en) VALUES (1, 'x', 'v', ?)",
                Timestamp.valueOf(LocalDateTime.now().minusDays(RecifradoTokensLegados.DIAS_RESPALDO + 1)));
        jdbc.update("INSERT INTO cifrado_legado_respaldo (usuario_id, columna, valor, respaldado_en) VALUES (2, 'x', 'v', ?)",
                Timestamp.valueOf(LocalDateTime.now().minusDays(1)));

        recifrado.run();

        assertEquals(2L, jdbc.queryForObject("SELECT usuario_id FROM cifrado_legado_respaldo", Long.class));
    }
}
