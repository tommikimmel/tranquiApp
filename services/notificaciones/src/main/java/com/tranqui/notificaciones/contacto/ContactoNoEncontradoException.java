package com.tranqui.notificaciones.contacto;

/**
 * El evento nombra a un usuario que todavía no está en la copia de contactos (por ejemplo, el
 * alta llegó después, o la carga inicial no terminó). No es un error definitivo: el consumidor
 * reintenta el evento (10 s, 1 min, 5 min) y recién después lo manda a la DLQ.
 */
public class ContactoNoEncontradoException extends RuntimeException {

    public ContactoNoEncontradoException(Long id) {
        super("No hay contacto para el usuario " + id + " (todavía)");
    }
}
