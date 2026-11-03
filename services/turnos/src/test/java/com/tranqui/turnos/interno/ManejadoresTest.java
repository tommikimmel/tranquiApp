package com.tranqui.turnos.interno;

import com.tranqui.eventos.EventEnvelope;
import com.tranqui.eventos.data.CheckoutCreadoData;
import com.tranqui.eventos.data.CuentaMpDesvinculadaData;
import com.tranqui.eventos.data.CuentaMpVinculadaData;
import com.tranqui.eventos.data.DisponibilidadProfesionalData;
import com.tranqui.eventos.data.MeetCreadoData;
import com.tranqui.eventos.data.PagoAprobadoData;
import com.tranqui.eventos.data.PagoRechazadoData;
import com.tranqui.eventos.data.PerfilProfesionalData;
import com.tranqui.eventos.data.ProfesionalActualizadoData;
import com.tranqui.eventos.data.RecetaEmitidaData;
import com.tranqui.eventos.data.ReembolsoRealizadoData;
import com.tranqui.eventos.data.TarifasProfesionalData;
import com.tranqui.eventos.data.UsuarioActualizadoData;
import com.tranqui.eventos.data.UsuarioRegistradoData;
import com.tranqui.turnos.datos.Personas;
import com.tranqui.turnos.datos.Profesionales;
import com.tranqui.turnos.turnos.Cobros;
import com.tranqui.turnos.turnos.Gestion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cada evento que consume turnos llega a la acción que corresponde. */
class ManejadoresTest {

    private final Manejadores manejadores = new Manejadores();
    private final Cobros cobros = mock(Cobros.class);
    private final Personas personas = mock(Personas.class);
    private final Profesionales profesionales = mock(Profesionales.class);
    private final Copias copias = mock(Copias.class);

    private static <T> EventEnvelope<T> evento(T data) {
        return EventEnvelope.<T>builder().eventId(UUID.randomUUID()).eventType("x").aggregateVersion(1).data(data).build();
    }

    @Test
    void pagosYCalendario_vanACobros() {
        PagoAprobadoData aprobado = mock(PagoAprobadoData.class);
        PagoRechazadoData rechazado = mock(PagoRechazadoData.class);
        ReembolsoRealizadoData reembolso = mock(ReembolsoRealizadoData.class);
        CheckoutCreadoData checkout = mock(CheckoutCreadoData.class);
        MeetCreadoData meet = mock(MeetCreadoData.class);
        manejadores.turnoPagado(cobros).manejar(evento(aprobado));
        manejadores.turnoPagoRechazado(cobros).manejar(evento(rechazado));
        manejadores.turnoReembolsado(cobros).manejar(evento(reembolso));
        manejadores.checkoutEnTurno(cobros).manejar(evento(checkout));
        manejadores.meetEnTurno(cobros).manejar(evento(meet));
        verify(cobros).pagoAprobado(aprobado);
        verify(cobros).pagoRechazado(rechazado);
        verify(cobros).reembolsado(reembolso);
        verify(cobros).checkout(checkout);
        verify(cobros).meet(meet);
    }

    @Test
    void cuentasDeMercadoPago() {
        CuentaMpVinculadaData vinculada = mock(CuentaMpVinculadaData.class);
        when(vinculada.profesionalId()).thenReturn(2L);
        CuentaMpDesvinculadaData desvinculada = mock(CuentaMpDesvinculadaData.class);
        when(desvinculada.profesionalId()).thenReturn(3L);
        manejadores.cuentaMpVinculada(personas).manejar(evento(vinculada));
        manejadores.cuentaMpDesvinculada(personas).manejar(evento(desvinculada));
        verify(personas).mercadoPago(2L, true);
        verify(personas).mercadoPago(3L, false);
    }

    @Test
    void profesionales_perfilDisponibilidadTarifasYRecetas() {
        ProfesionalActualizadoData conPerfil = mock(ProfesionalActualizadoData.class);
        PerfilProfesionalData perfil = mock(PerfilProfesionalData.class);
        when(conPerfil.profesionalId()).thenReturn(2L);
        when(conPerfil.nombre()).thenReturn("Lucía");
        when(conPerfil.perfil()).thenReturn(perfil);
        ProfesionalActualizadoData sinPerfil = mock(ProfesionalActualizadoData.class);
        when(sinPerfil.profesionalId()).thenReturn(3L);
        manejadores.agendaProfesional(profesionales, personas).manejar(evento(conPerfil));
        manejadores.agendaProfesional(profesionales, personas).manejar(evento(sinPerfil));
        verify(personas).nombre(eq(2L), eq("Lucía"), any());
        verify(profesionales).guardarPerfil(2L, perfil);
        verify(profesionales, never()).guardarPerfil(eq(3L), any());

        manejadores.disponibilidadProfesional(profesionales).manejar(evento(new DisponibilidadProfesionalData(2L, List.of())));
        manejadores.tarifasProfesional(profesionales).manejar(evento(new TarifasProfesionalData(2L, List.of())));
        verify(profesionales).reemplazarDisponibilidad(2L, List.of());
        verify(profesionales).reemplazarTarifas(2L, List.of());

        Gestion gestion = mock(Gestion.class);
        RecetaEmitidaData receta = mock(RecetaEmitidaData.class);
        when(receta.profesionalId()).thenReturn(2L);
        when(receta.pacienteId()).thenReturn(1L);
        manejadores.recetaEmitida(gestion).manejar(evento(receta));
        verify(gestion).recetasEmitidas(2L, 1L);
    }

    @Test
    void identidad_refrescaLaPersonaYReintentaSiFalla() {
        UsuarioRegistradoData registrado = mock(UsuarioRegistradoData.class);
        when(registrado.usuarioId()).thenReturn(1L);
        UsuarioActualizadoData actualizado = mock(UsuarioActualizadoData.class);
        when(actualizado.usuarioId()).thenReturn(4L);
        manejadores.usuarioRegistrado(copias).manejar(evento(registrado));
        verify(copias).refrescarPersona(1L);

        doThrow(new IllegalStateException("monolito caído")).when(copias).refrescarPersona(4L);
        assertThatThrownBy(() -> manejadores.usuarioActualizado(copias).manejar(evento(actualizado)))
                .isInstanceOf(IllegalStateException.class);
    }
}
