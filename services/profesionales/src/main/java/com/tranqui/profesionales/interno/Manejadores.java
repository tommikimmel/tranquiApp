package com.tranqui.profesionales.interno;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.consumo.ManejadorEvento;
import com.tranqui.eventos.data.ProfesionalVisibilidadCambiadaData;
import com.tranqui.eventos.data.UsuarioActualizadoData;
import com.tranqui.eventos.data.UsuarioEliminadoData;
import com.tranqui.eventos.data.UsuarioRegistradoData;
import com.tranqui.profesionales.datos.Profesionales;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Eventos del monolito que consume el servicio. No se copia el dato del evento: se refresca al profesional
 * desde el monolito, que tiene todo (el evento de identidad no trae el documento ni la foto). Si el monolito
 * no responde, no se reintenta el evento: lo corrige la sincronización del minuto siguiente.
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
}
