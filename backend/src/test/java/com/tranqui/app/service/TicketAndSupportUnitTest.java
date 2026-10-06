package com.tranqui.app.service;

import com.tranqui.app.controller.SoporteController;
import com.tranqui.app.controller.TicketController;
import com.tranqui.app.model.*;
import com.tranqui.app.model.dto.*;
import com.tranqui.app.repository.TicketMensajeRepository;
import com.tranqui.app.repository.TicketRepository;
import com.tranqui.app.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketAndSupportUnitTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketMensajeRepository ticketMensajeRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private NotificacionService notificacionService;

    @Mock
    private ResendEmailService resendEmailService;

    @Mock
    private UserDetails userDetails;

    @InjectMocks
    private TicketService ticketService;

    @InjectMocks
    private TicketController ticketController;

    @InjectMocks
    private SoporteController soporteController;

    private Usuario paciente;
    private Usuario admin;
    private Usuario otroUsuario;
    private Ticket ticket;
    private TicketMensaje mensaje;

    @BeforeEach
    void setUp() {
        paciente = Usuario.builder()
                .id(1L)
                .nombre("Juan")
                .apellido("Perez")
                .email("juan@example.com")
                .rol(Rol.PACIENTE)
                .build();

        admin = Usuario.builder()
                .id(2L)
                .nombre("Admin")
                .email("admin@tranqui.com")
                .rol(Rol.ADMIN)
                .build();

        otroUsuario = Usuario.builder()
                .id(3L)
                .email("otro@example.com")
                .rol(Rol.PACIENTE)
                .build();

        ticket = Ticket.builder()
                .id(10L)
                .creador(paciente)
                .asunto("Problema con turno")
                .estado(TicketEstado.PENDIENTE)
                .fechaCreacion(LocalDateTime.now())
                .fechaActualizacion(LocalDateTime.now())
                .build();

        mensaje = TicketMensaje.builder()
                .id(100L)
                .ticket(ticket)
                .autor(paciente)
                .contenido("No puedo ver mi turno")
                .fechaEnvio(LocalDateTime.now())
                .build();
    }

    // --- TicketService tests ---

    @Test
    void crearTicket_successAndValidation() {
        assertThrows(IllegalArgumentException.class, () -> ticketService.crearTicket("juan@example.com", "", "msg"));
        assertThrows(IllegalArgumentException.class, () -> ticketService.crearTicket("juan@example.com", "asunto", ""));

        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(paciente));
        when(ticketRepository.save(any(Ticket.class))).thenReturn(ticket);
        when(ticketMensajeRepository.save(any(TicketMensaje.class))).thenReturn(mensaje);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(ticketMensajeRepository.findByTicketIdOrderByFechaEnvioAsc(10L)).thenReturn(List.of(mensaje));
        when(usuarioRepository.findByRol(Rol.ADMIN)).thenReturn(List.of(admin));

        TicketDetalleDto detalle = ticketService.crearTicket("juan@example.com", "Problema con turno", "No puedo ver mi turno");
        assertNotNull(detalle);
        assertEquals("Problema con turno", detalle.getAsunto());
        verify(notificacionService).crearNotificacion(eq(admin), anyString(), anyString(), anyString());
    }

    @Test
    void listarTickets() {
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(paciente));
        when(ticketRepository.findByCreadorIdOrderByFechaActualizacionDesc(1L))
                .thenReturn(List.of(ticket));
        assertEquals(1, ticketService.listarMisTickets("juan@example.com").size());

        when(ticketRepository.findAllByOrderByFechaActualizacionDesc()).thenReturn(List.of(ticket));
        assertEquals(1, ticketService.listarTodos().size());
    }

    @Test
    void obtenerTicket_permissions() {
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(paciente));
        when(ticketMensajeRepository.findByTicketIdOrderByFechaEnvioAsc(10L)).thenReturn(List.of(mensaje));

        // Owner can access
        assertNotNull(ticketService.obtenerTicket(10L, "juan@example.com"));

        // Admin can access
        when(usuarioRepository.findByEmail("admin@tranqui.com")).thenReturn(Optional.of(admin));
        assertNotNull(ticketService.obtenerTicket(10L, "admin@tranqui.com"));

        // Other user is forbidden
        when(usuarioRepository.findByEmail("otro@example.com")).thenReturn(Optional.of(otroUsuario));
        assertThrows(AccessDeniedException.class, () -> ticketService.obtenerTicket(10L, "otro@example.com"));
    }

    @Test
    void agregarMensaje_byAdminAndByPatient() {
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(ticketMensajeRepository.save(any(TicketMensaje.class))).thenReturn(mensaje);
        when(ticketMensajeRepository.findByTicketIdOrderByFechaEnvioAsc(10L)).thenReturn(List.of(mensaje));

        // Admin responds -> updates status to ACTIVO and notifies patient
        when(usuarioRepository.findByEmail("admin@tranqui.com")).thenReturn(Optional.of(admin));
        ticketService.agregarMensaje(10L, "admin@tranqui.com", "Ya lo solucionamos.");
        assertEquals(TicketEstado.ACTIVO, ticket.getEstado());
        verify(notificacionService).crearNotificacion(eq(paciente), anyString(), anyString(), anyString());
        verify(resendEmailService).enviarRespuestaTicket(eq("juan@example.com"), eq("Juan"), eq("Problema con turno"), anyString());

        // Patient responds back -> updates status to PENDIENTE if it was RESUELTO
        ticket.setEstado(TicketEstado.RESUELTO);
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(paciente));
        when(usuarioRepository.findByRol(Rol.ADMIN)).thenReturn(List.of(admin));
        ticketService.agregarMensaje(10L, "juan@example.com", "Muchas gracias!");
        assertEquals(TicketEstado.PENDIENTE, ticket.getEstado());
        verify(notificacionService).crearNotificacion(eq(admin), anyString(), anyString(), anyString());
    }

    @Test
    void cambiarEstado_tests() {
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        ticketService.cambiarEstado(10L, "RESUELTO");
        assertEquals(TicketEstado.RESUELTO, ticket.getEstado());

        assertThrows(IllegalArgumentException.class, () -> ticketService.cambiarEstado(10L, "INVALIDO"));
    }

    // --- TicketController & SoporteController tests ---

    @Test
    void ticketController_endpoints() {
        org.springframework.test.util.ReflectionTestUtils.setField(ticketController, "ticketService", ticketService);
        when(userDetails.getUsername()).thenReturn("juan@example.com");
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(paciente));

        when(ticketRepository.findByCreadorIdOrderByFechaActualizacionDesc(1L))
                .thenReturn(List.of(ticket));
        ResponseEntity<List<TicketResumenDto>> resp = ticketController.misTickets(userDetails);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(1, resp.getBody().size());

        // SoporteController
        ResponseEntity<Void> soporteResp = soporteController.solicitarCopiaDatos(userDetails);
        assertEquals(HttpStatus.OK, soporteResp.getStatusCode());
        verify(resendEmailService).enviarSolicitudCopiaDatos(anyString(), eq("juan@example.com"), anyString());
    }
}
