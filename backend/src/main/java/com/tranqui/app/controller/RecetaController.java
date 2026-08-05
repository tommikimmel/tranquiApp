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

    @Autowired
    private com.tranqui.app.service.Qbi2RecipeClient qbi2RecipeClient;

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

    @GetMapping("/diagnosticos")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<com.tranqui.app.model.dto.Qbi2CatalogoDtos.DiagnosticoResponse> buscarDiagnosticos(
            @RequestParam(defaultValue = "") String texto) {
        return ResponseEntity.ok(qbi2RecipeClient.buscarDiagnosticos(texto));
    }

    @GetMapping("/medicamentos")
    @PreAuthorize("hasRole('PSIQUIATRA')")
    public ResponseEntity<com.tranqui.app.model.dto.Qbi2CatalogoDtos.MedicamentoResponse> buscarMedicamentos(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(defaultValue = "1") int pagina) {
        return ResponseEntity.ok(qbi2RecipeClient.buscarMedicamentos(texto, pagina));
    }

    // Public: needed at booking time by patients who aren't logged in yet (guest checkout in
    // CheckoutFlow.tsx) when the médico marked a service as "Requiere Obra Social" — see
    // SecurityConfig's permitAll list for /api/recetas/financiadores.
    @GetMapping("/financiadores")
    public ResponseEntity<com.tranqui.app.model.dto.Qbi2CatalogoDtos.FinanciadorResponse> buscarFinanciadores() {
        return ResponseEntity.ok(qbi2RecipeClient.buscarFinanciadores());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PACIENTE', 'PSIQUIATRA')")
    public ResponseEntity<RecetaResponseDto> obtenerRecetaPorId(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(recetaService.obtenerRecetaPorId(id, userDetails.getUsername()));
    }
}
