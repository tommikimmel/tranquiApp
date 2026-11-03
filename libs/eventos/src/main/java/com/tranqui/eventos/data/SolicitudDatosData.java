package com.tranqui.eventos.data;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Datos del evento identidad.soporte.solicitud-datos.v1.
 * Consumido por notificaciones para el aviso de solicitud de copia de datos personales (Ley 25.326).
 */
public record SolicitudDatosData(
    @NotNull @Positive Long usuarioId,
    String nombreCompleto,
    @NotBlank @Email String email,
    @Pattern(regexp = Valores.ROLES) String rol
) {}
