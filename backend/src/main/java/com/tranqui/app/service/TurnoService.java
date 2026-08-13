package com.tranqui.app.service;

import com.tranqui.app.model.EstadoPago;
import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.model.Modalidad;
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
import java.util.Set;
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

    @Autowired
    private ReembolsoService reembolsoService;

    // Off by default. Only turn this on temporarily while the real Mercado Pago integration
    // is broken/unlinked and you need to exercise the rest of the booking flow (Google
    // Calendar sync, notifications, etc). Never leave it "true" once real patients are paying —
    // it lets a booking be confirmed without any real payment. See reservarTurno() below and
    // MercadoPagoWebhookValidator for the other half of this.
    @Value("${payment.simulation.enabled:false}")
    private boolean paymentSimulationEnabled;

    @Transactional(readOnly = true)
    public List<java.time.LocalTime> obtenerHorariosDisponibles(Long medicoId, java.time.LocalDate fecha, Modalidad modalidad) {
        List<com.tranqui.app.model.Disponibilidad> disponibilidades = disponibilidadRepository.findByMedicoIdAndModalidadOLegacy(medicoId, modalidad);
        // IMPORTANT: turnosExistentes is loaded WITHOUT any modalidad filter on purpose — a
        // médico can't be in two consultas at once, so a turno booked in PRESENCIAL must also
        // block that same horario when calculating ONLINE availability (and vice versa). Do not
        // "fix" this by filtering turnosExistentes by modalidad, that would break the mutual lock.
        List<Turno> turnosRaw = turnoRepository.findByMedicoIdAndFechaAndEstadoNot(medicoId, fecha, EstadoTurno.CANCELADO);
        java.time.LocalDateTime limiteCincoMin = java.time.LocalDateTime.now().minusMinutes(5);

        List<Turno> turnosExistentes = turnosRaw.stream()
                .filter(t -> {
                    if (!t.isOcupaAgenda()) return false;
                    if (t.getEstado() == EstadoTurno.EXPIRADO) return false;
                    if (t.getEstado() == EstadoTurno.PENDIENTE_PAGO) {
                        return t.getFechaCreacion() != null && t.getFechaCreacion().isAfter(limiteCincoMin);
                    }
                    return true;
                })
                .collect(Collectors.toList());

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
                    // Must match the médico's actual configured session length (same
                    // duracionTurnoMinutos used above to generate `locales` itself) — a
                    // hardcoded 45 here under-checks the overlap window for any médico who
                    // configured a different duration, letting a slot near the edge of a Google
                    // Calendar event through when it shouldn't be offered.
                    java.time.LocalTime bloqueFin = hora.plusMinutes(duracionTurnoMinutos);

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
    public java.util.Map<Long, Integer> obtenerConteosDisponibilidad(List<Long> medicoIds, java.time.LocalDate fecha, Modalidad modalidad) {
        java.util.Map<Long, Integer> conteos = new java.util.LinkedHashMap<>();
        for (Long medicoId : medicoIds) {
            try {
                if (modalidad != null) {
                    conteos.put(medicoId, obtenerHorariosDisponibles(medicoId, fecha, modalidad).size());
                    continue;
                }
                // No modalidad chosen yet (public homepage badge, before the patient picks
                // presencial/online) — count the union of whatever modalidades this médico offers.
                Usuario medico = usuarioRepository.findById(medicoId).orElse(null);
                if (medico == null) {
                    conteos.put(medicoId, 0);
                    continue;
                }
                java.util.Set<java.time.LocalTime> union = new java.util.LinkedHashSet<>();
                if (medico.isOfrecePresencial()) {
                    union.addAll(obtenerHorariosDisponibles(medicoId, fecha, Modalidad.PRESENCIAL));
                }
                if (medico.isOfreceOnline()) {
                    union.addAll(obtenerHorariosDisponibles(medicoId, fecha, Modalidad.ONLINE));
                }
                conteos.put(medicoId, union.size());
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

    private String resolverTypeLabel(Turno t) {
        if ("receta-fuera".equals(t.getServicioId())) return "Receta fuera de turno";
        if ("certificado".equals(t.getServicioId())) return "Certificado";
        if (t.getTipo() == TipoTurno.OBRA_SOCIAL || t.getTipo() == TipoTurno.OSDE) return "Obra Social";
        if (!t.isOcupaAgenda()) return "Documento solicitado";
        return "Consulta particular";
    }

    // Same detection PagoWebhookHandler#resolverTipoDocumentoLabel uses for the "Nuevo Documento
    // Pendiente" notification — kept as its own explicit boolean (TurnoMedicoDto.esReceta)
    // instead of making the frontend infer it from the `type` label, which a médico can rename.
    private boolean esReceta(Turno t) {
        return "receta-fuera".equals(t.getServicioId()) || t.getTipo() == TipoTurno.RECETA;
    }

    // A tarifa's precioOnline/precioPresencial only override precio when the médico explicitly
    // set one for this modalidad — most services keep a single precio regardless of modalidad,
    // so a null override just falls back to it.
    private java.math.BigDecimal resolverPrecioPorModalidad(com.tranqui.app.model.TarifaMedico tarifa, Modalidad modalidad) {
        if (modalidad == Modalidad.ONLINE && tarifa.getPrecioOnline() != null) {
            return tarifa.getPrecioOnline();
        }
        if (modalidad == Modalidad.PRESENCIAL && tarifa.getPrecioPresencial() != null) {
            return tarifa.getPrecioPresencial();
        }
        return tarifa.getPrecio();
    }

    @Transactional
    public com.tranqui.app.model.dto.TurnoResponseDto reservarTurno(com.tranqui.app.model.dto.ReservaTurnoDto dto) {
        // Normalize the same way AuthController.register/login do — this "find or create
        // patient" path (used when a médico books/registers a turno for a patient who doesn't
        // have an account yet) used to look up and store dto.getEmailPaciente() verbatim, so a
        // mixed-case or padded email here could create a second Usuario row for an address that
        // already had an account, silently bypassing the "email already registered" check.
        String emailPacienteClean = dto.getEmailPaciente() != null ? dto.getEmailPaciente().trim().toLowerCase() : null;

        // Check if patient already has an active or pending appointment in the future
        boolean tieneTurnoActivo = turnoRepository.existsActiveTurnoByPacienteEmail(emailPacienteClean, java.time.LocalDate.now());
        if (tieneTurnoActivo) {
            throw new IllegalStateException("Ya tenés un turno activo o pendiente de pago. No podés reservar más de un turno a la vez.");
        }

        Usuario medico = usuarioRepository.findById(dto.getMedicoId())
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));

        // Resolve which tarifa this booking is for up front — before touching modalidad/agenda
        // below — because whether it reserves a slot at all (TarifaMedico.requiereAgenda) depends
        // on which tarifa it is, and that decides which path the rest of this method takes.
        String servicioId = "particular";
        Set<String> servicioIdsConocidos = Set.of("particular", "sobreturno", "obra_social", "osde", "receta-fuera", "certificado");
        boolean esServicioCustom = dto.getServicioId() != null && !dto.getServicioId().isBlank()
                && !servicioIdsConocidos.contains(dto.getServicioId());

        Optional<com.tranqui.app.model.TarifaMedico> tarifaCustomOpt = esServicioCustom
                ? tarifaRepository.findByMedicoIdAndServicioId(medico.getId(), dto.getServicioId())
                : Optional.empty();
        boolean usoTarifaCustom = tarifaCustomOpt.isPresent() && tarifaCustomOpt.get().isHabilitado();

        if (usoTarifaCustom) {
            servicioId = dto.getServicioId();
        } else if (dto.getTipo() == TipoTurno.OBRA_SOCIAL || dto.getTipo() == TipoTurno.OSDE) {
            servicioId = "obra_social";
        } else if (dto.getTipo() == TipoTurno.RECETA) {
            servicioId = "receta-fuera";
        } else if (dto.getTipo() == TipoTurno.CERTIFICADO) {
            servicioId = "certificado";
        } else if (dto.getTipo() == TipoTurno.SOBRETUNO) {
            servicioId = "sobreturno";
        }

        Optional<com.tranqui.app.model.TarifaMedico> tarifaOpt = usoTarifaCustom
                ? Optional.empty()
                : tarifaRepository.findByMedicoIdAndServicioId(medico.getId(), servicioId);
        if (!usoTarifaCustom && !tarifaOpt.isPresent() && (dto.getTipo() == TipoTurno.OBRA_SOCIAL || dto.getTipo() == TipoTurno.OSDE)) {
            tarifaOpt = tarifaRepository.findByMedicoIdAndServicioId(medico.getId(), "osde");
        }

        // Whether this booking requires a número de afiliado — resolved from the actual tarifa
        // (its specific obra social, see TarifaMedico.obraSocial) rather than trusting the
        // frontend, so the requirement can't be bypassed by calling this endpoint directly.
        boolean requiereAfiliado;
        // Whether this service reserves a slot on the médico's agenda at all — false for pure
        // document services (recetas, certificados, informes). Unknown/legacy tarifas default to
        // true, matching the old behavior of always blocking a slot.
        boolean requiereAgendaServicio;
        if (usoTarifaCustom) {
            requiereAfiliado = tarifaCustomOpt.get().isRequiereObraSocial();
            requiereAgendaServicio = tarifaCustomOpt.get().isRequiereAgenda();
        } else {
            boolean esTipoObraSocial = dto.getTipo() == TipoTurno.OBRA_SOCIAL || dto.getTipo() == TipoTurno.OSDE;
            requiereAfiliado = esTipoObraSocial || (tarifaOpt.isPresent() && tarifaOpt.get().isRequiereObraSocial());
            requiereAgendaServicio = tarifaOpt.isPresent() ? tarifaOpt.get().isRequiereAgenda() : true;
        }

        Modalidad modalidad;
        java.time.LocalDate fechaFinal;
        java.time.LocalTime horaInicioFinal;
        java.time.LocalTime horaFinFinal;

        if (!requiereAgendaServicio) {
            // Document-only service: no consultorio, no videollamada, so there's no real
            // modalidad or scheduled time to resolve — the booking timestamp is only for record
            // keeping and must never block obtenerHorariosDisponibles for anyone else (enforced
            // there by filtering out turnos with ocupaAgenda == false).
            modalidad = null;
            fechaFinal = java.time.LocalDate.now();
            horaInicioFinal = java.time.LocalTime.now();
            horaFinFinal = horaInicioFinal;
        } else {
            // Resolve which agenda (presencial/online) this turno books against. Only ambiguous
            // when the médico offers both and the caller didn't say — reject rather than guess,
            // since guessing wrong would silently book against the wrong agenda's horarios.
            boolean ofreceAmbasModalidades = medico.isOfrecePresencial() && medico.isOfreceOnline();
            modalidad = dto.getModalidad();
            if (modalidad == null) {
                if (ofreceAmbasModalidades) {
                    throw new IllegalArgumentException("Debés indicar si el turno es presencial u online.");
                }
                modalidad = medico.isOfrecePresencial() ? Modalidad.PRESENCIAL : Modalidad.ONLINE;
            }

            // Check if slot is still available (only for standard/non-sobreturno appointments)
            if (dto.getTipo() != TipoTurno.SOBRETUNO) {
                List<java.time.LocalTime> disponibles = obtenerHorariosDisponibles(medico.getId(), dto.getFecha(), modalidad);
                if (!disponibles.contains(dto.getHora())) {
                    throw new IllegalStateException("El horario seleccionado ya no está disponible");
                }
            } else {
                // For SOBRETUNO, verify the doctor has no other active appointment at the requested hour
                List<Turno> turnosExistentes = turnoRepository.findByMedicoIdAndFechaAndEstadoNot(medico.getId(), dto.getFecha(), EstadoTurno.CANCELADO);
                boolean yaOcupado = turnosExistentes.stream()
                        .anyMatch(t -> t.isOcupaAgenda() && t.getHoraInicio().equals(dto.getHora()));
                if (yaOcupado) {
                    throw new IllegalStateException("El horario del sobreturno seleccionado ya se encuentra ocupado por otro turno");
                }
            }
            fechaFinal = dto.getFecha();
            horaInicioFinal = dto.getHora();
            horaFinFinal = dto.getHora().plusMinutes(45);
        }

        String formattedTelefono = dto.getTelefonoPaciente();
        if (formattedTelefono != null && !formattedTelefono.trim().isEmpty() && !formattedTelefono.trim().startsWith("+54")) {
            formattedTelefono = "+54 " + formattedTelefono.trim();
        }

        // Find or create patient
        String finalTel = formattedTelefono;
        Usuario paciente = usuarioRepository.findByEmail(emailPacienteClean)
                .orElseGet(() -> {
                    Usuario nuevo = Usuario.builder()
                            .nombre(dto.getNombrePaciente())
                            .email(emailPacienteClean)
                            .telefono(finalTel)
                            .rol(com.tranqui.app.model.Rol.PACIENTE)
                            .build();
                    return usuarioRepository.save(nuevo);
                });

        // Update patient's Obra Social details if provided
        if (dto.getObraSocial() != null && !dto.getObraSocial().isBlank()) {
            paciente.setObraSocial(dto.getObraSocial());
        }
        if (dto.getMetadataAfiliado() != null && !dto.getMetadataAfiliado().isBlank()) {
            paciente.setNumAfiliado(dto.getMetadataAfiliado());
        }
        paciente = usuarioRepository.save(paciente);

        // Determine price based on the tarifa resolved earlier (servicioId/tarifaOpt/
        // tarifaCustomOpt/usoTarifaCustom), now that modalidad is known.
        java.math.BigDecimal precio;
        if (usoTarifaCustom) {
            precio = resolverPrecioPorModalidad(tarifaCustomOpt.get(), modalidad);
        } else if (tarifaOpt.isPresent() && tarifaOpt.get().isHabilitado()) {
            precio = resolverPrecioPorModalidad(tarifaOpt.get(), modalidad);
        } else {
            // Fallback to defaults
            if (dto.getTipo() == TipoTurno.OBRA_SOCIAL || dto.getTipo() == TipoTurno.OSDE) {
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

        if (requiereAfiliado && (dto.getMetadataAfiliado() == null || dto.getMetadataAfiliado().isBlank())) {
            throw new IllegalStateException("Este servicio requiere número de afiliado.");
        }

        String metaAfiliado = dto.getMetadataAfiliado();
        if (dto.getObraSocial() != null && !dto.getObraSocial().isBlank()) {
            if (metaAfiliado != null && !metaAfiliado.isBlank() && !metaAfiliado.startsWith(dto.getObraSocial())) {
                metaAfiliado = dto.getObraSocial() + " - " + metaAfiliado;
            } else if (metaAfiliado == null || metaAfiliado.isBlank()) {
                metaAfiliado = dto.getObraSocial();
            }
        }

        Turno turno = Turno.builder()
                .medico(medico)
                .paciente(paciente)
                .fecha(fechaFinal)
                .horaInicio(horaInicioFinal)
                .horaFin(horaFinFinal)
                .tipo(dto.getTipo())
                .modalidad(modalidad)
                .estado(EstadoTurno.PENDIENTE_PAGO)
                .precio(precio)
                .metadataAfiliado(metaAfiliado)
                .servicioId(servicioId)
                .idFinanciador(dto.getIdFinanciador())
                .ocupaAgenda(requiereAgendaServicio)
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

        // Sincronizar agenda en Google Calendar solo para turnos online; los presenciales no llevan videollamada
        if (turno.getModalidad() == Modalidad.ONLINE) {
            String meetUrl = calendarService.crearEventoReunion(turno);
            turno.setTelemedicinaUrl(meetUrl);
        }

        return turnoRepository.save(turno);
    }

    @Transactional(readOnly = true)
    public List<com.tranqui.app.model.dto.TurnoMedicoDto> obtenerTurnosDeHoy(String medicoEmail) {
        Usuario medico = usuarioRepository.findByEmail(medicoEmail)
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));

        java.time.LocalDate hoy = java.time.LocalDate.now();
        List<Turno> turnosRaw = turnoRepository.findByMedicoIdAndFechaAndEstadoNot(medico.getId(), hoy, EstadoTurno.CANCELADO);
        List<Turno> turnos = turnosRaw.stream()
                .filter(t -> t.getEstado() == EstadoTurno.CONFIRMADO || t.getEstado() == EstadoTurno.PENDIENTE_VALIDACION)
                .collect(Collectors.toList());

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

                    String typeLabel = resolverTypeLabel(t);

                    return com.tranqui.app.model.dto.TurnoMedicoDto.builder()
                            .id(t.getId())
                            .patientName(t.getPaciente().getNombre())
                            .hour(String.format("%02d", t.getHoraInicio().getHour()))
                            .horaInicio(String.format("%02d:%02d", t.getHoraInicio().getHour(), t.getHoraInicio().getMinute()))
                            .ampm("hs")
                            .type(typeLabel)
                            .modalidad(t.getModalidad() != null ? t.getModalidad().toString() : null)
                            .status(status)
                            .attendanceStatus(t.getAsistencia() != null ? t.getAsistencia().name() : "ESPERANDO")
                            .meetLink(t.getTelemedicinaUrl() != null ? t.getTelemedicinaUrl() : "")
                            .fecha(t.getFecha().toString())
                            .firstConsultation(!emailsNoPrimeraConsulta.contains(t.getPaciente().getEmail()))
                            .patientInfo(construirPacienteDto(t.getPaciente()))
                            .metadataAfiliado(t.getMetadataAfiliado())
                            .ocupaAgenda(t.isOcupaAgenda())
                            .documentoEnviado(t.isDocumentoEnviado())
                            .esReceta(esReceta(t))
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

        List<Turno> turnosRaw = turnoRepository.findByMedicoIdAndEstadoNot(medico.getId(), EstadoTurno.CANCELADO);
        List<Turno> turnos = turnosRaw.stream()
                .filter(t -> t.getEstado() == EstadoTurno.CONFIRMADO || t.getEstado() == EstadoTurno.PENDIENTE_VALIDACION)
                .collect(Collectors.toList());

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

                    String typeLabel = resolverTypeLabel(t);

                    return com.tranqui.app.model.dto.TurnoMedicoDto.builder()
                            .id(t.getId())
                            .patientName(t.getPaciente().getNombre())
                            .hour(String.format("%02d", t.getHoraInicio().getHour()))
                            .horaInicio(String.format("%02d:%02d", t.getHoraInicio().getHour(), t.getHoraInicio().getMinute()))
                            .ampm("hs")
                            .type(typeLabel)
                            .modalidad(t.getModalidad() != null ? t.getModalidad().toString() : null)
                            .status(status)
                            .attendanceStatus(t.getAsistencia() != null ? t.getAsistencia().name() : "ESPERANDO")
                            .meetLink(t.getTelemedicinaUrl() != null ? t.getTelemedicinaUrl() : "")
                            .fecha(t.getFecha().toString())
                            .firstConsultation(!emailsNoPrimeraConsultaTodos.contains(t.getPaciente().getEmail()))
                            .patientInfo(construirPacienteDto(t.getPaciente()))
                            .metadataAfiliado(t.getMetadataAfiliado())
                            .ocupaAgenda(t.isOcupaAgenda())
                            .documentoEnviado(t.isDocumentoEnviado())
                            .esReceta(esReceta(t))
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

                    String typeLabel = resolverTypeLabel(t);

                    // The checkout URL is generated once at booking time (reservarTurno) and
                    // persisted on the turno — re-generating it here on every list read used to
                    // mean one synchronous external Mercado Pago call per pending turno.
                    String checkoutUrl = t.getCheckoutUrl() != null ? t.getCheckoutUrl() : "";

                    return com.tranqui.app.model.dto.TurnoMedicoDto.builder()
                            .id(t.getId())
                            .patientName(t.getMedico().getNombre()) // Show doctor name to patient
                            .hour(String.format("%02d", t.getHoraInicio().getHour()))
                            .horaInicio(String.format("%02d:%02d", t.getHoraInicio().getHour(), t.getHoraInicio().getMinute()))
                            .ampm("hs")
                            .type(typeLabel)
                            .modalidad(t.getModalidad() != null ? t.getModalidad().toString() : null)
                            .status(status)
                            .attendanceStatus(t.getAsistencia() != null ? t.getAsistencia().name() : "ESPERANDO")
                            .meetLink(t.getTelemedicinaUrl() != null ? t.getTelemedicinaUrl() : "")
                            .fecha(t.getFecha().toString())
                            .checkoutUrl(checkoutUrl)
                            .domicilioAtencion(t.getMedico().getDomicilioAtencion())
                            .domicilioLat(t.getMedico().getDomicilioLat())
                            .domicilioLng(t.getMedico().getDomicilioLng())
                            .domicilioAtencionTorre(t.getMedico().getDomicilioAtencionTorre())
                            .domicilioAtencionPiso(t.getMedico().getDomicilioAtencionPiso())
                            .domicilioAtencionDepto(t.getMedico().getDomicilioAtencionDepto())
                            .domicilioAtencionBarrio(t.getMedico().getDomicilioAtencionBarrio())
                            .ocupaAgenda(t.isOcupaAgenda())
                            .documentoEnviado(t.isDocumentoEnviado())
                            .esReceta(esReceta(t))
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
    public void cancelarTurno(Long turnoId, String requesterEmail) {
        Turno turno = turnoRepository.findById(turnoId)
                .orElseThrow(() -> new EntityNotFoundException("Turno no encontrado"));

        boolean teniaPagoAprobado = turno.getPago() != null && turno.getPago().getEstado() == EstadoPago.APROBADO;
        // Whoever is cancelling — used to decide whether the 48h refund-withholding policy
        // applies (see ReembolsoService#procesarReembolso): it shouldn't when the médico
        // themselves cancels, only when the patient bails last-minute.
        boolean profesionalCancela = requesterEmail != null
                && turno.getMedico().getEmail() != null
                && turno.getMedico().getEmail().equalsIgnoreCase(requesterEmail);

        turno.setEstado(EstadoTurno.CANCELADO);

        try {
            calendarService.eliminarEventoReunion(turno);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(TurnoService.class)
                .error("Error al eliminar evento en Google Calendar para turno ID: {}", turnoId, e);
        }

        // Reembolsar en Mercado Pago si el turno ya estaba pagado. Igual que con Google Calendar,
        // un fallo acá no debe impedir que la cancelación se confirme: se loguea y el médico puede
        // resolverlo manualmente desde Mercado Pago si hace falta.
        if (teniaPagoAprobado) {
            try {
                reembolsoService.procesarReembolso(turno, turno.getMedico(), profesionalCancela);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(TurnoService.class)
                    .error("Error al procesar el reembolso de Mercado Pago para turno ID: {}", turnoId, e);
            }
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

    // Only for document-only turnos (ocupaAgenda == false): the médico sends the actual
    // receta/certificado/informe through their own email or WhatsApp client (see
    // AppointmentCard's mailto:/wa.me links — there's no file storage/generation pipeline behind
    // these purchases to send server-side), then clicks this to record that it went out, which is
    // what flips the patient's "Mis Turnos" from "Documento pendiente" to "Documento enviado".
    @Transactional
    public void marcarDocumentoEnviado(Long turnoId, String medicoEmail) {
        Turno turno = turnoRepository.findById(turnoId)
                .orElseThrow(() -> new EntityNotFoundException("Turno no encontrado"));

        if (!turno.getMedico().getEmail().equalsIgnoreCase(medicoEmail)) {
            throw new IllegalStateException("No tenés permiso para modificar este turno.");
        }
        if (turno.isOcupaAgenda()) {
            throw new IllegalStateException("Este turno no corresponde a un documento.");
        }
        if (turno.getEstado() != EstadoTurno.CONFIRMADO) {
            throw new IllegalStateException("El documento todavía no fue pagado.");
        }

        turno.setDocumentoEnviado(true);
        turnoRepository.save(turno);
    }

    @Transactional
    public void reprogramarTurno(Long turnoId, String fechaStr, String horaStr) {
        Turno turno = turnoRepository.findById(turnoId)
                .orElseThrow(() -> new EntityNotFoundException("Turno no encontrado"));

        java.time.LocalDate nuevaFecha = com.tranqui.app.config.DateConfig.parseLocalDate(fechaStr);
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
            if (turno.getPaciente().isNotificacionesWhatsappHabilitadas()) {
                whatsappService.enviarMensajeWhatsApp(turno.getPaciente().getTelefono(), body);
            }
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
