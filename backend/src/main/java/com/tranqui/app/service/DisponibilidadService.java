package com.tranqui.app.service;

import com.tranqui.app.model.Disponibilidad;
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
    public List<DisponibilidadDto> obtenerDisponibilidades(String email) {
        Usuario medico = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));

        List<Disponibilidad> dispList = disponibilidadRepository.findByMedicoId(medico.getId());
        
        // If empty, return a default availability (e.g., Mon-Fri 09:00-12:00, 14:00-17:00)
        if (dispList.isEmpty()) {
            return inicializarDisponibilidadesPorDefecto(medico);
        }

        return dispList.stream()
                .map(d -> DisponibilidadDto.builder()
                        .diaSemana(d.getDiaSemana())
                        .horaInicio(d.getHoraInicio().format(TIME_FORMATTER))
                        .horaFin(d.getHoraFin().format(TIME_FORMATTER))
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public List<DisponibilidadDto> guardarDisponibilidades(String email, List<DisponibilidadDto> dtos) {
        Usuario medico = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));

        // Delete existing availability
        List<Disponibilidad> existentes = disponibilidadRepository.findByMedicoId(medico.getId());
        disponibilidadRepository.deleteAll(existentes);

        // Save new availability
        List<Disponibilidad> nuevas = dtos.stream()
                .map(dto -> Disponibilidad.builder()
                        .medico(medico)
                        .diaSemana(dto.getDiaSemana())
                        .horaInicio(LocalTime.parse(dto.getHoraInicio()))
                        .horaFin(LocalTime.parse(dto.getHoraFin()))
                        .build())
                .collect(Collectors.toList());

        disponibilidadRepository.saveAll(nuevas);

        return dtos;
    }

    private List<DisponibilidadDto> inicializarDisponibilidadesPorDefecto(Usuario medico) {
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
                        .build())
                .collect(Collectors.toList());
        disponibilidadRepository.saveAll(nuevas);
        
        return defaults;
    }
}
