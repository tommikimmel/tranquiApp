package com.tranqui.app.service;

import com.tranqui.app.model.*;
import com.tranqui.app.model.dto.TicketDetalleDto;
import com.tranqui.app.model.dto.TicketMensajeDto;
import com.tranqui.app.model.dto.TicketResumenDto;
import com.tranqui.app.repository.TicketMensajeRepository;
import com.tranqui.app.repository.TicketRepository;
import com.tranqui.app.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

// Sistema de tickets de soporte — reemplaza el viejo "Quejas y Soporte" (ComplaintModal /
// SoporteController#enviarQueja), que solo mandaba un mail fijo sin persistir nada. Acá el
// paciente/profesional abre un ticket, lo puede seguir viendo, y un admin responde desde su
// panel — igual que un chat, pero de a un hilo por ticket en vez del patrón 1-a-1 por usuario
// que usa Mensaje/ChatController (ese modelo no encaja con "varios tickets por persona").
@Service
public class TicketService {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketMensajeRepository ticketMensajeRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private NotificacionService notificacionService;

    @Autowired
    private ResendEmailService resendEmailService;

    @Transactional
    public TicketDetalleDto crearTicket(String creadorEmail, String asunto, String mensaje) {
        if (asunto == null || asunto.trim().isEmpty()) {
            throw new IllegalArgumentException("El asunto no puede estar vacío.");
        }
        if (mensaje == null || mensaje.trim().isEmpty()) {
            throw new IllegalArgumentException("El mensaje no puede estar vacío.");
        }

        Usuario creador = usuarioRepository.findByEmail(creadorEmail)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

        Ticket ticket = Ticket.builder()
                .creador(creador)
                .asunto(asunto.trim())
                .estado(TicketEstado.PENDIENTE)
                .build();
        ticket = ticketRepository.save(ticket);

        TicketMensaje primerMensaje = TicketMensaje.builder()
                .ticket(ticket)
                .autor(creador)
                .contenido(mensaje.trim())
                .build();
        ticketMensajeRepository.save(primerMensaje);

        String nombreCreador = nombreCompleto(creador);
        for (Usuario admin : usuarioRepository.findByRol(Rol.ADMIN)) {
            notificacionService.crearNotificacion(admin, "Nuevo ticket de soporte",
                    nombreCreador + " abrió un ticket: " + ticket.getAsunto(), "TICKET_NUEVO");
        }

        return obtenerTicket(ticket.getId(), creadorEmail);
    }

    @Transactional(readOnly = true)
    public List<TicketResumenDto> listarMisTickets(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        return ticketRepository.findByCreadorIdOrderByFechaActualizacionDesc(usuario.getId()).stream()
                .map(this::toResumenDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TicketResumenDto> listarTodos() {
        return ticketRepository.findAllByOrderByFechaActualizacionDesc().stream()
                .map(this::toResumenDto)
                .collect(Collectors.toList());
    }

    // requesterEmail puede ser el creador del ticket o cualquier admin — cualquier otro usuario
    // no tiene por qué ver esta conversación.
    @Transactional(readOnly = true)
    public TicketDetalleDto obtenerTicket(Long ticketId, String requesterEmail) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new EntityNotFoundException("Ticket no encontrado"));
        Usuario requester = usuarioRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

        boolean esDueno = ticket.getCreador().getId().equals(requester.getId());
        boolean esAdmin = requester.getRol() == Rol.ADMIN;
        if (!esDueno && !esAdmin) {
            throw new AccessDeniedException("No tenés permiso para ver este ticket.");
        }

        List<TicketMensajeDto> mensajes = ticketMensajeRepository.findByTicketIdOrderByFechaEnvioAsc(ticketId).stream()
                .map(m -> TicketMensajeDto.builder()
                        .id(m.getId())
                        .autorId(m.getAutor().getId())
                        .autorNombre(nombreCompleto(m.getAutor()))
                        .deAdmin(m.getAutor().getRol() == Rol.ADMIN)
                        .contenido(m.getContenido())
                        .fechaEnvio(m.getFechaEnvio().toString())
                        .build())
                .collect(Collectors.toList());

        return TicketDetalleDto.builder()
                .id(ticket.getId())
                .asunto(ticket.getAsunto())
                .estado(ticket.getEstado().name())
                .fechaCreacion(ticket.getFechaCreacion().toString())
                .fechaActualizacion(ticket.getFechaActualizacion().toString())
                .creadorId(ticket.getCreador().getId())
                .creadorNombre(nombreCompleto(ticket.getCreador()))
                .creadorEmail(ticket.getCreador() != null ? ticket.getCreador().getEmail() : null)
                .creadorRol(ticket.getCreador().getRol().name())
                .mensajes(mensajes)
                .build();
    }

    @Transactional
    public TicketDetalleDto agregarMensaje(Long ticketId, String autorEmail, String contenido) {
        if (contenido == null || contenido.trim().isEmpty()) {
            throw new IllegalArgumentException("El mensaje no puede estar vacío.");
        }

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new EntityNotFoundException("Ticket no encontrado"));
        Usuario autor = usuarioRepository.findByEmail(autorEmail)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

        boolean esDueno = ticket.getCreador().getId().equals(autor.getId());
        boolean esAdmin = autor.getRol() == Rol.ADMIN;
        if (!esDueno && !esAdmin) {
            throw new AccessDeniedException("No tenés permiso para responder este ticket.");
        }

        TicketMensaje ticketMensaje = TicketMensaje.builder()
                .ticket(ticket)
                .autor(autor)
                .contenido(contenido.trim())
                .build();
        ticketMensajeRepository.save(ticketMensaje);

        if (esAdmin) {
            // Cualquier respuesta de soporte deja el ticket "en curso" — nunca lo cierra
            // automáticamente, eso solo lo hace el cambio de estado explícito del admin.
            ticket.setEstado(TicketEstado.ACTIVO);
            ticket.setFechaActualizacion(java.time.LocalDateTime.now());
            ticketRepository.save(ticket);

            notificacionService.crearNotificacion(ticket.getCreador(), "Respondieron tu ticket",
                    "Soporte respondió: \"" + ticket.getAsunto() + "\"", "TICKET_RESPUESTA");

            if (ticket.getCreador().getEmail() != null) {
                resendEmailService.enviarRespuestaTicket(
                        ticket.getCreador().getEmail(),
                        ticket.getCreador().getNombre(),
                        ticket.getAsunto(),
                        contenido.trim());
            }
        } else {
            // El creador escribió — si el ticket ya estaba resuelto, un mensaje nuevo significa
            // que necesita atención de nuevo.
            if (ticket.getEstado() == TicketEstado.RESUELTO) {
                ticket.setEstado(TicketEstado.PENDIENTE);
            }
            ticket.setFechaActualizacion(java.time.LocalDateTime.now());
            ticketRepository.save(ticket);

            String nombreCreador = nombreCompleto(autor);
            for (Usuario admin : usuarioRepository.findByRol(Rol.ADMIN)) {
                notificacionService.crearNotificacion(admin, "Nueva respuesta en un ticket",
                        nombreCreador + " respondió: \"" + ticket.getAsunto() + "\"", "TICKET_RESPUESTA");
            }
        }

        return obtenerTicket(ticketId, autorEmail);
    }

    @Transactional
    public void cambiarEstado(Long ticketId, String estadoStr) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new EntityNotFoundException("Ticket no encontrado"));
        TicketEstado nuevoEstado;
        try {
            nuevoEstado = TicketEstado.valueOf(estadoStr.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Estado inválido.");
        }
        ticket.setEstado(nuevoEstado);
        ticket.setFechaActualizacion(java.time.LocalDateTime.now());
        ticketRepository.save(ticket);
    }

    private TicketResumenDto toResumenDto(Ticket ticket) {
        String ultimoMensaje = ticketMensajeRepository.findByTicketIdOrderByFechaEnvioAsc(ticket.getId()).stream()
                .reduce((first, second) -> second)
                .map(TicketMensaje::getContenido)
                .orElse(null);

        return TicketResumenDto.builder()
                .id(ticket.getId())
                .asunto(ticket.getAsunto())
                .estado(ticket.getEstado().name())
                .fechaCreacion(ticket.getFechaCreacion().toString())
                .fechaActualizacion(ticket.getFechaActualizacion().toString())
                .creadorId(ticket.getCreador().getId())
                .creadorNombre(nombreCompleto(ticket.getCreador()))
                .creadorEmail(ticket.getCreador() != null ? ticket.getCreador().getEmail() : null)
                .creadorRol(ticket.getCreador().getRol().name())
                .ultimoMensaje(ultimoMensaje)
                .build();
    }

    private String nombreCompleto(Usuario u) {
        String nombre = (u.getNombre() != null ? u.getNombre() : "") +
                (u.getApellido() != null ? " " + u.getApellido() : "");
        return nombre.trim().isEmpty() ? u.getEmail() : nombre.trim();
    }
}
