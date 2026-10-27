package com.tranqui.app.eventos;

import com.tranqui.app.service.CopiaSuscripciones;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.consumo.ManejadorEvento;
import com.tranqui.eventos.data.SuscripcionCambioEstadoV2Data;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fase 8: la copia del monolito del estado de acceso a suscripciones.
 * Descarta versiones viejas; la resincronización periódica
 * ({@link com.tranqui.app.service.ResincronizacionSuscripciones}) corrige un evento perdido.
 *
 * <p>El consumo se prende solo con {@code EVENTOS_CONSUMO_SERVICIO=monolito} (los compose); sin esa
 * variable este handler no se registra en ningún consumidor.
 */
@Configuration
public class ManejadoresSuscripciones {

    @Bean
    ManejadorEvento<SuscripcionCambioEstadoV2Data> copiaSuscripcion(CopiaSuscripciones copia) {
        return ManejadorEvento.conVersion("copia-suscripcion-acceso", Topicos.SUSCRIPCIONES_SUSCRIPCION_CAMBIO_ESTADO_V2,
                SuscripcionCambioEstadoV2Data.class,
                e -> copia.aplicarEvento(e.data(), e.aggregateVersion()));
    }
}