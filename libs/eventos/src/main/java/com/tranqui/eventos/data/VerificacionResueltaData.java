package com.tranqui.eventos.data;

/**
 * Datos del evento identidad.profesional.verificacion-resuelta.v1.
 * Publicado cuando el administrador resuelve la verificación de matrícula de un profesional
 * (resultado: "VERIFICADO" o "RECHAZADO").
 */
public record VerificacionResueltaData(
    Long usuarioId,
    String resultado
) {}
