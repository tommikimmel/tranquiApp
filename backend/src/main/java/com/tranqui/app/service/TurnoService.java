package com.tranqui.app.service;

import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.model.TipoTurno;
import com.tranqui.app.model.Turno;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.TurnoRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class TurnoService {

    @Autowired
    private TurnoRepository turnoRepository;

    @Autowired
    private GoogleCalendarService calendarService;

    @Autowired
    private AgendaService agendaService;

    @Autowired
    private com.tranqui.app.repository.DisponibilidadRepository disponibilidadRepository;

    @Autowired
    private com.tranqui.app.repository.UsuarioRepository usuarioRepository;

    @Autowired
    private com.tranqui.app.repository.TarifaMedicoRepository tarifaRepository;

    @Autowired
    private MercadoPagoService mercadoPagoService;

    @Autowired
    private WhatsAppService whatsappService;

    @Autowired
    private NotificacionService notificacionService;

    @Transactional(readOnly = true)
    public List<java.time.LocalTime> obtenerHorariosDisponibles(Long medicoId, java.time.LocalDate fecha) {
        List<com.tranqui.app.model.Disponibilidad> disponibilidades = disponibilidadRepository.findByMedicoId(medicoId);
        List<Turno> turnosExistentes = turnoRepository.findByMedicoIdAndFechaAndEstadoNot(medicoId, fecha, EstadoTurno.CANCELADO);
        return agendaService.calcularBloquesDisponibles(disponibilidades, turnosExistentes, fecha);
    }

    @Transactional(readOnly = true)
    public boolean esPrimeraConsulta(String email) {
        return !turnoRepository.existsByPacienteEmailAndEstadoNot(email, EstadoTurno.CANCELADO);
    }

    @Transactional
    public com.tranqui.app.model.dto.TurnoResponseDto reservarTurno(com.tranqui.app.model.dto.ReservaTurnoDto dto) {
        // Check if patient already has an active or pending appointment in the future
        boolean tieneTurnoActivo = turnoRepository.existsActiveTurnoByPacienteEmail(dto.getEmailPaciente(), java.time.LocalDate.now());
        if (tieneTurnoActivo) {
            throw new IllegalStateException("Ya tenés un turno activo o pendiente de pago. No podés reservar más de un turno a la vez.");
        }

        Usuario medico = usuarioRepository.findById(dto.getMedicoId())
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));

        // Check if slot is still available (only for standard/non-sobreturno appointments)
        if (dto.getTipo() != TipoTurno.SOBRETUNO) {
            List<java.time.LocalTime> disponibles = obtenerHorariosDisponibles(medico.getId(), dto.getFecha());
            if (!disponibles.contains(dto.getHora())) {
                throw new IllegalStateException("El horario seleccionado ya no está disponible");
            }
        } else {
            // For SOBRETUNO, verify the doctor has no other active appointment at the requested hour
            List<Turno> turnosExistentes = turnoRepository.findByMedicoIdAndFechaAndEstadoNot(medico.getId(), dto.getFecha(), EstadoTurno.CANCELADO);
            boolean yaOcupado = turnosExistentes.stream()
                    .anyMatch(t -> t.getHoraInicio().equals(dto.getHora()));
            if (yaOcupado) {
                throw new IllegalStateException("El horario del sobreturno seleccionado ya se encuentra ocupado por otro turno");
            }
        }

        // Find or create patient
        Usuario paciente = usuarioRepository.findByEmail(dto.getEmailPaciente())
                .orElseGet(() -> {
                    Usuario nuevo = Usuario.builder()
                            .nombre(dto.getNombrePaciente())
                            .email(dto.getEmailPaciente())
                            .telefono(dto.getTelefonoPaciente())
                            .rol(com.tranqui.app.model.Rol.PACIENTE)
                            .build();
                    return usuarioRepository.save(nuevo);
                });

        // Determine price based on selected service
        java.math.BigDecimal precio = java.math.BigDecimal.ZERO;
        String servicioId = "particular";
        if (dto.getTipo() == TipoTurno.OSDE) {
            servicioId = "osde";
        } else if (dto.getTipo() == TipoTurno.RECETA) {
            servicioId = "receta-fuera";
        } else if (dto.getTipo() == TipoTurno.CERTIFICADO) {
            servicioId = "certificado";
        } else if (dto.getTipo() == TipoTurno.SOBRETUNO) {
            servicioId = "sobreturno";
        }
        
        Optional<com.tranqui.app.model.TarifaMedico> tarifaOpt = tarifaRepository.findByMedicoIdAndServicioId(medico.getId(), servicioId);
        if (tarifaOpt.isPresent() && tarifaOpt.get().isHabilitado()) {
            precio = tarifaOpt.get().getPrecio();
        } else {
            // Fallback to defaults
            if (dto.getTipo() == TipoTurno.OSDE) {
                precio = new java.math.BigDecimal("10500");
            } else if (dto.getTipo() == TipoTurno.RECETA) {
                precio = new java.math.BigDecimal("45000");
            } else if (dto.getTipo() == TipoTurno.CERTIFICADO) {
                precio = new java.math.BigDecimal("55000");
            } else if (dto.getTipo() == TipoTurno.SOBRETUNO) {
                precio = new java.math.BigDecimal("90000");
            } else {
                precio = medico.getPrecio() != null ? medico.getPrecio() : new java.math.BigDecimal("60000");
            }
        }

        // If it is the first consultation, apply a 30% surcharge and round to nearest whole number
        if (dto.getTipo() == TipoTurno.PARTICULAR && esPrimeraConsulta(dto.getEmailPaciente())) {
            java.math.BigDecimal surcharge = precio.multiply(new java.math.BigDecimal("0.30"));
            precio = precio.add(surcharge).setScale(0, java.math.RoundingMode.HALF_UP);
        }

        Turno turno = Turno.builder()
                .medico(medico)
                .paciente(paciente)
                .fecha(dto.getFecha())
                .horaInicio(dto.getHora())
                .horaFin(dto.getHora().plusMinutes(45))
                .tipo(dto.getTipo())
                .estado(EstadoTurno.PENDIENTE_PAGO)
                .precio(precio)
                .metadataAfiliado(dto.getMetadataAfiliado())
                .build();

        turno = turnoRepository.save(turno);
        String checkoutUrl = "";
        try {
            checkoutUrl = mercadoPagoService.crearPreferenciaPago(turno, medico);
        } catch (Exception e) {
            throw new RuntimeException("Error al conectar con la pasarela de Mercado Pago: " + e.getMessage(), e);
        }

        return com.tranqui.app.model.dto.TurnoResponseDto.builder()
                .turnoId(turno.getId())
                .estado(turno.getEstado().name())
                .attendanceStatus(turno.getAsistencia() != null ? turno.getAsistencia().name() : null)
                .fecha(turno.getFecha())
                .horaInicio(turno.getHoraInicio())
                .precio(turno.getPrecio())
                .checkoutUrl(checkoutUrl)
                .build();
    }

    @Transactional
    public Turno confirmarTurnoOsde(Long turnoId, String numeroAfiliado) {
        Turno turno = turnoRepository.findById(turnoId)
                .orElseThrow(() -> new EntityNotFoundException("Turno no encontrado"));

        turno.setTipo(TipoTurno.OSDE);
        turno.setMetadataAfiliado(numeroAfiliado);
        
        // Muta a confirmado
        turno.setEstado(EstadoTurno.CONFIRMADO);
        
        // Sincronizar agenda en Google Calendar
        String meetUrl = calendarService.crearEventoReunion(turno);
        turno.setTelemedicinaUrl(meetUrl);

        return turnoRepository.save(turno);
    }

    @Transactional(readOnly = true)
    public List<com.tranqui.app.model.dto.TurnoMedicoDto> obtenerTurnosDeHoy(String medicoEmail) {
        Usuario medico = usuarioRepository.findByEmail(medicoEmail)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));

        java.time.LocalDate hoy = java.time.LocalDate.now();
        List<Turno> turnos = turnoRepository.findByMedicoIdAndFechaAndEstadoNot(medico.getId(), hoy, EstadoTurno.CANCELADO);

        // Sort by start time
        turnos.sort(java.util.Comparator.comparing(Turno::getHoraInicio));

        return turnos.stream()
                .map(t -> {
                    String status = "pending";
                    if (t.getEstado() == EstadoTurno.CONFIRMADO) {
                        status = "confirmed";
                    }
                    // If start time is past, could be completed
                    if (t.getEstado() == EstadoTurno.CONFIRMADO && t.getHoraFin().isBefore(java.time.LocalTime.now())) {
                        status = "completed";
                    }

                    String typeLabel = t.getTipo() == TipoTurno.OSDE ? "Copago OSDE" : "Consulta particular";

                    return com.tranqui.app.model.dto.TurnoMedicoDto.builder()
                            .id(t.getId())
                            .patientName(t.getPaciente().getNombre())
                            .hour(String.format("%02d", t.getHoraInicio().getHour()))
                            .ampm("hs")
                            .type(typeLabel)
                            .status(status)
                            .attendanceStatus(t.getAsistencia() != null ? t.getAsistencia().name() : "ESPERANDO")
                            .meetLink(t.getTelemedicinaUrl() != null ? t.getTelemedicinaUrl() : "")
                            .fecha(t.getFecha().toString())
                            .firstConsultation(esPrimeraConsulta(t.getPaciente().getEmail()))
                            .patientInfo(construirPacienteDto(t.getPaciente()))
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<com.tranqui.app.model.dto.TurnoMedicoDto> obtenerTodosTurnos(String medicoEmail) {
        Usuario medico = usuarioRepository.findByEmail(medicoEmail)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));

        List<Turno> turnos = turnoRepository.findByMedicoIdAndEstadoNot(medico.getId(), EstadoTurno.CANCELADO);

        turnos.sort(java.util.Comparator.comparing(Turno::getFecha).thenComparing(Turno::getHoraInicio));

        return turnos.stream()
                .map(t -> {
                    String status = "pending";
                    if (t.getEstado() == EstadoTurno.CONFIRMADO) {
                        status = "confirmed";
                    }
                    if (t.getEstado() == EstadoTurno.CONFIRMADO && t.getHoraFin().isBefore(java.time.LocalTime.now()) && t.getFecha().isEqual(java.time.LocalDate.now())) {
                        status = "completed";
                    } else if (t.getEstado() == EstadoTurno.CONFIRMADO && t.getFecha().isBefore(java.time.LocalDate.now())) {
                        status = "completed";
                    }

                    String typeLabel = t.getTipo() == TipoTurno.OSDE ? "Copago OSDE" : "Consulta particular";

                    return com.tranqui.app.model.dto.TurnoMedicoDto.builder()
                            .id(t.getId())
                            .patientName(t.getPaciente().getNombre())
                            .hour(String.format("%02d", t.getHoraInicio().getHour()))
                            .ampm("hs")
                            .type(typeLabel)
                            .status(status)
                            .attendanceStatus(t.getAsistencia() != null ? t.getAsistencia().name() : "ESPERANDO")
                            .meetLink(t.getTelemedicinaUrl() != null ? t.getTelemedicinaUrl() : "")
                            .fecha(t.getFecha().toString())
                            .firstConsultation(esPrimeraConsulta(t.getPaciente().getEmail()))
                            .patientInfo(construirPacienteDto(t.getPaciente()))
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<com.tranqui.app.model.dto.TurnoMedicoDto> obtenerTurnosPaciente(String pacienteEmail) {
        Usuario paciente = usuarioRepository.findByEmail(pacienteEmail)
                .orElseThrow(() -> new EntityNotFoundException("Paciente no encontrado"));

        List<Turno> turnos = turnoRepository.findByPacienteIdAndEstadoNot(paciente.getId(), EstadoTurno.CANCELADO);

        turnos.sort(java.util.Comparator.comparing(Turno::getFecha).thenComparing(Turno::getHoraInicio));

        return turnos.stream()
                .map(t -> {
                    String status = "pending";
                    if (t.getEstado() == EstadoTurno.CONFIRMADO) {
                        status = "confirmed";
                    }
                    if (t.getEstado() == EstadoTurno.CONFIRMADO && t.getHoraFin().isBefore(java.time.LocalTime.now()) && t.getFecha().isEqual(java.time.LocalDate.now())) {
                        status = "completed";
                    } else if (t.getEstado() == EstadoTurno.CONFIRMADO && t.getFecha().isBefore(java.time.LocalDate.now())) {
                        status = "completed";
                    }

                    String typeLabel = t.getTipo() == TipoTurno.OSDE ? "Copago OSDE" : "Consulta particular";

                    String checkoutUrl = "";
                    if ("pending".equals(status)) {
                        try {
                            checkoutUrl = mercadoPagoService.crearPreferenciaPago(t, t.getMedico());
                        } catch (Exception e) {
                            // keep empty
                        }
                    }

                    return com.tranqui.app.model.dto.TurnoMedicoDto.builder()
                            .id(t.getId())
                            .patientName(t.getMedico().getNombre()) // Show doctor name to patient
                            .hour(String.format("%02d", t.getHoraInicio().getHour()))
                            .ampm("hs")
                            .type(typeLabel)
                            .status(status)
                            .attendanceStatus(t.getAsistencia() != null ? t.getAsistencia().name() : "ESPERANDO")
                            .meetLink(t.getTelemedicinaUrl() != null ? t.getTelemedicinaUrl() : "")
                            .fecha(t.getFecha().toString())
                            .checkoutUrl(checkoutUrl)
                            .domicilioAtencion(t.getMedico().getDomicilioAtencion())
                            .domicilioLat(t.getMedico().getDomicilioLat())
                            .domicilioLng(t.getMedico().getDomicilioLng())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void cancelarTurno(Long turnoId) {
        Turno turno = turnoRepository.findById(turnoId)
                .orElseThrow(() -> new EntityNotFoundException("Turno no encontrado"));
        turno.setEstado(EstadoTurno.CANCELADO);
        turno = turnoRepository.save(turno);

        // Notify Doctor
        String tituloMed = "Turno cancelado";
        String mensajeMed = "El turno del " + turno.getFecha() + " a las " + 
                turno.getHoraInicio() + " hs con el paciente " + 
                turno.getPaciente().getNombre() + " ha sido cancelado.";
        notificacionService.crearNotificacion(turno.getMedico(), tituloMed, mensajeMed, "TURNO_CANCELADO");

        // Notify Patient
        String tituloPac = "Turno cancelado";
        String mensajePac = "El turno del " + turno.getFecha() + " a las " + 
                turno.getHoraInicio() + " hs con el profesional " + 
                turno.getMedico().getNombre() + " ha sido cancelado.";
        notificacionService.crearNotificacion(turno.getPaciente(), tituloPac, mensajePac, "TURNO_CANCELADO");
    }

    @Transactional
    public void actualizarAsistencia(Long turnoId, String asistenciaStr) {
        Turno turno = turnoRepository.findById(turnoId)
                .orElseThrow(() -> new EntityNotFoundException("Turno no encontrado"));
        try {
            com.tranqui.app.model.EstadoAsistencia estado = com.tranqui.app.model.EstadoAsistencia.valueOf(asistenciaStr.toUpperCase());
            turno.setAsistencia(estado);
            turnoRepository.save(turno);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado de asistencia inválido: " + asistenciaStr);
        }
    }

    @Transactional
    public void reprogramarTurno(Long turnoId, String fechaStr, String horaStr) {
        Turno turno = turnoRepository.findById(turnoId)
                .orElseThrow(() -> new EntityNotFoundException("Turno no encontrado"));

        java.time.LocalDate nuevaFecha = java.time.LocalDate.parse(fechaStr);
        java.time.LocalTime nuevaHoraInicio = java.time.LocalTime.parse(horaStr);
        java.time.LocalTime nuevaHoraFin = nuevaHoraInicio.plusMinutes(45);

        turno.setFecha(nuevaFecha);
        turno.setHoraInicio(nuevaHoraInicio);
        turno.setHoraFin(nuevaHoraFin);
        turnoRepository.save(turno);

        try {
            String linkInfo = (turno.getTelemedicinaUrl() != null && !turno.getTelemedicinaUrl().isEmpty()) 
                    ? " Enlace de videollamada: " + turno.getTelemedicinaUrl() 
                    : "";
            String body = String.format(
                "Hola %s, tu turno con el Dr. %s ha sido reprogramado para el día %s a las %s hs.%s",
                turno.getPaciente().getNombre(),
                turno.getMedico().getNombre(),
                fechaStr,
                horaStr,
                linkInfo
            );
            whatsappService.enviarMensajeWhatsApp(turno.getPaciente().getTelefono(), body);
        } catch (Exception e) {
            System.err.println("Fallo al enviar notificación de reprogramación: " + e.getMessage());
        }
    }

    private com.tranqui.app.model.dto.PacienteDto construirPacienteDto(Usuario p) {
        if (p == null) return null;
        com.tranqui.app.model.dto.PacienteDto.CredencialInfoDto cred = null;
        if (p.getCredencialCodEntidad() != null || p.getCredencialPan() != null) {
            cred = com.tranqui.app.model.dto.PacienteDto.CredencialInfoDto.builder()
                    .codEntidad(p.getCredencialCodEntidad())
                    .pan(p.getCredencialPan())
                    .plan(p.getCredencialPlan())
                    .token(p.getCredencialToken())
                    .build();
        }

        return com.tranqui.app.model.dto.PacienteDto.builder()
                .id(p.getId())
                .nombre(p.getNombre())
                .email(p.getEmail())
                .telefono(p.getTelefono())
                .dni(p.getDni())
                .direccion(p.getDireccion())
                .obraSocial(p.getObraSocial())
                .numAfiliado(p.getNumAfiliado())
                .apellido(p.getApellido())
                .sexo(p.getSexo())
                .fechaNacimiento(p.getFechaNacimiento() != null ? p.getFechaNacimiento().toString() : null)
                .cuil(p.getCuil())
                .mail(p.getEmail())
                .tipoDocumento(p.getTipoDocumento())
                .numeroDocumento(p.getNumeroDocumento())
                .datosOfuscado(p.getDatosOfuscado() != null ? p.getDatosOfuscado() : "N")
                .credencial(cred)
                .build();
    }
}
