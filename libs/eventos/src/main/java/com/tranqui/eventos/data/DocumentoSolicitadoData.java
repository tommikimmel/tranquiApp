package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Datos del evento turnos.documento.solicitado.v1.
 * Consumido por notificaciones para dar aviso al profesional sobre documentos pedidos.
 */
public record DocumentoSolicitadoData(
    @NotNull @Positive Long turnoId,
    @Positive Long pacienteId,
    @Positive Long profesionalId,
    String tipoDocumento,
    String descripcion
) {}
