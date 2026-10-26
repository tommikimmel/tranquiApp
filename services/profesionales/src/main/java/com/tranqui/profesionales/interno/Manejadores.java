package com.tranqui.profesionales.interno;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.consumo.ManejadorEvento;
import com.tranqui.eventos.data.ProfesionalVisibilidadCambiadaData;
import com.tranqui.eventos.data.TurnoActualizadoData;
import com.tranqui.eventos.data.UsuarioActualizadoData;
import com.tranqui.eventos.data.UsuarioEliminadoData;
import com.tranqui.eventos.data.UsuarioRegistradoData;
import com.tranqui.profesionales.datos.Personas;
import com.tranqui.profesionales.datos.Profesionales;
import com.tranqui.profesionales.datos.TurnosAtendidos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Eventos del monolito y de otros servicios que consume profesionales:
 * - Identidad y verificación para los profesionales (se refrescan con la sincronización).
 * - Identidad para la copia de personas (ficha del paciente, lista y recetas). Si el monolito
 *   no responde, se tira la excepción para reintentar el evento.
 * - Turnos para la copia de turnos atendidos (con descarte de versiones viejas).
 */
@Configuration
public class Manejadores {

    private static final Logger log = LoggerFactory.getLogger(Manejadores.class);

    private final SincronizacionMonolito sincronizacion;
    private final Profesionales profesionales;

    public Manejadores(SincronizacionMonolito sincronizacion, Profesionales profesionales) {
        this.sincronizacion = sincronizacion;
        this.profesionales = profesionales;
    }

    // ------------------------------------------------------------------ profesionales

    @Bean
    ManejadorEvento<UsuarioRegistradoData> profesionalRegistrado() {
        return ManejadorEvento.de("profesional-registrado", Topicos.IDENTIDAD_USUARIO_REGISTRADO_V1, UsuarioRegistradoData.class,
                e -> {
                    if (SincronizacionMonolito.ROL_PROFESIONAL.equals(e.data().rol())) {
                        refrescar(e.data().usuarioId());
                    }
                });
    }

    @Bean
    ManejadorEvento<UsuarioActualizadoData> profesionalActualizado() {
        return ManejadorEvento.de("profesional-identidad", Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1, UsuarioActualizadoData.class,
                e -> {
                    // También si dejó de ser profesional (cambio de rol) o si ya estaba copiado.
                    if (SincronizacionMonolito.ROL_PROFESIONAL.equals(e.data().rol())
                            || profesionales.porId(e.data().usuarioId()).isPresent()) {
                        refrescar(e.data().usuarioId());
                    }
                });
    }

    @Bean
    ManejadorEvento<UsuarioEliminadoData> profesionalEliminado() {
        return ManejadorEvento.de("profesional-eliminado", Topicos.IDENTIDAD_USUARIO_ELIMINADO_V1, UsuarioEliminadoData.class,
                e -> profesionales.eliminar(e.data().usuarioId()));
    }

    /** La verificación del administrador (decisión 4: queda en el monolito). */
    @Bean
    ManejadorEvento<ProfesionalVisibilidadCambiadaData> verificacionCambiada() {
        return ManejadorEvento.de("profesional-verificacion", Topicos.PROFESIONALES_PROFESIONAL_VISIBILIDAD_CAMBIADA_V1,
                ProfesionalVisibilidadCambiadaData.class, e -> refrescar(e.data().profesionalId()));
    }

    private void refrescar(Long id) {
        try {
            sincronizacion.refrescar(id);
        } catch (RuntimeException ex) {
            log.warn("No se pudo refrescar al profesional {} desde el monolito; lo corrige la sincronización: {}", id, ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ personas (copia)

    @Bean
    ManejadorEvento<UsuarioRegistradoData> personaRegistrada(Copias copias) {
        return ManejadorEvento.de("persona-registrada", Topicos.IDENTIDAD_USUARIO_REGISTRADO_V1, UsuarioRegistradoData.class,
                e -> refrescarPersona(copias, e.data().usuarioId()));
    }

    @Bean
    ManejadorEvento<UsuarioActualizadoData> personaActualizada(Copias copias) {
        return ManejadorEvento.de("persona-actualizada", Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1, UsuarioActualizadoData.class,
                e -> refrescarPersona(copias, e.data().usuarioId()));
    }

    @Bean
    ManejadorEvento<UsuarioEliminadoData> personaEliminada(Personas personas) {
        return ManejadorEvento.de("persona-eliminada", Topicos.IDENTIDAD_USUARIO_ELIMINADO_V1, UsuarioEliminadoData.class,
                e -> personas.eliminar(e.data().usuarioId()));
    }

    private static void refrescarPersona(Copias copias, Long id) {
        try {
            copias.refrescarPersona(id);
        } catch (RuntimeException ex) {
            log.warn("No se pudo refrescar a la persona {} desde el monolito: {}", id, ex.getMessage());
            throw ex;
        }
    }

    // ------------------------------------------------------------------ turnos (copia)

    @Bean
    ManejadorEvento<TurnoActualizadoData> turnoActualizado(TurnosAtendidos turnosAtendidos) {
        return ManejadorEvento.conVersion("turno-actualizado", Topicos.TURNOS_TURNO_ACTUALIZADO_V1,
                TurnoActualizadoData.class, e -> {
                    TurnoActualizadoData d = e.data();
                    turnosAtendidos.guardar(d.turnoId(), d.profesionalId(), d.pacienteId(), d.fecha(), d.estado(), e.aggregateVersion());
                });
    }
}
