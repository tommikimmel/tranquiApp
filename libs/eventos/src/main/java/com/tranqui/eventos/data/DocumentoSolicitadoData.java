package com.tranqui.eventos.data;

/**
 * Datos del evento turnos.documento.solicitado.v1.
 * Consumido por notificaciones para dar aviso al profesional sobre documentos pedidos.
 */
public record DocumentoSolicitadoData(
    Long turnoId,
    Long pacienteId,
    Long profesionalId,
    String tipoDocumento,
    String descripcion
) {}
