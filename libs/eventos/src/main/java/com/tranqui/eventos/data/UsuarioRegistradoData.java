package com.tranqui.eventos.data;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Datos del evento identidad.usuario.registrado.v1.
 * Consumido por notificaciones (bienvenida) y profesionales (creación de perfil).
 */
public record UsuarioRegistradoData(
    @NotNull @Positive Long usuarioId,
    @Email String email,
    String nombre,
    String apellido,
    @Pattern(regexp = Valores.ROLES) String rol
) {}
