package com.tranqui.eventos.data;

/**
 * Datos del evento profesionales.profesional.visibilidad-cambiada.v1.
 * Consumido por turnos para aceptar o rechazar nuevas reservas.
 */
public record ProfesionalVisibilidadCambiadaData(
    Long profesionalId,
    boolean visible,
    String motivo
) {}
