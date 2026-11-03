package com.tranqui.app.eventos;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fase 10, T3: los tests del monolito corren con H2 y ddl-auto, sin Flyway, así que una tabla de libs/eventos que
 * falta en las migraciones solo se veía en el stack (faltaba versiones_aplicadas y fallaban todas las copias).
 */
class MigracionesTablasEventosTest {

    private static final Pattern TABLA = Pattern.compile("CREATE TABLE IF NOT EXISTS (\\w+)");

    @Test
    void cadaTablaQuePideLibsEventosEstaEnUnaMigracionDelMonolito() throws IOException {
        Set<String> pedidas = new TreeSet<>();
        try (InputStream in = getClass().getResourceAsStream("/db/eventos/consumo.sql")) {
            tablas(new String(in.readAllBytes(), StandardCharsets.UTF_8), pedidas);
        }
        Set<String> migradas = new TreeSet<>();
        try (Stream<Path> archivos = Files.list(Path.of("src/main/resources/db/migration"))) {
            for (Path p : (Iterable<Path>) archivos::iterator) {
                tablas(Files.readString(p), migradas);
            }
        }
        assertThat(pedidas).isNotEmpty();
        assertThat(migradas).containsAll(pedidas);
    }

    private static void tablas(String sql, Set<String> destino) {
        Matcher m = TABLA.matcher(sql);
        while (m.find()) {
            destino.add(m.group(1));
        }
    }
}
