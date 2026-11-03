package com.tranqui.turnos.turnos;

/** 403: el turno no es de quien lo pide. */
public class ProhibidoException extends RuntimeException {

    public ProhibidoException(String mensaje) {
        super(mensaje);
    }
}
