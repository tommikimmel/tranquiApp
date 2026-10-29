package com.tranqui.eventos.contratos;

import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContratosTest {

    private static final String RUTA = "prueba/forma.json";

    @Test
    void mismaForma_aceptaOtrosValoresYNulos() {
        Contratos.mismaForma(RUTA, "{\"id\": 7, \"nombre\": \"Bea\", \"fin\": \"2026-11-03\", \"franjas\": [{\"dia\": \"MARTES\"}, {\"dia\": null}]}");
        Contratos.mismaForma(RUTA, "{\"id\": 7, \"nombre\": null, \"fin\": null, \"franjas\": []}");
    }

    @Test
    void mismaForma_detectaCampoFaltanteSobranteYTipoDistinto() {
        assertThatThrownBy(() -> Contratos.mismaForma(RUTA, "{\"id\": \"7\", \"fin\": null, \"franjas\": [{\"day\": \"LUNES\"}], \"extra\": 1}"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("$.id: se esperaba NUMBER y vino STRING")
                .hasMessageContaining("$.nombre: falta")
                .hasMessageContaining("$.franjas[].dia: falta")
                .hasMessageContaining("$.franjas[].day: no está en el contrato")
                .hasMessageContaining("$.extra: no está en el contrato");
    }

    record Franja(String dia) {}
    record Completo(@Positive Long id, String nombre, List<Franja> franjas) {}
    record ConCampoDeMas(Long id, String apellido) {}
    record Invalido(@jakarta.validation.constraints.Max(0) Long id, String nombre) {}

    @Test
    void leer_exigeTodosLosCamposYLasValidaciones() {
        assertThat(Contratos.leer(RUTA, Completo.class).franjas()).extracting(Franja::dia).containsExactly("LUNES");
        assertThatThrownBy(() -> Contratos.leer(RUTA, ConCampoDeMas.class)).hasMessageContaining("ConCampoDeMas.apellido no está");
        assertThatThrownBy(() -> Contratos.leer("prueba/no-existe.json", Completo.class)).hasMessageContaining("No existe");
        assertThatThrownBy(() -> Contratos.leer(RUTA, Invalido.class)).hasMessageContaining("id: Max");
    }
}
