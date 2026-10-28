package com.tranqui.identidad.cuenta;

/**
 * Punto de extensión para el borrado del contacto de novedades en Resend al eliminar una cuenta.
 * La implementación real la agrega I2c; por ahora existe una implementación vacía.
 */
public interface BorradorContactoNovedades {
    void eliminarContacto(String email);
}
