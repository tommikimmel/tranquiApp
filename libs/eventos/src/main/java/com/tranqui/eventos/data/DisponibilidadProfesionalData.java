package com.tranqui.eventos.data;

import java.util.List;

/**
 * Datos del evento profesionales.disponibilidad.actualizada.v2 (Fase 5): la disponibilidad completa del
 * profesional, de las dos modalidades. El monolito (turnos) reemplaza su copia entera con esto.
 * {@code modalidad} null es una franja vieja, válida para las dos modalidades (como en el monolito).
 */
public record DisponibilidadProfesionalData(
    Long profesionalId,
    List<Franja> franjas
) {
    public record Franja(int diaSemana, String horaInicio, String horaFin, String modalidad) {}
}
