package com.tranqui.app.service;

import com.tranqui.app.model.SuscripcionAcceso;
import com.tranqui.app.repository.SuscripcionAccesoRepository;
import com.tranqui.eventos.data.SuscripcionCambioEstadoV2Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDateTime;

/**
 * Fase 8: la copia del monolito del estado de acceso a suscripciones que administra el servicio de pagos.
 * SubscriptionAccessFilter, el portón del gateway y el servicio de profesionales leen de esta copia.
 * Solo hace algo cuando {@code suscripciones.externo=true}.
 */
@Service
public class CopiaSuscripciones {

    private static final Logger log = LoggerFactory.getLogger(CopiaSuscripciones.class);

    private final SuscripcionAccesoRepository repository;
    private final boolean suscripcionesExterno;

    public CopiaSuscripciones(SuscripcionAccesoRepository repository,
                              @Value("${suscripciones.externo:false}") boolean suscripcionesExterno) {
        this.repository = repository;
        this.suscripcionesExterno = suscripcionesExterno;
    }

    @Transactional
    public void aplicarEvento(SuscripcionCambioEstadoV2Data data, long aggregateVersion) {
        if (!suscripcionesExterno) {
            return;
        }
        if (data == null || data.profesionalId() == null) {
            return;
        }
        upsert(data.profesionalId(), data.estadoNuevo(), data.planCode(), data.acceso(),
                data.puedeRecetar(), data.periodoHasta(), data.graciaHasta(), aggregateVersion);
    }

    @Transactional
    public void upsert(Long professionalId, String estado, String planCode, boolean acceso,
                       boolean puedeRecetar, LocalDateTime periodoHasta, LocalDateTime graciaHasta, long version) {
        if (!suscripcionesExterno) {
            return;
        }
        if (professionalId == null) {
            return;
        }

        SuscripcionAcceso actual = repository.findById(professionalId).orElse(null);
        if (actual != null && actual.getVersion() != null && actual.getVersion() >= version) {
            log.debug("Ignorando actualización de suscripcion_acceso para profesional {}: versión actual {} >= nueva {}",
                    professionalId, actual.getVersion(), version);
            return;
        }

        SuscripcionAcceso entidad = actual != null ? actual : new SuscripcionAcceso();
        entidad.setProfessionalId(professionalId);
        entidad.setEstado(estado);
        entidad.setPlanCode(planCode);
        entidad.setAcceso(acceso);
        entidad.setPuedeRecetar(puedeRecetar);
        entidad.setPeriodoHasta(periodoHasta);
        entidad.setGraciaHasta(graciaHasta);
        entidad.setVersion(version);
        entidad.setActualizadoEn(LocalDateTime.now());

        repository.save(entidad);
        log.info("suscripcion_acceso actualizada para profesional {}: estado={}, acceso={}, version={}",
                professionalId, estado, acceso, version);
    }
}
