package com.tranqui.eventos.data;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Datos del evento identidad.usuario.actualizado.v1.
 * Consumido por turnos, chat, notificaciones y pagos para actualizar copias locales.
 *
 * <p>Campos opcionales agregados en la Fase 1: el rol y si el usuario acepta mails
 * (Mi Cuenta, "Notificaciones por email"), que notificaciones respeta.
 */
public record UsuarioActualizadoData(
    @NotNull @Positive Long usuarioId,
    @Email String email,
    String nombre,
    String apellido,
    String telefono,
    @Pattern(regexp = Valores.ROLES) String rol,
    Boolean emailHabilitado
) {
    public UsuarioActualizadoData(Long usuarioId, String email, String nombre, String apellido, String telefono) {
        this(usuarioId, email, nombre, apellido, telefono, null, null);
    }
}
