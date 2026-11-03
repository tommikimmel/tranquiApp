package com.tranqui.identidad.web;

/** 403: el recurso no es de quien lo pide (por ejemplo, el ticket de otro usuario). */
public class ProhibidoException extends RuntimeException {

    public ProhibidoException(String mensaje) {
        super(mensaje);
    }
}
