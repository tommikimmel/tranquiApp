package com.tranqui.app.service;

import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.model.TipoTurno;
import com.tranqui.app.model.Turno;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.TurnoRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    // Off by default. Only turn this on temporarily while the real Mercado Pago integration
    // is broken/unlinked and you need to exercise the rest of the booking flow (Google
    // Calendar sync, notifications, etc). Never leave it "true" once real patients are paying —
    // it lets a booking be confirmed without any real payment. See reservarTurno() below and
    // MercadoPagoWebhookValidator for the other half of this.
    @Value("${payment.simulation.enabled:false}")
    private boolean paymentSimulationEnabled;

    @Transactional(readOnly = true)
    public List<java.time.LocalTime> obtenerHorariosDisponibles(Long medicoId, java.time.LocalDate fecha) {
        List<com.tranqui.app.model.Disponibilidad> disponibilidades = disponibilidadRepository.findByMedicoId(medicoId);
        List<Turno> turnosExistentes = turnoRepository.findByMedicoIdAndFechaAndEstadoNot(medicoId, fecha, EstadoTurno.CANCELADO);

        Usuario medico = usuarioRepository.findById(medicoId).orElse(null);
        int duracionTurnoMinutos = (medico != null && medico.getDuracionTurnoMinutos() != null) ? medico.getDuracionTurnoMinutos() : 45;
        int intervaloEntreTurnosMinutos = (medico != null && medico.getIntervaloEntreTurnosMinutos() != null) ? medico.getIntervaloEntreTurnosMinutos() : 10;
        List<java.time.LocalTime> locales = agendaService.calcularBloquesDisponibles(
                disponibilidades, turnosExistentes, fecha, duracionTurnoMinutos, intervaloEntreTurnosMinutos);

        if (medico == null) {
            return locales;
        }

        // Consultar eventos de Google Calendar del día
        List<com.google.api.services.calendar.model.Event> eventosGoogle = java.util.Collections.emptyList();
        try {
            eventosGoogle = calendarService.obtenerEventosDelDia(medico, fecha);
        } catch (Exception e) {
            // Fallback silencioso: loguear y seguir con la disponibilidad local
            org.slf4j.LoggerFactory.getLogger(TurnoService.class)
                .warn("Error consultando disponibilidad en Google Calendar para médico ID: {}. Usando fallback local.", medicoId, e);
        }

        if (eventosGoogle.isEmpty()) {
            return locales;
        }

        java.time.ZoneId zoneId = java.time.ZoneId.of("America/Argentina/Buenos_Aires");
        List<com.google.api.services.calendar.model.Event> finalEventos = eventosGoogle;

        return locales.stream()
                .filter(hora -> {
                    java.time.LocalTime bloqueInicio = hora;
                    java.time.LocalTime bloqueFin = hora.plusMinutes(45);

                    for (com.google.api.services.calendar.model.Event event : finalEventos) {
                        // Eventos transparent (disponibles) no bloquean la agenda
                        if ("transparent".equals(event.getTransparency())) {
                            continue;
                        }

                        // Validar evento de todo el día (all-day event)
                        if (event.getStart() != null && event.getStart().getDateTime() == null && event.getStart().getDate() != null) {
                            // Evento de todo el día bloquea todo el día
                            return false;
                        }

                        if (event.getStart() != null && event.getStart().getDateTime() != null &&
                            event.getEnd() != null && event.getEnd().getDateTime() != null) {
                            
                            java.time.Instant startInstant = java.time.Instant.ofEpochMilli(event.getStart().getDateTime().getValue());
                            java.time.Instant endInstant = java.time.Instant.ofEpochMilli(event.getEnd().getDateTime().getValue());
                            
                            java.time.LocalTime eventStart = startInstant.atZone(zoneId).toLocalTime();
                            java.time.LocalTime eventEnd = endInstant.atZone(zoneId).toLocalTime();

                            // Solapamiento: bloqueInicio < eventEnd && bloqueFin > eventStart
                            if (bloqueInicio.isBefore(eventEnd) && bloqueFin.isAfter(eventStart)) {
                                return false; // Está ocupado por Google Calendar
                            }
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    // Batched counterpart of obtenerHorariosDisponibles for the public homepage, which used to
    // fire one HTTP request per visible professional every time a date filter changed. Reuses
    // the exact same per-médico logic (including the Google Calendar overlay) unchanged — this
    // only collapses the *transport* into a single request/response instead of N of them.
    @Transactional(readOnly = true)
    public java.util.Map<Long, Integer> obtenerConteosDisponibilidad(List<Long> medicoIds, java.time.LocalDate fecha) {
        java.util.Map<Long, Integer> conteos = new java.util.LinkedHashMap<>();
        for (Long medicoId : medicoIds) {
            try {
                conteos.put(medicoId, obtenerHorariosDisponibles(medicoId, fecha).size());
            } catch (Exception e) {
                conteos.put(medicoId, 0);
            }
        }
        return conteos;
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
            turno.setCheckoutUrl(checkoutUrl);
            turno = turnoRepository.save(turno);
        } catch (IllegalStateException e) {
            if (paymentSimulationEnabled) {
                // Real Mercado Pago checkout isn't reachable (professional not linked, or the
                // integration itself is broken) but simulation mode is explicitly on, so fall
                // back to the same mock preference URL the frontend already knows how to render
                // as a "Simular Pago" dialog instead of blocking the booking outright.
                checkoutUrl = "https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=mock-preference-id";
                turno.setCheckoutUrl(checkoutUrl);
                turno = turnoRepository.save(turno);
            } else {
                // Preserve the type so GlobalExceptionHandler maps it to a clean 409 with this
                // exact message, instead of it getting wrapped below into an opaque 500.
                throw e;
            }
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

        java.util.Set<String> emailsNoPrimeraConsulta = emailsConTurnoNoCancelado(turnos);

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
                            .firstConsultation(!emailsNoPrimeraConsulta.contains(t.getPaciente().getEmail()))
                            .patientInfo(construirPacienteDto(t.getPaciente()))
                            .build();
                })
                .collect(Collectors.toList());
    }

    // Replaces calling esPrimeraConsulta() once per turno (one exists-query per row) with a
    // single batched lookup of which of this list's patient emails already have a non-cancelled
    // turno — same semantics as esPrimeraConsulta, computed for many patients in one query.
    private java.util.Set<String> emailsConTurnoNoCancelado(List<Turno> turnos) {
        List<String> emails = turnos.stream()
                .map(t -> t.getPaciente().getEmail())
                .distinct()
                .collect(Collectors.toList());
        if (emails.isEmpty()) {
            return new java.util.HashSet<>();
        }
        return new java.util.HashSet<>(turnoRepository.findPacienteEmailsConTurnoNoCancelado(emails));
    }

    @Transactional(readOnly = true)
    public List<com.tranqui.app.model.dto.TurnoMedicoDto> obtenerTodosTurnos(String medicoEmail) {
        Usuario medico = usuarioRepository.findByEmail(medicoEmail)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));

        List<Turno> turnos = turnoRepository.findByMedicoIdAndEstadoNot(medico.getId(), EstadoTurno.CANCELADO);

        turnos.sort(java.util.Comparator.comparing(Turno::getFecha).thenComparing(Turno::getHoraInicio));

        java.util.Set<String> emailsNoPrimeraConsultaTodos = emailsConTurnoNoCancelado(turnos);

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
                            .firstConsultation(!emailsNoPrimeraConsultaTodos.contains(t.getPaciente().getEmail()))
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

                    // The checkout URL is generated once at booking time (reservarTurno) and
                    // persisted on the turno — re-generating it here on every list read used to
                    // mean one synchronous external Mercado Pago call per pending turno.
                    String checkoutUrl = t.getCheckoutUrl() != null ? t.getCheckoutUrl() : "";

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
    public void abandonarReservaPendiente(Long turnoId) {
        Turno turno = turnoRepository.findById(turnoId)
                .orElseThrow(() -> new EntityNotFoundException("Turno no encontrado"));

        // Only a still-unpaid reservation may be self-released through this unauthenticated
        // endpoint; anything already confirmed/cancelled is left untouched.
        if (turno.getEstado() != EstadoTurno.PENDIENTE_PAGO) {
            return;
        }

        turno.setEstado(EstadoTurno.CANCELADO);
        turnoRepository.save(turno);
    }

    @Transactional
    public void cancelarTurno(Long turnoId) {
        Turno turno = turnoRepository.findById(turnoId)
                .orElseThrow(() -> new EntityNotFoundException("Turno no encontrado"));
        turno.setEstado(EstadoTurno.CANCELADO);
        
        try {
            calendarService.eliminarEventoReunion(turno);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(TurnoService.class)
                .error("Error al eliminar evento en Google Calendar para turno ID: {}", turnoId, e);
        }

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
        turno = turnoRepository.save(turno);

        try {
            calendarService.actualizarEventoReunion(turno);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(TurnoService.class)
                .error("Error al actualizar evento en Google Calendar para turno ID: {}", turnoId, e);
        }

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
