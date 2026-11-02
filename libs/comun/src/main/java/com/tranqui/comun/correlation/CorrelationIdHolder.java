package com.tranqui.comun.correlation;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Acceso utilitario al correlation ID actual almacenado en el MDC del hilo de ejecución.
 */
public final class CorrelationIdHolder {

    private CorrelationIdHolder() {}

    /**
     * Obtiene el correlation ID actual del MDC o null si no se encuentra definido.
     */
    public static String getCorrelationId() {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        return (correlationId != null && !correlationId.isBlank()) ? correlationId : null;
    }

    /**
     * Obtiene el correlation ID actual del MDC o genera un nuevo UUID si no existe.
     */
    public static String getOrGenerateCorrelationId() {
        String correlationId = getCorrelationId();
        return correlationId != null ? correlationId : UUID.randomUUID().toString();
    }
}
