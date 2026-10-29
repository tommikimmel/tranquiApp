package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Datos del evento identidad.profesional.verificacion-resuelta.v1.
 * Publicado cuando el administrador resuelve la verificación de matrícula de un profesional
 * (resultado: "VERIFICADO" o "RECHAZADO").
 */
public record VerificacionResueltaData(
    @NotNull @Positive Long usuarioId,
    @NotBlank @Pattern(regexp = "VERIFICADO|RECHAZADO") String resultado
) {}
