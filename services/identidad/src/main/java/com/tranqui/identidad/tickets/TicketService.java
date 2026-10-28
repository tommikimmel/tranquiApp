package com.tranqui.identidad.tickets;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.TicketCreadoData;
import com.tranqui.eventos.data.TicketMensajeRecibidoData;
import com.tranqui.eventos.data.TicketRespondidoData;
import com.tranqui.eventos.outbox.OutboxPublisher;
import com.tranqui.identidad.datos.*;
import com.tranqui.identidad.web.NoEncontradoException;
import com.tranqui.identidad.web.dto.TicketDetalleDto;
import com.tranqui.identidad.web.dto.TicketMensajeDto;
import com.tranqui.identidad.web.dto.TicketResumenDto;
import com.tranqui.identidad.web.ProhibidoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sistema de tickets de soporte en el servicio de identidad (Fase 9, I2c).
 * Administra tickets y mensajes persistidos en JDBC con control de versión optimista.
 * Publica eventos por outbox con aggregate 'ticket':
 * - identidad.ticket.creado.v1
 * - identidad.ticket.respondido.v1
 * - identidad.ticket.mensaje-recibido.v1
 * Las notificaciones y mails los realiza el servicio de notificaciones a partir de estos eventos.
 */
@Service
public class TicketService {

    private static final Set<String> ESTADOS_VALIDOS = Set.of("PENDIENTE", "ACTIVO", "RESUELTO");

    private final Tickets tickets;
    private final TicketMensajes ticketMensajes;
    private final Usuarios usuarios;
    private final OutboxPublisher outboxPublisher;

    public TicketService(Tickets tickets,
                         TicketMensajes ticketMensajes,
                         Usuarios usuarios,
                         OutboxPublisher outboxPublisher) {
        this.tickets = tickets;
        this.ticketMensajes = ticketMensajes;
        this.usuarios = usuarios;
        this.outboxPublisher = outboxPublisher;
    }

    @Transactional
    public TicketDetalleDto crearTicket(String creadorEmail, String asunto, String mensaje) {
        if (asunto == null || asunto.trim().isEmpty()) {
            throw new IllegalArgumentException("El asunto no puede estar vacío.");
        }
        if (mensaje == null || mensaje.trim().isEmpty()) {
            throw new IllegalArgumentException("El mensaje no puede estar vacío.");
        }

        Usuario creador = usuarios.buscarPorEmail(creadorEmail)
                .orElseThrow(() -> new NoEncontradoException("Usuario no encontrado"));

        LocalDateTime ahora = LocalDateTime.now();
        Ticket ticket = new Ticket(
                null,
                0,
                creador.id(),
                asunto.trim(),
                "PENDIENTE",
                ahora,
                ahora
        );
        Long ticketId = tickets.insertarNuevo(ticket);

        TicketMensaje primerMensaje = new TicketMensaje(
                null,
                ticketId,
                creador.id(),
                mensaje.trim(),
                ahora
        );
        ticketMensajes.insertarNuevo(primerMensaje);

        String nombreCreador = nombreCompleto(creador);
        outboxPublisher.publicar(
                Topicos.IDENTIDAD_TICKET_CREADO_V1,
                "ticket",
                String.valueOf(ticketId),
                1,
                new TicketCreadoData(ticketId, creador.id(), nombreCreador, asunto.trim())
        );

        return obtenerTicket(ticketId, creadorEmail);
    }

    @Transactional(readOnly = true)
    public List<TicketResumenDto> listarMisTickets(String email) {
        Usuario usuario = usuarios.buscarPorEmail(email)
                .orElseThrow(() -> new NoEncontradoException("Usuario no encontrado"));
        return tickets.buscarPorCreador(usuario.id()).stream()
                .map(this::toResumenDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TicketResumenDto> listarTodos() {
        return tickets.todosOrdenadosPorFechaActualizacionDesc().stream()
                .map(this::toResumenDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TicketDetalleDto obtenerTicket(Long ticketId, String requesterEmail) {
        Ticket ticket = tickets.buscarPorId(ticketId)
                .orElseThrow(() -> new NoEncontradoException("Ticket no encontrado"));
        Usuario requester = usuarios.buscarPorEmail(requesterEmail)
                .orElseThrow(() -> new NoEncontradoException("Usuario no encontrado"));

        boolean esDueno = ticket.creadorId().equals(requester.id());
        boolean esAdmin = "ADMIN".equals(requester.rol());
        if (!esDueno && !esAdmin) {
            throw new ProhibidoException("No tenés permiso para ver este ticket.");
        }

        Usuario creador = usuarios.buscarPorId(ticket.creadorId()).orElse(null);

        List<TicketMensajeDto> mensajes = ticketMensajes.buscarPorTicketId(ticketId).stream()
                .map(m -> {
                    Usuario autor = usuarios.buscarPorId(m.autorId()).orElse(null);
                    String nombreAutor = autor != null ? nombreCompleto(autor) : "Usuario";
                    boolean deAdmin = autor != null && "ADMIN".equals(autor.rol());
                    return new TicketMensajeDto(
                            m.id(),
                            m.autorId(),
                            nombreAutor,
                            deAdmin,
                            m.contenido(),
                            m.fechaEnvio().toString()
                    );
                })
                .collect(Collectors.toList());

        return new TicketDetalleDto(
                ticket.id(),
                ticket.asunto(),
                ticket.estado(),
                ticket.fechaCreacion().toString(),
                ticket.fechaActualizacion().toString(),
                ticket.creadorId(),
                creador != null ? nombreCompleto(creador) : "Usuario",
                creador != null ? creador.email() : null,
                creador != null ? creador.rol() : null,
                mensajes
        );
    }

    @Transactional
    public TicketDetalleDto agregarMensaje(Long ticketId, String autorEmail, String contenido) {
        if (contenido == null || contenido.trim().isEmpty()) {
            throw new IllegalArgumentException("El mensaje no puede estar vacío.");
        }

        Ticket ticket = tickets.buscarPorId(ticketId)
                .orElseThrow(() -> new NoEncontradoException("Ticket no encontrado"));
        Usuario autor = usuarios.buscarPorEmail(autorEmail)
                .orElseThrow(() -> new NoEncontradoException("Usuario no encontrado"));

        boolean esDueno = ticket.creadorId().equals(autor.id());
        boolean esAdmin = "ADMIN".equals(autor.rol());
        if (!esDueno && !esAdmin) {
            throw new ProhibidoException("No tenés permiso para responder este ticket.");
        }

        LocalDateTime ahora = LocalDateTime.now();
        TicketMensaje ticketMensaje = new TicketMensaje(
                null,
                ticket.id(),
                autor.id(),
                contenido.trim(),
                ahora
        );
        ticketMensajes.insertarNuevo(ticketMensaje);

        Usuario creador = usuarios.buscarPorId(ticket.creadorId())
                .orElseThrow(() -> new NoEncontradoException("Creador del ticket no encontrado"));

        if (esAdmin) {
            Ticket ticketActualizado = new Ticket(
                    ticket.id(),
                    ticket.version(),
                    ticket.creadorId(),
                    ticket.asunto(),
                    "ACTIVO",
                    ticket.fechaCreacion(),
                    ahora
            );
            ticketActualizado = tickets.actualizar(ticketActualizado);

            int aggVersion = ticketActualizado.aggregateVersion();
            outboxPublisher.publicar(
                    Topicos.IDENTIDAD_TICKET_RESPONDIDO_V1,
                    "ticket",
                    String.valueOf(ticket.id()),
                    aggVersion,
                    new TicketRespondidoData(
                            ticket.id(),
                            creador.id(),
                            creador.email(),
                            ticket.asunto(),
                            contenido.trim()
                    )
            );
        } else {
            String nuevoEstado = "RESUELTO".equals(ticket.estado()) ? "PENDIENTE" : ticket.estado();
            Ticket ticketActualizado = new Ticket(
                    ticket.id(),
                    ticket.version(),
                    ticket.creadorId(),
                    ticket.asunto(),
                    nuevoEstado,
                    ticket.fechaCreacion(),
                    ahora
            );
            ticketActualizado = tickets.actualizar(ticketActualizado);

            int aggVersion = ticketActualizado.aggregateVersion();
            String nombreAutor = nombreCompleto(autor);
            outboxPublisher.publicar(
                    Topicos.IDENTIDAD_TICKET_MENSAJE_RECIBIDO_V1,
                    "ticket",
                    String.valueOf(ticket.id()),
                    aggVersion,
                    new TicketMensajeRecibidoData(
                            ticket.id(),
                            autor.id(),
                            nombreAutor,
                            ticket.asunto()
                    )
            );
        }

        return obtenerTicket(ticketId, autorEmail);
    }

    @Transactional
    public void cambiarEstado(Long ticketId, String estadoStr) {
        Ticket ticket = tickets.buscarPorId(ticketId)
                .orElseThrow(() -> new NoEncontradoException("Ticket no encontrado"));
        if (estadoStr == null || !ESTADOS_VALIDOS.contains(estadoStr.trim().toUpperCase())) {
            throw new IllegalArgumentException("Estado inválido.");
        }
        String nuevoEstado = estadoStr.trim().toUpperCase();
        Ticket ticketActualizado = new Ticket(
                ticket.id(),
                ticket.version(),
                ticket.creadorId(),
                ticket.asunto(),
                nuevoEstado,
                ticket.fechaCreacion(),
                LocalDateTime.now()
        );
        tickets.actualizar(ticketActualizado);
    }

    private TicketResumenDto toResumenDto(Ticket ticket) {
        List<TicketMensaje> msgs = ticketMensajes.buscarPorTicketId(ticket.id());
        String ultimoMensaje = msgs.isEmpty() ? null : msgs.get(msgs.size() - 1).contenido();
        Usuario creador = usuarios.buscarPorId(ticket.creadorId()).orElse(null);

        return new TicketResumenDto(
                ticket.id(),
                ticket.asunto(),
                ticket.estado(),
                ticket.fechaCreacion().toString(),
                ticket.fechaActualizacion().toString(),
                ticket.creadorId(),
                creador != null ? nombreCompleto(creador) : "Usuario",
                creador != null ? creador.email() : null,
                creador != null ? creador.rol() : null,
                ultimoMensaje
        );
    }

    private String nombreCompleto(Usuario u) {
        String nombre = (u.nombre() != null ? u.nombre() : "") +
                (u.apellido() != null ? " " + u.apellido() : "");
        return nombre.trim().isEmpty() ? u.email() : nombre.trim();
    }
}
