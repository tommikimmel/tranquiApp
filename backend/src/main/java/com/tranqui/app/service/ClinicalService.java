package com.tranqui.app.service;

import com.tranqui.app.model.*;
import com.tranqui.app.model.dto.PacienteDto;
import com.tranqui.app.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ClinicalService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TurnoRepository turnoRepository;

    @Autowired
    private SeguimientoDiarioRepository seguimientoDiarioRepository;

    @Autowired
    private InformeClinicoRepository informeClinicoRepository;

    @Autowired
    private NotificacionService notificacionService;

    @Transactional(readOnly = true)
    public List<PacienteDto> obtenerPacientesAtendidos(String medicoEmail) {
        Usuario medico = usuarioRepository.findByEmail(medicoEmail)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));

        List<Usuario> pacientes = turnoRepository.findDistinctPacientesByMedicoId(medico.getId());

        // For priority checking, check if there are confirmed appointments in the next 3 days
        LocalDate hoy = LocalDate.now();
        LocalDate limite = hoy.plusDays(3);

        List<PacienteDto> dtos = new ArrayList<>();
        for (Usuario p : pacientes) {
            // Find all confirmed appointments for this patient and doctor
            List<Turno> turnos = turnoRepository.findByMedicoIdAndEstadoNot(medico.getId(), EstadoTurno.CANCELADO);
            
            // Filter turnos for this patient
            List<Turno> patientTurnos = turnos.stream()
                    .filter(t -> t.getPaciente().getId().equals(p.getId()))
                    .collect(Collectors.toList());

            // Determine if clinical priority is high
            boolean highPriority = patientTurnos.stream()
                    .anyMatch(t -> t.getEstado() == EstadoTurno.CONFIRMADO && 
                                   !t.getFecha().isBefore(hoy) && 
                                   !t.getFecha().isAfter(limite));

            // Find last visit date
            String ultimaVisita = patientTurnos.stream()
                    .filter(t -> t.getFecha().isBefore(hoy) || t.getFecha().isEqual(hoy))
                    .map(t -> t.getFecha().toString())
                    .max(String::compareTo)
                    .orElse("Ninguna");

            dtos.add(PacienteDto.builder()
                    .id(p.getId())
                    .nombre(p.getNombre())
                    .email(p.getEmail())
                    .telefono(p.getTelefono())
                    .dni(p.getDni())
                    .direccion(p.getDireccion())
                    .obraSocial(p.getObraSocial())
                    .numAfiliado(p.getNumAfiliado())
                    .ultimaVisita(ultimaVisita)
                    .prioridadClinica(highPriority ? "PRIORIDAD_ALTA" : "PRIORIDAD_BAJA")
                    .build());
        }

        return dtos;
    }

    @Transactional(readOnly = true)
    public List<SeguimientoDiario> obtenerSeguimientos(Long pacienteId, String medicoEmail) {
        Usuario medico = usuarioRepository.findByEmail(medicoEmail)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));
        return seguimientoDiarioRepository.findByPacienteIdAndMedicoIdOrderByFechaDesc(pacienteId, medico.getId());
    }

    @Transactional
    public SeguimientoDiario guardarSeguimiento(Long pacienteId, String medicoEmail, SeguimientoDiario entry) {
        Usuario medico = usuarioRepository.findByEmail(medicoEmail)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));
        Usuario paciente = usuarioRepository.findById(pacienteId)
                .orElseThrow(() -> new EntityNotFoundException("Paciente no encontrado"));

        entry.setMedico(medico);
        entry.setPaciente(paciente);
        if (entry.getFecha() == null) {
            entry.setFecha(LocalDate.now());
        }
        
        SeguimientoDiario saved = seguimientoDiarioRepository.save(entry);
        
        // Notify professional/patient (in this case, since the professional logged it or the patient logged it, we can notify the user)
        notificacionService.crearNotificacion(
                medico, 
                "Seguimiento Diario cargado", 
                "Se registró un nuevo seguimiento diario para " + paciente.getNombre(), 
                "SEGUIMIENTO"
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public List<InformeClinico> obtenerInformes(Long pacienteId, String medicoEmail) {
        Usuario medico = usuarioRepository.findByEmail(medicoEmail)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));
        return informeClinicoRepository.findByPacienteIdAndMedicoIdOrderByFechaDesc(pacienteId, medico.getId());
    }

    @Transactional
    public InformeClinico guardarInforme(String medicoEmail, Long pacienteId, String tipoInforme, String planTrabajo, String contenido, String nombreArchivo) {
        Usuario medico = usuarioRepository.findByEmail(medicoEmail)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));
        Usuario paciente = usuarioRepository.findById(pacienteId)
                .orElseThrow(() -> new EntityNotFoundException("Paciente no encontrado"));

        InformeClinico informe = InformeClinico.builder()
                .medico(medico)
                .paciente(paciente)
                .fecha(LocalDate.now())
                .tipoInforme(tipoInforme)
                .planTrabajo(planTrabajo)
                .contenido(contenido)
                .nombreArchivo(nombreArchivo)
                .build();

        InformeClinico saved = informeClinicoRepository.save(informe);

        notificacionService.crearNotificacion(
                medico,
                "Informe clínico creado",
                "Se creó un nuevo informe de tipo " + tipoInforme + " para " + paciente.getNombre(),
                "INFORME"
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public List<SeguimientoDiario> obtenerSeguimientosPaciente(String email) {
        Usuario paciente = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Paciente no encontrado"));
        return seguimientoDiarioRepository.findByPacienteIdOrderByFechaDesc(paciente.getId());
    }

    @Transactional
    public SeguimientoDiario guardarSeguimientoPaciente(String email, SeguimientoDiario entry) {
        Usuario paciente = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Paciente no encontrado"));

        if (entry.getMedico() == null) {
            Usuario medico = usuarioRepository.findByEmail("paula@tranqui.com")
                    .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));
            entry.setMedico(medico);
        }
        
        entry.setPaciente(paciente);
        if (entry.getFecha() == null) {
            entry.setFecha(LocalDate.now());
        }

        SeguimientoDiario saved = seguimientoDiarioRepository.save(entry);

        notificacionService.crearNotificacion(
                entry.getMedico(),
                "Seguimiento cargado por paciente",
                paciente.getNombre() + " registró su seguimiento clínico diario.",
                "SEGUIMIENTO"
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public List<InformeClinico> obtenerInformesPaciente(String email) {
        Usuario paciente = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Paciente no encontrado"));
        return informeClinicoRepository.findByPacienteIdOrderByFechaDesc(paciente.getId());
    }
}
