package com.tranqui.eventos.data;

import java.util.List;

/**
 * Datos del evento profesionales.profesional.actualizado.v1.
 * Consumido por turnos, chat y pagos para actualizar copias locales.
 */
public record ProfesionalActualizadoData(
    Long profesionalId,
    String nombre,
    String apellido,
    String especialidad,
    String fotoUrl,
    List<String> modalidades,
    Integer duracionTurnoMinutos
) {}
