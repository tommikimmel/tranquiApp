package com.tranqui.eventos;

/**
 * Constantes de topología de RabbitMQ según la sección 5.1 del plan de microservicios.
 */
public final class RabbitMQConstantes {

    private RabbitMQConstantes() {}

    /**
     * Exchange principal para publicación de eventos de dominio (tipo topic, durable).
     */
    public static final String EXCHANGE_EVENTOS = "tranqui.eventos";

    /**
     * Exchange para reintentos con espera progresiva (tipo direct, durable).
     */
    public static final String EXCHANGE_REINTENTOS = "tranqui.reintentos";

    /**
     * Exchange para mensajes muertos / Dead Letter Exchange (tipo direct, durable).
     */
    public static final String EXCHANGE_DLX = "tranqui.dlx";

    /**
     * Sufijos de nombres de colas.
     */
    public static final String SUFIJO_COLA_EVENTOS = ".eventos";
    public static final String SUFIJO_COLA_REINTENTO_10S = ".reintento.10s";
    public static final String SUFIJO_COLA_REINTENTO_1M = ".reintento.1m";
    public static final String SUFIJO_COLA_REINTENTO_5M = ".reintento.5m";
    public static final String SUFIJO_COLA_DLQ = ".dlq";

    /**
     * Tiempos de espera (TTL en milisegundos) para reintentos progresivos.
     */
    public static final int TTL_REINTENTO_10S_MS = 10_000;
    public static final int TTL_REINTENTO_1M_MS = 60_000;
    public static final int TTL_REINTENTO_5M_MS = 300_000;

    public static String colaEventos(String servicio) {
        return servicio + SUFIJO_COLA_EVENTOS;
    }

    public static String colaReintento10s(String servicio) {
        return servicio + SUFIJO_COLA_REINTENTO_10S;
    }

    public static String colaReintento1m(String servicio) {
        return servicio + SUFIJO_COLA_REINTENTO_1M;
    }

    public static String colaReintento5m(String servicio) {
        return servicio + SUFIJO_COLA_REINTENTO_5M;
    }

    public static String colaDlq(String servicio) {
        return servicio + SUFIJO_COLA_DLQ;
    }
}
