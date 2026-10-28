package com.tranqui.pagos.interno;

import com.tranqui.comun.correlation.CorrelationIdHolder;
import com.tranqui.eventos.jwt.ServiceJwt;
import com.tranqui.pagos.datos.EventosSuscripcion;
import com.tranqui.pagos.datos.Facturas;
import com.tranqui.pagos.datos.PagosSuscripcion;
import com.tranqui.pagos.datos.Planes;
import com.tranqui.pagos.datos.SecuenciasFactura;
import com.tranqui.pagos.datos.Suscripciones;
import com.tranqui.pagos.datos.Suscriptores;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Llamadas internas de pagos al monolito, siempre por el gateway con un JWT de servicio (sección 4.2):
 * las consultas de la carga inicial, el reenvío de las notificaciones de suscripciones de Mercado Pago,
 * y la carga inicial y resincronización de suscriptores y suscripciones (Fase 8, S2a).
 */
@Component
public class ClienteInterno {

    static final String SERVICIO = "pagos";

    public record Contacto(Long id, String email, String nombre, String apellido, String rol, Boolean emailHabilitado) {}

    /** Los tokens llegan cifrados, tal cual están en el monolito. */
    public record Cuenta(Long profesionalId, String accessTokenCifrado, String refreshTokenCifrado,
                         LocalDateTime tokenVenceEn, String mpUserId) {}

    public record Pago(Long id, Long turnoId, Long solicitudId, Long profesionalId, Long pacienteId, String transactionId,
                       String estado, BigDecimal monto, LocalDateTime fechaPago) {}

    public record Solicitud(Long solicitudId, Long profesionalId, Long pacienteId, String tipoConcepto, BigDecimal precio,
                            String estado, String transactionId, String titulo) {}

    public record CobroPendiente(Long turnoId, Long profesionalId, Long pacienteId, BigDecimal monto, String titulo,
                                 String checkoutUrl, LocalDateTime creadoEn) {}

    // ------------------------------------------------------------------ DTOs de Suscripciones (Fase 8)

    public record PlanMonolito(
            Long id,
            String code,
            String name,
            String description,
            BigDecimal priceArs,
            BigDecimal priceUsdRef,
            BigDecimal priceArsAnual,
            String billingPeriod,
            Integer minSeats,
            String mpPreapprovalPlanId,
            Boolean requiresPrescriber,
            Boolean isActive,
            LocalDateTime effectiveFrom,
            LocalDateTime effectiveTo
    ) {
        public Planes.Plan aPlan() {
            return new Planes.Plan(id, code, name, description, priceArs, priceUsdRef, priceArsAnual,
                    billingPeriod, minSeats, mpPreapprovalPlanId, requiresPrescriber, isActive,
                    effectiveFrom, effectiveTo);
        }
    }

    public record FeatureMonolito(
            String key,
            String name,
            String description
    ) {
        public Planes.Feature aFeature() {
            return new Planes.Feature(key, name, description);
        }
    }

    public record PlanFeatureMonolito(
            Long id,
            Long planId,
            String featureKey,
            Integer limitValue
    ) {
        public Planes.PlanFeature aPlanFeature() {
            return new Planes.PlanFeature(id, planId, featureKey, limitValue);
        }
    }

    public record SubscriptionMonolito(
            Long id,
            Integer version,
            Long professionalId,
            Long planId,
            String status,
            Integer seats,
            String billingSource,
            String mpPreapprovalId,
            BigDecimal amountArs,
            String billingCycle,
            LocalDateTime nextBillingDate,
            LocalDateTime currentPeriodStart,
            LocalDateTime currentPeriodEnd,
            LocalDateTime graceUntil,
            Boolean cancelAtPeriodEnd,
            LocalDateTime cancelledAt,
            Boolean renewalReminderSent,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public Suscripciones.Suscripcion aSuscripcion() {
            return new Suscripciones.Suscripcion(id, version, professionalId, planId, status, seats,
                    billingSource, mpPreapprovalId, amountArs, billingCycle, nextBillingDate,
                    currentPeriodStart, currentPeriodEnd, graceUntil, cancelAtPeriodEnd, cancelledAt,
                    renewalReminderSent, createdAt, updatedAt);
        }
    }

    public record SubscriptionEventMonolito(
            Long id,
            Long subscriptionId,
            String eventType,
            String previousStatus,
            String newStatus,
            String actorType,
            String actorId,
            String payloadJson,
            LocalDateTime createdAt
    ) {
        public EventosSuscripcion.Evento aEvento() {
            return new EventosSuscripcion.Evento(id, subscriptionId, eventType, previousStatus, newStatus,
                    actorType, actorId, payloadJson, createdAt);
        }
    }

    public record SubscriptionPaymentMonolito(
            Long id,
            Long subscriptionId,
            Long professionalId,
            String mpPaymentId,
            String idempotencyKey,
            String method,
            String status,
            BigDecimal amountArs,
            LocalDateTime paidAt,
            LocalDateTime periodStart,
            LocalDateTime periodEnd,
            Long invoiceId,
            Long registeredByAdminId,
            String receiptReference,
            String notes,
            LocalDateTime createdAt
    ) {
        public PagosSuscripcion.PagoSuscripcion aPago() {
            return new PagosSuscripcion.PagoSuscripcion(id, subscriptionId, professionalId, mpPaymentId,
                    idempotencyKey, method, status, amountArs, paidAt, periodStart, periodEnd,
                    invoiceId, registeredByAdminId, receiptReference, notes, createdAt);
        }
    }

    public record InvoiceMonolito(
            Long id,
            Long paymentId,
            Integer cbteTipo,
            Integer puntoVenta,
            Long cbteNumero,
            String cae,
            LocalDate caeVencimiento,
            Integer receptorDocTipo,
            Long receptorDocNro,
            String receptorNombre,
            Integer receptorCondicionIva,
            BigDecimal importeTotal,
            String moneda,
            BigDecimal cotizacion,
            LocalDate fechaEmision,
            String pdfUrl,
            String pdfBase64,
            String status,
            Integer attempts,
            String lastError,
            String arcaRequestJson,
            String arcaResponseJson,
            Long comprobanteAsociadoId,
            LocalDateTime createdAt,
            LocalDateTime issuedAt
    ) {
        public Facturas.Factura aFactura() {
            return new Facturas.Factura(id, paymentId, cbteTipo, puntoVenta, cbteNumero, cae, caeVencimiento,
                    receptorDocTipo, receptorDocNro, receptorNombre, receptorCondicionIva, importeTotal,
                    moneda, cotizacion, fechaEmision, pdfUrl, pdfBase64, status, attempts, lastError,
                    arcaRequestJson, arcaResponseJson, comprobanteAsociadoId, createdAt, issuedAt);
        }
    }

    public record InvoiceSequenceMonolito(
            Long id,
            Integer puntoVenta,
            Integer cbteTipo,
            Long lastNumber,
            LocalDateTime updatedAt
    ) {
        public SecuenciasFactura.Secuencia aSecuencia() {
            return new SecuenciasFactura.Secuencia(id, puntoVenta, cbteTipo, lastNumber, updatedAt);
        }
    }

    public record SuscriptorMonolito(
            Long id,
            String email,
            String nombre,
            String apellido,
            String rol,
            String profession,
            Boolean verificadoAdmin,
            int version,
            String taxIdType,
            String taxId,
            String legalName,
            Integer ivaConditionId,
            String fiscalAddress,
            String cuit,
            Long cuil,
            String dni,
            Integer numeroDocumento
    ) {
        public Suscriptores.Suscriptor aSuscriptor() {
            return new Suscriptores.Suscriptor(id, email, nombre, apellido, rol, profession,
                    verificadoAdmin, version, taxIdType, taxId, legalName, ivaConditionId,
                    fiscalAddress, cuit, cuil, dni, numeroDocumento, LocalDateTime.now());
        }
    }

    private final RestClient http;
    private final ServiceJwt serviceJwt;

    // /internal/monolito o /internal/identidad (ver rutaIdentidad).
    private final String baseIdentidad;

    public ClienteInterno(@Value("${tranqui.gateway.url}") String gatewayUrl,
                          @Value("${tranqui.jwt.service-secret}") String claveServicio,
                          @Value("${tranqui.fuente-identidad:monolito}") String fuenteIdentidad) {
        this.baseIdentidad = rutaIdentidad(fuenteIdentidad);
        if (claveServicio == null || claveServicio.isBlank()) {
            throw new IllegalStateException("Falta SERVICE_JWT_KEY: el servicio no puede hacer consultas internas.");
        }
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(2_000);
        fabrica.setReadTimeout(30_000);
        this.http = RestClient.builder().baseUrl(gatewayUrl).requestFactory(fabrica).build();
        this.serviceJwt = new ServiceJwt(claveServicio);
    }

    /**
     * De dónde se leen los datos de usuario (Fase 9): {@code monolito} (por defecto, hasta el corte) o
     * {@code identidad}. Volver atrás es sacar la variable {@code TRANQUI_FUENTE_IDENTIDAD}.
     */
    static String rutaIdentidad(String fuente) {
        String f = fuente == null ? "" : fuente.trim().toLowerCase();
        return switch (f) {
            case "", "monolito" -> "/internal/monolito";
            case "identidad" -> "/internal/identidad";
            default -> throw new IllegalStateException(
                    "TRANQUI_FUENTE_IDENTIDAD tiene que ser 'monolito' o 'identidad', no '" + fuente + "'.");
        };
    }

    /** Si los datos de usuario se leen del servicio de identidad (y lo que no es suyo, de su dueño). */
    public boolean fuenteIdentidad() {
        return "/internal/identidad".equals(baseIdentidad);
    }

    private <T> List<T> lista(String uri, ParameterizedTypeReference<List<T>> tipo, Object... variables) {
        List<T> r = http.get().uri(uri, variables)
                .headers(h -> {
                    h.setBearerAuth(serviceJwt.emitirToken(SERVICIO));
                    h.set("X-Correlation-Id", CorrelationIdHolder.getOrGenerateCorrelationId());
                })
                .retrieve().body(tipo);
        return r != null ? r : List.of();
    }

    public List<Contacto> contactos(int pagina, int tamanio) {
        return lista("/internal/monolito/contactos?pagina={p}&tamanio={t}", new ParameterizedTypeReference<>() {}, pagina, tamanio);
    }

    public List<Cuenta> cuentas() {
        return lista("/internal/monolito/pagos/cuentas", new ParameterizedTypeReference<>() {});
    }

    public List<Pago> pagos(int pagina, int tamanio) {
        return lista("/internal/monolito/pagos/pagos?pagina={p}&tamanio={t}", new ParameterizedTypeReference<>() {}, pagina, tamanio);
    }

    public List<Solicitud> solicitudes() {
        return lista("/internal/monolito/pagos/solicitudes", new ParameterizedTypeReference<>() {});
    }

    public List<CobroPendiente> cobrosPendientes() {
        return lista("/internal/monolito/pagos/cobros-pendientes", new ParameterizedTypeReference<>() {});
    }

    /**
     * Le pasa al monolito una notificación de suscripción de Mercado Pago tal cual llegó (ya con la firma
     * validada acá). Si el monolito no responde, tira la excepción: el webhook responde 500 y Mercado Pago
     * la reintenta.
     */
    public void reenviarSuscripcion(String cuerpo, String dataId) {
        http.post().uri(dataId != null ? "/internal/monolito/pagos/notificacion-suscripcion?dataId={d}"
                        : "/internal/monolito/pagos/notificacion-suscripcion", dataId)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> {
                    h.setBearerAuth(serviceJwt.emitirToken(SERVICIO));
                    h.set("X-Correlation-Id", CorrelationIdHolder.getOrGenerateCorrelationId());
                })
                .body(cuerpo)
                .retrieve().toBodilessEntity();
    }

    // ------------------------------------------------------------------ Consultas para Suscripciones (Fase 8, S2a)

    public List<PlanMonolito> planes(int pagina, int tamanio) {
        return lista("/internal/monolito/suscripciones/planes?pagina={p}&tamanio={t}",
                new ParameterizedTypeReference<>() {}, pagina, tamanio);
    }

    public List<FeatureMonolito> features(int pagina, int tamanio) {
        return lista("/internal/monolito/suscripciones/features?pagina={p}&tamanio={t}",
                new ParameterizedTypeReference<>() {}, pagina, tamanio);
    }

    public List<PlanFeatureMonolito> planFeatures(int pagina, int tamanio) {
        return lista("/internal/monolito/suscripciones/plan-features?pagina={p}&tamanio={t}",
                new ParameterizedTypeReference<>() {}, pagina, tamanio);
    }

    public List<SubscriptionMonolito> suscripciones(int pagina, int tamanio) {
        return lista("/internal/monolito/suscripciones/suscripciones?pagina={p}&tamanio={t}",
                new ParameterizedTypeReference<>() {}, pagina, tamanio);
    }

    public List<SubscriptionEventMonolito> eventosSuscripcion(int pagina, int tamanio) {
        return lista("/internal/monolito/suscripciones/eventos?pagina={p}&tamanio={t}",
                new ParameterizedTypeReference<>() {}, pagina, tamanio);
    }

    public List<SubscriptionPaymentMonolito> pagosSuscripcion(int pagina, int tamanio) {
        return lista("/internal/monolito/suscripciones/pagos?pagina={p}&tamanio={t}",
                new ParameterizedTypeReference<>() {}, pagina, tamanio);
    }

    public List<InvoiceMonolito> facturas(int pagina, int tamanio) {
        return lista("/internal/monolito/suscripciones/facturas?pagina={p}&tamanio={t}",
                new ParameterizedTypeReference<>() {}, pagina, tamanio);
    }

    public List<InvoiceSequenceMonolito> secuenciasFactura(int pagina, int tamanio) {
        return lista("/internal/monolito/suscripciones/secuencias?pagina={p}&tamanio={t}",
                new ParameterizedTypeReference<>() {}, pagina, tamanio);
    }

    public List<SuscriptorMonolito> suscriptores(int pagina, int tamanio) {
        return lista(baseIdentidad + "/suscriptores?pagina={p}&tamanio={t}",
                new ParameterizedTypeReference<>() {}, pagina, tamanio);
    }

    public SuscriptorMonolito suscriptor(Long id) {
        return http.get().uri(baseIdentidad + "/suscriptores/{id}", id)
                .headers(h -> {
                    h.setBearerAuth(serviceJwt.emitirToken(SERVICIO));
                    h.set("X-Correlation-Id", CorrelationIdHolder.getOrGenerateCorrelationId());
                })
                .retrieve().body(SuscriptorMonolito.class);
    }
}
