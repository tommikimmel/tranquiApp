package com.tranqui.identidad.interno;

import com.tranqui.eventos.contratos.Contratos;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fase 10, T2, lado consumidor: el monolito manda las tablas de identidad como mapas sin esquema, así que el contrato
 * son sus claves. Toda clave que lee la carga inicial tiene que estar en alguno de los contratos.
 */
class ContratosConsumidorTest {

    @Test
    void cadaClaveQueLeeLaCargaInicialEstaEnElContrato() throws Exception {
        Set<String> enLosContratos = new HashSet<>();
        for (String tabla : new String[] {"usuarios", "tickets", "ticket-mensajes", "novedades-envios"}) {
            Contratos.fixture("internas/monolito/identidad-" + tabla + ".json").get(0).fieldNames().forEachRemaining(enLosContratos::add);
        }
        String fuente = Files.readString(Path.of("src/main/java/com/tranqui/identidad/interno/CargaInicialIdentidad.java"));
        Set<String> leidas = new TreeSet<>();
        Matcher m = Pattern.compile("\\.get\\(\"([a-z_]+)\"\\)").matcher(fuente);
        while (m.find()) {
            leidas.add(m.group(1));
        }
        assertThat(leidas).hasSizeGreaterThan(30);
        assertThat(enLosContratos).containsAll(leidas);
    }
}
