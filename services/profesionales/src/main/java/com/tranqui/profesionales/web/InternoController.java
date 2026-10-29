package com.tranqui.profesionales.web;

import com.tranqui.eventos.data.DisponibilidadProfesionalData;
import com.tranqui.eventos.data.PerfilProfesionalData;
import com.tranqui.eventos.data.TarifasProfesionalData;
import com.tranqui.profesionales.datos.Agendas;
import com.tranqui.profesionales.datos.Profesionales;
import com.tranqui.profesionales.datos.Tarifas;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Lo que el servicio le responde al monolito (sección 4.2, solo con JWT de servicio): la resincronización
 * periódica de su copia ({@code ResincronizacionProfesionales}) y la vuelta atrás.
 */
@RestController
@RequestMapping("/internal/profesionales")
public class InternoController {

    /** {@code ResincronizacionProfesionales.ProfesionalServicio} del monolito. */
    public record ProfesionalServicio(Long profesionalId, int version, PerfilProfesionalData perfil,
                                      List<DisponibilidadProfesionalData.Franja> disponibilidad,
                                      List<TarifasProfesionalData.Tarifa> tarifas) {}

    private final Profesionales profesionales;
    private final Agendas agendas;
    private final Tarifas tarifas;

    public InternoController(Profesionales profesionales, Agendas agendas, Tarifas tarifas) {
        this.profesionales = profesionales;
        this.agendas = agendas;
        this.tarifas = tarifas;
    }

    /** Todos, o los que cambiaron (perfil, disponibilidad o tarifas) desde {@code desde}. Sin la foto. */
    @GetMapping("/profesionales")
    public List<ProfesionalServicio> profesionales(
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde) {
        return profesionales.cambiadosDesde(desde).stream()
                .map(p -> new ProfesionalServicio(p.id(), p.version(), p.perfil(), agendas.franjas(p.id()),
                        tarifas.deProfesional(p.id())))
                .toList();
    }
}
