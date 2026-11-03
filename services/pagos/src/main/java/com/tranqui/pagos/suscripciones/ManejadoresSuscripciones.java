package com.tranqui.pagos.suscripciones;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.consumo.ManejadorEvento;
import com.tranqui.eventos.data.UsuarioRegistradoData;
import com.tranqui.eventos.data.VerificacionResueltaData;
import com.tranqui.pagos.datos.Planes;
import com.tranqui.pagos.datos.Suscripciones;
import com.tranqui.pagos.datos.Suscriptores;
import com.tranqui.pagos.interno.CargaInicialSuscripciones;
import com.tranqui.pagos.interno.Copias;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Consumo de eventos de identidad para alta y verificación de suscripciones (Fase 8, S2b).
 * Activos únicamente cuando {@code pagos.suscripciones.habilitada=true} y la carga inicial ha finalizado.
 */
@Configuration
public class ManejadoresSuscripciones {

    private static final Logger log = LoggerFactory.getLogger(ManejadoresSuscripciones.class);

    private final Suscripciones suscripciones;
    private final Planes planes;
    private final Suscriptores suscriptores;
    private final Copias copias;
    private final SuscripcionService suscripcionService;
    private final CargaInicialSuscripciones cargaInicialSuscripciones;
    private final boolean habilitada;

    public ManejadoresSuscripciones(
            Suscripciones suscripciones,
            Planes planes,
            Suscriptores suscriptores,
            Copias copias,
            SuscripcionService suscripcionService,
            CargaInicialSuscripciones cargaInicialSuscripciones,
            @Value("${pagos.suscripciones.habilitada:false}") boolean habilitada
    ) {
        this.suscripciones = suscripciones;
        this.planes = planes;
        this.suscriptores = suscriptores;
        this.copias = copias;
        this.suscripcionService = suscripcionService;
        this.cargaInicialSuscripciones = cargaInicialSuscripciones;
        this.habilitada = habilitada;
    }

    private boolean activo() {
        return habilitada && cargaInicialSuscripciones.listo();
    }

    /**
     * Alta de suscripción al registrarse un usuario con rol PSIQUIATRA o profesión cargada (mismo criterio
     * que AuthController del monolito). Asigna plan "clinico" a psiquiatras y "consultorio" a psicólogos.
     */
    @Bean
    ManejadorEvento<UsuarioRegistradoData> suscripcionAlta() {
        return ManejadorEvento.de("suscripcion-alta", Topicos.IDENTIDAD_USUARIO_REGISTRADO_V1, UsuarioRegistradoData.class, e -> {
            if (!activo()) {
                return;
            }
            Long usuarioId = e.data().usuarioId();
            if (suscripciones.porProfesionalId(usuarioId).isPresent()) {
                return;
            }

            Suscriptores.Suscriptor suscriptor = suscriptores.porId(usuarioId).orElse(null);
            if (suscriptor == null || suscriptor.profession() == null) {
                copias.refrescarSuscriptor(usuarioId);
                suscriptor = suscriptores.porId(usuarioId).orElse(null);
            }

            boolean esProfesional = "PSIQUIATRA".equals(e.data().rol())
                    || (suscriptor != null && ("PSIQUIATRA".equals(suscriptor.rol()) || suscriptor.profession() != null));

            if (esProfesional) {
                String profession = suscriptor != null ? suscriptor.profession() : null;
                String planCode = "psiquiatra".equalsIgnoreCase(profession) ? "clinico" : "consultorio";
                Planes.Plan plan = planes.porCodigo(planCode).orElse(null);

                if (plan != null) {
                    LocalDateTime now = LocalDateTime.now();
                    Suscripciones.Suscripcion sub = new Suscripciones.Suscripcion(
                            null, 0, usuarioId, plan.id(), "PENDING_VERIFICATION", 1,
                            "MANUAL_TRANSFER", null, plan.priceArs(), "monthly", null,
                            null, null, null, false, null, false, now, now
                    );
                    try {
                        suscripciones.insertarNuevo(sub);
                        log.info("Suscripción PENDING_VERIFICATION creada para profesional {} con plan {}", usuarioId, planCode);
                    } catch (Exception ex) {
                        log.warn("Suscripción para profesional {} ya existía o carrera de inserción: {}", usuarioId, ex.getMessage());
                    }
                }
            }
        });
    }

    /**
     * Resolución de matrícula por el administrador:
     * - VERIFICADO pasa a VERIFIED únicamente desde PENDING_VERIFICATION o REGISTERED.
     * - RECHAZADO pasa a REJECTED.
     *
     * <p>Manejo de orden invertido: si el evento de verificación llega antes que el de alta (por latencia
     * o partición de red), se crea de forma anticipada la fila en PENDING_VERIFICATION con suscriptor
     * refrescado, y luego se aplica la transición para no descartar la decisión del administrador.
     */
    @Bean
    ManejadorEvento<VerificacionResueltaData> suscripcionVerificacion() {
        return ManejadorEvento.de("suscripcion-verificacion", Topicos.IDENTIDAD_PROFESIONAL_VERIFICACION_RESUELTA_V1,
                VerificacionResueltaData.class, e -> {
            if (!activo()) {
                return;
            }
            Long usuarioId = e.data().usuarioId();
            String resultado = e.data().resultado();

            Optional<Suscripciones.Suscripcion> subOpt = suscripciones.porProfesionalId(usuarioId);
            if (subOpt.isEmpty()) {
                // Orden invertido: creamos la fila anticipadamente para no perder la verificación
                copias.refrescarSuscriptor(usuarioId);
                Suscriptores.Suscriptor suscriptor = suscriptores.porId(usuarioId).orElse(null);
                String profession = suscriptor != null ? suscriptor.profession() : null;
                String planCode = "psiquiatra".equalsIgnoreCase(profession) ? "clinico" : "consultorio";
                Planes.Plan plan = planes.porCodigo(planCode).orElse(null);
                if (plan != null) {
                    LocalDateTime now = LocalDateTime.now();
                    Suscripciones.Suscripcion sub = new Suscripciones.Suscripcion(
                            null, 0, usuarioId, plan.id(), "PENDING_VERIFICATION", 1,
                            "MANUAL_TRANSFER", null, plan.priceArs(), "monthly", null,
                            null, null, null, false, null, false, now, now
                    );
                    try {
                        Long id = suscripciones.insertarNuevo(sub);
                        subOpt = suscripciones.porId(id);
                    } catch (Exception ex) {
                        subOpt = suscripciones.porProfesionalId(usuarioId);
                    }
                }
            }

            if (subOpt.isPresent()) {
                Suscripciones.Suscripcion sub = subOpt.get();
                String oldStatus = sub.status();
                LocalDateTime now = LocalDateTime.now();

                if ("VERIFICADO".equalsIgnoreCase(resultado)) {
                    if ("PENDING_VERIFICATION".equals(oldStatus) || "REGISTERED".equals(oldStatus)) {
                        Suscripciones.Suscripcion actualizada = new Suscripciones.Suscripcion(
                                sub.id(), sub.version(), sub.professionalId(), sub.planId(), "VERIFIED",
                                sub.seats(), sub.billingSource(), sub.mpPreapprovalId(), sub.amountArs(),
                                sub.billingCycle(), sub.nextBillingDate(), sub.currentPeriodStart(),
                                sub.currentPeriodEnd(), sub.graceUntil(), sub.cancelAtPeriodEnd(),
                                sub.cancelledAt(), sub.renewalReminderSent(), sub.createdAt(), now
                        );
                        suscripciones.actualizar(actualizada);
                        suscripcionService.logEvent(sub.id(), "PROFESSIONAL_VERIFIED", oldStatus, "VERIFIED",
                                "ADMIN", "admin", "Matrícula verificada por admin.");
                        log.info("Suscripción {} de profesional {} pasada a VERIFIED tras aprobación admin", sub.id(), usuarioId);
                    }
                } else if ("RECHAZADO".equalsIgnoreCase(resultado)) {
                    Suscripciones.Suscripcion actualizada = new Suscripciones.Suscripcion(
                            sub.id(), sub.version(), sub.professionalId(), sub.planId(), "REJECTED",
                            sub.seats(), sub.billingSource(), sub.mpPreapprovalId(), sub.amountArs(),
                            sub.billingCycle(), sub.nextBillingDate(), sub.currentPeriodStart(),
                            sub.currentPeriodEnd(), sub.graceUntil(), sub.cancelAtPeriodEnd(),
                            sub.cancelledAt(), sub.renewalReminderSent(), sub.createdAt(), now
                    );
                    suscripciones.actualizar(actualizada);
                    suscripcionService.logEvent(sub.id(), "PROFESSIONAL_REJECTED", oldStatus, "REJECTED",
                            "ADMIN", "admin", "Verificación rechazada.");
                    log.info("Suscripción {} de profesional {} pasada a REJECTED tras rechazo admin", sub.id(), usuarioId);
                }
            }
        });
    }
}
