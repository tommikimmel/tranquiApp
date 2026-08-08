package com.tranqui.app.service;

import com.tranqui.app.model.Disponibilidad;
import com.tranqui.app.model.Modalidad;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.DisponibilidadDto;
import com.tranqui.app.repository.DisponibilidadRepository;
import com.tranqui.app.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DisponibilidadService {

    @Autowired
    private DisponibilidadRepository disponibilidadRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    @Transactional
    public List<DisponibilidadDto> obtenerDisponibilidades(String email, Modalidad modalidad) {
        Usuario medico = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));

        List<Disponibilidad> dispList = disponibilidadRepository.findByMedicoIdAndModalidadOLegacy(medico.getId(), modalidad);
        if (!dispList.isEmpty()) {
            return mapearADto(dispList);
        }

        // Nothing for this modalidad yet (not even a legacy row). Only seed the default
        // schedule the very first time the médico has NO availability at all, and only into
        // their prioritized modalidad — the other one starts empty so the UI can offer
        // "copiar de la otra agenda" instead of two default grids nobody asked for.
        boolean medicoTieneAlgunaDisponibilidad = !disponibilidadRepository.findByMedicoId(medico.getId()).isEmpty();
        if (medicoTieneAlgunaDisponibilidad) {
            return Collections.emptyList();
        }

        Modalidad prioritaria = medico.isOfrecePresencial() ? Modalidad.PRESENCIAL : Modalidad.ONLINE;
        if (modalidad != prioritaria) {
            return Collections.emptyList();
        }
        return inicializarDisponibilidadesPorDefecto(medico, prioritaria);
    }

    @Transactional
    public List<DisponibilidadDto> guardarDisponibilidades(String email, Modalidad modalidad, List<DisponibilidadDto> dtos) {
        Usuario medico = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));

        // Consumes both rows already tagged with this modalidad AND legacy (null) rows —
        // saving either grid for the first time "claims" the legacy schedule for itself.
        List<Disponibilidad> existentes = disponibilidadRepository.findByMedicoIdAndModalidadOLegacy(medico.getId(), modalidad);
        disponibilidadRepository.deleteAll(existentes);

        List<Disponibilidad> nuevas = dtos.stream()
                .map(dto -> Disponibilidad.builder()
                        .medico(medico)
                        .diaSemana(dto.getDiaSemana())
                        .horaInicio(LocalTime.parse(dto.getHoraInicio()))
                        .horaFin(LocalTime.parse(dto.getHoraFin()))
                        .modalidad(modalidad)
                        .build())
                .collect(Collectors.toList());

        disponibilidadRepository.saveAll(nuevas);

        return dtos;
    }

    private List<DisponibilidadDto> inicializarDisponibilidadesPorDefecto(Usuario medico, Modalidad modalidad) {
        List<DisponibilidadDto> defaults = new ArrayList<>();
        // Mon-Fri: 09:00-12:00 and 14:00-17:00
        for (int i = 1; i <= 5; i++) {
            defaults.add(new DisponibilidadDto(i, "09:00", "12:00"));
            defaults.add(new DisponibilidadDto(i, "14:00", "17:00"));
        }

        List<Disponibilidad> nuevas = defaults.stream()
                .map(dto -> Disponibilidad.builder()
                        .medico(medico)
                        .diaSemana(dto.getDiaSemana())
                        .horaInicio(LocalTime.parse(dto.getHoraInicio()))
                        .horaFin(LocalTime.parse(dto.getHoraFin()))
                        .modalidad(modalidad)
                        .build())
                .collect(Collectors.toList());
        disponibilidadRepository.saveAll(nuevas);

        return defaults;
    }

    private List<DisponibilidadDto> mapearADto(List<Disponibilidad> dispList) {
        return dispList.stream()
                .map(d -> DisponibilidadDto.builder()
                        .diaSemana(d.getDiaSemana())
                        .horaInicio(d.getHoraInicio().format(TIME_FORMATTER))
                        .horaFin(d.getHoraFin().format(TIME_FORMATTER))
                        .build())
                .collect(Collectors.toList());
    }
}
