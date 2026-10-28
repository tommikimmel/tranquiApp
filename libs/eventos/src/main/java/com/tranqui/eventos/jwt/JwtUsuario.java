package com.tranqui.eventos.jwt;

import java.util.Set;

/**
 * Constantes y definiciones del JWT de usuario de Tranqui App.
 */
public final class JwtUsuario {

    private JwtUsuario() {}

    public static final String COOKIE_SESION = "SESSION-TOKEN";
    public static final String EMISOR = "tranqui-identidad";

    public static final String CLAIM_ROL = "rol";
    public static final String CLAIM_NOMBRE = "nombre";
    public static final String CLAIM_UID = "uid";

    public static final String ROL_ADMIN = "ADMIN";
    public static final String ROL_PSIQUIATRA = "PSIQUIATRA";
    public static final String ROL_PACIENTE = "PACIENTE";
    public static final String ROL_VISITADOR = "VISITADOR";

    public static final Set<String> ROLES_VALIDOS = Set.of(
            ROL_ADMIN, ROL_PSIQUIATRA, ROL_PACIENTE, ROL_VISITADOR
    );

    public static boolean esRolValido(String rol) {
        return rol != null && ROLES_VALIDOS.contains(rol.trim().toUpperCase());
    }
}
