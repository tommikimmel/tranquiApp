package com.tranqui.identidad.interno;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.consumo.ManejadorEvento;
import com.tranqui.eventos.data.ProfesionalActualizadoData;
import com.tranqui.identidad.datos.PerfilesRegistro;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fase 11: el panel admin (GET /api/admin/users) muestra el perfil profesional de perfil_registro, que solo se llenaba
 * al registrarse; en main veía el perfil actual. Con cada perfil que guarda profesionales queda al día.
 */
@Configuration
public class ManejadoresPerfil {

    @Bean
    ManejadorEvento<ProfesionalActualizadoData> perfilActualDelProfesional(PerfilesRegistro perfiles) {
        return ManejadorEvento.conVersion("perfil-profesional", Topicos.PROFESIONALES_PROFESIONAL_ACTUALIZADO_V1,
                ProfesionalActualizadoData.class, e -> {
                    if (e.data().perfil() != null) {
                        perfiles.guardar(e.data().profesionalId(), e.data().perfil());
                    }
                });
    }
}
