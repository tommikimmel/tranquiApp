package com.tranqui.calendario.interno;

import com.tranqui.calendario.datos.CuentasGoogle;
import com.tranqui.calendario.datos.EventosExternos;
import com.tranqui.calendario.datos.Participantes;
import com.tranqui.calendario.datos.TurnosAgendados;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El consumo de eventos espera a la carga inicial (encontrado en la verificación G6): una confirmación
 * vieja que quedó en la cola durante una vuelta atrás no puede procesarse antes de copiar el turno.
 */
class CargaInicialTest {

    private final Participantes participantes = mock(Participantes.class);
    private final CuentasGoogle cuentas = mock(CuentasGoogle.class);
    private final EventosExternos externos = mock(EventosExternos.class);
    private final TurnosAgendados turnos = mock(TurnosAgendados.class);
    private final ClienteInterno interno = mock(ClienteInterno.class);

    private CargaInicial carga(boolean habilitada) {
        return new CargaInicial(participantes, cuentas, externos, turnos, interno, habilitada);
    }

    @Test
    void sinCargaInicial_noHayQueEsperar() {
        assertThat(carga(false).listo()).isTrue();
    }

    @Test
    void primerArranque_listoRecienDespuesDeCopiar() {
        when(interno.contactos(anyInt(), anyInt())).thenReturn(List.of());
        when(interno.eventosExternos(anyLong(), anyInt())).thenReturn(List.of());
        CargaInicial carga = carga(true);
        assertThat(carga.listo()).isFalse();

        carga.cargarConReintentos();

        assertThat(carga.listo()).isTrue();
        verify(interno).turnosConfirmados();
    }

    @Test
    void conDatos_listoSinCopiarNada() {
        when(participantes.cantidad()).thenReturn(28L);
        CargaInicial carga = carga(true);

        carga.cargarConReintentos();

        assertThat(carga.listo()).isTrue();
        verify(interno, never()).turnosConfirmados();
    }

    @Test
    void copiaEventosPaginandoPorIdYSalteaLosDocumentos() {
        when(interno.contactos(anyInt(), anyInt())).thenReturn(List.of());
        java.util.List<ClienteInterno.EventoExterno> pagina = new java.util.ArrayList<>();
        for (long i = 1; i <= 500; i++) pagina.add(new ClienteInterno.EventoExterno(i, 2L, "g" + i, "t", null, null, false, "confirmed"));
        when(interno.eventosExternos(0L, 500)).thenReturn(pagina);
        when(interno.eventosExternos(500L, 500)).thenReturn(List.of());
        when(interno.turnosConfirmados()).thenReturn(List.of(
                new ClienteInterno.TurnoConfirmado(1L, 3L, 2L, java.time.LocalDate.of(2026, 11, 5), "10:00", "10:45", "ONLINE", true, "g", "m"),
                new ClienteInterno.TurnoConfirmado(2L, 3L, 2L, java.time.LocalDate.of(2026, 11, 5), "11:00", null, "ONLINE", null, null, null),
                new ClienteInterno.TurnoConfirmado(3L, 3L, 2L, java.time.LocalDate.of(2026, 11, 5), "12:00", null, null, false, null, null),
                new ClienteInterno.TurnoConfirmado(4L, 3L, 2L, java.time.LocalDate.of(2026, 11, 5), null, null, null, true, null, null)));
        carga(true).cargarConReintentos();
        verify(externos, org.mockito.Mockito.times(500)).copiar(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.any());
        verify(turnos, org.mockito.Mockito.times(2)).guardar(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void siFallaReintentaYSiLaInterrumpenSeCorta() {
        when(interno.contactos(anyInt(), anyInt())).thenThrow(new IllegalStateException("monolito caído")).thenReturn(List.of());
        when(interno.eventosExternos(anyLong(), anyInt())).thenReturn(List.of());
        CargaInicial carga = carga(true);
        carga.cargarConReintentos();
        assertThat(carga.listo()).isTrue();

        Thread.currentThread().interrupt();
        try {
            CargaInicial cortada = carga(true);
            cortada.cargarConReintentos();
            assertThat(cortada.listo()).isFalse();
        } finally {
            Thread.interrupted();
        }
        carga(false).alArrancar();
    }
}
