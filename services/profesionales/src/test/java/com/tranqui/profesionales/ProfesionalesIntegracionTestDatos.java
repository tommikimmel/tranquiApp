package com.tranqui.profesionales;

import com.tranqui.profesionales.datos.Profesionales.Profesional;

import java.time.LocalDateTime;

/** Profesionales armados a mano para los tests de reglas. */
public final class ProfesionalesIntegracionTestDatos {

    private ProfesionalesIntegracionTestDatos() {}

    public static Profesional profesional(String rol, String estado, LocalDateTime hasta) {
        return new Profesional(1L, "ana@mail.com", rol, "psiquiatra", "Ana", "Paz", null, null, null, null, null, null,
                null, null, true, false, estado, hasta, false, ProfesionalesIntegracionTest.perfil(true, false), 1, 0, 0, null);
    }
}
