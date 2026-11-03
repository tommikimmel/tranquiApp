package com.tranqui.eventos;

import com.fasterxml.jackson.databind.JsonNode;
import com.tranqui.eventos.contratos.Contratos;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fase 10, T2: el JSON de cada record de {@code data} queda fijado en {@code contratos/eventos}. Renombrar o cambiar
 * el tipo de un campo rompe este test: un servicio desplegado con la versión anterior de la librería leería ese campo
 * en null sin avisar, así que el cambio tiene que ser compatible y quedar a la vista en el PR.
 * Con {@code -Dcontratos.generar=true} escribe los contratos que falten.
 */
class ContratoFixturesEventosTest {

    private static final Path DIR = Path.of("src/test/resources/contratos/eventos");

    @Test
    void cadaRecordSeSerializaIgualQueSuContrato() throws Exception {
        boolean generar = Boolean.getBoolean("contratos.generar");
        List<String> distintos = new ArrayList<>();
        List<Class<?>> records = ValidacionDatosTest.records().stream().filter(c -> c.getEnclosingClass() == null).toList();
        assertThat(records).hasSizeGreaterThanOrEqualTo(44);
        for (Class<?> tipo : records) {
            Object ejemplo = ValidacionDatosTest.instancia(tipo, -1, null);
            JsonNode real = Contratos.MAPPER.readTree(Contratos.MAPPER.writeValueAsString(ejemplo));
            String archivo = tipo.getSimpleName() + ".json";
            if (generar && !Files.exists(DIR.resolve(archivo))) {
                Files.createDirectories(DIR);
                Files.writeString(DIR.resolve(archivo), Contratos.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(real) + "\n");
                continue;
            }
            if (!Contratos.fixture("eventos/" + archivo).equals(real)) {
                distintos.add(tipo.getSimpleName());
            }
            // El contrato se lee con el mismo record sin perder campos y pasa sus validaciones.
            Contratos.leer("eventos/" + archivo, tipo);
        }
        assertThat(distintos).as("records cuyo JSON cambió respecto de su contrato").isEmpty();
    }
}
