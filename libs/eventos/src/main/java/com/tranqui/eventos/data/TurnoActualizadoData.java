package com.tranqui.eventos.data;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Datos del evento turnos.turno.actualizado.v1 (Fase 6): el turno completo, publicado por el servicio de
 * turnos en cada cambio. El monolito lo usa para su copia (estadísticas, historia clínica, recetas y el
 * pago). Trae también el estado de su pago (lo registra el servicio de pagos y turnos lo sigue), así la
 * copia del pago en el monolito sale del mismo evento y no depende del orden de llegada. No lleva los
 * tokens de los links de los mails (confirmar asistencia, abandonar el pago) ni datos de salud.
 *
 * <p>La fecha se escribe siempre en ISO: el monolito manda este record en una ruta interna y su Jackson
 * serializa LocalDate como dd/MM/yyyy.
 */
public record TurnoActualizadoData(
    Long turnoId,
    Long pacienteId,
    Long profesionalId,
    @JsonSerialize(using = LocalDateSerializer.class)
    LocalDate fecha,
    LocalTime horaInicio,
    LocalTime horaFin,
    String tipo,
    String modalidad,
    String estado,
    String asistencia,
    BigDecimal precio,
    String metadataAfiliado,
    String servicioId,
    boolean ocupaAgenda,
    String idFinanciador,
    String telemedicinaUrl,
    String googleEventId,
    String checkoutUrl,
    boolean documentoEnviado,
    boolean esReceta,
    LocalDateTime fechaCreacion,
    boolean confirmacionAsistenciaEmailEnviado,
    boolean asistenciaConfirmadaPaciente,
    String pagoTransactionId,
    String pagoEstado,
    BigDecimal pagoMonto,
    LocalDateTime pagoFecha
) {}
