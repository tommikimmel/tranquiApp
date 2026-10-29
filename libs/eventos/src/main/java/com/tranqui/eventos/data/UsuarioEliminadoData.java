package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Datos del evento identidad.usuario.eliminado.v1.
 * Consumido por todos los servicios para anonimización (Ley 25.326) y cancelación de turnos.
 */
public record UsuarioEliminadoData(
    @NotNull @Positive Long usuarioId,
    String motivo
) {}
