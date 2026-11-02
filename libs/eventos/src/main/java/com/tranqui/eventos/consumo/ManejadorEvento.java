package com.tranqui.eventos.consumo;

import com.tranqui.eventos.EventEnvelope;

import java.util.Set;

/**
 * Un handler de eventos de un servicio (sección 5.1 del plan de microservicios).
 *
 * <p>Cada handler tiene un nombre propio de consumidor, que es la clave del inbox junto con el
 * {@code eventId}: si dos handlers reaccionan al mismo evento (por ejemplo, el mail y la
 * notificación en la app), cada uno se registra por separado y un reintento solo vuelve a correr
 * el que falló.
 *
 * @param <T> tipo del {@code data} del evento (un record de {@code com.tranqui.eventos.data})
 */
public interface ManejadorEvento<T> {

    /** Nombre del consumidor para el inbox, por ejemplo {@code mail-turno-confirmado}. Máximo 80 caracteres. */
    String consumidor();

    /** Tópicos completos que atiende, por ejemplo {@code turnos.turno.confirmado.v1}. */
    Set<String> topicos();

    /** Clase del {@code data} a la que se convierte el JSON del evento. */
    Class<T> tipoData();

    /**
     * Procesa el evento. Corre dentro de la transacción que registra el inbox: si tira una
     * excepción, el registro se deshace y el evento se reintenta.
     *
     * @throws EventoInvalidoException si el evento no se puede procesar nunca (va directo a la DLQ)
     */
    void manejar(EventEnvelope<T> evento);

    /**
     * Si es {@code true}, se descarta un evento cuyo {@code aggregateVersion} no supera al último
     * aplicado por este consumidor para la misma entidad (sección 6.4). Requiere la tabla
     * {@code versiones_aplicadas}. Por defecto no se descarta nada.
     */
    default boolean descartarVersionesViejas() {
        return false;
    }

    /** Handler de un solo tópico a partir de una lambda, para declararlo como {@code @Bean}. */
    static <T> ManejadorEvento<T> de(String consumidor, String topico, Class<T> tipoData,
                                     java.util.function.Consumer<EventEnvelope<T>> accion) {
        return new Simple<>(consumidor, Set.of(topico), tipoData, accion, false);
    }

    /** Como {@link #de}, pero descartando eventos con versión vieja (copias locales, sección 6.4). */
    static <T> ManejadorEvento<T> conVersion(String consumidor, String topico, Class<T> tipoData,
                                             java.util.function.Consumer<EventEnvelope<T>> accion) {
        return new Simple<>(consumidor, Set.of(topico), tipoData, accion, true);
    }

    record Simple<T>(String consumidor, Set<String> topicos, Class<T> tipoData,
                     java.util.function.Consumer<EventEnvelope<T>> accion, boolean porVersion)
            implements ManejadorEvento<T> {
        @Override
        public void manejar(EventEnvelope<T> evento) {
            accion.accept(evento);
        }

        @Override
        public boolean descartarVersionesViejas() {
            return porVersion;
        }
    }
}
