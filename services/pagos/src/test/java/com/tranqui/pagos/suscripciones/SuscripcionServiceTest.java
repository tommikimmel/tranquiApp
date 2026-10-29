package com.tranqui.pagos.suscripciones;

import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.net.MPResponse;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.SuscripcionAvisoData;
import com.tranqui.eventos.data.SuscripcionCambioEstadoV2Data;
import com.tranqui.eventos.outbox.OutboxPublisher;
import com.tranqui.pagos.datos.EventosSuscripcion;
import com.tranqui.pagos.datos.PagosSuscripcion;
import com.tranqui.pagos.datos.Planes;
import com.tranqui.pagos.datos.Suscripciones;
import com.tranqui.pagos.datos.Suscriptores;
import com.tranqui.pagos.mp.MercadoPagoAdminApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Ramas de SuscripcionService que el test de integración no alcanza: errores de Mercado Pago, webhooks y features. */
class SuscripcionServiceTest {

    private static final LocalDateTime AHORA = LocalDateTime.now();

    private final Suscripciones suscripciones = mock(Suscripciones.class);
    private final Planes planes = mock(Planes.class);
    private final PagosSuscripcion pagos = mock(PagosSuscripcion.class);
    private final EventosSuscripcion eventos = mock(EventosSuscripcion.class);
    private final Suscriptores suscriptores = mock(Suscriptores.class);
    private final OutboxPublisher outbox = mock(OutboxPublisher.class);
    private final AvisoSuscripcionPublisher avisos = mock(AvisoSuscripcionPublisher.class);
    private final FacturacionSuscripciones facturacion = mock(FacturacionSuscripciones.class);
    private final MercadoPagoAdminApi mp = mock(MercadoPagoAdminApi.class);
    private SuscripcionService service;

    @BeforeEach
    void setUp() {
        service = new SuscripcionService(suscripciones, planes, pagos, eventos, suscriptores, outbox, avisos, facturacion, mp);
        when(planes.porId(1L)).thenReturn(Optional.of(plan(new BigDecimal("12000"))));
        when(suscriptores.porId(10L)).thenReturn(Optional.of(profesional("PSIQUIATRA", "psiquiatra")));
        when(pagos.insertarNuevo(any())).thenReturn(500L);
        // la base le asigna el id 500 al pago insertado
        when(pagos.porId(500L)).thenAnswer(inv -> Optional.of(new PagosSuscripcion.PagoSuscripcion(500L,
                ultimoPago.subscriptionId(), ultimoPago.professionalId(), ultimoPago.mpPaymentId(), ultimoPago.idempotencyKey(),
                ultimoPago.method(), ultimoPago.status(), ultimoPago.amountArs(), ultimoPago.paidAt(), ultimoPago.periodStart(),
                ultimoPago.periodEnd(), null, ultimoPago.registeredByAdminId(), ultimoPago.receiptReference(), ultimoPago.notes(),
                ultimoPago.createdAt())));
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    private PagosSuscripcion.PagoSuscripcion ultimoPago;

    private static Planes.Plan plan(BigDecimal anual) {
        return new Planes.Plan(1L, "PRO", "Pro", "", new BigDecimal("1000"), null, anual, "monthly", 1, null, false, true, null, null);
    }

    private static Suscriptores.Suscriptor profesional(String rol, String profesion) {
        return new Suscriptores.Suscriptor(10L, "m@test.com", "Ana", "Paz", rol, profesion, true, 0,
                null, null, null, null, null, null, null, null, null, null);
    }

    private static Suscripciones.Suscripcion sub(String status, String preapproval, String fuente, LocalDateTime finPeriodo,
                                                 boolean cancelAlFinal, String ciclo) {
        return new Suscripciones.Suscripcion(100L, 3, 10L, 1L, status, null, fuente, preapproval, new BigDecimal("1000"),
                ciclo, null, null, finPeriodo, null, cancelAlFinal, null, false, AHORA, AHORA);
    }

    private void existe(Suscripciones.Suscripcion s) {
        when(suscripciones.porProfesionalId(10L)).thenReturn(Optional.of(s));
        when(suscripciones.porId(100L)).thenReturn(Optional.of(s));
        when(suscripciones.porMpPreapprovalId(s.mpPreapprovalId())).thenReturn(Optional.of(s));
    }

    private void guardarPagos() {
        when(pagos.insertarNuevo(any())).thenAnswer(inv -> {
            ultimoPago = inv.getArgument(0);
            return 500L;
        });
    }

    private static MPApiException errorMp(int status) {
        return new MPApiException("error", new MPResponse(status, Map.of(), "{\"message\":\"x\"}"));
    }

    // ---- checkout ----

    @Test
    void checkout_anualSinPrecio_falla() {
        when(planes.porId(1L)).thenReturn(Optional.of(plan(null)));
        existe(sub("ACTIVE", null, "MANUAL_TRANSFER", null, false, "monthly"));
        assertThatThrownBy(() -> service.iniciarCheckout(10L, 1L, "annual")).hasMessageContaining("precio anual");
    }

    @Test
    void checkout_anual_cancelaElAnteriorAunqueFalleYDevuelveElLink() throws Exception {
        existe(sub("ACTIVE", "pre-viejo", "MERCADOPAGO", null, false, "monthly"));
        doThrow(new RuntimeException("caído")).when(mp).cancelarPreapproval("pre-viejo");
        when(mp.crearPreapproval(anyString(), anyString(), any(), anyBoolean(), any()))
                .thenReturn(new MercadoPagoAdminApi.PreapprovalMp("pre-nuevo", "https://mp/checkout"));

        assertThat(service.iniciarCheckout(10L, 1L, "ANNUAL")).isEqualTo("https://mp/checkout");
        verify(mp).crearPreapproval("m@test.com", "Pro", new BigDecimal("12000"), true, 100L);
        ArgumentCaptor<Suscripciones.Suscripcion> captor = ArgumentCaptor.forClass(Suscripciones.Suscripcion.class);
        verify(suscripciones).actualizar(captor.capture());
        assertThat(captor.getValue().mpPreapprovalId()).isEqualTo("pre-nuevo");
        assertThat(captor.getValue().billingCycle()).isEqualTo("annual");
    }

    @Test
    void checkout_mercadoPagoLimitando_mensajeEspecifico() throws Exception {
        existe(sub("ACTIVE", null, "MANUAL_TRANSFER", null, false, "monthly"));
        when(mp.crearPreapproval(anyString(), anyString(), any(), anyBoolean(), any())).thenThrow(errorMp(429));
        assertThatThrownBy(() -> service.iniciarCheckout(10L, 1L, "monthly")).hasMessageContaining("limitando");
    }

    @Test
    void checkout_otrosErrores_mensajeGenerico() throws Exception {
        existe(sub("ACTIVE", null, "MANUAL_TRANSFER", null, false, "monthly"));
        when(mp.crearPreapproval(anyString(), anyString(), any(), anyBoolean(), any())).thenThrow(errorMp(500));
        assertThatThrownBy(() -> service.iniciarCheckout(10L, 1L, "monthly")).hasMessageContaining("No se pudo iniciar");

        doThrow(new java.io.IOException("red")).when(mp).crearPreapproval(anyString(), anyString(), any(), anyBoolean(), any());
        assertThatThrownBy(() -> service.iniciarCheckout(10L, 1L, "monthly")).hasMessageContaining("No se pudo iniciar");
    }

    @Test
    void checkout_profesionalOPlanInexistente() {
        assertThatThrownBy(() -> service.iniciarCheckout(99L, 1L, "monthly")).hasMessageContaining("Profesional");
        assertThatThrownBy(() -> service.iniciarCheckout(10L, 9L, "monthly")).hasMessageContaining("Plan");
    }

    // ---- features y acceso ----

    @Test
    void hasFeature_casos() {
        assertThat(service.hasFeature(null, "x")).isFalse();
        assertThat(service.hasFeature(99L, "x")).isFalse();

        when(suscriptores.porId(20L)).thenReturn(Optional.of(new Suscriptores.Suscriptor(20L, "a@t", "A", "B", "ADMIN", null,
                true, 0, null, null, null, null, null, null, null, null, null, null)));
        assertThat(service.hasFeature(20L, "x")).isTrue();

        // sin suscripción: solo recetas para quien no es psicólogo
        assertThat(service.hasFeature(10L, "recetas_electronicas")).isTrue();
        assertThat(service.hasFeature(10L, "otra")).isFalse();
        when(suscriptores.porId(10L)).thenReturn(Optional.of(profesional("PSIQUIATRA", " Psicologo ")));
        assertThat(service.hasFeature(10L, "recetas_electronicas")).isFalse();

        existe(sub("SUSPENDED", null, "MERCADOPAGO", null, false, "monthly"));
        assertThat(service.hasFeature(10L, "x")).isFalse();

        existe(sub("CANCELLED", null, "MERCADOPAGO", AHORA.plusDays(3), true, "monthly"));
        when(planes.existePorPlanIdYFeatureKey(1L, "x")).thenReturn(true);
        assertThat(service.hasFeature(10L, "x")).isTrue();

        existe(new Suscripciones.Suscripcion(100L, 0, 10L, null, "ACTIVE", 1, "MERCADOPAGO", null, null, "monthly",
                null, null, null, null, false, null, false, AHORA, AHORA));
        assertThat(service.hasFeature(10L, "x")).isFalse();
    }

    @Test
    void isAccessAllowed_casos() {
        assertThat(service.isAccessAllowed(null)).isFalse();
        assertThat(service.isAccessAllowed(99L)).isFalse();
        assertThat(service.isAccessAllowed(10L)).isFalse();

        when(suscriptores.porId(30L)).thenReturn(Optional.of(new Suscriptores.Suscriptor(30L, "p@t", "P", "Q", "PACIENTE", null,
                true, 0, null, null, null, null, null, null, null, null, null, null)));
        assertThat(service.isAccessAllowed(30L)).isTrue();

        existe(sub("ACTIVE", null, "MERCADOPAGO", null, false, "monthly"));
        assertThat(service.isAccessAllowed(10L)).isFalse();
        existe(sub("PAST_DUE", null, "MERCADOPAGO", AHORA.plusDays(3), false, "monthly"));
        assertThat(service.isAccessAllowed(10L)).isFalse();
        existe(sub("ACTIVE", null, "MERCADOPAGO", AHORA.plusDays(3), false, "monthly"));
        assertThat(service.isAccessAllowed(10L)).isTrue();
    }

    // ---- cancelación ----

    @Test
    void cancelar_yaCancelada_noHaceNada() {
        Suscripciones.Suscripcion s = sub("ACTIVE", "pre", "MERCADOPAGO", AHORA.plusDays(3), true, "monthly");
        existe(s);
        assertThat(service.cancelarSuscripcionPorProfesional(10L)).isSameAs(s);
        verify(suscripciones, never()).actualizar(any());
    }

    @Test
    void cancelar_errorDeMercadoPago_noCambiaNada() throws Exception {
        existe(sub("ACTIVE", "pre", "MERCADOPAGO", AHORA.plusDays(3), false, "monthly"));
        doThrow(errorMp(500)).when(mp).cancelarPreapproval("pre");
        assertThatThrownBy(() -> service.cancelarSuscripcionPorProfesional(10L)).hasMessageContaining("cobro recurrente");
        doThrow(new java.io.IOException("red")).when(mp).cancelarPreapproval("pre");
        assertThatThrownBy(() -> service.cancelarSuscripcionPorProfesional(10L)).hasMessageContaining("cobro recurrente");
        verify(suscripciones, never()).actualizar(any());
    }

    @Test
    void cancelar_periodoVencido_quedaCancelada() {
        existe(sub("ACTIVE", null, "MANUAL_TRANSFER", AHORA.minusDays(1), false, "monthly"));
        service.cancelarSuscripcionPorProfesional(10L);
        ArgumentCaptor<Suscripciones.Suscripcion> captor = ArgumentCaptor.forClass(Suscripciones.Suscripcion.class);
        verify(suscripciones).actualizar(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("CANCELLED");
        verify(avisos).publicar(any(), eq("Pro"), eq(SuscripcionAvisoData.CANCELACION_CONFIRMADA), isNull(), any(), isNull(), isNull(), isNull(), isNull());
    }

    // ---- pago manual ----

    @Test
    void pagoManual_sinClaveORepetido() {
        assertThatThrownBy(() -> service.registerManualPayment(1L, 10L, 1L, null, null, null, null, null, null, false, " "))
                .isInstanceOf(IllegalArgumentException.class);
        PagosSuscripcion.PagoSuscripcion previo = mock(PagosSuscripcion.PagoSuscripcion.class);
        when(pagos.porClaveIdempotencia("k")).thenReturn(Optional.of(previo));
        assertThat(service.registerManualPayment(1L, 10L, 1L, null, null, null, null, null, null, false, "k")).isSameAs(previo);
    }

    @Test
    void pagoManual_cortesia_sinFacturaNiAviso() {
        guardarPagos();
        existe(sub("PAST_DUE", null, "MANUAL_TRANSFER", null, false, "monthly"));
        service.registerManualPayment(1L, 10L, 1L, null, "courtesy", null, null, null, null, true, "k2");
        assertThat(ultimoPago.method()).isEqualTo("COURTESY");
        assertThat(ultimoPago.amountArs()).isEqualByComparingTo("1000");
        verify(facturacion, never()).emitirFactura(any(), any());
        verify(avisos, never()).publicar(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void pagoManual_conFactura_laEmiteDespuesDelCommit() {
        guardarPagos();
        existe(sub("PAST_DUE", null, "MANUAL_TRANSFER", null, false, "monthly"));
        TransactionSynchronizationManager.initSynchronization();
        service.registerManualPayment(1L, 10L, 1L, new BigDecimal("900"), "transfer", null, null, " ", null, true, "k3");
        verify(facturacion, never()).emitirFactura(any(), any());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(facturacion).emitirFactura(500L, "pago manual");
        verify(avisos).publicar(any(), eq("Pro"), eq(SuscripcionAvisoData.PAGO_CONFIRMADO), any(), any(), any(), isNull(),
                eq("Pago Manual transfer"), isNull());
    }

    // ---- webhooks de pagos ----

    @Test
    void webhookPago_repetidoODesconocido() {
        PagosSuscripcion.PagoSuscripcion previo = mock(PagosSuscripcion.PagoSuscripcion.class);
        when(pagos.porMpPaymentId("p1")).thenReturn(Optional.of(previo));
        assertThat(service.processMercadoPagoPaymentWebhook("p1", "pre", null, "approved")).isSameAs(previo);
        assertThat(service.processMercadoPagoPaymentWebhook("p2", "pre-x", null, "approved")).isNull();
    }

    @Test
    void webhookPago_aprobadoAnual_activaFacturaYAvisa() {
        guardarPagos();
        existe(sub("PAST_DUE", "pre", "MERCADOPAGO", null, false, "annual"));
        service.processMercadoPagoPaymentWebhook("p3", "pre", null, "approved");
        assertThat(ultimoPago.status()).isEqualTo("APPROVED");
        assertThat(ultimoPago.amountArs()).isEqualByComparingTo("1000");
        assertThat(ultimoPago.periodEnd()).isAfter(AHORA.plusMonths(11));
        verify(facturacion).emitirFactura(500L, "pago MP");
        verify(avisos).publicar(any(), any(), eq(SuscripcionAvisoData.PAGO_CONFIRMADO), any(), any(), any(), any(), any(), any());
    }

    @Test
    void webhookPago_rechazado_avisaPagoFallido() {
        guardarPagos();
        existe(sub("ACTIVE", "pre", "MERCADOPAGO", null, false, "monthly"));
        service.processMercadoPagoPaymentWebhook("p4", "pre", new BigDecimal("1500"), "rejected");
        assertThat(ultimoPago.status()).isEqualTo("REJECTED");
        verify(facturacion, never()).emitirFactura(any(), any());
        verify(avisos).publicar(any(), any(), eq(SuscripcionAvisoData.PAGO_FALLIDO), any(), any(), any(), any(), any(), any());
    }

    @Test
    void webhookPagoPorReferencia_casos() {
        guardarPagos();
        assertThat(service.processMercadoPagoPaymentByExternalReference(null, "p", null, "approved")).isNull();
        assertThat(service.processMercadoPagoPaymentByExternalReference("turno-5", "p", null, "approved")).isNull();
        assertThat(service.processMercadoPagoPaymentByExternalReference("sub-abc", "p", null, "approved")).isNull();
        assertThat(service.processMercadoPagoPaymentByExternalReference("sub-77", "p", null, "approved")).isNull();

        PagosSuscripcion.PagoSuscripcion previo = mock(PagosSuscripcion.PagoSuscripcion.class);
        when(pagos.porMpPaymentId("p-rep")).thenReturn(Optional.of(previo));
        assertThat(service.processMercadoPagoPaymentByExternalReference("sub-100", "p-rep", null, "approved")).isSameAs(previo);

        existe(sub("PAST_DUE", "pre", "MERCADOPAGO", null, false, "monthly"));
        assertThat(service.processMercadoPagoPaymentByExternalReference("sub-100", "p5", null, "approved")).isNotNull();
        assertThat(ultimoPago.idempotencyKey()).isEqualTo("MP-p5");
    }

    // ---- webhook de preapproval ----

    private Suscripciones.Suscripcion actualizada() {
        ArgumentCaptor<Suscripciones.Suscripcion> captor = ArgumentCaptor.forClass(Suscripciones.Suscripcion.class);
        verify(suscripciones).actualizar(captor.capture());
        return captor.getValue();
    }

    @Test
    void preapproval_desconocido_noHaceNada() {
        service.processMercadoPagoPreapprovalWebhook("nada", "authorized");
        verify(suscripciones, never()).actualizar(any());
    }

    @Test
    void preapproval_autorizadoSinPeriodo_abreUnPeriodoNuevo() {
        existe(sub("PENDING", "pre", "MERCADOPAGO", null, false, "annual"));
        service.processMercadoPagoPreapprovalWebhook("pre", "AUTHORIZED");
        Suscripciones.Suscripcion s = actualizada();
        assertThat(s.status()).isEqualTo("ACTIVE");
        assertThat(s.currentPeriodEnd()).isAfter(AHORA.plusMonths(11));
        assertThat(s.graceUntil()).isEqualTo(s.currentPeriodEnd().plusDays(7));
    }

    @Test
    void preapproval_pausado_pasaAPastDue() {
        existe(sub("ACTIVE", "pre", "MERCADOPAGO", AHORA.plusDays(5), false, "monthly"));
        service.processMercadoPagoPreapprovalWebhook("pre", "paused");
        assertThat(actualizada().status()).isEqualTo("PAST_DUE");
    }

    @Test
    void preapproval_canceladoConPeriodoVigente_mantieneAccesoYAvisa() {
        LocalDateTime fin = AHORA.plusDays(5);
        existe(sub("ACTIVE", "pre", "MERCADOPAGO", fin, false, "monthly"));
        service.processMercadoPagoPreapprovalWebhook("pre", "cancelled");
        Suscripciones.Suscripcion s = actualizada();
        assertThat(s.status()).isEqualTo("ACTIVE");
        assertThat(s.cancelAtPeriodEnd()).isTrue();
        assertThat(s.currentPeriodEnd()).isEqualTo(fin);
        verify(avisos).publicar(any(), any(), eq(SuscripcionAvisoData.CANCELACION_CONFIRMADA), any(), eq(fin), any(), any(), any(), any());
    }

    @Test
    void preapproval_canceladoVencido_yEstadoDesconocido() {
        existe(sub("ACTIVE", "pre", "MERCADOPAGO", AHORA.minusDays(1), false, "monthly"));
        service.processMercadoPagoPreapprovalWebhook("pre", "cancelled");
        assertThat(actualizada().status()).isEqualTo("CANCELLED");

        Suscripciones.Suscripcion otra = new Suscripciones.Suscripcion(101L, 0, 11L, 1L, "PAST_DUE", 2, "MERCADOPAGO", "pre2",
                null, "monthly", null, null, AHORA.plusDays(2), null, false, null, false, AHORA, AHORA);
        when(suscripciones.porMpPreapprovalId("pre2")).thenReturn(Optional.of(otra));
        service.processMercadoPagoPreapprovalWebhook("pre2", null);
        ArgumentCaptor<Suscripciones.Suscripcion> captor = ArgumentCaptor.forClass(Suscripciones.Suscripcion.class);
        verify(suscripciones, org.mockito.Mockito.times(2)).actualizar(captor.capture());
        assertThat(captor.getAllValues().get(1).status()).isEqualTo("PAST_DUE");
        assertThat(captor.getAllValues().get(1).seats()).isEqualTo(2);
    }

    // ---- auditoría y outbox ----

    @Test
    void logEvent_cambioDeEstado_publicaV2ConLaVersionSiguiente() {
        existe(sub("ACTIVE", null, "MERCADOPAGO", AHORA.plusDays(5), false, "monthly"));
        when(planes.existePorPlanIdYFeatureKey(1L, "recetas_electronicas")).thenReturn(true);
        service.logEvent(100L, "X", "PAST_DUE", "ACTIVE", "ADMIN", "1", "{}");
        ArgumentCaptor<SuscripcionCambioEstadoV2Data> captor = ArgumentCaptor.forClass(SuscripcionCambioEstadoV2Data.class);
        verify(outbox).publicar(eq(Topicos.SUSCRIPCIONES_SUSCRIPCION_CAMBIO_ESTADO_V2), eq("100"), eq(4), captor.capture());
        assertThat(captor.getValue().acceso()).isTrue();
        assertThat(captor.getValue().puedeRecetar()).isTrue();
        assertThat(captor.getValue().planCode()).isEqualTo("PRO");
    }

    @Test
    void logEvent_sinCambioDeEstado_soloAudita() {
        service.logEvent(100L, "X", "ADMIN", "1", "{}");
        service.logEvent(100L, "X", "ACTIVE", "ACTIVE", "ADMIN", "1", "{}");
        verify(eventos, org.mockito.Mockito.times(2)).insertarNuevo(any());
        verify(outbox, never()).publicar(anyString(), anyString(), anyInt(), any());
    }

    @Test
    void resumenAdmin_calculaMrrYAlertaDeMonotributo() {
        when(suscripciones.porEstado("ACTIVE")).thenReturn(List.of(sub("ACTIVE", null, "MP", null, false, "monthly"),
                new Suscripciones.Suscripcion(101L, 0, 11L, 1L, "ACTIVE", 1, "MP", null, null, "monthly",
                        null, null, null, null, false, null, false, AHORA, AHORA)));
        when(suscripciones.porEstado("PAST_DUE")).thenReturn(List.of());
        when(pagos.sumaAprobadosDesde(any())).thenReturn(new BigDecimal("10000000"));
        Map<String, Object> stats = service.getAdminOverviewStats();
        assertThat(stats.get("suscriptoresActivos")).isEqualTo(2L);
        assertThat((BigDecimal) stats.get("mrr")).isEqualByComparingTo("1000");
        assertThat(stats.get("porcentajeUsoCategoriaA")).isEqualTo(83.3);
        assertThat(stats.get("alertaMonotributo80")).isEqualTo(true);
    }
}
