package com.tranqui.app.service;

import com.tranqui.app.model.*;
import com.tranqui.app.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private SubscriptionPaymentRepository paymentRepository;

    @Autowired
    private SubscriptionEventRepository eventRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private PlanFeatureRepository planFeatureRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private ResendEmailService resendEmailService;

    @Autowired
    private MercadoPagoService mercadoPagoService;

    @Autowired
    private com.tranqui.app.service.arca.ArcaConfig arcaConfig;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    /**
     * Etapa 2 del paywall: crea el Preapproval de Mercado Pago (cobrado a la cuenta del admin) para
     * que este profesional pague/renueve su suscripción, y devuelve la URL de checkout a la que
     * hay que redirigirlo. No activa nada todavía — la activación real llega después, por
     * webhook (processMercadoPagoPreapprovalWebhook), cuando el profesional autoriza el pago.
     */
    @Transactional
    public String iniciarCheckout(Long professionalId, Long planId, String billingCycleInput) {
        Usuario profesional = usuarioRepository.findById(professionalId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Profesional no encontrado"));
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Plan no encontrado"));
        // Self-heal: cuentas creadas antes de que /register empezara a crear la fila de
        // suscripción (o dadas de alta por otra vía, p.ej. Google signup) llegan acá sin ninguna
        // fila — antes esto tiraba 422 y dejaba al profesional sin forma de salir del bloqueo del
        // paywall (no podía pagar porque no tenía suscripción, y no tenía suscripción porque nunca
        // había pagado). Crear la fila en el momento del checkout es la misma inicialización que
        // hace AuthController.register, solo que tardía.
        Subscription sub = subscriptionRepository.findByProfessionalId(professionalId)
                .orElseGet(() -> subscriptionRepository.save(Subscription.builder()
                        .professional(profesional)
                        .plan(plan)
                        .status(SubscriptionStatus.PENDING_VERIFICATION)
                        .seats(1)
                        .billingSource(BillingSource.MANUAL_TRANSFER)
                        .amountArs(plan.getPriceArs())
                        .build()));

        // Cualquier valor que no sea exactamente "annual" se trata como "monthly" — nunca se cobra
        // el ciclo anual por accidente ante un valor inesperado del frontend.
        String billingCycle = "annual".equalsIgnoreCase(billingCycleInput) ? "annual" : "monthly";
        if ("annual".equals(billingCycle) && plan.getPriceArsAnual() == null) {
            throw new IllegalStateException("El plan " + plan.getName() + " todavía no tiene precio anual configurado.");
        }
        BigDecimal montoElegido = "annual".equals(billingCycle) ? plan.getPriceArsAnual() : plan.getPriceArs();

        try {
            var preapproval = mercadoPagoService.crearSuscripcionPreapproval(profesional, plan, sub.getId(), billingCycle);

            sub.setPlan(plan);
            sub.setMpPreapprovalId(preapproval.getId());
            sub.setBillingSource(BillingSource.MERCADOPAGO);
            sub.setBillingCycle(billingCycle);
            sub.setAmountArs(montoElegido);
            sub.setUpdatedAt(LocalDateTime.now());
            subscriptionRepository.save(sub);

            logEvent(sub.getId(), "MP_CHECKOUT_CREATED", "professional", String.valueOf(professionalId),
                    "Preapproval creado: " + preapproval.getId() + " para plan " + plan.getCode() + " (" + billingCycle + ")");

            return mercadoPagoService.checkoutUrlFor(preapproval);
        } catch (IllegalStateException e) {
            throw e;
        } catch (com.mercadopago.exceptions.MPApiException e) {
            // e.getMessage() de MPApiException es siempre el genérico "Api error. Check response
            // for details" — el motivo real (payer == collector, moneda inválida, cuenta de
            // prueba vs. producción, etc.) viene en el body de la respuesta HTTP, no en el mensaje
            // de la excepción. Sin loguear esto acá, un 422 de Mercado Pago es indiagnosticable.
            Integer status = e.getApiResponse() != null ? e.getApiResponse().getStatusCode() : null;
            String detalle = e.getApiResponse() != null ? e.getApiResponse().getContent() : e.getMessage();
            log.error("Error de la API de Mercado Pago creando checkout para profesional {} (status {}): {}",
                    professionalId, status, detalle);
            // 429 = "local_rate_limited": Mercado Pago le puso un límite temporal a la cuenta admin
            // por demasiados intentos seguidos de crear Preapproval — no es un error del profesional
            // ni de nuestro lado, así que vale la pena distinguirlo del mensaje genérico.
            String mensaje = (status != null && status == 429)
                    ? "Mercado Pago está limitando temporalmente los intentos de pago. Esperá unos minutos y volvé a intentar."
                    : "No se pudo iniciar el pago con Mercado Pago. Intentá de nuevo en unos minutos.";
            throw new IllegalStateException(mensaje, e);
        } catch (Exception e) {
            log.error("Error creando checkout de Mercado Pago para profesional {}: {}", professionalId, e.getMessage(), e);
            throw new IllegalStateException("No se pudo iniciar el pago con Mercado Pago. Intentá de nuevo en unos minutos.", e);
        }
    }

    /**
     * Checks whether a professional has access to a particular system feature (Gate 1).
     */
    public boolean hasFeature(Long professionalId, String featureKey) {
        if (professionalId == null) return false;

        Usuario usuario = usuarioRepository.findById(professionalId).orElse(null);
        if (usuario == null) return false;

        // Admin has universal access
        if (usuario.getRol() == Rol.ADMIN) {
            return true;
        }

        // Check active subscription
        Optional<Subscription> subOpt = subscriptionRepository.findByProfessionalId(professionalId);
        if (subOpt.isEmpty()) {
            // Si el profesional no tiene suscripción aún (entorno de pruebas o profesionales existentes),
            // permitimos el acceso a recetas solo si tiene capacidad legal real para prescribir. El rol
            // "PSIQUIATRA" es el único rol de profesional en el sistema (lo comparten psicólogos y
            // psiquiatras) — no sirve para distinguirlos. Usuario.profession sí distingue.
            if ("recetas_electronicas".equals(featureKey) && esPsiquiatraOMedico(usuario)) {
                return true;
            }
            return false;
        }

        Subscription sub = subOpt.get();
        if (sub.getStatus() != SubscriptionStatus.ACTIVE && sub.getStatus() != SubscriptionStatus.PAST_DUE
                && sub.getStatus() != SubscriptionStatus.CANCELLED) {
            return false;
        }

        Plan plan = sub.getPlan();
        if (plan == null) return false;

        return planFeatureRepository.existsByPlanIdAndFeatureKey(plan.getId(), featureKey);
    }

    // Un psicólogo nunca tiene capacidad legal para prescribir, sin importar el rol compartido.
    // Cuentas legacy sin profession explícita se tratan como médico (comportamiento previo a esta
    // distinción, para no bloquear de golpe a psiquiatras ya migrados sin ese campo cargado).
    private boolean esPsiquiatraOMedico(Usuario usuario) {
        String profession = usuario.getProfession();
        return profession == null || profession.isBlank() || !"psicologo".equalsIgnoreCase(profession.trim());
    }

    /**
     * Paywall (Etapa 1): ¿este profesional tiene acceso real a la app en este momento?
     * Deliberadamente estricto y en tiempo real (no depende del cron diario de reconciliación,
     * que solo corre 1 vez por día y mira graceUntil, no currentPeriodEnd) — se evalúa en cada
     * request vía SubscriptionAccessFilter. Admin y roles no-profesionales (paciente, visitador)
     * nunca están sujetos a este gate.
     */
    public boolean isAccessAllowed(Long userId) {
        if (userId == null) return false;

        Usuario usuario = usuarioRepository.findById(userId).orElse(null);
        if (usuario == null) return false;
        if (usuario.getRol() != Rol.PSIQUIATRA) return true; // el paywall solo aplica a profesionales

        Subscription sub = subscriptionRepository.findByProfessionalId(userId).orElse(null);
        if (sub == null) return false; // sin fila de suscripción = sin acceso

        // CANCELLED también pasa acá: cancelar (desde el panel o directo en Mercado Pago) corta el
        // próximo cobro, pero el profesional ya pagó este período y lo sigue usando hasta que
        // termine — el check de abajo (currentPeriodEnd) es lo que corta el acceso que realmente
        // corresponde, no el status.
        if (sub.getStatus() != SubscriptionStatus.ACTIVE && sub.getStatus() != SubscriptionStatus.CANCELLED) return false;
        if (sub.getCurrentPeriodEnd() == null) return false; // ACTIVE sin período pagado = inconsistente, no dejamos pasar

        return sub.getCurrentPeriodEnd().isAfter(LocalDateTime.now());
    }

    // Corre una sola vez por arranque real (idempotente: solo toca filas que nunca tuvieron
    // currentPeriodEnd, así que después de la primera pasada no vuelve a encontrar candidatos).
    // Migra a los profesionales que YA estaban verificados/usando la app antes de que existiera
    // este paywall, dándoles 14 días de acceso (ACTIVE) antes de que el filtro los bloquee, en
    // vez de cortarles el acceso de un día para el otro sin aviso. Los profesionales nuevos que
    // se registren de acá en más NO entran acá — arrancan en PENDING_VERIFICATION sin
    // currentPeriodEnd, así que el paywall los bloquea desde el primer momento hasta que paguen
    // (Etapa 2) o el admin les registre un pago manual.
    //
    // Cubre DOS casos, no solo uno: cuentas creadas antes de que existiera la tabla Subscription
    // (seed/DataInitializer, o cualquier alta anterior a este feature) no tienen NINGUNA fila —
    // a esas hay que crearles una desde cero, no solo completarles currentPeriodEnd. Se detectó
    // este caso en el primer deploy: TODOS los profesionales de producción no tenían fila de
    // suscripción y quedaron bloqueados porque la versión anterior de este método los saltaba.
    private static final int DIAS_GRACIA_MIGRACION_PROFESIONALES_EXISTENTES = 14;

    @PostConstruct
    @Transactional
    public void migrateExistingVerifiedProfessionalsOnStartup() {
        try {
            List<Usuario> psiquiatras = usuarioRepository.findByRol(Rol.PSIQUIATRA);
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime gracePeriodEnd = now.plusDays(DIAS_GRACIA_MIGRACION_PROFESIONALES_EXISTENTES);
            int migrados = 0;

            for (Usuario u : psiquiatras) {
                if (!Boolean.TRUE.equals(u.getVerificadoAdmin())) continue;

                Subscription sub = subscriptionRepository.findByProfessionalId(u.getId()).orElse(null);
                if (sub != null && sub.getCurrentPeriodEnd() != null) continue; // ya tiene un período real (pago manual o migración previa)

                if (sub == null) {
                    // Rol=PSIQUIATRA es el único rol de profesional del sistema (lo comparten
                    // psicólogos y psiquiatras) — no alcanza para elegir el plan de migración.
                    // "clinico" incluye recetas_electronicas y no debe otorgársele a un psicólogo,
                    // así que la elección real depende de Usuario.profession.
                    String profession = u.getProfession();
                    String codigoPlan = "psicologo".equalsIgnoreCase(profession) ? "consultorio" : "clinico";
                    Plan planMigracion = planRepository.findByCode(codigoPlan).orElse(null);
                    if (planMigracion == null) {
                        log.error("Paywall: no se encontró el plan '{}' para migrar al profesional {} — se omite.", codigoPlan, u.getId());
                        continue;
                    }
                    sub = Subscription.builder()
                            .professional(u)
                            .plan(planMigracion)
                            .status(SubscriptionStatus.ACTIVE)
                            .seats(1)
                            .billingSource(BillingSource.MANUAL_TRANSFER)
                            .amountArs(planMigracion.getPriceArs())
                            .currentPeriodStart(now)
                            .currentPeriodEnd(gracePeriodEnd)
                            .nextBillingDate(gracePeriodEnd)
                            .cancelAtPeriodEnd(false)
                            .build();
                    subscriptionRepository.save(sub);

                    logEvent(sub.getId(), "MIGRATION_GRACE_PERIOD_GRANTED", null, SubscriptionStatus.ACTIVE.name(),
                            "system", "startup",
                            "Profesional existente (sin fila de suscripción previa) migrado al paywall con " +
                                    DIAS_GRACIA_MIGRACION_PROFESIONALES_EXISTENTES + " días de gracia, plan clinico. Vence: " + gracePeriodEnd);
                    migrados++;
                    continue;
                }

                SubscriptionStatus estadoPrevioMigracion = sub.getStatus();
                sub.setStatus(SubscriptionStatus.ACTIVE);
                sub.setCurrentPeriodStart(now);
                sub.setCurrentPeriodEnd(gracePeriodEnd);
                sub.setNextBillingDate(gracePeriodEnd);
                sub.setUpdatedAt(now);
                subscriptionRepository.save(sub);

                logEvent(sub.getId(), "MIGRATION_GRACE_PERIOD_GRANTED",
                        estadoPrevioMigracion != null ? estadoPrevioMigracion.name() : null, SubscriptionStatus.ACTIVE.name(),
                        "system", "startup",
                        "Profesional existente migrado al paywall con " + DIAS_GRACIA_MIGRACION_PROFESIONALES_EXISTENTES +
                                " días de gracia. Vence: " + gracePeriodEnd);
                migrados++;
            }

            if (migrados > 0) {
                log.info("Paywall: {} profesional(es) existente(s) migrado(s) con {} días de gracia (vence {})",
                        migrados, DIAS_GRACIA_MIGRACION_PROFESIONALES_EXISTENTES, gracePeriodEnd);
            }
        } catch (Exception e) {
            // Nunca debe tumbar el arranque del server por esto — si falla, simplemente esos
            // profesionales quedan sin migrar y se pueden migrar a mano después (o en el próximo restart).
            log.error("Error migrando profesionales existentes al paywall de suscripciones: {}", e.getMessage(), e);
        }
    }

    /**
     * Valida el Doble Gate (§7) para recetas electrónicas:
     * Gate 1: Plan contratado incluye 'recetas_electronicas'
     * Gate 2: Capacidad legal (médico / psiquiatra habilitado)
     */
    public void validarAccesoRecetas(Usuario prof) {
        if (prof == null) {
            throw new IllegalArgumentException("Usuario no autenticado");
        }

        if (prof.getRol() == Rol.ADMIN) {
            return;
        }

        // Gate 1: Plan
        if (!hasFeature(prof.getId(), "recetas_electronicas")) {
            throw new IllegalStateException("Tu plan actual no incluye recetas electrónicas oficiales. Podés actualizar tu suscripción al plan Clínico.");
        }

        // Gate 2: Capacidad legal independiente
        if (prof.getRol() != Rol.PSIQUIATRA && !prof.isCanPrescribe()) {
            throw new IllegalStateException("Tu matrícula profesional no se encuentra verificada y habilitada para prescribir medicamentos.");
        }
    }

    /**
     * Registra un pago manual (efectivo / transferencia / cortesía) desde el panel de admin.
     * Implementa protección contra doble submit vía idempotency_key (§8).
     */
    @Transactional
    public SubscriptionPayment registerManualPayment(
            Long adminId,
            Long professionalId,
            Long planId,
            BigDecimal amountArs,
            String method, // MANUAL_CASH | MANUAL_TRANSFER | COURTESY
            LocalDateTime periodStart,
            LocalDateTime periodEnd,
            String receiptReference,
            String notes,
            boolean emitInvoice,
            String idempotencyKey
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("La clave de idempotencia es obligatoria");
        }

        // 1. Idempotencia: si ya existe el pago con esa clave, retornarlo sin duplicar
        Optional<SubscriptionPayment> existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingPayment.isPresent()) {
            log.info("Pago idempotente ya registrado con key {}", idempotencyKey);
            return existingPayment.get();
        }

        // 2. Precondición fiscal si se va a emitir factura
        Usuario prof = usuarioRepository.findById(professionalId)
                .orElseThrow(() -> new IllegalArgumentException("Profesional no encontrado: " + professionalId));

        // Solo bloquea el alta si la facturación ARCA está realmente habilitada — mientras
        // arca.enabled=false (todavía sin certificados/alta real ante ARCA) generateInvoiceForPayment
        // no va a intentar nada igual, así que exigir CUIT acá solo trabaría el alta manual sin
        // motivo real.
        if (emitInvoice && arcaConfig.isEnabled() && !"COURTESY".equalsIgnoreCase(method)) {
            if (prof.getEffectiveTaxId() == null || prof.getEffectiveTaxId().isBlank()) {
                throw new IllegalStateException("No se puede emitir factura: el profesional no tiene CUIT/CUIL cargado");
            }
        }

        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan no encontrado: " + planId));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = periodStart != null ? periodStart : now;
        LocalDateTime end = periodEnd != null ? periodEnd : now.plusMonths(1);
        LocalDateTime grace = end.plusDays(7); // 7 días de ventana de gracia

        BillingSource source;
        try {
            source = BillingSource.valueOf(method.toUpperCase());
        } catch (Exception e) {
            source = BillingSource.MANUAL_TRANSFER;
        }

        // 3. Upsert suscripción
        Subscription subscription = subscriptionRepository.findByProfessionalId(prof.getId())
                .orElseGet(() -> Subscription.builder()
                        .professional(prof)
                        .createdAt(now)
                        .build());

        SubscriptionStatus estadoPrevioPagoManual = subscription.getStatus();
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setBillingSource(source);
        subscription.setAmountArs(amountArs != null ? amountArs : plan.getPriceArs());
        subscription.setCurrentPeriodStart(start);
        subscription.setCurrentPeriodEnd(end);
        subscription.setNextBillingDate(end);
        subscription.setGraceUntil(grace);
        subscription.setCancelAtPeriodEnd(false);
        subscription.setAvisoVencimientoEnviado(false);
        subscription.setUpdatedAt(now);

        subscription = subscriptionRepository.save(subscription);

        // 4. Insertar registro en subscription_payments
        SubscriptionPayment payment = SubscriptionPayment.builder()
                .subscriptionId(subscription.getId())
                .professionalId(prof.getId())
                .idempotencyKey(idempotencyKey)
                .method(source.name())
                .status("APPROVED")
                .amountArs(amountArs != null ? amountArs : plan.getPriceArs())
                .paidAt(now)
                .periodStart(start)
                .periodEnd(end)
                .registeredByAdminId(adminId)
                .receiptReference(receiptReference)
                .notes(notes)
                .createdAt(now)
                .build();

        payment = paymentRepository.save(payment);

        // 5. Auditoría en subscription_events (Ley 25.326)
        logEvent(subscription.getId(), "MANUAL_PAYMENT_ACTIVATED",
                estadoPrevioPagoManual != null ? estadoPrevioPagoManual.name() : null, SubscriptionStatus.ACTIVE.name(),
                "ADMIN", String.valueOf(adminId),
                "Método: " + method + ", Monto: " + payment.getAmountArs() + ", Período hasta: " + end);

        // 6. Facturación ARCA si corresponde
        if (emitInvoice && !"COURTESY".equalsIgnoreCase(method)) {
            emitirFacturaTrasCommit(payment.getId(), "pago manual");
        }

        // 7. Mail de pago confirmado al profesional — mismo criterio que la facturación: un alta
        // de cortesía no es un pago real, así que no corresponde el mail de "pago confirmado".
        if (!"COURTESY".equalsIgnoreCase(method)) {
            try {
                resendEmailService.enviarSuscripcionPagoConfirmado(
                        prof.getEmail(), prof.getNombre(), plan.getName(), payment.getAmountArs(), end);
            } catch (Exception e) {
                log.error("Error al enviar mail de pago confirmado para profesional {}: {}", prof.getId(), e.getMessage(), e);
            }
        }

        return payment;
    }

    // Dispara la emisión de factura ARCA recién DESPUÉS de que la transacción que activa la
    // suscripción/registra el pago haya confirmado — nunca desde adentro de esa misma
    // transacción. generateInvoiceForPayment es @Transactional (REQUIRED): si se la llama desde
    // adentro y tira una excepción (ej. "el profesional no tiene CUIT/CUIL cargado"), Spring
    // marca la transacción compartida como rollback-only en el aspecto ANTES de que nuestro
    // try/catch llegue a atraparla — así que igual termina en UnexpectedRollbackException al
    // commitear, revirtiendo silenciosamente TODO (suscripción activada, pago registrado
    // incluidos), sin ningún error visible para quien miraba la pantalla. Se detectó así: un
    // profesional pagaba de verdad en Mercado Pago, pero la suscripción se quedaba en el estado
    // viejo para siempre. La factura fallida igual se recupera sola — hay un cron diario
    // (SubscriptionReconciliationScheduler.checkPaymentsWithoutInvoice) que reintenta facturar
    // cualquier pago aprobado sin factura.
    private void emitirFacturaTrasCommit(Long paymentId, String contexto) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // No hay transacción activa (ej. tests) — generarla en el momento es lo mejor posible.
            try {
                invoiceService.generateInvoiceForPayment(paymentId);
            } catch (Exception e) {
                log.error("Error emitiendo factura ARCA para {} {}: {}", contexto, paymentId, e.getMessage(), e);
            }
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    invoiceService.generateInvoiceForPayment(paymentId);
                } catch (Exception e) {
                    log.error("Error emitiendo factura ARCA para {} {}: {}", contexto, paymentId, e.getMessage(), e);
                }
            }
        });
    }

    /**
     * Procesa cobro exitoso proveniente del Webhook de Mercado Pago (subscription_authorized_payment).
     */
    @Transactional
    public SubscriptionPayment processMercadoPagoPaymentWebhook(
            String mpPaymentId,
            String mpPreapprovalId,
            BigDecimal amount,
            String status
    ) {
        Optional<SubscriptionPayment> existing = paymentRepository.findByMpPaymentId(mpPaymentId);
        if (existing.isPresent()) {
            return existing.get();
        }

        Subscription sub = subscriptionRepository.findByMpPreapprovalId(mpPreapprovalId)
                .orElse(null);

        if (sub == null) {
            log.warn("Webhook Mercado Pago recibido para preapproval desconocido: {}", mpPreapprovalId);
            return null;
        }

        return activarPagoSuscripcion(sub, mpPaymentId, amount, status);
    }

    /**
     * Mismo caso que processMercadoPagoPaymentWebhook, pero para cuando Mercado Pago notifica el
     * cobro de una suscripción como un webhook "payment" genérico (type=payment) en vez del
     * "subscription_authorized_payment" esperado — depende de qué tópicos tenga suscriptos la
     * app de MP en su panel de desarrollador, y en la práctica el genérico es el que más llega.
     * Ahí no hay preapproval_id a mano, pero sí external_reference — que es justamente el "sub-
     * {subscriptionId}" que nosotros mismos seteamos al crear el Preapproval (ver
     * crearSuscripcionPreapproval), así que identificamos la suscripción por ahí en vez de por
     * mpPreapprovalId. Sin esto, un profesional que paga y solo recibe el webhook genérico se
     * queda viendo el cartel de "reactivá tu cuenta" para siempre pese a haber pagado.
     */
    @Transactional
    public SubscriptionPayment processMercadoPagoPaymentByExternalReference(
            String externalReference,
            String mpPaymentId,
            BigDecimal amount,
            String status
    ) {
        if (externalReference == null || !externalReference.startsWith("sub-")) {
            return null;
        }
        Long subscriptionId;
        try {
            subscriptionId = Long.parseLong(externalReference.substring(4));
        } catch (NumberFormatException e) {
            log.warn("external_reference de suscripción con formato inesperado: {}", externalReference);
            return null;
        }

        Optional<SubscriptionPayment> existing = paymentRepository.findByMpPaymentId(mpPaymentId);
        if (existing.isPresent()) {
            return existing.get();
        }

        Subscription sub = subscriptionRepository.findById(subscriptionId).orElse(null);
        if (sub == null) {
            log.warn("Webhook de pago de Mercado Pago recibido para suscripción desconocida: {}", subscriptionId);
            return null;
        }

        return activarPagoSuscripcion(sub, mpPaymentId, amount, status);
    }

    private SubscriptionPayment activarPagoSuscripcion(Subscription sub, String mpPaymentId, BigDecimal amount, String status) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextEnd = now.plusMonths(mesesDelCiclo(sub));

        SubscriptionStatus estadoPrevioPagoMp = sub.getStatus();
        SubscriptionStatus estadoNuevoPagoMp = "approved".equalsIgnoreCase(status) ? SubscriptionStatus.ACTIVE : SubscriptionStatus.PAST_DUE;
        sub.setStatus(estadoNuevoPagoMp);
        sub.setCurrentPeriodStart(now);
        sub.setCurrentPeriodEnd(nextEnd);
        sub.setNextBillingDate(nextEnd);
        sub.setGraceUntil(nextEnd.plusDays(7));
        sub.setAvisoVencimientoEnviado(false);
        subscriptionRepository.save(sub);

        String idemKey = "MP-" + mpPaymentId;
        SubscriptionPayment payment = SubscriptionPayment.builder()
                .subscriptionId(sub.getId())
                .professionalId(sub.getProfessional().getId())
                .mpPaymentId(mpPaymentId)
                .idempotencyKey(idemKey)
                .method("MERCADOPAGO")
                .status("approved".equalsIgnoreCase(status) ? "APPROVED" : "REJECTED")
                .amountArs(amount != null ? amount : sub.getAmountArs())
                .paidAt(now)
                .periodStart(now)
                .periodEnd(nextEnd)
                .receiptReference("MP ID " + mpPaymentId)
                .createdAt(now)
                .build();

        payment = paymentRepository.save(payment);

        logEvent(sub.getId(), "MP_SUBSCRIPTION_PAYMENT",
                estadoPrevioPagoMp != null ? estadoPrevioPagoMp.name() : null, estadoNuevoPagoMp.name(),
                "MP_WEBHOOK", mpPaymentId,
                "Estado: " + status + ", Monto: " + payment.getAmountArs());

        if ("APPROVED".equalsIgnoreCase(payment.getStatus())) {
            emitirFacturaTrasCommit(payment.getId(), "pago MP");
            try {
                resendEmailService.enviarSuscripcionPagoConfirmado(
                        sub.getProfessional().getEmail(), sub.getProfessional().getNombre(),
                        sub.getPlan() != null ? sub.getPlan().getName() : null, payment.getAmountArs(), nextEnd);
            } catch (Exception e) {
                log.error("Error al enviar mail de pago confirmado (MP) para suscripción {}: {}", sub.getId(), e.getMessage(), e);
            }
        }

        return payment;
    }

    /**
     * Cancelación desde el propio panel del profesional (Ajustes > Mi Suscripción). Corta el
     * próximo cobro recurrente en Mercado Pago, pero NO revoca el acceso ahora mismo — el
     * profesional ya pagó el período vigente y lo sigue usando hasta currentPeriodEnd (ver
     * isAccessAllowed, que trata CANCELLED igual que ACTIVE mientras el período no haya vencido).
     */
    @Transactional
    public void cancelarSuscripcion(Long professionalId) {
        Subscription sub = subscriptionRepository.findByProfessionalId(professionalId)
                .orElseThrow(() -> new IllegalStateException("No contás con una suscripción registrada."));

        if (Boolean.TRUE.equals(sub.getCancelAtPeriodEnd())) {
            return; // ya estaba cancelada — idempotente, no reintenta cancelar en MP de nuevo
        }

        if (sub.getMpPreapprovalId() != null && sub.getBillingSource() == BillingSource.MERCADOPAGO) {
            try {
                mercadoPagoService.cancelarSuscripcionPreapproval(sub.getMpPreapprovalId());
            } catch (com.mercadopago.exceptions.MPApiException e) {
                // Mismo problema que en iniciarCheckout: e.getMessage() es siempre el genérico
                // "Api error. Check response for details" — el motivo real viene en el body.
                String detalle = e.getApiResponse() != null ? e.getApiResponse().getContent() : e.getMessage();
                log.error("Error de la API de Mercado Pago cancelando preapproval {} para profesional {} (status {}): {}",
                        sub.getMpPreapprovalId(), professionalId,
                        e.getApiResponse() != null ? e.getApiResponse().getStatusCode() : null, detalle);
                throw new IllegalStateException(
                        "No se pudo cancelar el cobro recurrente en Mercado Pago. Intentá de nuevo o escribinos a soporte.", e);
            } catch (Exception e) {
                log.error("Error cancelando preapproval {} en Mercado Pago para profesional {}: {}",
                        sub.getMpPreapprovalId(), professionalId, e.getMessage(), e);
                throw new IllegalStateException(
                        "No se pudo cancelar el cobro recurrente en Mercado Pago. Intentá de nuevo o escribinos a soporte.", e);
            }
        }

        sub.setCancelAtPeriodEnd(true);
        sub.setUpdatedAt(LocalDateTime.now());
        subscriptionRepository.save(sub);

        logEvent(sub.getId(), "SUBSCRIPTION_CANCELLED_BY_PROFESSIONAL", "professional", String.valueOf(professionalId),
                "El profesional canceló su suscripción — mantiene acceso hasta " + sub.getCurrentPeriodEnd());

        try {
            resendEmailService.enviarSuscripcionCancelada(
                    sub.getProfessional().getEmail(), sub.getProfessional().getNombre(),
                    sub.getPlan() != null ? sub.getPlan().getName() : null, sub.getCurrentPeriodEnd());
        } catch (Exception e) {
            log.error("Error al enviar mail de suscripción cancelada para profesional {}: {}", professionalId, e.getMessage(), e);
        }
    }

    /**
     * Actualiza el estado de preapproval de Mercado Pago (authorized, paused, cancelled).
     */
    @Transactional
    public void processMercadoPagoPreapprovalWebhook(String mpPreapprovalId, String status) {
        Subscription sub = subscriptionRepository.findByMpPreapprovalId(mpPreapprovalId).orElse(null);
        if (sub == null) return;

        SubscriptionStatus oldStatus = sub.getStatus();
        SubscriptionStatus newStatus = switch (status.toLowerCase()) {
            case "authorized" -> SubscriptionStatus.ACTIVE;
            case "paused" -> SubscriptionStatus.PAST_DUE;
            case "cancelled" -> SubscriptionStatus.CANCELLED;
            default -> oldStatus;
        };

        sub.setStatus(newStatus);
        if (newStatus == SubscriptionStatus.CANCELLED) {
            sub.setCancelledAt(LocalDateTime.now());
        }
        // Activa el período de acceso acá mismo en vez de esperar al webhook separado
        // subscription_authorized_payment (que confirma el primer cobro, pero puede tardar más en
        // llegar) — así isAccessAllowed() desbloquea al profesional apenas autoriza, no cuando MP
        // termina de procesar el cargo. Si currentPeriodEnd ya está vigente (ej. venía de la
        // migración de gracia), no lo pisa para no acortarle el acceso que ya tenía.
        if (newStatus == SubscriptionStatus.ACTIVE
                && (sub.getCurrentPeriodEnd() == null || sub.getCurrentPeriodEnd().isBefore(LocalDateTime.now()))) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime periodEnd = now.plusMonths(mesesDelCiclo(sub));
            sub.setCurrentPeriodStart(now);
            sub.setCurrentPeriodEnd(periodEnd);
            sub.setNextBillingDate(periodEnd);
            sub.setGraceUntil(periodEnd.plusDays(7));
        }
        subscriptionRepository.save(sub);

        logEvent(sub.getId(), "MP_PREAPPROVAL_STATUS_CHANGED", oldStatus.name(), newStatus.name(),
                "MP_WEBHOOK", mpPreapprovalId,
                "Cambio de estado: " + oldStatus + " -> " + newStatus);
    }

    // 12 meses si el profesional contrató el ciclo anual, 1 mes en cualquier otro caso
    // (incluidas filas viejas sin billingCycle seteado — el default de la entidad es "monthly").
    private int mesesDelCiclo(Subscription sub) {
        return "annual".equalsIgnoreCase(sub.getBillingCycle()) ? 12 : 1;
    }

    public void logEvent(Long subscriptionId, String eventType, String actorType, String actorId, String payloadJson) {
        logEvent(subscriptionId, eventType, null, null, actorType, actorId, payloadJson);
    }

    /**
     * Variante para eventos que sí representan una transición de estado de la suscripción
     * (ej. ACTIVE -> SUSPENDED), para que el panel de admin pueda mostrar "de dónde a dónde"
     * pasó la suscripción en cada evento del historial de auditoría.
     */
    public void logEvent(Long subscriptionId, String eventType, String previousStatus, String newStatus,
                          String actorType, String actorId, String payloadJson) {
        SubscriptionEvent event = SubscriptionEvent.builder()
                .subscriptionId(subscriptionId)
                .eventType(eventType)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .actorType(actorType)
                .actorId(actorId)
                .payloadJson(payloadJson)
                .createdAt(LocalDateTime.now())
                .build();
        eventRepository.save(event);
    }

    public Optional<Subscription> getSubscriptionForProfessional(Long professionalId) {
        return subscriptionRepository.findByProfessionalId(professionalId);
    }

    public List<Subscription> getAllSubscriptions() {
        return subscriptionRepository.findAll();
    }

    /**
     * Retorna métricas clave para el panel de administración:
     * - Suscriptores activos por plan
     * - MRR (Monthly Recurring Revenue)
     * - Acumulado rodante 12 meses vs Tope de Monotributo Categoría A / B
     */
    public Map<String, Object> getAdminOverviewStats() {
        List<Subscription> activeSubs = subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE);
        long totalActive = activeSubs.size();

        // "En período de gracia" = suscripciones PAST_DUE: el cobro venció pero todavía tienen
        // acceso mientras corre la ventana de graceUntil (ver SubscriptionReconciliationScheduler,
        // que las suspende automáticamente cuando esa ventana vence).
        long totalEnGracia = subscriptionRepository.findByStatus(SubscriptionStatus.PAST_DUE).size();

        BigDecimal mrr = activeSubs.stream()
                .map(Subscription::getAmountArs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime twelveMonthsAgo = LocalDateTime.now().minusMonths(12);
        BigDecimal rolling12m = paymentRepository.sumApprovedPaymentsSince(twelveMonthsAgo);

        // Topes Monotributo vigentes agosto 2026 (§3)
        BigDecimal topeCatA = new BigDecimal("12009410.00");
        BigDecimal topeCatB = new BigDecimal("17595183.00");
        BigDecimal topeCatC = new BigDecimal("24670494.00");
        BigDecimal topeCatK = new BigDecimal("126610839.00");

        double percentCatA = rolling12m.divide(topeCatA, 4, java.math.RoundingMode.HALF_UP).doubleValue() * 100;
        boolean alerta80Porciento = percentCatA >= 80.0;

        // Nombres en español: son los que lee AdminSubscriptionsView.tsx en el frontend. Antes
        // estas claves no coincidían (ej. acá se mandaba "activeSubscribers" pero el frontend leía
        // "suscriptoresActivos"), por eso las cards de "Suscriptores Activos" y "En Período de
        // Gracia" siempre se veían vacías pese a que el backend sí tenía los datos.
        Map<String, Object> stats = new HashMap<>();
        stats.put("suscriptoresActivos", totalActive);
        stats.put("suscriptoresEnGracia", totalEnGracia);
        stats.put("mrr", mrr);
        stats.put("totalFacturado12Meses", rolling12m);
        stats.put("topeCategoriaA", topeCatA);
        stats.put("topeCatB", topeCatB);
        stats.put("topeCatK", topeCatK);
        stats.put("porcentajeUsoCategoriaA", Math.round(percentCatA * 10.0) / 10.0);
        stats.put("alertaMonotributo80", alerta80Porciento);

        return stats;
    }
}
