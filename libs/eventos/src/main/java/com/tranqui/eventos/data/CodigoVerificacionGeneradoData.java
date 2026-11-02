package com.tranqui.eventos.data;

/**
 * Datos del evento identidad.codigo-verificacion.generado.v1.
 * Consumido por notificaciones para el envío del código OTP de verificación o recuperación.
 */
public record CodigoVerificacionGeneradoData(
    Long usuarioId,
    String email,
    String codigo,
    String tipo
) {}
