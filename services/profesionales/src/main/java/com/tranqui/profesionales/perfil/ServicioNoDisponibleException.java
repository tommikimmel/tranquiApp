package com.tranqui.profesionales.perfil;

/** 503: el monolito no respondió y no se guardó nada. */
public class ServicioNoDisponibleException extends RuntimeException {

    public ServicioNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
