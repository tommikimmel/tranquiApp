package com.tranqui.app.controller;

import com.tranqui.app.model.EstadoPago;
import com.tranqui.app.model.SolicitudDocumento;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.model.dto.PagoResponseDto;
import com.tranqui.app.model.dto.SolicitarConceptoDto;
import com.tranqui.app.repository.SolicitudDocumentoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.ConceptoService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/conceptos")
public class ConceptoController {

    @Autowired
    private ConceptoService conceptoService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private SolicitudDocumentoRepository solicitudRepository;

    @PostMapping("/solicitar")
    @PreAuthorize("hasRole('PACIENTE')")
    public ResponseEntity<?> solicitarDocumento(
            @RequestBody SolicitarConceptoDto dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        SolicitudDocumento solicitud = conceptoService.crearSolicitud(dto, userDetails.getUsername());
        String checkoutUrl = conceptoService.generarCheckoutUrl(solicitud);
        
        return ResponseEntity.ok(new PagoResponseDto(solicitud.getId(), checkoutUrl));
    }

    @GetMapping("/pendientes")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<?> obtenerDocumentosPendientes(
            @AuthenticationPrincipal UserDetails userDetails) {
        
        Usuario medico = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("Médico no encontrado"));

        List<SolicitudDocumento> pendientes = solicitudRepository.findByMedicoIdAndEstadoAndEmitido(medico.getId(), EstadoPago.APROBADO, false);
        return ResponseEntity.ok(pendientes);
    }

    @PostMapping("/{id}/emitir")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<?> emitirDocumento(
            @PathVariable Long id,
            @RequestParam String urlDescarga,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        SolicitudDocumento solicitud = conceptoService.emitirDocumento(id, urlDescarga, userDetails.getUsername());
        return ResponseEntity.ok(solicitud);
    }
}
