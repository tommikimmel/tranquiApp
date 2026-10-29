package com.tranqui.pagos.suscripciones;

import com.tranqui.eventos.data.SuscripcionAvisoData;
import com.tranqui.pagos.datos.PagosSuscripcion;
import com.tranqui.pagos.datos.Planes;
import com.tranqui.pagos.datos.Suscripciones;
import com.tranqui.pagos.datos.Suscriptores;
import com.tranqui.pagos.interno.CargaInicialSuscripciones;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Los crons de reconciliación: qué estado deja cada uno, qué aviso publica y que un error no corta el lote. */
class SubscriptionReconciliationSchedulerTest {

    private final Suscripciones suscripciones = mock(Suscripciones.class);
    private final PagosSuscripcion pagos = mock(PagosSuscripcion.class);
    private final Planes planes = mock(Planes.class);
    private final Suscriptores suscriptores = mock(Suscriptores.class);
    private final SuscripcionService service = mock(SuscripcionService.class);
    private final AvisoSuscripcionPublisher avisos = mock(AvisoSuscripcionPublisher.class);
    private final FacturacionSuscripciones facturacion = mock(FacturacionSuscripciones.class);
    private final CargaInicialSuscripciones carga = mock(CargaInicialSuscripciones.class);
    private SubscriptionReconciliationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = nuevo(true);
        when(carga.listo()).thenReturn(true);
        when(pagos.sumaAprobadosDesde(any())).thenReturn(BigDecimal.ZERO);
        when(planes.porId(1L)).thenReturn(Optional.of(new Planes.Plan(1L, "PRO", "Pro", "", BigDecimal.TEN, null, null,
                "monthly", 1, null, false, true, null, null)));
        when(suscriptores.porId(10L)).thenReturn(Optional.of(new Suscriptores.Suscriptor(10L, "m@test.com", "Ana", "Paz",
                "PSIQUIATRA", null, true, 0, null, null, null, null, null, null, null, null, null, null)));
    }

    private SubscriptionReconciliationScheduler nuevo(boolean habilitada) {
        return new SubscriptionReconciliationScheduler(suscripciones, pagos, planes, suscriptores, service, avisos,
                facturacion, carga, habilitada);
    }

    private static Suscripciones.Suscripcion sub(long id, long profesional, long plan, String status, LocalDateTime fin,
                                                 LocalDateTime proximoCobro) {
        return new Suscripciones.Suscripcion(id, 1, profesional, plan, status, 1, "MANUAL_TRANSFER", null, BigDecimal.TEN,
                "monthly", proximoCobro, null, fin, null, true, null, false, null, null);
    }

    private Suscripciones.Suscripcion actualizada() {
        ArgumentCaptor<Suscripciones.Suscripcion> captor = ArgumentCaptor.forClass(Suscripciones.Suscripcion.class);
        verify(suscripciones).actualizar(captor.capture());
        return captor.getValue();
    }

    @Test
    void deshabilitadoOCargaPendiente_noCorre() {
        nuevo(false).runDailyReconciliation();
        nuevo(false).runHourlyAlertsCheck();
        when(carga.listo()).thenReturn(false);
        scheduler.runDailyReconciliation();
        verifyNoInteractions(suscripciones, facturacion);
    }

    @Test
    void diario_correTodosLosPasos() {
        when(pagos.sumaAprobadosDesde(any())).thenReturn(new BigDecimal("11000000"));
        scheduler.runDailyReconciliation();
        verify(suscripciones).proximasRenovacionesSinAviso(any(), any());
        verify(suscripciones).canceladasVencidas(any());
        verify(facturacion).reconciliarPagosSinFactura();
        verify(facturacion).verificarFacturasFallidas();
        verify(suscripciones).activasVencidasConGracia(any());
        verify(suscripciones).manualesPorVencer(any(), any());
    }

    @Test
    void horario_unErrorNoSePropaga() {
        when(suscripciones.proximasRenovacionesSinAviso(any(), any())).thenThrow(new IllegalStateException("base caída"));
        scheduler.runHourlyAlertsCheck();
        verify(suscripciones, never()).canceladasVencidas(any());
    }

    @Test
    void preaviso_marcaElAvisoYUsaElProximoCobro() {
        LocalDateTime cobro = LocalDateTime.now().plusDays(3);
        when(suscripciones.proximasRenovacionesSinAviso(any(), any())).thenReturn(List.of(
                sub(100L, 10L, 1L, "ACTIVE", cobro.plusDays(1), cobro),
                sub(101L, 99L, 1L, "ACTIVE", cobro, null)));
        scheduler.checkUpcomingRenewals();
        assertThat(actualizada().renewalReminderSent()).isTrue();
        verify(avisos).publicar(any(), eq("Pro"), eq(SuscripcionAvisoData.PREAVISO_RENOVACION), eq(BigDecimal.TEN), eq(cobro),
                isNull(), eq("monthly"), isNull(), isNull());
        verify(service).logEvent(eq(100L), eq("RENEWAL_PREAVISO_EMAIL_SENT"), eq("SYSTEM"), eq("CRON"), anyString());
    }

    @Test
    void preaviso_planDesconocidoYErrorEnUno_sigueConElResto() {
        LocalDateTime fin = LocalDateTime.now().plusDays(3);
        when(suscripciones.proximasRenovacionesSinAviso(any(), any())).thenReturn(List.of(
                sub(100L, 10L, 1L, "ACTIVE", fin, null), sub(102L, 10L, 9L, "ACTIVE", fin, null)));
        doThrow(new RuntimeException("x")).when(avisos).publicar(any(), eq("Pro"), any(), any(), any(), any(), any(), any(), any());
        scheduler.checkUpcomingRenewals();
        verify(avisos).publicar(any(), eq("Profesional"), any(), any(), eq(fin), any(), any(), any(), any());
    }

    @Test
    void canceladasVencidas_pasanACancelledYAvisan() {
        when(suscripciones.canceladasVencidas(any())).thenReturn(List.of(sub(100L, 10L, 9L, "ACTIVE", null, null)));
        scheduler.checkCancelledSubscriptionsPeriodEnd();
        Suscripciones.Suscripcion s = actualizada();
        assertThat(s.status()).isEqualTo("CANCELLED");
        assertThat(s.cancelAtPeriodEnd()).isFalse();
        verify(service).logEvent(eq(100L), anyString(), eq("ACTIVE"), eq("CANCELLED"), eq("SYSTEM"), eq("CRON"), anyString());
        verify(avisos).publicar(any(), eq("Profesional"), eq(SuscripcionAvisoData.SUSPENSION), any(), any(), any(), any(), any(), any());
    }

    @Test
    void canceladasVencidas_errorEnUnaNoCortaElLote() {
        when(suscripciones.canceladasVencidas(any())).thenReturn(List.of(sub(100L, 10L, 1L, "ACTIVE", null, null),
                sub(101L, 99L, 1L, "ACTIVE", null, null)));
        doThrow(new RuntimeException("x")).doNothing().when(suscripciones).actualizar(any());
        scheduler.checkCancelledSubscriptionsPeriodEnd();
        verify(service).logEvent(eq(101L), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(avisos, never()).publicar(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void vencidasConGracia_pasanASuspended() {
        when(suscripciones.activasVencidasConGracia(any())).thenReturn(List.of(sub(100L, 10L, 1L, "PAST_DUE", null, null)));
        scheduler.checkExpiredActiveSubscriptions();
        assertThat(actualizada().status()).isEqualTo("SUSPENDED");
        verify(avisos).publicar(any(), eq("Pro"), eq(SuscripcionAvisoData.SUSPENSION), any(), any(), any(), any(), any(), any());
    }

    @Test
    void vencidasConGracia_errorYProfesionalDesconocido() {
        when(suscripciones.activasVencidasConGracia(any())).thenReturn(List.of(sub(100L, 10L, 9L, "PAST_DUE", null, null),
                sub(101L, 99L, 1L, "PAST_DUE", null, null)));
        doThrow(new RuntimeException("x")).when(service).logEvent(eq(100L), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        scheduler.checkExpiredActiveSubscriptions();
        verify(avisos, never()).publicar(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void manualesPorVencer_avisaSoloA7_3Y1Dias() {
        LocalDateTime hoy = LocalDateTime.now();
        when(suscripciones.manualesPorVencer(any(), any())).thenReturn(List.of(
                sub(100L, 10L, 1L, "ACTIVE", hoy.plusDays(7), null),
                sub(101L, 10L, 9L, "ACTIVE", hoy.plusDays(3), null),
                sub(102L, 10L, 1L, "ACTIVE", hoy.plusDays(5), null),
                sub(103L, 10L, 1L, "ACTIVE", null, null),
                sub(104L, 10L, 1L, "ACTIVE", hoy.plusDays(1), null)));
        doThrow(new RuntimeException("x")).when(avisos).publicar(any(), any(), any(), any(), any(), any(), any(), any(), eq(1));
        scheduler.checkExpiringManualSubscriptions();
        verify(avisos).publicar(any(), eq("Pro"), eq(SuscripcionAvisoData.VENCIMIENTO_MANUAL), isNull(), any(), isNull(), isNull(), isNull(), eq(7));
        verify(avisos).publicar(any(), eq("Profesional"), eq(SuscripcionAvisoData.VENCIMIENTO_MANUAL), isNull(), any(), isNull(), isNull(), isNull(), eq(3));
        verify(avisos, never()).publicar(any(), any(), any(), any(), any(), any(), any(), any(), eq(5));
    }
}
