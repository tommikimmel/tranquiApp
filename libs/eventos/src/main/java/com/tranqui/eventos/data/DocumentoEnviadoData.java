package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Datos del evento turnos.documento.enviado.v1 (agregado en la Fase 1).
 * Un documento pedido (certificado, informe o receta fuera de turno) ya le llegó al paciente.
 * Consumido por notificaciones (aviso en la app). No lleva el documento, solo su tipo.
 *
 * <p>{@code origen}: {@code ADJUNTO} (el profesional lo mandó por mail) o {@code RECETA} (se generó
 * desde Recetas Electrónicas).
 */
public record DocumentoEnviadoData(
    @NotNull @Positive Long turnoId,
    @Positive Long pacienteId,
    @Positive Long profesionalId,
    String tipoLabel,
    String origen
) {}
