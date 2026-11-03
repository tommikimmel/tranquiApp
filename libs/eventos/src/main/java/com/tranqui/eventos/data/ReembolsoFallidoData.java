package com.tranqui.eventos.data;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

/**
 * Datos del evento pagos.reembolso.fallido.v1 (Fase 4).
 * Correspondía reembolsar un turno cancelado pero Mercado Pago no lo aceptó. Consumido por
 * notificaciones, que le avisa al paciente que se revisa a mano (el mismo texto que el monolito
 * mandaba en el mail de cancelación cuando el reembolso fallaba).
 */
public record ReembolsoFallidoData(
    Long turnoId,
    Long pacienteId,
    Long profesionalId,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDate fecha,
    String horaInicio,
    String motivo
) {}
