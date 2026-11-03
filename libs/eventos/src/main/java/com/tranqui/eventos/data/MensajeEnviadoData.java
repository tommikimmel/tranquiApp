package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.OffsetDateTime;

/**
 * Datos del evento chat.mensaje.enviado.v1.
 * Consumido por notificaciones para alertar al destinatario sobre un nuevo mensaje.
 * Nota: Nunca incluye contenido clínico ni texto privado del mensaje.
 */
public record MensajeEnviadoData(
    @NotNull @Positive Long mensajeId,
    @Positive Long conversacionId,
    @Positive Long remitenteId,
    @Positive Long destinatarioId,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    OffsetDateTime enviadoEn
) {}
