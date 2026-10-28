package com.tranqui.eventos;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TopicosTest {

    @Test
    void todosLosTopicos_cumplenConLaExpresionRegular() {
        assertThat(Topicos.TODOS_LOS_TOPICOS).isNotEmpty();
        assertThat(Topicos.TODOS_LOS_TOPICOS).hasSize(43);

        for (String topico : Topicos.TODOS_LOS_TOPICOS) {
            assertThat(Topicos.esValido(topico))
                    .as("El tópico '%s' no cumple con el formato <dominio>.<entidad>.<evento>.v<n>", topico)
                    .isTrue();
        }
    }

    @Test
    void armarTopico_combinaEventTypeYVersion() {
        String topico = Topicos.armarTopico("turnos.turno.confirmado", 1);
        assertThat(topico).isEqualTo("turnos.turno.confirmado.v1");
        assertThat(Topicos.esValido(topico)).isTrue();
    }

    @Test
    void armarTopico_siYaTieneVersion_noLaDuplica() {
        String topico = Topicos.armarTopico("turnos.turno.confirmado.v1", 1);
        assertThat(topico).isEqualTo("turnos.turno.confirmado.v1");
    }

    @Test
    void armarTopico_conParametrosInvalidos_lanzaExcepcion() {
        assertThatThrownBy(() -> Topicos.armarTopico(null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Topicos.armarTopico("   ", 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void esValido_rechazaFormatosInvalidos() {
        assertThat(Topicos.esValido(null)).isFalse();
        assertThat(Topicos.esValido("")).isFalse();
        assertThat(Topicos.esValido("turnos.turno.confirmado")).isFalse(); // falta .v1
        assertThat(Topicos.esValido("Turnos.Turno.Confirmado.v1")).isFalse(); // mayúsculas
        assertThat(Topicos.esValido("turnos.v1")).isFalse(); // partes incompletas
    }

    @Test
    void extraerDominioTipoEventoYVersion() {
        String topico = "turnos.turno.confirmado.v1";
        assertThat(Topicos.extraerDominio(topico)).isEqualTo("turnos");
        assertThat(Topicos.extraerTipoEvento(topico)).isEqualTo("turnos.turno.confirmado");
        assertThat(Topicos.extraerVersion(topico)).isEqualTo(1);

        assertThat(Topicos.extraerDominio("pagos.pago.aprobado.v2")).isEqualTo("pagos");
        assertThat(Topicos.extraerTipoEvento("pagos.pago.aprobado.v2")).isEqualTo("pagos.pago.aprobado");
        assertThat(Topicos.extraerVersion("pagos.pago.aprobado.v2")).isEqualTo(2);

        assertThat(Topicos.extraerDominio(null)).isEqualTo("monolito");
        assertThat(Topicos.extraerTipoEvento(null)).isEmpty();
        assertThat(Topicos.extraerVersion(null)).isEqualTo(1);
    }
}
