package com.tranqui.eventos.data;

/**
 * Datos del evento identidad.usuario.actualizado.v1.
 * Consumido por turnos, chat, notificaciones y pagos para actualizar copias locales.
 */
public record UsuarioActualizadoData(
    Long usuarioId,
    String email,
    String nombre,
    String apellido,
    String telefono
) {}
