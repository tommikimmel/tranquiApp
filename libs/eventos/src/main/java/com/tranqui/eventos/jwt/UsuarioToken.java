package com.tranqui.eventos.jwt;

/**
 * Token de usuario autenticado (sección 4.1 del plan de microservicios).
 * Contiene los claims del JWT de usuario: email (sub), rol, uid (id del usuario) y nombre.
 * El campo {@code uid} puede ser null en tokens emitidos por versiones anteriores (HS256).
 */
public record UsuarioToken(
    String email,
    String rol,
    Long uid,
    String nombre
) {}
