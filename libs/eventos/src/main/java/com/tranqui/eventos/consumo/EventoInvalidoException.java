package com.tranqui.eventos.consumo;

/**
 * Evento que no se puede procesar nunca (mal formado, data que no respeta el contrato). No se
 * reintenta: va directo a la DLQ del servicio (sección 5.1).
 */
public class EventoInvalidoException extends RuntimeException {

    public EventoInvalidoException(String mensaje) {
        super(mensaje);
    }

    public EventoInvalidoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
