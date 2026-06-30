package com.tranqui.app.controller;

import com.tranqui.app.model.dto.MedicoDto;
import com.tranqui.app.service.MedicoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/medicos")
public class MedicoController {

    @Autowired
    private MedicoService medicoService;

    @GetMapping
    public ResponseEntity<List<MedicoDto>> obtenerMedicos() {
        return ResponseEntity.ok(medicoService.obtenerMedicosActivos());
    }

    @GetMapping("/perfil")
    public ResponseEntity<MedicoDto> obtenerPerfil(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(medicoService.obtenerPerfil(userDetails.getUsername()));
    }

    @PutMapping("/perfil")
    public ResponseEntity<MedicoDto> actualizarPerfil(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody MedicoDto dto) {
        return ResponseEntity.ok(medicoService.actualizarPerfil(userDetails.getUsername(), dto));
    }
}
