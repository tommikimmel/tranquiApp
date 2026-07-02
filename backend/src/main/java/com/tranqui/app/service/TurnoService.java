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

    @Transactional(readOnly = true)
    public List<java.time.LocalTime> obtenerHorariosDisponibles(Long medicoId, java.time.LocalDate fecha) {
        List<com.tranqui.app.model.Disponibilidad> disponibilidades = disponibilidadRepository.findByMedicoId(medicoId);
        List<Turno> turnosExistentes = turnoRepository.findByMedicoIdAndFechaAndEstadoNot(medicoId, fecha, EstadoTurno.CANCELADO);
        return agendaService.calcularBloquesDisponibles(disponibilidades, turnosExistentes, fecha);
    }

    @Transactional
    public com.tranqui.app.model.dto.TurnoResponseDto reservarTurno(com.tranqui.app.model.dto.ReservaTurnoDto dto) {
        Usuario medico = usuarioRepository.findById(dto.getMedicoId())
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));

        // Check if slot is still available
        List<java.time.LocalTime> disponibles = obtenerHorariosDisponibles(medico.getId(), dto.getFecha());
        if (!disponibles.contains(dto.getHora())) {
            throw new IllegalStateException("El horario seleccionado ya no está disponible");
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
        String servicioId = dto.getTipo() == TipoTurno.OSDE ? "osde" : "particular";
        
        Optional<com.tranqui.app.model.TarifaMedico> tarifaOpt = tarifaRepository.findByMedicoIdAndServicioId(medico.getId(), servicioId);
        if (tarifaOpt.isPresent() && tarifaOpt.get().isHabilitado()) {
            precio = tarifaOpt.get().getPrecio();
        } else {
            // Fallback to doctor's base price or default
            precio = medico.getPrecio() != null ? medico.getPrecio() : new java.math.BigDecimal(dto.getTipo() == TipoTurno.OSDE ? "10500" : "60000");
        }

        Turno turno = Turno.builder()
                .medico(medico)
                .paciente(paciente)
                .fecha(dto.getFecha())
                .horaInicio(dto.getHora())
                .horaFin(dto.getHora().plusMinutes(45))
                .tipo(dto.getTipo())
                .estado(dto.getTipo() == TipoTurno.OSDE ? EstadoTurno.CONFIRMADO : EstadoTurno.PENDIENTE_PAGO)
                .precio(precio)
                .metadataAfiliado(dto.getMetadataAfiliado())
                .build();

        if (dto.getTipo() == TipoTurno.OSDE) {
            // Confirm immediately, generate Meet link
            String meetUrl = calendarService.crearEventoReunion(turno);
            turno.setTelemedicinaUrl(meetUrl);
            turno = turnoRepository.save(turno);

            return com.tranqui.app.model.dto.TurnoResponseDto.builder()
                    .turnoId(turno.getId())
                    .estado(turno.getEstado().name())
                    .fecha(turno.getFecha())
                    .horaInicio(turno.getHoraInicio())
                    .precio(turno.getPrecio())
                    .meetLink(turno.getTelemedicinaUrl())
                    .build();
        } else {
            // For PARTICULAR, generate MP checkout link
            turno = turnoRepository.save(turno);
            String checkoutUrl = "";
            try {
                checkoutUrl = mercadoPagoService.crearPreferenciaPago(turno, medico);
            } catch (Exception e) {
                // If MP fails, we keep the turn but checkoutUrl is empty (or we throw error)
                throw new RuntimeException("Error al conectar con la pasarela de Mercado Pago", e);
            }

            return com.tranqui.app.model.dto.TurnoResponseDto.builder()
                    .turnoId(turno.getId())
                    .estado(turno.getEstado().name())
                    .fecha(turno.getFecha())
                    .horaInicio(turno.getHoraInicio())
                    .precio(turno.getPrecio())
                    .checkoutUrl(checkoutUrl)
                    .build();
        }
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
                            .meetLink(t.getTelemedicinaUrl() != null ? t.getTelemedicinaUrl() : "")
                            .build();
                })
                .collect(Collectors.toList());
    }
}
