package com.tranqui.app.config;

import com.tranqui.app.util.EncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Vuelve a cifrar con {@code ENCRYPTION_KEY} los tokens de Mercado Pago y de Google guardados hasta la
 * v1.1.0, que estaban cifrados con una clave pública (ver {@link EncryptionUtil}). Corre en cada
 * arranque y no hace nada una vez que no quedan valores en el formato viejo.
 *
 * <p>Antes de reemplazar cada valor guarda el original en {@code cifrado_legado_respaldo}, para poder
 * volver a una imagen anterior (docs/entornos/produccion.md, "Clave de cifrado"). Ese respaldo está
 * cifrado con la clave pública, así que se borra solo a los {@value #DIAS_RESPALDO} días.
 */
@Component
public class RecifradoTokensLegados implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RecifradoTokensLegados.class);
    static final List<String> COLUMNAS = List.of("mp_access_token_encrypted", "mp_refresh_token_encrypted",
            "google_access_token_encrypted", "google_refresh_token_encrypted");
    static final int DIAS_RESPALDO = 14;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final EncryptionUtil encryptionUtil;

    public RecifradoTokensLegados(JdbcTemplate jdbc, TransactionTemplate tx, EncryptionUtil encryptionUtil) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.encryptionUtil = encryptionUtil;
    }

    @Override
    public void run(String... args) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS cifrado_legado_respaldo ("
                + "usuario_id BIGINT NOT NULL, columna VARCHAR(64) NOT NULL, valor TEXT NOT NULL, "
                + "respaldado_en TIMESTAMP NOT NULL, PRIMARY KEY (usuario_id, columna))");
        int borrados = jdbc.update("DELETE FROM cifrado_legado_respaldo WHERE respaldado_en < ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(DIAS_RESPALDO)));
        if (borrados > 0) {
            log.info("Respaldo del cifrado anterior: {} valor(es) de más de {} días borrados.", borrados, DIAS_RESPALDO);
        }

        int recifrados = 0;
        int fallidos = 0;
        for (String columna : COLUMNAS) {
            // La columna sale de la lista fija de arriba, nunca de una entrada.
            List<Map<String, Object>> filas = jdbc.queryForList("SELECT id, " + columna + " AS valor FROM usuario "
                    + "WHERE " + columna + " IS NOT NULL AND " + columna + " NOT LIKE 'v2:%'");
            for (Map<String, Object> fila : filas) {
                long id = ((Number) fila.get("id")).longValue();
                String valor = (String) fila.get("valor");
                String nuevo;
                try {
                    nuevo = encryptionUtil.encrypt(encryptionUtil.decrypt(valor));
                } catch (Exception e) {
                    fallidos++;
                    log.warn("No se pudo descifrar {} del usuario ID {}: queda como estaba", columna, id);
                    continue;
                }
                tx.executeWithoutResult(estado -> {
                    jdbc.update("DELETE FROM cifrado_legado_respaldo WHERE usuario_id = ? AND columna = ?", id, columna);
                    jdbc.update("INSERT INTO cifrado_legado_respaldo (usuario_id, columna, valor, respaldado_en) "
                            + "VALUES (?, ?, ?, ?)", id, columna, valor, Timestamp.valueOf(LocalDateTime.now()));
                    // Si el valor cambió mientras tanto (un token renovado), no se pisa: ya está en v2.
                    jdbc.update("UPDATE usuario SET " + columna + " = ? WHERE id = ? AND " + columna + " = ?",
                            nuevo, id, valor);
                });
                recifrados++;
            }
        }
        if (recifrados > 0 || fallidos > 0) {
            log.info("Cifrado de tokens: {} valor(es) pasados a la clave actual, {} sin poder descifrar.",
                    recifrados, fallidos);
        }
    }
}
