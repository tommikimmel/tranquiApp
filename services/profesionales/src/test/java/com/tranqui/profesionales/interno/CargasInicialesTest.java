package com.tranqui.profesionales.interno;

import com.tranqui.profesionales.datos.Informes;
import com.tranqui.profesionales.datos.Personas;
import com.tranqui.profesionales.datos.Profesionales;
import com.tranqui.profesionales.datos.Recetas;
import com.tranqui.profesionales.datos.Seguimientos;
import com.tranqui.profesionales.datos.Solicitudes;
import com.tranqui.profesionales.datos.TurnosAtendidos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El primer arranque de profesionales y de la clínica: qué copia, cuándo se saltea y cómo reintenta. */
class CargasInicialesTest {

    private final Profesionales profesionales = mock(Profesionales.class);
    private final Personas personas = mock(Personas.class);
    private final TurnosAtendidos turnosAtendidos = mock(TurnosAtendidos.class);
    private final Copias copias = mock(Copias.class);
    private final ClienteInterno interno = mock(ClienteInterno.class);
    private final SincronizacionMonolito sincronizacion = mock(SincronizacionMonolito.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private final Seguimientos seguimientos = mock(Seguimientos.class);
    private final Informes informes = mock(Informes.class);
    private final Recetas recetas = mock(Recetas.class);
    private final Solicitudes solicitudes = mock(Solicitudes.class);

    @BeforeEach
    void setUp() {
        doAnswer(inv -> {
            Consumer<TransactionStatus> accion = inv.getArgument(0);
            accion.accept(null);
            return null;
        }).when(tx).executeWithoutResult(any());
    }

    private CargaInicial carga() {
        return new CargaInicial(profesionales, personas, turnosAtendidos, copias, interno, sincronizacion, tx, true);
    }

    @Test
    void primerArranque_copiaTodoPaginandoYHabilitaLaSincronizacion() {
        ClienteInterno.ProfesionalMonolito p = mock(ClienteInterno.ProfesionalMonolito.class);
        when(p.disponibilidad()).thenReturn(List.of(mock(com.tranqui.eventos.data.DisponibilidadProfesionalData.Franja.class)));
        when(p.tarifas()).thenReturn(null);
        when(interno.profesionales(0, CargaInicial.PAGINA)).thenReturn(Collections.nCopies(CargaInicial.PAGINA, p));
        when(interno.profesionales(1, CargaInicial.PAGINA)).thenReturn(List.of());
        CargaInicial c = carga();
        assertThat(c.listo()).isFalse();
        c.cargarConReintentos();
        assertThat(c.listo()).isTrue();
        verify(sincronizacion, times(CargaInicial.PAGINA)).alta(p);
        verify(copias).copiarPersonas();
        verify(copias).copiarTurnos();
        verify(sincronizacion).habilitar();
    }

    @Test
    void conDatosPrevios_noCopiaNadaYQuedaLista() {
        when(profesionales.cantidad()).thenReturn(1L);
        when(personas.cantidad()).thenReturn(1L);
        when(turnosAtendidos.cantidad()).thenReturn(1L);
        CargaInicial c = carga();
        c.cargarConReintentos();
        assertThat(c.listo()).isTrue();
        verify(tx, never()).executeWithoutResult(any());
    }

    @Test
    void siFallaReintentaSinRepetirLoQueYaCopio() {
        when(interno.profesionales(anyInt(), anyInt())).thenReturn(List.of());
        when(copias.copiarPersonas()).thenThrow(new IllegalStateException("monolito caído")).thenReturn(0);
        CargaInicial c = carga();
        c.cargarConReintentos();
        assertThat(c.listo()).isTrue();
        verify(interno, times(1)).profesionales(0, CargaInicial.PAGINA);
        verify(copias, times(2)).copiarPersonas();
    }

    @Test
    void interrumpidaODeshabilitada() {
        Thread.currentThread().interrupt();
        try {
            CargaInicial c = carga();
            c.cargarConReintentos();
            assertThat(c.listo()).isFalse();
        } finally {
            Thread.interrupted();
        }
        CargaInicial apagada = new CargaInicial(profesionales, personas, turnosAtendidos, copias, interno, sincronizacion, tx, false);
        apagada.alArrancar();
        assertThat(apagada.listo()).isTrue();
        verify(sincronizacion).habilitar();
    }

    // ---------------------------------------------------------------- clínica

    private CargaInicialClinica clinica(boolean habilitada) {
        return new CargaInicialClinica(seguimientos, informes, recetas, solicitudes, interno, tx, habilitada);
    }

    @Test
    void clinica_primerArranqueCopiaLasCuatroTablas() {
        ClienteInterno.SeguimientoMonolito s = mock(ClienteInterno.SeguimientoMonolito.class);
        ClienteInterno.InformeMonolito i = mock(ClienteInterno.InformeMonolito.class);
        ClienteInterno.RecetaMonolito r = mock(ClienteInterno.RecetaMonolito.class);
        ClienteInterno.SolicitudMonolito so = mock(ClienteInterno.SolicitudMonolito.class);
        when(interno.seguimientos(0, CargaInicialClinica.PAGINA)).thenReturn(Collections.nCopies(CargaInicialClinica.PAGINA, s));
        when(interno.seguimientos(1, CargaInicialClinica.PAGINA)).thenReturn(List.of());
        when(interno.informes(anyInt(), anyInt())).thenReturn(List.of(i));
        when(interno.recetas(anyInt(), anyInt())).thenReturn(List.of(r));
        when(interno.solicitudes(anyInt(), anyInt())).thenReturn(List.of(so));
        CargaInicialClinica c = clinica(true);
        c.cargarConReintentos();
        assertThat(c.listo()).isTrue();
        verify(seguimientos, times(CargaInicialClinica.PAGINA)).insertar(any());
        verify(informes).insertar(any());
        verify(recetas).insertar(any());
        verify(solicitudes).insertar(any());
        verify(solicitudes).ajustarSecuencia();
    }

    @Test
    void clinica_conDatosReintentoEInterrupcion() {
        when(seguimientos.cantidad()).thenReturn(2L);
        CargaInicialClinica conDatos = clinica(true);
        conDatos.cargarConReintentos();
        assertThat(conDatos.listo()).isTrue();
        verify(tx, never()).executeWithoutResult(any());

        when(seguimientos.cantidad()).thenReturn(0L);
        when(interno.seguimientos(eq(0), anyInt())).thenThrow(new IllegalStateException("caído")).thenReturn(List.of());
        when(interno.informes(anyInt(), anyInt())).thenReturn(List.of());
        when(interno.recetas(anyInt(), anyInt())).thenReturn(List.of());
        when(interno.solicitudes(anyInt(), anyInt())).thenReturn(List.of());
        CargaInicialClinica reintento = clinica(true);
        reintento.cargarConReintentos();
        assertThat(reintento.listo()).isTrue();

        Thread.currentThread().interrupt();
        try {
            CargaInicialClinica cortada = clinica(true);
            cortada.cargarConReintentos();
            assertThat(cortada.listo()).isFalse();
        } finally {
            Thread.interrupted();
        }
        CargaInicialClinica apagada = clinica(false);
        apagada.alArrancar();
        assertThat(apagada.listo()).isTrue();
    }
}
