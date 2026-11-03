package com.tranqui.app.service;

import com.tranqui.app.model.SuscripcionAcceso;
import com.tranqui.app.repository.SuscripcionAccesoRepository;
import com.tranqui.eventos.data.SuscripcionCambioEstadoV2Data;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CopiaSuscripcionesTest {

    @Test
    void conFlagFalse_noHaceNada() {
        SuscripcionAccesoRepository repo = mock(SuscripcionAccesoRepository.class);
        CopiaSuscripciones copia = new CopiaSuscripciones(repo, false);

        SuscripcionCambioEstadoV2Data data = new SuscripcionCambioEstadoV2Data(
                1L, 10L, "PENDING", "ACTIVE", LocalDate.of(2026, 12, 1),
                "consultorio", true, true,
                LocalDateTime.of(2026, 12, 1, 12, 0),
                LocalDateTime.of(2026, 12, 15, 12, 0)
        );

        copia.aplicarEvento(data, 1);
        copia.upsert(10L, "ACTIVE", "consultorio", true, true,
                LocalDateTime.of(2026, 12, 1, 12, 0),
                LocalDateTime.of(2026, 12, 15, 12, 0), 1);

        verifyNoInteractions(repo);
    }

    @Test
    void conFlagTrue_insertaRegistroNuevo() {
        SuscripcionAccesoRepository repo = mock(SuscripcionAccesoRepository.class);
        when(repo.findById(10L)).thenReturn(Optional.empty());

        CopiaSuscripciones copia = new CopiaSuscripciones(repo, true);

        SuscripcionCambioEstadoV2Data data = new SuscripcionCambioEstadoV2Data(
                1L, 10L, "PENDING", "ACTIVE", LocalDate.of(2026, 12, 1),
                "clinico", true, true,
                LocalDateTime.of(2026, 12, 1, 18, 30),
                LocalDateTime.of(2026, 12, 15, 18, 30)
        );

        copia.aplicarEvento(data, 2);

        ArgumentCaptor<SuscripcionAcceso> captor = ArgumentCaptor.forClass(SuscripcionAcceso.class);
        verify(repo).save(captor.capture());

        SuscripcionAcceso guardado = captor.getValue();
        assertThat(guardado.getProfessionalId()).isEqualTo(10L);
        assertThat(guardado.getEstado()).isEqualTo("ACTIVE");
        assertThat(guardado.getPlanCode()).isEqualTo("clinico");
        assertThat(guardado.isAcceso()).isTrue();
        assertThat(guardado.isPuedeRecetar()).isTrue();
        assertThat(guardado.getPeriodoHasta()).isEqualTo(LocalDateTime.of(2026, 12, 1, 18, 30));
        assertThat(guardado.getGraciaHasta()).isEqualTo(LocalDateTime.of(2026, 12, 15, 18, 30));
        assertThat(guardado.getVersion()).isEqualTo(2L);
        assertThat(guardado.getActualizadoEn()).isNotNull();
    }

    @Test
    void conFlagTrue_actualizaSiVersionEsMayor() {
        SuscripcionAccesoRepository repo = mock(SuscripcionAccesoRepository.class);
        SuscripcionAcceso existente = SuscripcionAcceso.builder()
                .professionalId(10L)
                .estado("PENDING")
                .planCode("consultorio")
                .acceso(false)
                .puedeRecetar(false)
                .version(1L)
                .build();
        when(repo.findById(10L)).thenReturn(Optional.of(existente));

        CopiaSuscripciones copia = new CopiaSuscripciones(repo, true);
        copia.upsert(10L, "ACTIVE", "clinico", true, true, LocalDateTime.of(2026, 12, 1, 23, 59), null, 3);

        verify(repo).save(existente);
        assertThat(existente.getEstado()).isEqualTo("ACTIVE");
        assertThat(existente.getPlanCode()).isEqualTo("clinico");
        assertThat(existente.isAcceso()).isTrue();
        assertThat(existente.isPuedeRecetar()).isTrue();
        assertThat(existente.getVersion()).isEqualTo(3L);
        assertThat(existente.getPeriodoHasta()).isEqualTo(LocalDateTime.of(2026, 12, 1, 23, 59));
    }

    @Test
    void conFlagTrue_ignoraSiVersionEsMenorOIgual() {
        SuscripcionAccesoRepository repo = mock(SuscripcionAccesoRepository.class);
        SuscripcionAcceso existente = SuscripcionAcceso.builder()
                .professionalId(10L)
                .estado("ACTIVE")
                .version(5L)
                .build();
        when(repo.findById(10L)).thenReturn(Optional.of(existente));

        CopiaSuscripciones copia = new CopiaSuscripciones(repo, true);
        copia.upsert(10L, "CANCELLED", "clinico", false, false, null, null, 5);
        copia.upsert(10L, "CANCELLED", "clinico", false, false, null, null, 4);

        verify(repo, never()).save(any());
    }
}