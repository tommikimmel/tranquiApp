package com.tranqui.eventos.data;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Datos del evento identidad.codigo-verificacion.generado.v1.
 * Consumido por notificaciones para el envío del código OTP de verificación o recuperación.
 *
 * <p>{@code tipo}: {@code REGISTRO} y {@code EMAIL_VERIFICATION} (código de verificación) o
 * {@code PASSWORD_RESET} (código de recuperación). Campo opcional agregado
 * en la Fase 1: el nombre del usuario para el saludo del mail.
 */
public record CodigoVerificacionGeneradoData(
    @NotNull @Positive Long usuarioId,
    @NotBlank @Email String email,
    @NotBlank String codigo,
    String tipo,
    String nombre
) {
    public CodigoVerificacionGeneradoData(Long usuarioId, String email, String codigo, String tipo) {
        this(usuarioId, email, codigo, tipo, null);
    }
}
