package com.tranqui.eventos.data;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.OffsetDateTime;

/**
 * Datos del evento chat.mensaje.enviado.v1.
 * Consumido por notificaciones para alertar al destinatario sobre un nuevo mensaje.
 * Nota: Nunca incluye contenido clínico ni texto privado del mensaje.
 */
public record MensajeEnviadoData(
    Long mensajeId,
    Long conversacionId,
    Long remitenteId,
    Long destinatarioId,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    OffsetDateTime enviadoEn
) {}
