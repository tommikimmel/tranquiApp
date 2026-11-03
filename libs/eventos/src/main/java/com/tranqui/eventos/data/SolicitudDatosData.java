package com.tranqui.eventos.data;

/**
 * Datos del evento identidad.soporte.solicitud-datos.v1.
 * Consumido por notificaciones para el aviso de solicitud de copia de datos personales (Ley 25.326).
 */
public record SolicitudDatosData(
    Long usuarioId,
    String nombreCompleto,
    String email,
    String rol
) {}
