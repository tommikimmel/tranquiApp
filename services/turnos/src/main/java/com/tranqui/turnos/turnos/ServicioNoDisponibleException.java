package com.tranqui.turnos.turnos;

/** 503: el monolito (identidad) no respondió y no se guardó nada. */
public class ServicioNoDisponibleException extends RuntimeException {

    public ServicioNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
