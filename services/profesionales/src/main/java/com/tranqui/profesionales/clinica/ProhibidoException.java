package com.tranqui.profesionales.clinica;

/** 403 Forbidden: la acción no está permitida para el usuario o profesional. */
public class ProhibidoException extends RuntimeException {

    public ProhibidoException(String mensaje) {
        super(mensaje);
    }
}
