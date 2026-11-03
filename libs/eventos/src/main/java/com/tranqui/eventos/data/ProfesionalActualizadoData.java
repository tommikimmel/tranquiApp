package com.tranqui.eventos.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.Valid;

import java.util.List;

/**
 * Datos del evento profesionales.profesional.actualizado.v1.
 * Consumido por turnos, chat y pagos para actualizar copias locales.
 *
 * <p>Fase 5: {@code intervaloEntreTurnosMinutos} y {@code perfil} son opcionales (los publica el servicio
 * de profesionales; el monolito los deja en null). Con {@code perfil} el monolito actualiza su copia.
 */
public record ProfesionalActualizadoData(
    @NotNull @Positive Long profesionalId,
    String nombre,
    String apellido,
    String especialidad,
    String fotoUrl,
    List<String> modalidades,
    Integer duracionTurnoMinutos,
    Integer intervaloEntreTurnosMinutos,
    @Valid PerfilProfesionalData perfil
) {
    public ProfesionalActualizadoData(Long profesionalId, String nombre, String apellido, String especialidad,
                                      String fotoUrl, List<String> modalidades, Integer duracionTurnoMinutos) {
        this(profesionalId, nombre, apellido, especialidad, fotoUrl, modalidades, duracionTurnoMinutos, null, null);
    }
}
