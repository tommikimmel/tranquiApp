package com.tranqui.eventos.data;

/**
 * Datos del evento identidad.password-temporal.generada.v1.
 * Consumido por notificaciones para el envío de la contraseña temporal creada por un admin.
 */
public record PasswordTemporalGeneradaData(
    Long usuarioId,
    String email,
    String nombre,
    String passwordTemporal
) {}
