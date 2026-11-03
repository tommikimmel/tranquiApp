package com.tranqui.pagos.cobros;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tranqui.pagos.cobros.NotificacionesMercadoPago.Resultado;
import com.tranqui.pagos.datos.CuentasMp;
import com.tranqui.pagos.interno.ClienteInterno;
import com.tranqui.pagos.mp.MercadoPagoAdminApi;
import com.tranqui.pagos.mp.MercadoPagoApi;
import com.tranqui.pagos.mp.Vinculacion;
import com.tranqui.pagos.suscripciones.SuscripcionService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cómo se reconoce y a dónde va cada aviso del webhook de Mercado Pago. */
class NotificacionesMercadoPagoTest {

    private final CuentasMp cuentas = mock(CuentasMp.class);
    private final Vinculacion vinculacion = mock(Vinculacion.class);
    private final MercadoPagoApi mercadoPago = mock(MercadoPagoApi.class);
    private final Aprobaciones aprobaciones = mock(Aprobaciones.class);
    private final ClienteInterno interno = mock(ClienteInterno.class);
    private final SuscripcionService suscripciones = mock(SuscripcionService.class);
    private final MercadoPagoAdminApi admin = mock(MercadoPagoAdminApi.class);
    private final ObjectMapper json = new ObjectMapper();

    private NotificacionesMercadoPago notificaciones(boolean suscripcionesHabilitada) {
        return new NotificacionesMercadoPago(cuentas, vinculacion, mercadoPago, aprobaciones, interno, suscripciones, admin,
                suscripcionesHabilitada);
    }

    @Test
    void suscripcionesApagadas_seReenvianAlMonolito() throws Exception {
        var n = notificaciones(false);
        assertThat(n.procesar("{\"type\":\"subscription_preapproval\"}", "1")).isEqualTo(Resultado.REENVIADO);
        assertThat(n.procesar("{\"type\":\"payment\",\"data\":{\"id\":\"5\"}}", "5")).isEqualTo(Resultado.REENVIADO);
        assertThat(n.procesar("{\"external_reference\":\"sub-3\"}", null)).isEqualTo(Resultado.REENVIADO);
        verify(interno, org.mockito.Mockito.times(3)).reenviarSuscripcion(anyString(), any());
    }

    @Test
    void preapproval_conEstadoEnElCuerpo() throws Exception {
        assertThat(notificaciones(true).procesar("{\"type\":\"subscription_preapproval\",\"data\":{\"id\":\"pre\"},\"status\":\"paused\"}", "x"))
                .isEqualTo(Resultado.PROCESADO);
        verify(suscripciones).processMercadoPagoPreapprovalWebhook("pre", "paused");
        verify(admin, never()).obtenerPreapproval(any());
    }

    @Test
    void preapproval_sinEstado_loConsultaAMercadoPago() throws Exception {
        when(admin.obtenerPreapproval("pre")).thenReturn(new MercadoPagoAdminApi.PreapprovalMp("pre", null, "cancelled"));
        notificaciones(true).procesar("{\"action\":\"subscription_preapproval.updated\"}", "pre");
        verify(suscripciones).processMercadoPagoPreapprovalWebhook("pre", "cancelled");
    }

    @Test
    void preapproval_siMercadoPagoFallaAsumeAutorizado() throws Exception {
        when(admin.obtenerPreapproval("pre")).thenThrow(new IllegalStateException("caído"));
        notificaciones(true).procesar("{\"topic\":\"subscription_preapproval\"}", "pre");
        verify(suscripciones).processMercadoPagoPreapprovalWebhook("pre", "authorized");
    }

    @Test
    void cobroRecurrente_completaLosDatosConElDetalle() throws Exception {
        when(admin.obtenerDetalleAuthorizedPayment("ap-1")).thenReturn(json.readTree(
                "{\"payment\":{\"id\":777},\"preapproval_id\":\"pre\",\"transaction_amount\":\"1500\",\"status\":\"rejected\"}"));
        notificaciones(true).procesar("{\"type\":\"subscription_authorized_payment\",\"data\":{\"id\":\"ap-1\"}}", "ap-1");
        verify(suscripciones).processMercadoPagoPaymentWebhook("777", "pre", new BigDecimal("1500"), "rejected");
    }

    @Test
    void cobroRecurrente_conDatosEnElCuerpoYDetalleCaido() throws Exception {
        when(admin.obtenerDetalleAuthorizedPayment("ap-2")).thenThrow(new IllegalStateException("caído"));
        notificaciones(true).procesar("{\"action\":\"subscription_authorized_payment.created\",\"preapproval_id\":\"pre\","
                + "\"transaction_amount\":\"900\",\"status\":\"approved\"}", "ap-2");
        verify(suscripciones).processMercadoPagoPaymentWebhook("ap-2", "pre", new BigDecimal("900"), "approved");
    }

    @Test
    void cobroRecurrente_sinNadaUsaValoresPorDefecto() throws Exception {
        notificaciones(true).procesar("{\"type\":\"subscription_authorized_payment\"}", null);
        verify(suscripciones).processMercadoPagoPaymentWebhook(null, "", null, "approved");
    }

    @Test
    void otroTipoDeSuscripcion_seIgnora() throws Exception {
        assertThat(notificaciones(true).procesar("{\"type\":\"subscription_otro\"}", null)).isEqualTo(Resultado.IGNORADO);
    }

    @Test
    void pagoDeProfesional_seAplicaConSuToken() throws Exception {
        when(cuentas.porMpUserId("u1")).thenReturn(Optional.of(new CuentasMp.Cuenta(10L, "a", "r", null, "u1")));
        when(vinculacion.accessTokenValido(10L)).thenReturn(Optional.of("tok"));
        when(mercadoPago.obtenerPago("tok", 5L)).thenReturn(new MercadoPagoApi.PagoMp(5L, "approved", "turno-9", BigDecimal.TEN));
        assertThat(notificaciones(true).procesar("{\"type\":\"payment\",\"user_id\":\"u1\",\"data\":{\"id\":\"5\"}}", "5"))
                .isEqualTo(Resultado.PROCESADO);
        verify(aprobaciones).aplicar("turno-9", "5", "approved", BigDecimal.TEN);
    }

    @Test
    void pagoDeLaPlataforma_conReferenciaDeSuscripcion() throws Exception {
        when(admin.obtenerPagoAdmin(6L)).thenReturn(new MercadoPagoAdminApi.PagoAdminMp(6L, "approved", "sub-4", BigDecimal.ONE));
        assertThat(notificaciones(true).procesar("{\"type\":\"payment\",\"data\":{\"id\":\"6\"}}", "6")).isEqualTo(Resultado.PROCESADO);
        verify(suscripciones).processMercadoPagoPaymentByExternalReference("sub-4", "6", BigDecimal.ONE, "approved");
    }

    @Test
    void pagoDeLaPlataforma_sinReferenciaOIlegible_seIgnora() throws Exception {
        when(admin.obtenerPagoAdmin(7L)).thenReturn(new MercadoPagoAdminApi.PagoAdminMp(7L, "approved", "turno-1", BigDecimal.ONE));
        when(admin.obtenerPagoAdmin(8L)).thenThrow(new IllegalStateException("403"));
        var n = notificaciones(true);
        assertThat(n.procesar("{\"type\":\"payment\",\"user_id\":\"otro\",\"data\":{\"id\":\"7\"}}", "7")).isEqualTo(Resultado.IGNORADO);
        assertThat(n.procesar("{\"type\":\"payment\",\"data\":{\"id\":\"8\"}}", "8")).isEqualTo(Resultado.IGNORADO);
        verify(aprobaciones, never()).aplicar(any(), any(), any(), any());
    }

    @Test
    void simuladorLocal_suscripcionYTurno() throws Exception {
        var n = notificaciones(true);
        n.procesar("{\"external_reference\":\"sub-3\",\"transaction_id\":\"t1\",\"amount\":\"100\",\"status\":\"rejected\"}", null);
        n.procesar("{\"external_reference\":\"sub-4\"}", null);
        n.procesar("{\"external_reference\":\"turno-5\"}", null);
        n.procesar("{\"external_reference\":\"turno-6\",\"transaction_id\":\"t2\"}", null);
        verify(suscripciones).processMercadoPagoPaymentByExternalReference("sub-3", "t1", new BigDecimal("100"), "rejected");
        verify(suscripciones).processMercadoPagoPaymentByExternalReference("sub-4", "mock-tx-123", null, "approved");
        verify(aprobaciones).aplicar("turno-5", "mock-tx-123", "approved", null);
        verify(aprobaciones).aplicar("turno-6", "t2", "approved", null);
    }

    @Test
    void avisoDesconocido_seIgnora() throws Exception {
        assertThat(notificaciones(true).procesar("{\"type\":\"merchant_order\"}", null)).isEqualTo(Resultado.IGNORADO);
    }
}
