package com.tranqui.identidad.cuenta;

/**
 * Punto de extensión para el borrado del contacto de novedades en Resend al eliminar una cuenta.
 * La implementa {@code novedades.BorradorContactoNovedadesResend}.
 */
public interface BorradorContactoNovedades {
    void eliminarContacto(String email);
}
