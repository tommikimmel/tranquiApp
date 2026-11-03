package com.tranqui.profesionales.perfil;

import com.tranqui.eventos.data.DisponibilidadProfesionalData.Franja;
import com.tranqui.profesionales.datos.Agendas;
import com.tranqui.profesionales.datos.Profesionales;
import com.tranqui.profesionales.datos.Profesionales.Profesional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/** La grilla de "Agenda", con las mismas reglas que {@code DisponibilidadService} del monolito. */
@Service
public class Disponibilidades {

    private final Profesionales profesionales;
    private final Agendas agendas;
    private final Perfiles perfiles;
    private final Publicador publicador;
    private final TransactionTemplate tx;

    public Disponibilidades(Profesionales profesionales, Agendas agendas, Perfiles perfiles, Publicador publicador,
                            TransactionTemplate tx) {
        this.profesionales = profesionales;
        this.agendas = agendas;
        this.perfiles = perfiles;
        this.publicador = publicador;
        this.tx = tx;
    }

    public List<MedicoDto.DisponibilidadDto> obtener(String email, String modalidad) {
        Profesional p = perfiles.buscar(email);
        List<Franja> propias = agendas.franjas(p.id(), modalidad);
        if (!propias.isEmpty()) {
            return dtos(propias);
        }
        // Sin nada para esta modalidad: la grilla por defecto solo la primera vez que no tiene ninguna, y
        // solo en su modalidad prioritaria (la otra arranca vacía para ofrecer "copiar de la otra agenda").
        if (!agendas.franjas(p.id()).isEmpty()) {
            return List.of();
        }
        String prioritaria = p.perfil().ofrecePresencial() ? "PRESENCIAL" : "ONLINE";
        if (!prioritaria.equals(modalidad)) {
            return List.of();
        }
        List<Franja> porDefecto = new ArrayList<>();
        for (int dia = 1; dia <= 5; dia++) {
            porDefecto.add(new Franja(dia, "09:00", "12:00", prioritaria));
            porDefecto.add(new Franja(dia, "14:00", "17:00", prioritaria));
        }
        guardarYAvisar(p.id(), prioritaria, porDefecto);
        return dtos(porDefecto);
    }

    public List<MedicoDto.DisponibilidadDto> guardar(String email, String modalidad, List<MedicoDto.DisponibilidadDto> dtos) {
        Profesional p = perfiles.buscar(email);
        List<Franja> franjas = dtos.stream()
                .map(d -> new Franja(d.diaSemana(), d.horaInicio(), d.horaFin(), modalidad))
                .toList();
        guardarYAvisar(p.id(), modalidad, franjas);
        return dtos;
    }

    private void guardarYAvisar(Long id, String modalidad, List<Franja> franjas) {
        tx.executeWithoutResult(estado -> {
            agendas.reemplazar(id, modalidad, franjas);
            publicador.disponibilidad(id, profesionales.subirVersionDisponibilidad(id), agendas.franjas(id));
        });
    }

    private static List<MedicoDto.DisponibilidadDto> dtos(List<Franja> franjas) {
        return franjas.stream().map(f -> new MedicoDto.DisponibilidadDto(f.diaSemana(), f.horaInicio(), f.horaFin())).toList();
    }
}
