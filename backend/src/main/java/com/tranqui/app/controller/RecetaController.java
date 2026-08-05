package com.tranqui.app.controller;

import com.tranqui.app.model.dto.RecetaDto;
import com.tranqui.app.model.dto.RecetaResponseDto;
import com.tranqui.app.service.RecetaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recetas")
public class RecetaController {

    @Autowired
    private RecetaService recetaService;

    @PostMapping("/enviar")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<RecetaResponseDto> enviarReceta(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody RecetaDto dto) {
        return ResponseEntity.ok(recetaService.emitirReceta(userDetails.getUsername(), dto));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('PACIENTE', 'PSIQUIATRA')")
    public ResponseEntity<List<RecetaResponseDto>> obtenerMisRecetas(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(recetaService.obtenerMisRecetas(userDetails.getUsername()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PACIENTE', 'PSIQUIATRA')")
    public ResponseEntity<RecetaResponseDto> obtenerRecetaPorId(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(recetaService.obtenerRecetaPorId(id, userDetails.getUsername()));
    }
}
