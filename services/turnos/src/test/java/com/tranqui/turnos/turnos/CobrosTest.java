package com.tranqui.turnos.turnos;

import com.tranqui.eventos.data.CheckoutCreadoData;
import com.tranqui.eventos.data.MeetCreadoData;
import com.tranqui.eventos.data.PagoAprobadoData;
import com.tranqui.eventos.data.PagoRechazadoData;
import com.tranqui.eventos.data.ReembolsoRealizadoData;
import com.tranqui.turnos.datos.Turno;
import com.tranqui.turnos.datos.Turnos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Los eventos de pagos y del calendario sobre el turno: idempotentes y sin pisar un estado más avanzado. */
class CobrosTest {

    private static final LocalDate FECHA = LocalDate.now().plusDays(2);

    private final Turnos turnos = mock(Turnos.class);
    private final Publicador publicador = mock(Publicador.class);
    private final Gestion gestion = mock(Gestion.class);
    private final Cobros cobros = new Cobros(turnos, publicador, gestion);

    @BeforeEach
    void setUp() {
        when(turnos.porIdParaActualizar(any())).thenReturn(Optional.empty());
        when(gestion.direccion(any())).thenReturn("Consultorio 4");
    }

    private Turno turno(long id, String estado) {
        Turno t = new Turno();
        t.id = id;
        t.medicoId = 2L;
        t.pacienteId = 1L;
        t.fecha = FECHA;
        t.horaInicio = LocalTime.of(10, 0);
        t.horaFin = LocalTime.of(11, 0);
        t.estado = estado;
        t.modalidad = "PRESENCIAL";
        t.precio = new BigDecimal("15000");
        t.fechaCreacion = LocalDateTime.now().minusHours(1);
        when(turnos.porIdParaActualizar(id)).thenReturn(Optional.of(t));
        return t;
    }

    @Test
    void sinTurnoOTurnoInexistente_noHaceNada() {
        cobros.pagoAprobado(new PagoAprobadoData(1L, null, "mp", BigDecimal.ONE, "x"));
        cobros.pagoAprobado(new PagoAprobadoData(1L, 99L, "mp", BigDecimal.ONE, "x"));
        cobros.pagoRechazado(new PagoRechazadoData(1L, 99L, "mp", "x"));
        verifyNoInteractions(publicador);
    }

    @Test
    void pagoAprobado_confirmaElTurno() {
        Turno t = turno(10L, Turno.PENDIENTE_PAGO);
        cobros.pagoAprobado(new PagoAprobadoData(1L, 10L, "mp-1", t.precio, "tarjeta"));
        assertThat(t.estado).isEqualTo(Turno.CONFIRMADO);
        assertThat(t.pagoEstado).isEqualTo(Turno.PAGO_APROBADO);
        assertThat(t.pagoTransactionId).isEqualTo("mp-1");
        verify(publicador).confirmado(eq(t), any(), isNull(), eq("Consultorio 4"));
        verify(publicador, never()).documentoSolicitado(any(), any(), any());
        verify(publicador).actualizado(t);
    }

    @Test
    void pagoAprobado_documentoOnline_pideElDocumento() {
        Turno t = turno(10L, Turno.PENDIENTE_PAGO);
        t.ocupaAgenda = false;
        t.modalidad = "ONLINE";
        cobros.pagoAprobado(new PagoAprobadoData(1L, 10L, "mp-1", t.precio, "tarjeta"));
        verify(publicador).confirmado(eq(t), isNull(), any(), isNull());
        verify(publicador).documentoSolicitado(eq(t), any(), eq("DOCUMENTO"));
    }

    @Test
    void pagoAprobado_yaConfirmado_noRepite() {
        turno(10L, Turno.CONFIRMADO);
        cobros.pagoAprobado(new PagoAprobadoData(1L, 10L, "mp-1", BigDecimal.ONE, "x"));
        verifyNoInteractions(publicador);
    }

    @Test
    void pagoAprobado_turnoVencidoConElHorarioTomado_seCancelaConReembolso() {
        Turno vencido = turno(10L, Turno.EXPIRADO);
        Turno otro = new Turno();
        otro.id = 11L;
        otro.estado = Turno.CONFIRMADO;
        otro.horaInicio = LocalTime.of(10, 30);
        otro.horaFin = LocalTime.of(11, 30);
        when(turnos.delDia(2L, FECHA)).thenReturn(List.of(vencido, otro));
        cobros.pagoAprobado(new PagoAprobadoData(1L, 10L, "mp-1", BigDecimal.ONE, "x"));
        assertThat(vencido.estado).isEqualTo(Turno.CANCELADO);
        verify(publicador).cancelado(eq(vencido), eq("SISTEMA"), anyString(), eq("EN_PROCESO"));
        verify(publicador, never()).confirmado(any(), any(), any(), any());
    }

    @Test
    void pagoAprobado_turnoCanceladoConElHorarioLibre_seConfirma() {
        Turno cancelado = turno(10L, Turno.CANCELADO);
        Turno pendienteViejo = new Turno();
        pendienteViejo.id = 12L;
        pendienteViejo.estado = Turno.PENDIENTE_PAGO;
        pendienteViejo.fechaCreacion = LocalDateTime.now().minusMinutes(30);
        pendienteViejo.horaInicio = LocalTime.of(10, 0);
        pendienteViejo.horaFin = LocalTime.of(11, 0);
        Turno expirado = new Turno();
        expirado.id = 13L;
        expirado.estado = Turno.EXPIRADO;
        Turno otroHorario = new Turno();
        otroHorario.id = 14L;
        otroHorario.estado = Turno.CONFIRMADO;
        otroHorario.horaInicio = LocalTime.of(12, 0);
        otroHorario.horaFin = LocalTime.of(13, 0);
        when(turnos.delDia(2L, FECHA)).thenReturn(List.of(pendienteViejo, expirado, otroHorario));
        cobros.pagoAprobado(new PagoAprobadoData(1L, 10L, "mp-1", BigDecimal.ONE, "x"));
        assertThat(cancelado.estado).isEqualTo(Turno.CONFIRMADO);
    }

    @Test
    void pagoAprobado_documentoCancelado_noMiraElHorario() {
        Turno t = turno(10L, Turno.CANCELADO);
        t.ocupaAgenda = false;
        cobros.pagoAprobado(new PagoAprobadoData(1L, 10L, "mp-1", BigDecimal.ONE, "x"));
        assertThat(t.estado).isEqualTo(Turno.CONFIRMADO);
        verify(turnos, never()).delDia(any(), any());
    }

    @Test
    void pagoRechazado_soloElPrimero() {
        Turno t = turno(10L, Turno.PENDIENTE_PAGO);
        cobros.pagoRechazado(new PagoRechazadoData(1L, 10L, "mp-2", "fondos"));
        assertThat(t.pagoEstado).isEqualTo(Turno.PAGO_RECHAZADO);
        cobros.pagoRechazado(new PagoRechazadoData(1L, 10L, "mp-3", "fondos"));
        assertThat(t.pagoTransactionId).isEqualTo("mp-2");
        verify(publicador).actualizado(t);
    }

    @Test
    void reembolso_marcaUnaSolaVezYNoSinPago() {
        Turno sinPago = turno(10L, Turno.CANCELADO);
        cobros.reembolsado(new ReembolsoRealizadoData(1L, 1L, 10L, BigDecimal.ONE, "x", 1L, 2L, FECHA, "10:00", true));
        assertThat(sinPago.pagoEstado).isNull();

        sinPago.pagoEstado = Turno.PAGO_APROBADO;
        cobros.reembolsado(new ReembolsoRealizadoData(1L, 1L, 10L, BigDecimal.ONE, "x", 1L, 2L, FECHA, "10:00", true));
        cobros.reembolsado(new ReembolsoRealizadoData(1L, 1L, 10L, BigDecimal.ONE, "x", 1L, 2L, FECHA, "10:00", true));
        assertThat(sinPago.pagoEstado).isEqualTo(Turno.PAGO_REEMBOLSADO);
        verify(publicador).actualizado(sinPago);
    }

    @Test
    void checkout_soloMientrasEsperaElPagoYSiCambia() {
        Turno t = turno(10L, Turno.PENDIENTE_PAGO);
        cobros.checkout(new CheckoutCreadoData("turno-10", 10L, null, "https://mp/1"));
        cobros.checkout(new CheckoutCreadoData("turno-10", 10L, null, "https://mp/1"));
        assertThat(t.checkoutUrl).isEqualTo("https://mp/1");
        t.estado = Turno.CONFIRMADO;
        cobros.checkout(new CheckoutCreadoData("turno-10", 10L, null, "https://mp/2"));
        assertThat(t.checkoutUrl).isEqualTo("https://mp/1");
        verify(publicador).actualizado(t);
    }

    @Test
    void meet_guardaLinkYEventoSoloSiCambian() {
        Turno t = turno(10L, Turno.CONFIRMADO);
        cobros.meet(new MeetCreadoData(10L, "https://meet/a", "ev-1"));
        cobros.meet(new MeetCreadoData(10L, "https://meet/a", "ev-1"));
        cobros.meet(new MeetCreadoData(10L, " ", null));
        assertThat(t.telemedicinaUrl).isEqualTo("https://meet/a");
        assertThat(t.googleEventId).isEqualTo("ev-1");
        cobros.meet(new MeetCreadoData(10L, null, "ev-2"));
        assertThat(t.googleEventId).isEqualTo("ev-2");
        verify(publicador, org.mockito.Mockito.times(2)).actualizado(t);
    }
}
