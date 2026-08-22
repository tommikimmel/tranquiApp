package com.tranqui.app.controller;

import com.tranqui.app.model.dto.CrearTicketDto;
import com.tranqui.app.model.dto.NuevoMensajeTicketDto;
import com.tranqui.app.model.dto.TicketDetalleDto;
import com.tranqui.app.model.dto.TicketResumenDto;
import com.tranqui.app.service.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Lado paciente/profesional del sistema de tickets. El detalle y la respuesta también los usa el
// admin desde su panel (mismo hilo, ver TicketService#obtenerTicket/agregarMensaje para la
// autorización "dueño o admin") — evita duplicar estos dos endpoints en AdminController.
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    @Autowired
    private TicketService ticketService;

    @PostMapping
    @PreAuthorize("hasAnyRole('PACIENTE', 'PSIQUIATRA')")
    public ResponseEntity<?> crearTicket(@RequestBody CrearTicketDto dto, @AuthenticationPrincipal UserDetails userDetails) {
        try {
            return ResponseEntity.ok(ticketService.crearTicket(userDetails.getUsername(), dto.getAsunto(), dto.getMensaje()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @GetMapping("/mios")
    @PreAuthorize("hasAnyRole('PACIENTE', 'PSIQUIATRA')")
    public ResponseEntity<List<TicketResumenDto>> misTickets(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ticketService.listarMisTickets(userDetails.getUsername()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PACIENTE', 'PSIQUIATRA', 'ADMIN')")
    public ResponseEntity<?> obtenerTicket(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        try {
            return ResponseEntity.ok(ticketService.obtenerTicket(id, userDetails.getUsername()));
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    @PostMapping("/{id}/mensajes")
    @PreAuthorize("hasAnyRole('PACIENTE', 'PSIQUIATRA', 'ADMIN')")
    public ResponseEntity<?> agregarMensaje(
            @PathVariable Long id,
            @RequestBody NuevoMensajeTicketDto dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            return ResponseEntity.ok(ticketService.agregarMensaje(id, userDetails.getUsername(), dto.getContenido()));
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}
