package com.tranqui.turnos.interno;

import com.tranqui.turnos.datos.Personas;
import com.tranqui.turnos.datos.Turnos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El primer arranque de turnos: copia todo con la base vacía, se saltea con datos y reintenta si el monolito falla. */
class CargaInicialTest {

    private final Copias copias = mock(Copias.class);
    private final Turnos turnos = mock(Turnos.class);
    private final Personas personas = mock(Personas.class);
    private final ClienteInterno interno = mock(ClienteInterno.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);

    @BeforeEach
    void setUp() {
        doAnswer(inv -> {
            Consumer<TransactionStatus> accion = inv.getArgument(0);
            accion.accept(null);
            return null;
        }).when(tx).executeWithoutResult(any());
    }

    private CargaInicial carga(boolean habilitada) {
        return new CargaInicial(copias, turnos, personas, interno, tx, habilitada);
    }

    @Test
    void conBaseVacia_copiaYAjustaLaSecuencia() {
        when(interno.turnos(anyInt(), anyInt())).thenReturn(List.of());
        CargaInicial c = carga(true);
        assertThat(c.listo()).isFalse();
        c.cargarConReintentos();
        assertThat(c.listo()).isTrue();
        verify(copias).copiarPersonas();
        verify(copias).copiarProfesionales();
        verify(turnos).ajustarSecuencia();
    }

    @Test
    void conDatos_noCopia() {
        when(turnos.cantidad()).thenReturn(4L);
        CargaInicial c = carga(true);
        c.cargarConReintentos();
        assertThat(c.listo()).isTrue();
        verify(tx, never()).executeWithoutResult(any());
    }

    @Test
    void siFallaReintenta() {
        when(copias.copiarPersonas()).thenThrow(new IllegalStateException("monolito caído")).thenReturn(0);
        when(interno.turnos(anyInt(), anyInt())).thenReturn(List.of());
        CargaInicial c = carga(true);
        c.cargarConReintentos();
        assertThat(c.listo()).isTrue();
        verify(copias, times(2)).copiarPersonas();
    }

    @Test
    void interrumpidaODeshabilitada() {
        Thread.currentThread().interrupt();
        try {
            CargaInicial c = carga(true);
            c.cargarConReintentos();
            assertThat(c.listo()).isFalse();
        } finally {
            Thread.interrupted();
        }
        CargaInicial apagada = carga(false);
        apagada.alArrancar();
        assertThat(apagada.listo()).isTrue();
        verify(copias, never()).copiarPersonas();
    }
}
