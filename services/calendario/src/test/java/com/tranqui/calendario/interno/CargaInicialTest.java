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
}
