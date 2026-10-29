package com.tranqui.profesionales.perfil;

/** 404 con el mensaje, como {@code EntityNotFoundException} en el monolito. */
public class NoEncontradoException extends RuntimeException {

    public NoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
