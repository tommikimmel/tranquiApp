package com.tranqui.app.service;

import com.tranqui.app.model.Subscription;
import com.tranqui.app.model.SuscripcionAcceso;
import com.tranqui.app.repository.SubscriptionRepository;
import com.tranqui.app.repository.SuscripcionAccesoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Fase 8: punto de consulta unificado del acceso, recetas, estado y período de suscripción de un profesional.
 * Sin flag delega en {@link SubscriptionService} y {@link SubscriptionRepository}; con flag lee de la copia local
 * {@code suscripcion_acceso}.
 */
@Component
public class AccesoSuscripciones {

    private final boolean suscripcionesExterno;
    private final SubscriptionService subscriptionService;
    private final SubscriptionRepository subscriptionRepository;
    private final SuscripcionAccesoRepository suscripcionAccesoRepository;

    public AccesoSuscripciones(@Value("${suscripciones.externo:false}") boolean suscripcionesExterno,
                               SubscriptionService subscriptionService,
                               SubscriptionRepository subscriptionRepository,
                               SuscripcionAccesoRepository suscripcionAccesoRepository) {
        this.suscripcionesExterno = suscripcionesExterno;
        this.subscriptionService = subscriptionService;
        this.subscriptionRepository = subscriptionRepository;
        this.suscripcionAccesoRepository = suscripcionAccesoRepository;
    }

    public boolean acceso(Long profesionalId) {
        if (profesionalId == null) {
            return false;
        }
        if (suscripcionesExterno) {
            return suscripcionAccesoRepository.findById(profesionalId)
                    .map(this::evaluarAccesoCopia)
                    .orElse(false);
        }
        return subscriptionService.isAccessAllowed(profesionalId);
    }

    /**
     * Con el flag activo, evalúa el acceso sobre los datos de la copia local.
     * El acceso final combina el booleano guardado (que refleja condiciones como rol, verificado, etc.)
     * con la misma regla temporal de {@link SubscriptionService#isAccessAllowed(Long)}:
     * - Status válido: ACTIVE, o CANCELLED con período pago vigente.
     * - Período hasta no nulo y en el futuro (periodoHasta > now).
     * En SubscriptionService.isAccessAllowed la gracia (graceUntil) no otorga acceso, por lo que no se usa acá.
     */
    private boolean evaluarAccesoCopia(SuscripcionAcceso sa) {
        if (!sa.isAcceso()) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean periodoVigente = sa.getPeriodoHasta() != null && sa.getPeriodoHasta().isAfter(now);
        String estado = sa.getEstado();
        boolean statusValido = "ACTIVE".equalsIgnoreCase(estado)
                || ("CANCELLED".equalsIgnoreCase(estado) && periodoVigente);

        if (!statusValido) {
            return false;
        }
        if (sa.getPeriodoHasta() == null) {
            return false;
        }
        return periodoVigente;
    }

    public boolean puedeRecetar(Long profesionalId) {
        if (profesionalId == null) {
            return false;
        }
        if (suscripcionesExterno) {
            return suscripcionAccesoRepository.findById(profesionalId)
                    .map(SuscripcionAcceso::isPuedeRecetar)
                    .orElse(false);
        }
        return subscriptionService.hasFeature(profesionalId, "recetas_electronicas");
    }

    public String estado(Long profesionalId) {
        if (profesionalId == null) {
            return null;
        }
        if (suscripcionesExterno) {
            return suscripcionAccesoRepository.findById(profesionalId)
                    .map(SuscripcionAcceso::getEstado)
                    .orElse(null);
        }
        return subscriptionRepository.findByProfessionalId(profesionalId)
                .map(s -> s.getStatus() != null ? s.getStatus().name() : null)
                .orElse(null);
    }

    public LocalDateTime periodoHasta(Long profesionalId) {
        if (profesionalId == null) {
            return null;
        }
        if (suscripcionesExterno) {
            return suscripcionAccesoRepository.findById(profesionalId)
                    .map(SuscripcionAcceso::getPeriodoHasta)
                    .orElse(null);
        }
        return subscriptionRepository.findByProfessionalId(profesionalId)
                .map(Subscription::getCurrentPeriodEnd)
                .orElse(null);
    }
}