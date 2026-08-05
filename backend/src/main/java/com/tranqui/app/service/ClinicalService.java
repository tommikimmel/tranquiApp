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

    @Autowired
    private MensajeRepository mensajeRepository;

    @Transactional(readOnly = true)
    public List<PacienteDto> obtenerPacientesAtendidos(String medicoEmail) {
        Usuario medico = usuarioRepository.findByEmail(medicoEmail)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));

        List<Usuario> pacientesConTurno = turnoRepository.findDistinctPacientesByMedicoId(medico.getId());
        List<Usuario> pacientesConChat = mensajeRepository.findPacientesConMensajesConMedico(medico.getId());

        java.util.Map<Long, Usuario> pacientesMap = new java.util.LinkedHashMap<>();
        for (Usuario p : pacientesConTurno) {
            pacientesMap.put(p.getId(), p);
        }
        for (Usuario p : pacientesConChat) {
            pacientesMap.put(p.getId(), p);
        }

        java.util.List<Usuario> todosLosPacientes = new java.util.ArrayList<>(pacientesMap.values());

        // For priority checking, check if there are confirmed appointments in the next 3 days
        LocalDate hoy = LocalDate.now();
        LocalDate limite = hoy.plusDays(3);

        List<PacienteDto> dtos = new ArrayList<>();
        for (Usuario p : todosLosPacientes) {
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

            int unreadMessagesCount = mensajeRepository.countUnreadMessages(p.getId(), medico.getId());
            boolean sinTurno = patientTurnos.isEmpty();
            dtos.add(construirPacienteDto(p, sinTurno ? "Sin turnos registrados" : ultimaVisita, sinTurno, highPriority, unreadMessagesCount));
        }

        return dtos;
    }

    private PacienteDto construirPacienteDto(Usuario p, String ultimaVisita, boolean sinTurno, boolean highPriority, int unreadMessagesCount) {
        PacienteDto.CredencialInfoDto cred = null;
        if (p.getCredencialCodEntidad() != null || p.getCredencialPan() != null) {
            cred = PacienteDto.CredencialInfoDto.builder()
                    .codEntidad(p.getCredencialCodEntidad())
                    .pan(p.getCredencialPan())
                    .plan(p.getCredencialPlan())
                    .token(p.getCredencialToken())
                    .build();
        }

        return PacienteDto.builder()
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
                .sinTurno(sinTurno)
                .unreadMessagesCount(unreadMessagesCount)
                .apellido(p.getApellido())
                .sexo(p.getSexo())
                .fechaNacimiento(p.getFechaNacimiento() != null ? p.getFechaNacimiento().toString() : null)
                .cuil(p.getCuil())
                .mail(p.getEmail())
                .tipoDocumento(p.getTipoDocumento())
                .numeroDocumento(p.getNumeroDocumento())
                .datosOfuscado(p.getDatosOfuscado() != null ? p.getDatosOfuscado() : "N")
                .credencial(cred)
                .domicilio(PacienteDto.DomicilioDto.builder()
                        .calle(p.getDomicilioCalle())
                        .numero(p.getDomicilioNumero())
                        .piso(p.getDomicilioPiso())
                        .dpto(p.getDomicilioDpto())
                        .codigoPostal(p.getDomicilioCodigoPostal())
                        .localidad(p.getDomicilioLocalidad())
                        .provincia(p.getDomicilioProvincia())
                        .pais(p.getDomicilioPais())
                        .build())
                .build();
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

        notificacionService.crearNotificacion(
                paciente,
                "Nuevo Informe Clínico disponible",
                "El profesional " + medico.getNombre() + " ha emitido un informe de tipo " + tipoInforme + ".",
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

    @Transactional
    public PacienteDto actualizarPaciente(Long pacienteId, String medicoEmail, PacienteDto dto) {
        Usuario medico = usuarioRepository.findByEmail(medicoEmail)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));

        Usuario paciente = usuarioRepository.findById(pacienteId)
                .orElseThrow(() -> new EntityNotFoundException("Paciente no encontrado"));

        paciente.setNombre(dto.getNombre());
        paciente.setApellido(dto.getApellido());
        paciente.setNumAfiliado(dto.getNumAfiliado());
        paciente.setDni(dto.getDni());
        paciente.setObraSocial(dto.getObraSocial());
        paciente.setDireccion(dto.getDireccion());
        String patientTel = dto.getTelefono();
        if (patientTel != null && !patientTel.trim().isEmpty() && !patientTel.trim().startsWith("+54")) {
            patientTel = "+54 " + patientTel.trim();
        }
        paciente.setTelefono(patientTel);
        paciente.setSexo(dto.getSexo());
        if (dto.getFechaNacimiento() != null && !dto.getFechaNacimiento().trim().isEmpty()) {
            paciente.setFechaNacimiento(java.time.LocalDate.parse(dto.getFechaNacimiento().trim()));
        }
        paciente.setCuil(dto.getCuil());
        paciente.setTipoDocumento(dto.getTipoDocumento());
        paciente.setNumeroDocumento(dto.getNumeroDocumento());
        paciente.setDatosOfuscado(dto.getDatosOfuscado());

        if (dto.getDomicilio() != null) {
            paciente.setDomicilioCalle(dto.getDomicilio().getCalle());
            paciente.setDomicilioNumero(dto.getDomicilio().getNumero());
            paciente.setDomicilioPiso(dto.getDomicilio().getPiso());
            paciente.setDomicilioDpto(dto.getDomicilio().getDpto());
            paciente.setDomicilioCodigoPostal(dto.getDomicilio().getCodigoPostal());
            paciente.setDomicilioLocalidad(dto.getDomicilio().getLocalidad());
            paciente.setDomicilioProvincia(dto.getDomicilio().getProvincia());
            paciente.setDomicilioPais(dto.getDomicilio().getPais());
        }

        if (dto.getCredencial() != null) {
            paciente.setCredencialCodEntidad(dto.getCredencial().getCodEntidad());
            paciente.setCredencialPan(dto.getCredencial().getPan());
            paciente.setCredencialPlan(dto.getCredencial().getPlan());
            paciente.setCredencialToken(dto.getCredencial().getToken());
            if (dto.getCredencial().getPlan() != null && !dto.getCredencial().getPlan().trim().isEmpty()) {
                paciente.setObraSocial("OSDE " + dto.getCredencial().getPlan());
            } else {
                paciente.setObraSocial(dto.getObraSocial());
            }
        } else {
            paciente.setCredencialCodEntidad(null);
            paciente.setCredencialPan(null);
            paciente.setCredencialPlan(null);
            paciente.setCredencialToken(null);
            paciente.setObraSocial(null);
        }
        
        Usuario saved = usuarioRepository.save(paciente);
        int unreadMessagesCount = mensajeRepository.countUnreadMessages(saved.getId(), medico.getId());

        return construirPacienteDto(saved, "Hoy", false, false, unreadMessagesCount);
    }

    @Transactional
    public void eliminarInforme(Long id, String medicoEmail) {
        InformeClinico informe = informeClinicoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Informe no encontrado"));
        if (!informe.getMedico().getEmail().equals(medicoEmail)) {
            throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para eliminar este informe");
        }
        informeClinicoRepository.delete(informe);
    }

    @Transactional
    public InformeClinico editarInforme(Long id, String medicoEmail, String tipoInforme, String planTrabajo, String contenido) {
        InformeClinico informe = informeClinicoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Informe no encontrado"));
        if (!informe.getMedico().getEmail().equals(medicoEmail)) {
            throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para editar este informe");
        }
        informe.setTipoInforme(tipoInforme);
        informe.setPlanTrabajo(planTrabajo);
        informe.setContenido(contenido);
        return informeClinicoRepository.save(informe);
    }

    @Transactional
    public void eliminarSeguimiento(Long id, String medicoEmail) {
        SeguimientoDiario seguimiento = seguimientoDiarioRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Seguimiento no encontrado"));
        if (!seguimiento.getMedico().getEmail().equals(medicoEmail)) {
            throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para eliminar este seguimiento");
        }
        seguimientoDiarioRepository.delete(seguimiento);
    }
}
