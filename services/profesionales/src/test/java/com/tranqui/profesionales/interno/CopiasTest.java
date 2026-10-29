package com.tranqui.profesionales.interno;

import com.tranqui.eventos.data.TurnoActualizadoData;
import com.tranqui.profesionales.datos.Personas;
import com.tranqui.profesionales.datos.TurnosAtendidos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Las copias de personas y turnos atendidos: refresco, carga paginada y resincronización con filas inválidas. */
class CopiasTest {

    private final ClienteInterno interno = mock(ClienteInterno.class);
    private final Personas personas = mock(Personas.class);
    private final TurnosAtendidos turnos = mock(TurnosAtendidos.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<CargaInicial> provider = mock(ObjectProvider.class);
    private final Copias copias = new Copias(interno, personas, turnos, tx, provider);

    private static ClienteInterno.TurnoMonolito turno(Long id, int version) {
        ClienteInterno.TurnoMonolito m = mock(ClienteInterno.TurnoMonolito.class);
        TurnoActualizadoData d = id == null ? null : mock(TurnoActualizadoData.class);
        if (d != null) {
            when(d.turnoId()).thenReturn(id);
            when(d.profesionalId()).thenReturn(2L);
            when(d.pacienteId()).thenReturn(1L);
            when(d.fecha()).thenReturn(LocalDate.of(2026, 11, 5));
            when(d.estado()).thenReturn("CONFIRMADO");
        }
        when(m.turno()).thenReturn(d);
        when(m.version()).thenReturn(version);
        return m;
    }

    @Test
    void refrescarPersona_guardaOSiNoExisteLaDejaComoEsta() {
        ClienteInterno.PacienteMonolito p = mock(ClienteInterno.PacienteMonolito.class);
        when(interno.paciente(1L)).thenReturn(p);
        when(interno.paciente(2L)).thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null,
                new byte[0], StandardCharsets.UTF_8));
        copias.refrescarPersona(1L);
        copias.refrescarPersona(2L);
        copias.refrescarPersona(3L);
        verify(personas, times(1)).guardar(any());
    }

    @Test
    void copiarPersonas_pagina() {
        ClienteInterno.PacienteMonolito p = mock(ClienteInterno.PacienteMonolito.class);
        when(interno.pacientes(0, Copias.PAGINA)).thenReturn(Collections.nCopies(Copias.PAGINA, p));
        when(interno.pacientes(1, Copias.PAGINA)).thenReturn(List.of(p));
        assertThat(copias.copiarPersonas()).isEqualTo(Copias.PAGINA + 1);
    }

    @Test
    void copiarTurnos_delMonolitoPaginado() {
        ClienteInterno.TurnoMonolito t = turno(10L, 1);
        when(interno.pacientes(anyInt(), anyInt())).thenReturn(List.of());
        when(interno.turnos(0, Copias.PAGINA)).thenReturn(Collections.nCopies(Copias.PAGINA, t));
        when(interno.turnos(1, Copias.PAGINA)).thenReturn(List.of());
        assertThat(copias.copiarTurnos()).isEqualTo(Copias.PAGINA);
    }

    @Test
    void copiarTurnos_delServicio_salteaLasFilasInvalidas() {
        when(interno.fuenteIdentidad()).thenReturn(true);
        List<ClienteInterno.TurnoMonolito> filas = Arrays.asList(turno(10L, 2), turno(null, 1), turno(-3L, 1), turno(11L, -1), null);
        when(interno.turnosDelServicio()).thenReturn(filas);
        assertThat(copias.copiarTurnos()).isEqualTo(1);
        verify(turnos).guardar(10L, 2L, 1L, LocalDate.of(2026, 11, 5), "CONFIRMADO", 2);
        verify(turnos, never()).guardar(eq(11L), any(), any(), any(), any(), anyInt());
    }

    @Test
    @SuppressWarnings("unchecked")
    void resincronizar_soloConLaCargaListaYSinRomperSiFalla() {
        copias.resincronizarTurnos();
        CargaInicial carga = mock(CargaInicial.class);
        when(provider.getIfAvailable()).thenReturn(carga);
        copias.resincronizarTurnos();
        verify(tx, never()).execute(any());

        when(carga.listo()).thenReturn(true);
        when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<Integer>) inv.getArgument(0)).doInTransaction(null));
        when(interno.fuenteIdentidad()).thenReturn(true);
        when(interno.turnosDelServicio()).thenReturn(List.of()).thenThrow(new IllegalStateException("caído"));
        copias.resincronizarTurnos();
        copias.resincronizarTurnos();
        verify(tx, times(2)).execute(any());
    }
}
