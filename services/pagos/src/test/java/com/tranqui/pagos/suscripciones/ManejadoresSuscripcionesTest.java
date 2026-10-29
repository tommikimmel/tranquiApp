package com.tranqui.pagos.suscripciones;

import com.tranqui.eventos.EventEnvelope;
import com.tranqui.eventos.data.UsuarioRegistradoData;
import com.tranqui.eventos.data.VerificacionResueltaData;
import com.tranqui.pagos.datos.Planes;
import com.tranqui.pagos.datos.Suscripciones;
import com.tranqui.pagos.datos.Suscriptores;
import com.tranqui.pagos.interno.CargaInicialSuscripciones;
import com.tranqui.pagos.interno.Copias;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Alta y verificación de la suscripción por eventos de identidad: rechazo, estados que no cambian y carreras. */
class ManejadoresSuscripcionesTest {

    private final Suscripciones suscripciones = mock(Suscripciones.class);
    private final Planes planes = mock(Planes.class);
    private final Suscriptores suscriptores = mock(Suscriptores.class);
    private final Copias copias = mock(Copias.class);
    private final SuscripcionService service = mock(SuscripcionService.class);
    private final CargaInicialSuscripciones carga = mock(CargaInicialSuscripciones.class);

    @BeforeEach
    void setUp() {
        when(carga.listo()).thenReturn(true);
        when(planes.porCodigo(anyString())).thenReturn(Optional.of(new Planes.Plan(1L, "consultorio", "Consultorio", "",
                BigDecimal.TEN, null, null, "monthly", 1, null, false, true, null, null)));
    }

    private ManejadoresSuscripciones manejadores(boolean habilitada) {
        return new ManejadoresSuscripciones(suscripciones, planes, suscriptores, copias, service, carga, habilitada);
    }

    private static <T> EventEnvelope<T> evento(T data) {
        return EventEnvelope.<T>builder().eventId(UUID.randomUUID()).eventType("x").data(data).build();
    }

    private static Suscripciones.Suscripcion sub(String estado) {
        return new Suscripciones.Suscripcion(100L, 1, 10L, 1L, estado, 1, "MANUAL_TRANSFER", null, BigDecimal.TEN, "monthly",
                null, null, null, null, false, null, false, LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void deshabilitado_noHaceNada() {
        manejadores(false).suscripcionAlta().manejar(evento(new UsuarioRegistradoData(10L, "a@t.com", "A", "B", "PSIQUIATRA")));
        manejadores(false).suscripcionVerificacion().manejar(evento(new VerificacionResueltaData(10L, "VERIFICADO")));
        verifyNoInteractions(suscripciones);
    }

    @Test
    void alta_yaExistenteODeUnPaciente_noCrea() {
        when(suscripciones.porProfesionalId(10L)).thenReturn(Optional.of(sub("ACTIVE")));
        manejadores(true).suscripcionAlta().manejar(evento(new UsuarioRegistradoData(10L, "a@t.com", "A", "B", "PSIQUIATRA")));
        manejadores(true).suscripcionAlta().manejar(evento(new UsuarioRegistradoData(11L, "p@t.com", "P", "Q", "PACIENTE")));
        verify(copias).refrescarSuscriptor(11L);
        verify(suscripciones, never()).insertarNuevo(any());
    }

    @Test
    void alta_carreraDeInsercion_seTolera() {
        when(suscripciones.insertarNuevo(any())).thenThrow(new IllegalStateException("duplicada"));
        manejadores(true).suscripcionAlta().manejar(evento(new UsuarioRegistradoData(10L, "a@t.com", "A", "B", "PSIQUIATRA")));
        verify(suscripciones).insertarNuevo(any());
    }

    @Test
    void rechazo_pasaARejected() {
        when(suscripciones.porProfesionalId(10L)).thenReturn(Optional.of(sub("PENDING_VERIFICATION")));
        manejadores(true).suscripcionVerificacion().manejar(evento(new VerificacionResueltaData(10L, "RECHAZADO")));
        ArgumentCaptor<Suscripciones.Suscripcion> captor = ArgumentCaptor.forClass(Suscripciones.Suscripcion.class);
        verify(suscripciones).actualizar(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("REJECTED");
        verify(service).logEvent(eq(100L), eq("PROFESSIONAL_REJECTED"), eq("PENDING_VERIFICATION"), eq("REJECTED"),
                anyString(), anyString(), anyString());
    }

    @Test
    void verificado_desdeUnEstadoAvanzado_noCambia() {
        when(suscripciones.porProfesionalId(10L)).thenReturn(Optional.of(sub("ACTIVE")));
        manejadores(true).suscripcionVerificacion().manejar(evento(new VerificacionResueltaData(10L, "VERIFICADO")));
        verify(suscripciones, never()).actualizar(any());
    }

    @Test
    void ordenInvertido_conCarreraUsaLaFilaQueGanoYSinPlanNoHaceNada() {
        when(suscripciones.porProfesionalId(10L)).thenReturn(Optional.empty(), Optional.of(sub("PENDING_VERIFICATION")));
        when(suscripciones.insertarNuevo(any())).thenThrow(new IllegalStateException("duplicada"));
        manejadores(true).suscripcionVerificacion().manejar(evento(new VerificacionResueltaData(10L, "VERIFICADO")));
        verify(suscripciones).actualizar(any());

        when(suscripciones.porProfesionalId(12L)).thenReturn(Optional.empty());
        when(planes.porCodigo(anyString())).thenReturn(Optional.empty());
        manejadores(true).suscripcionVerificacion().manejar(evento(new VerificacionResueltaData(12L, "VERIFICADO")));
        verify(copias).refrescarSuscriptor(12L);
    }
}
