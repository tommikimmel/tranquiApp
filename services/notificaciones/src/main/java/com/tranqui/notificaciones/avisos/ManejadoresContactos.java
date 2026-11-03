package com.tranqui.notificaciones.avisos;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.consumo.ManejadorEvento;
import com.tranqui.eventos.data.ProfesionalActualizadoData;
import com.tranqui.eventos.data.UsuarioActualizadoData;
import com.tranqui.eventos.data.UsuarioEliminadoData;
import com.tranqui.eventos.data.UsuarioRegistradoData;
import com.tranqui.notificaciones.contacto.ContactoService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Copia de contactos (sección 6.6), alimentada por los eventos de identidad y profesionales. Todos
 * comparten el consumidor "contactos" para descartar eventos viejos que lleguen tarde (sección 6.4).
 */
@Configuration
public class ManejadoresContactos {

    static final String CONSUMIDOR = "contactos";

    @Bean
    ManejadorEvento<UsuarioRegistradoData> contactoRegistrado(ContactoService contactos) {
        return ManejadorEvento.conVersion(CONSUMIDOR, Topicos.IDENTIDAD_USUARIO_REGISTRADO_V1, UsuarioRegistradoData.class,
                e -> contactos.guardar(e.data().usuarioId(), e.data().email(), e.data().nombre(), e.data().apellido(),
                        e.data().rol(), null));
    }

    @Bean
    ManejadorEvento<UsuarioActualizadoData> contactoActualizado(ContactoService contactos) {
        return ManejadorEvento.conVersion(CONSUMIDOR, Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1, UsuarioActualizadoData.class,
                e -> contactos.guardar(e.data().usuarioId(), e.data().email(), e.data().nombre(), e.data().apellido(),
                        e.data().rol(), e.data().emailHabilitado()));
    }

    @Bean
    ManejadorEvento<UsuarioEliminadoData> contactoEliminado(ContactoService contactos) {
        return ManejadorEvento.de(CONSUMIDOR, Topicos.IDENTIDAD_USUARIO_ELIMINADO_V1, UsuarioEliminadoData.class,
                e -> contactos.eliminar(e.data().usuarioId()));
    }

    @Bean
    ManejadorEvento<ProfesionalActualizadoData> contactoProfesionalActualizado(ContactoService contactos) {
        return ManejadorEvento.conVersion(CONSUMIDOR, Topicos.PROFESIONALES_PROFESIONAL_ACTUALIZADO_V1,
                ProfesionalActualizadoData.class,
                e -> contactos.actualizarNombre(e.data().profesionalId(), e.data().nombre(), e.data().apellido()));
    }
}
