package com.tranqui.app.controller;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.tranqui.app.model.*;
import com.tranqui.app.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Fase 8: rutas internas del monolito para la carga inicial del servicio de pagos (S2a).
 * Expone planes, features, plan-features, suscripciones, eventos de suscripción, pagos, facturas y secuencias,
 * además de los suscriptores con sus datos fiscales.
 * Protegidas por {@link com.tranqui.app.config.FiltroServicioInterno}, como el resto de {@code /internal/monolito}.
 */
@RestController
@RequestMapping("/internal/monolito")
public class InternoSuscripcionesController {

    private static final int TAMANIO_MAXIMO = 1000;
    private static final int TAMANIO_MAXIMO_FACTURAS = 20;

    public record PlanDto(
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
    ) {}

    public record FeatureDto(
            String key,
            String name,
            String description
    ) {}

    public record PlanFeatureDto(
            Long id,
            Long planId,
            String featureKey,
            Integer limitValue
    ) {}

    public record SubscriptionDto(
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
    ) {}

    public record SubscriptionEventDto(
            Long id,
            Long subscriptionId,
            String eventType,
            String previousStatus,
            String newStatus,
            String actorType,
            String actorId,
            String payloadJson,
            LocalDateTime createdAt
    ) {}

    public record SubscriptionPaymentDto(
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
    ) {}

    public record InvoiceDto(
            Long id,
            Long paymentId,
            Integer cbteTipo,
            Integer puntoVenta,
            Long cbteNumero,
            String cae,
            @JsonSerialize(using = LocalDateSerializer.class) LocalDate caeVencimiento,
            Integer receptorDocTipo,
            Long receptorDocNro,
            String receptorNombre,
            Integer receptorCondicionIva,
            BigDecimal importeTotal,
            String moneda,
            BigDecimal cotizacion,
            @JsonSerialize(using = LocalDateSerializer.class) LocalDate fechaEmision,
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
    ) {}

    public record InvoiceSequenceDto(
            Long id,
            Integer puntoVenta,
            Integer cbteTipo,
            Long lastNumber,
            LocalDateTime updatedAt
    ) {}

    public record SuscriptorDto(
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
    ) {}

    private final PlanRepository planRepository;
    private final FeatureRepository featureRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionEventRepository subscriptionEventRepository;
    private final SubscriptionPaymentRepository subscriptionPaymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceSequenceRepository invoiceSequenceRepository;
    private final UsuarioRepository usuarioRepository;

    public InternoSuscripcionesController(PlanRepository planRepository,
                                          FeatureRepository featureRepository,
                                          PlanFeatureRepository planFeatureRepository,
                                          SubscriptionRepository subscriptionRepository,
                                          SubscriptionEventRepository subscriptionEventRepository,
                                          SubscriptionPaymentRepository subscriptionPaymentRepository,
                                          InvoiceRepository invoiceRepository,
                                          InvoiceSequenceRepository invoiceSequenceRepository,
                                          UsuarioRepository usuarioRepository) {
        this.planRepository = planRepository;
        this.featureRepository = featureRepository;
        this.planFeatureRepository = planFeatureRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionEventRepository = subscriptionEventRepository;
        this.subscriptionPaymentRepository = subscriptionPaymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceSequenceRepository = invoiceSequenceRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/suscripciones/planes")
    @Transactional(readOnly = true)
    public List<PlanDto> planes(@RequestParam(name = "pagina", defaultValue = "0") int pagina,
                                @RequestParam(name = "tamanio", defaultValue = "500") int tamanio) {
        int tamanioSeguro = Math.max(1, Math.min(tamanio, TAMANIO_MAXIMO));
        return planRepository.findAll(PageRequest.of(Math.max(0, pagina), tamanioSeguro, Sort.by("id"))).stream()
                .map(InternoSuscripcionesController::plan)
                .toList();
    }

    @GetMapping("/suscripciones/features")
    @Transactional(readOnly = true)
    public List<FeatureDto> features(@RequestParam(name = "pagina", defaultValue = "0") int pagina,
                                     @RequestParam(name = "tamanio", defaultValue = "500") int tamanio) {
        int tamanioSeguro = Math.max(1, Math.min(tamanio, TAMANIO_MAXIMO));
        return featureRepository.findAll(PageRequest.of(Math.max(0, pagina), tamanioSeguro, Sort.by("key"))).stream()
                .map(InternoSuscripcionesController::feature)
                .toList();
    }

    @GetMapping("/suscripciones/plan-features")
    @Transactional(readOnly = true)
    public List<PlanFeatureDto> planFeatures(@RequestParam(name = "pagina", defaultValue = "0") int pagina,
                                             @RequestParam(name = "tamanio", defaultValue = "500") int tamanio) {
        int tamanioSeguro = Math.max(1, Math.min(tamanio, TAMANIO_MAXIMO));
        return planFeatureRepository.findAll(PageRequest.of(Math.max(0, pagina), tamanioSeguro, Sort.by("id"))).stream()
                .map(InternoSuscripcionesController::planFeature)
                .toList();
    }

    @GetMapping("/suscripciones/suscripciones")
    @Transactional(readOnly = true)
    public List<SubscriptionDto> suscripciones(@RequestParam(name = "pagina", defaultValue = "0") int pagina,
                                               @RequestParam(name = "tamanio", defaultValue = "500") int tamanio) {
        int tamanioSeguro = Math.max(1, Math.min(tamanio, TAMANIO_MAXIMO));
        return subscriptionRepository.findAll(PageRequest.of(Math.max(0, pagina), tamanioSeguro, Sort.by("id"))).stream()
                .map(InternoSuscripcionesController::subscription)
                .toList();
    }

    @GetMapping("/suscripciones/eventos")
    @Transactional(readOnly = true)
    public List<SubscriptionEventDto> eventos(@RequestParam(name = "pagina", defaultValue = "0") int pagina,
                                              @RequestParam(name = "tamanio", defaultValue = "500") int tamanio) {
        int tamanioSeguro = Math.max(1, Math.min(tamanio, TAMANIO_MAXIMO));
        return subscriptionEventRepository.findAll(PageRequest.of(Math.max(0, pagina), tamanioSeguro, Sort.by("id"))).stream()
                .map(InternoSuscripcionesController::subscriptionEvent)
                .toList();
    }

    @GetMapping("/suscripciones/pagos")
    @Transactional(readOnly = true)
    public List<SubscriptionPaymentDto> pagos(@RequestParam(name = "pagina", defaultValue = "0") int pagina,
                                              @RequestParam(name = "tamanio", defaultValue = "500") int tamanio) {
        int tamanioSeguro = Math.max(1, Math.min(tamanio, TAMANIO_MAXIMO));
        return subscriptionPaymentRepository.findAll(PageRequest.of(Math.max(0, pagina), tamanioSeguro, Sort.by("id"))).stream()
                .map(InternoSuscripcionesController::subscriptionPayment)
                .toList();
    }

    @GetMapping("/suscripciones/facturas")
    @Transactional(readOnly = true)
    public List<InvoiceDto> facturas(@RequestParam(name = "pagina", defaultValue = "0") int pagina,
                                     @RequestParam(name = "tamanio", defaultValue = "20") int tamanio) {
        int tamanioSeguro = Math.max(1, Math.min(tamanio, TAMANIO_MAXIMO_FACTURAS));
        return invoiceRepository.findAll(PageRequest.of(Math.max(0, pagina), tamanioSeguro, Sort.by("id"))).stream()
                .map(InternoSuscripcionesController::invoice)
                .toList();
    }

    @GetMapping("/suscripciones/secuencias")
    @Transactional(readOnly = true)
    public List<InvoiceSequenceDto> secuencias(@RequestParam(name = "pagina", defaultValue = "0") int pagina,
                                               @RequestParam(name = "tamanio", defaultValue = "500") int tamanio) {
        int tamanioSeguro = Math.max(1, Math.min(tamanio, TAMANIO_MAXIMO));
        return invoiceSequenceRepository.findAll(PageRequest.of(Math.max(0, pagina), tamanioSeguro, Sort.by("id"))).stream()
                .map(InternoSuscripcionesController::invoiceSequence)
                .toList();
    }

    @GetMapping("/suscriptores/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<SuscriptorDto> suscriptorPorId(@PathVariable Long id) {
        return usuarioRepository.findById(id)
                .filter(u -> u.getRol() == Rol.PSIQUIATRA || subscriptionRepository.findByProfessionalId(u.getId()).isPresent())
                .map(u -> ResponseEntity.ok(suscriptor(u)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/suscriptores")
    @Transactional(readOnly = true)
    public List<SuscriptorDto> suscriptores(@RequestParam(name = "pagina", defaultValue = "0") int pagina,
                                            @RequestParam(name = "tamanio", defaultValue = "500") int tamanio) {
        int tamanioSeguro = Math.max(1, Math.min(tamanio, TAMANIO_MAXIMO));
        return usuarioRepository.findSuscriptores(PageRequest.of(Math.max(0, pagina), tamanioSeguro, Sort.by("id"))).stream()
                .map(InternoSuscripcionesController::suscriptor)
                .toList();
    }

    private static PlanDto plan(Plan p) {
        return new PlanDto(
                p.getId(),
                p.getCode(),
                p.getName(),
                p.getDescription(),
                p.getPriceArs(),
                p.getPriceUsdRef(),
                p.getPriceArsAnual(),
                p.getBillingPeriod(),
                p.getMinSeats(),
                p.getMpPreapprovalPlanId(),
                p.getRequiresPrescriber(),
                p.getIsActive(),
                p.getEffectiveFrom(),
                p.getEffectiveTo()
        );
    }

    private static FeatureDto feature(Feature f) {
        return new FeatureDto(
                f.getKey(),
                f.getName(),
                f.getDescription()
        );
    }

    private static PlanFeatureDto planFeature(PlanFeature pf) {
        return new PlanFeatureDto(
                pf.getId(),
                pf.getPlan() != null ? pf.getPlan().getId() : null,
                pf.getFeatureKey(),
                pf.getLimitValue()
        );
    }

    private static SubscriptionDto subscription(Subscription s) {
        return new SubscriptionDto(
                s.getId(),
                s.getVersion(),
                s.getProfessional() != null ? s.getProfessional().getId() : null,
                s.getPlan() != null ? s.getPlan().getId() : null,
                s.getStatus() != null ? s.getStatus().name() : null,
                s.getSeats(),
                s.getBillingSource() != null ? s.getBillingSource().name() : null,
                s.getMpPreapprovalId(),
                s.getAmountArs(),
                s.getBillingCycle(),
                s.getNextBillingDate(),
                s.getCurrentPeriodStart(),
                s.getCurrentPeriodEnd(),
                s.getGraceUntil(),
                s.getCancelAtPeriodEnd(),
                s.getCancelledAt(),
                s.getRenewalReminderSent(),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }

    private static SubscriptionEventDto subscriptionEvent(SubscriptionEvent se) {
        return new SubscriptionEventDto(
                se.getId(),
                se.getSubscriptionId(),
                se.getEventType(),
                se.getPreviousStatus(),
                se.getNewStatus(),
                se.getActorType(),
                se.getActorId(),
                se.getPayloadJson(),
                se.getCreatedAt()
        );
    }

    private static SubscriptionPaymentDto subscriptionPayment(SubscriptionPayment sp) {
        return new SubscriptionPaymentDto(
                sp.getId(),
                sp.getSubscriptionId(),
                sp.getProfessionalId(),
                sp.getMpPaymentId(),
                sp.getIdempotencyKey(),
                sp.getMethod(),
                sp.getStatus(),
                sp.getAmountArs(),
                sp.getPaidAt(),
                sp.getPeriodStart(),
                sp.getPeriodEnd(),
                sp.getInvoiceId(),
                sp.getRegisteredByAdminId(),
                sp.getReceiptReference(),
                sp.getNotes(),
                sp.getCreatedAt()
        );
    }

    private static InvoiceDto invoice(Invoice i) {
        return new InvoiceDto(
                i.getId(),
                i.getPaymentId(),
                i.getCbteTipo(),
                i.getPuntoVenta(),
                i.getCbteNumero(),
                i.getCae(),
                i.getCaeVencimiento(),
                i.getReceptorDocTipo(),
                i.getReceptorDocNro(),
                i.getReceptorNombre(),
                i.getReceptorCondicionIva(),
                i.getImporteTotal(),
                i.getMoneda(),
                i.getCotizacion(),
                i.getFechaEmision(),
                i.getPdfUrl(),
                i.getPdfBase64(),
                i.getStatus() != null ? i.getStatus().name() : null,
                i.getAttempts(),
                i.getLastError(),
                i.getArcaRequestJson(),
                i.getArcaResponseJson(),
                i.getComprobanteAsociadoId(),
                i.getCreatedAt(),
                i.getIssuedAt()
        );
    }

    private static InvoiceSequenceDto invoiceSequence(InvoiceSequence is) {
        return new InvoiceSequenceDto(
                is.getId(),
                is.getPuntoVenta(),
                is.getCbteTipo(),
                is.getLastNumber(),
                is.getUpdatedAt()
        );
    }

    private static SuscriptorDto suscriptor(Usuario u) {
        return new SuscriptorDto(
                u.getId(),
                u.getEmail(),
                u.getNombre(),
                u.getApellido(),
                u.getRol() != null ? u.getRol().name() : null,
                u.getProfession(),
                u.getVerificadoAdmin(),
                u.getAggregateVersion(),
                u.getTaxIdType(),
                u.getTaxId(),
                u.getLegalName(),
                u.getIvaConditionId(),
                u.getFiscalAddress(),
                u.getCuit(),
                u.getCuil(),
                u.getDni(),
                u.getNumeroDocumento()
        );
    }
}
