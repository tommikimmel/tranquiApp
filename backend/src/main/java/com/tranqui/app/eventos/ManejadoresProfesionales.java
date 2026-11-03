package com.tranqui.app.eventos;

import com.tranqui.app.service.CopiaProfesionales;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.consumo.ManejadorEvento;
import com.tranqui.eventos.data.DisponibilidadProfesionalData;
import com.tranqui.eventos.data.ProfesionalActualizadoData;
import com.tranqui.eventos.data.TarifasProfesionalData;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fase 5: la copia del monolito del perfil, la disponibilidad y las tarifas que administra el servicio de
 * profesionales (turnos sigue leyendo de sus tablas). Descartan versiones viejas; la resincronización
 * periódica ({@link com.tranqui.app.service.ResincronizacionProfesionales}) corrige un evento perdido.
 *
 * <p>{@code profesional.actualizado.v1} también lo publica el monolito sin el flag, pero sin {@code perfil}:
 * esos eventos propios no se aplican. Se prenden con {@code EVENTOS_CONSUMO_SERVICIO=monolito}.
 */
@Configuration
public class ManejadoresProfesionales {

    @Bean
    ManejadorEvento<ProfesionalActualizadoData> copiaPerfilProfesional(CopiaProfesionales copia) {
        return ManejadorEvento.conVersion("copia-perfil-profesional", Topicos.PROFESIONALES_PROFESIONAL_ACTUALIZADO_V1,
                ProfesionalActualizadoData.class, e -> {
                    if (e.data().perfil() != null) {
                        copia.aplicarPerfil(e.data().profesionalId(), e.aggregateVersion(), e.data().perfil());
                    }
                });
    }

    @Bean
    ManejadorEvento<DisponibilidadProfesionalData> copiaDisponibilidad(CopiaProfesionales copia) {
        return ManejadorEvento.conVersion("copia-disponibilidad", Topicos.PROFESIONALES_DISPONIBILIDAD_ACTUALIZADA_V2,
                DisponibilidadProfesionalData.class, e -> copia.aplicarDisponibilidad(e.data()));
    }

    @Bean
    ManejadorEvento<TarifasProfesionalData> copiaTarifas(CopiaProfesionales copia) {
        return ManejadorEvento.conVersion("copia-tarifas", Topicos.PROFESIONALES_TARIFA_ACTUALIZADA_V2,
                TarifasProfesionalData.class, e -> copia.aplicarTarifas(e.data()));
    }
}
