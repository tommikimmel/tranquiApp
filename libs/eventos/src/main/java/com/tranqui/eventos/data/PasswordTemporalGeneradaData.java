package com.tranqui.eventos.data;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Datos del evento identidad.password-temporal.generada.v1.
 * Consumido por notificaciones para el envío de la contraseña temporal creada por un admin.
 */
public record PasswordTemporalGeneradaData(
    @NotNull @Positive Long usuarioId,
    @NotBlank @Email String email,
    String nombre,
    @NotBlank String passwordTemporal
) {}
