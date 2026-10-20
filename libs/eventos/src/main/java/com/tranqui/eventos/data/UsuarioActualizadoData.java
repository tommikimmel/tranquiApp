package com.tranqui.eventos.data;

/**
 * Datos del evento identidad.usuario.actualizado.v1.
 * Consumido por turnos, chat, notificaciones y pagos para actualizar copias locales.
 *
 * <p>Campos opcionales agregados en la Fase 1: el rol y si el usuario acepta mails
 * (Mi Cuenta, "Notificaciones por email"), que notificaciones respeta.
 */
public record UsuarioActualizadoData(
    Long usuarioId,
    String email,
    String nombre,
    String apellido,
    String telefono,
    String rol,
    Boolean emailHabilitado
) {
    public UsuarioActualizadoData(Long usuarioId, String email, String nombre, String apellido, String telefono) {
        this(usuarioId, email, nombre, apellido, telefono, null, null);
    }
}
