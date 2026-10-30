package com.tranqui.identidad.datos;

import java.time.LocalDateTime;

/**
 * Registro de un envío masivo de novedades (Fase 9, I2a).
 */
public record NovedadesEnvio(
        String id,
        String titulo,
        String estado,
        String detalle,
        LocalDateTime creadoEn,
        LocalDateTime finalizadoEn,
        boolean enviadoPacientes,
        boolean enviadoProfesionales
) {}
