package com.tranqui.chat.interno;

import com.tranqui.chat.copias.Copias;
import com.tranqui.eventos.ValidadorEventos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El primer arranque del chat: participantes, turnos y mensajes por lotes; con datos no copia y reintenta si falla. */
class CargaInicialTest {

    private final Copias copias = mock(Copias.class);
    private final ClienteInterno interno = mock(ClienteInterno.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);

    @BeforeEach
    void setUp() {
        doAnswer(inv -> {
            Consumer<TransactionStatus> accion = inv.getArgument(0);
            accion.accept(null);
            return null;
        }).when(tx).executeWithoutResult(any());
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
    }

    private CargaInicial carga(boolean habilitada) {
        return new CargaInicial(copias, interno, jdbc, tx, habilitada);
    }

    @Test
    void primerArranque_copiaTodoYRetomaLosMensajesPorId() {
        when(interno.contactos(0, 500)).thenReturn(List.of(new ClienteInterno.Contacto(1L, "a@t.com", "Ana", "Paz", "PACIENTE", true)));
        when(interno.turnosConfirmados()).thenReturn(List.of(new ClienteInterno.TurnoConfirmado(9L, 1L, 2L, LocalDate.now(), "10:00")));
        List<ClienteInterno.MensajeMonolito> pagina = new ArrayList<>();
        for (long i = 1; i <= 500; i++) {
            pagina.add(new ClienteInterno.MensajeMonolito(i, 1L, 2L, "hola", false, i % 2 == 0 ? LocalDateTime.now() : null));
        }
        when(interno.mensajes(0L, 500)).thenReturn(pagina);
        when(interno.mensajes(500L, 500)).thenReturn(List.of());

        carga(true).cargarConReintentos();

        verify(copias).guardarParticipante(1L, "a@t.com", "Ana", "Paz", "PACIENTE");
        verify(copias).guardarTurnoConfirmado(eq(9L), eq(1L), eq(2L), any(), eq("10:00"));
        verify(jdbc, times(500)).update(anyString(), any(Object[].class));
        verify(interno).mensajes(500L, 500);
    }

    @Test
    void unaFilaInvalidaEnUnaPaginaLlena_noCortaLaCarga() {
        // Lo que devuelve el cliente real: la página validada, con una fila salteada (Fase 10, T2).
        List<ClienteInterno.Contacto> llena = new ArrayList<>();
        for (long i = 1; i <= 500; i++) {
            llena.add(new ClienteInterno.Contacto(i == 7 ? -7L : i, "a" + i + "@t.com", "Ana", "Paz", "PACIENTE", true));
        }
        when(interno.contactos(0, 500)).thenReturn(ValidadorEventos.respuesta(llena, "prueba"));
        when(interno.contactos(1, 500)).thenReturn(List.of(new ClienteInterno.Contacto(501L, "b@t.com", "Bea", "Ruiz", "PACIENTE", true)));
        when(interno.mensajes(0L, 500)).thenReturn(List.of());

        carga(true).cargarConReintentos();

        verify(copias, times(500)).guardarParticipante(anyLong(), anyString(), anyString(), anyString(), anyString());
        verify(copias).guardarParticipante(501L, "b@t.com", "Bea", "Ruiz", "PACIENTE");
    }

    @Test
    void conDatos_noCopia() {
        when(copias.cantidadParticipantes()).thenReturn(3L);
        carga(true).cargarConReintentos();
        verify(interno, never()).contactos(anyInt(), anyInt());
    }

    @Test
    void siFallaReintentaYSiLaInterrumpenSeCorta() {
        when(interno.contactos(anyInt(), anyInt())).thenThrow(new IllegalStateException("caído")).thenReturn(List.of());
        when(interno.turnosConfirmados()).thenReturn(List.of());
        when(interno.mensajes(anyLong(), anyInt())).thenReturn(List.of());
        carga(true).cargarConReintentos();
        verify(interno, times(2)).contactos(anyInt(), anyInt());

        Thread.currentThread().interrupt();
        try {
            carga(true).cargarConReintentos();
        } finally {
            Thread.interrupted();
        }
        carga(false).alArrancar();
        verify(interno, times(1)).turnosConfirmados();
    }
}
