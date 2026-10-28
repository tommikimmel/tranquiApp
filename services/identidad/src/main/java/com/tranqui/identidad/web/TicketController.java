package com.tranqui.identidad.web;

import com.tranqui.identidad.datos.Usuario;
import com.tranqui.identidad.seguridad.FiltroAutenticacionUsuario;
import com.tranqui.identidad.tickets.TicketService;
import com.tranqui.identidad.web.dto.CrearTicketDto;
import com.tranqui.identidad.web.dto.NuevoMensajeTicketDto;
import com.tranqui.identidad.web.dto.TicketDetalleDto;
import com.tranqui.identidad.web.dto.TicketResumenDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * Lado paciente/profesional del sistema de tickets (Fase 9, I2c).
 * El detalle y la respuesta también los usa el admin desde su panel.
 */
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private static final Set<String> ROLES_CREACION = Set.of("PACIENTE", "PSIQUIATRA");
    private static final Set<String> ROLES_CONSULTA = Set.of("PACIENTE", "PSIQUIATRA", "ADMIN");

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    private Usuario usuarioAutenticado(HttpServletRequest request) {
        Object attr = request.getAttribute(FiltroAutenticacionUsuario.ATRIBUTO_USUARIO);
        if (attr instanceof Usuario u) {
            return u;
        }
        return null;
    }

    @PostMapping
    public ResponseEntity<?> crearTicket(@RequestBody CrearTicketDto dto, HttpServletRequest request) {
        Usuario usuario = usuarioAutenticado(request);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!ROLES_CREACION.contains(usuario.rol())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("No tenés permiso para crear tickets.");
        }
        try {
            return ResponseEntity.ok(ticketService.crearTicket(usuario.email(), dto.getAsunto(), dto.getMensaje()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @GetMapping("/mios")
    public ResponseEntity<List<TicketResumenDto>> misTickets(HttpServletRequest request) {
        Usuario usuario = usuarioAutenticado(request);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!ROLES_CREACION.contains(usuario.rol())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(ticketService.listarMisTickets(usuario.email()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerTicket(@PathVariable("id") Long id, HttpServletRequest request) {
        Usuario usuario = usuarioAutenticado(request);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!ROLES_CONSULTA.contains(usuario.rol())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("No tenés permiso para ver este ticket.");
        }
        try {
            return ResponseEntity.ok(ticketService.obtenerTicket(id, usuario.email()));
        } catch (ProhibidoException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    @PostMapping("/{id}/mensajes")
    public ResponseEntity<?> agregarMensaje(
            @PathVariable("id") Long id,
            @RequestBody NuevoMensajeTicketDto dto,
            HttpServletRequest request) {
        Usuario usuario = usuarioAutenticado(request);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!ROLES_CONSULTA.contains(usuario.rol())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("No tenés permiso para responder este ticket.");
        }
        try {
            return ResponseEntity.ok(ticketService.agregarMensaje(id, usuario.email(), dto.getContenido()));
        } catch (ProhibidoException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}
