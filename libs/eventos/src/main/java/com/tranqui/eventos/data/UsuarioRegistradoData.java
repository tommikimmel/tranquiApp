package com.tranqui.eventos.data;

/**
 * Datos del evento identidad.usuario.registrado.v1.
 * Consumido por notificaciones (bienvenida) y profesionales (creación de perfil).
 */
public record UsuarioRegistradoData(
    Long usuarioId,
    String email,
    String nombre,
    String apellido,
    String rol
) {}
