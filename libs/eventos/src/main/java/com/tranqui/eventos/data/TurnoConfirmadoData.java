package com.tranqui.eventos.data;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Datos del evento turnos.turno.confirmado.v1 (ejemplo exacto de la sección 5.3).
 * Consumido por notificaciones (mail al paciente), calendario (evento Meet) y chat (habilitación).
 *
 * <p>Campos opcionales agregados en la Fase 1 para los mails (sin cambiar de versión): si el turno
 * ocupa agenda o es un documento, la etiqueta del tipo de turno o de documento, la dirección del
 * consultorio (presencial), el link de Meet si ya existe y el origen de la confirmación (pago u
 * obra social), que cambia qué avisos salen.
 *
 * <p>Fase 3: {@code horaFin} (opcional), para que el calendario arme el evento de Google con su duración.
 */
public record TurnoConfirmadoData(
    Long turnoId,
    Long pacienteId,
    Long profesionalId,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDate fecha,
    String horaInicio,
    String modalidad,
    BigDecimal precio,
    Boolean ocupaAgenda,
    String tipoLabel,
    String tipoDocumentoLabel,
    String direccion,
    String meetUrl,
    String origen,
    String horaFin
) {
    /** El turno se confirmó por un pago aprobado. */
    public static final String ORIGEN_PAGO = "PAGO";
    /** El turno se confirmó por validación de la obra social (OSDE), sin pago. */
    public static final String ORIGEN_OSDE = "OSDE";

    public TurnoConfirmadoData(Long turnoId, Long pacienteId, Long profesionalId, LocalDate fecha,
                               String horaInicio, String modalidad, BigDecimal precio) {
        this(turnoId, pacienteId, profesionalId, fecha, horaInicio, modalidad, precio, null, null, null, null, null, null, null);
    }

    /** Sin {@code horaFin} (los productores anteriores a la Fase 3). */
    public TurnoConfirmadoData(Long turnoId, Long pacienteId, Long profesionalId, LocalDate fecha, String horaInicio,
                               String modalidad, BigDecimal precio, Boolean ocupaAgenda, String tipoLabel,
                               String tipoDocumentoLabel, String direccion, String meetUrl, String origen) {
        this(turnoId, pacienteId, profesionalId, fecha, horaInicio, modalidad, precio, ocupaAgenda, tipoLabel,
                tipoDocumentoLabel, direccion, meetUrl, origen, null);
    }

    /** Un turno sin {@code ocupaAgenda} explícito es un turno real (el valor por defecto antes de la Fase 1). */
    public boolean esDocumento() {
        return Boolean.FALSE.equals(ocupaAgenda);
    }
}
