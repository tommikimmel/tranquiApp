package com.tranqui.profesionales.perfil;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.DisponibilidadProfesionalData;
import com.tranqui.eventos.data.PerfilProfesionalData;
import com.tranqui.eventos.data.ProfesionalActualizadoData;
import com.tranqui.eventos.data.TarifasProfesionalData;
import com.tranqui.eventos.outbox.OutboxPublisher;
import com.tranqui.profesionales.datos.Profesionales.Profesional;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Los eventos del servicio, por la outbox y dentro de la transacción de cada cambio. Los tipos de agregado
 * salen del tópico ({@code profesional}, {@code disponibilidad}, {@code tarifa}), los mismos que usaba el
 * monolito: los consumidores descartan versiones viejas por tipo e id.
 */
@Component
public class Publicador {

    private final OutboxPublisher outbox;

    public Publicador(OutboxPublisher outbox) {
        this.outbox = outbox;
    }

    /** {@code profesional.actualizado}: lo que ya consumían calendario, chat, notificaciones y pagos, más el perfil. */
    public void perfil(Profesional p, int version) {
        PerfilProfesionalData perfil = p.perfil();
        List<String> modalidades = new ArrayList<>();
        if (perfil.ofreceOnline()) {
            modalidades.add("ONLINE");
        }
        if (perfil.ofrecePresencial()) {
            modalidades.add("PRESENCIAL");
        }
        outbox.publicar(Topicos.PROFESIONALES_PROFESIONAL_ACTUALIZADO_V1, String.valueOf(p.id()), version,
                new ProfesionalActualizadoData(p.id(), p.nombre(), p.apellido(), perfil.especialidad(), p.fotoUrl(), modalidades,
                        perfil.duracionTurnoMinutos() != null ? perfil.duracionTurnoMinutos() : Perfiles.DURACION_TURNO_POR_DEFECTO,
                        perfil.intervaloEntreTurnosMinutos(), perfil));
    }

    public void disponibilidad(Long profesionalId, int version, List<DisponibilidadProfesionalData.Franja> franjas) {
        outbox.publicar(Topicos.PROFESIONALES_DISPONIBILIDAD_ACTUALIZADA_V2, String.valueOf(profesionalId), version,
                new DisponibilidadProfesionalData(profesionalId, franjas));
    }

    public void tarifas(Long profesionalId, int version, List<TarifasProfesionalData.Tarifa> tarifas) {
        outbox.publicar(Topicos.PROFESIONALES_TARIFA_ACTUALIZADA_V2, String.valueOf(profesionalId), version,
                new TarifasProfesionalData(profesionalId, tarifas));
    }
}
