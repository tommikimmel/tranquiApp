package com.tranqui.eventos.data;

/**
 * Datos del evento identidad.usuario.eliminado.v1.
 * Consumido por todos los servicios para anonimización (Ley 25.326) y cancelación de turnos.
 */
public record UsuarioEliminadoData(
    Long usuarioId,
    String motivo
) {}
