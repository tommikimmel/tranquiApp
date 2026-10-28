package com.tranqui.profesionales.recetas;

import com.tranqui.profesionales.seguridad.FiltroAutenticacionUsuario;
import com.tranqui.profesionales.seguridad.UsuarioJwt;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Rutas de recetas electrónicas (QBI2 / Innovamed) en el servicio de profesionales.
 * Mismos paths, verbos HTTP y permisos que respondía {@code RecetaController} del monolito.
 * /api/recetas/financiadores es pública (sin sesión), para checkout de pacientes invitados.
 */
@RestController
@RequestMapping("/api/recetas")
public class RecetaController {

    private static final String ROL_PROFESIONAL = "PSIQUIATRA";
    private static final String ROL_PACIENTE = "PACIENTE";

    private final RecetaService recetaService;
    private final Qbi2RecipeClient qbi2RecipeClient;

    public RecetaController(RecetaService recetaService, Qbi2RecipeClient qbi2RecipeClient) {
        this.recetaService = recetaService;
        this.qbi2RecipeClient = qbi2RecipeClient;
    }

    @PostMapping("/enviar")
    public ResponseEntity<RecetaResponseDto> enviarReceta(
            @RequestBody RecetaDto dto,
            HttpServletRequest request) {
        String email = profesional(request);
        if (email == null) {
            return prohibido();
        }
        return ResponseEntity.ok(recetaService.emitirReceta(email, dto));
    }

    @GetMapping("/me")
    public ResponseEntity<List<RecetaResponseDto>> obtenerMisRecetas(HttpServletRequest request) {
        UsuarioJwt.Usuario usuario = pacienteOProfesional(request);
        if (usuario == null) {
            return prohibido();
        }
        return ResponseEntity.ok(recetaService.obtenerMisRecetas(usuario.email()));
    }

    @GetMapping("/diagnosticos")
    public ResponseEntity<Qbi2CatalogoDtos.DiagnosticoResponse> buscarDiagnosticos(
            @RequestParam(defaultValue = "") String texto,
            HttpServletRequest request) {
        String email = profesional(request);
        if (email == null) {
            return prohibido();
        }
        return ResponseEntity.ok(qbi2RecipeClient.buscarDiagnosticos(texto));
    }

    @GetMapping("/medicamentos")
    public ResponseEntity<Qbi2CatalogoDtos.MedicamentoResponse> buscarMedicamentos(
            @RequestParam(defaultValue = "") String texto,
            @RequestParam(defaultValue = "1") int pagina,
            HttpServletRequest request) {
        String email = profesional(request);
        if (email == null) {
            return prohibido();
        }
        return ResponseEntity.ok(qbi2RecipeClient.buscarMedicamentos(texto, pagina));
    }

    // Public: needed at booking time by patients who aren't logged in yet (guest checkout in
    // CheckoutFlow.tsx) when the médico marked a service as "Requiere Obra Social" — see
    // FiltroAutenticacionUsuario for the permitAll match for /api/recetas/financiadores.
    @GetMapping("/financiadores")
    public ResponseEntity<Qbi2CatalogoDtos.FinanciadorResponse> buscarFinanciadores() {
        return ResponseEntity.ok(qbi2RecipeClient.buscarFinanciadores());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecetaResponseDto> obtenerRecetaPorId(
            @PathVariable("id") Long id,
            HttpServletRequest request) {
        UsuarioJwt.Usuario usuario = pacienteOProfesional(request);
        if (usuario == null) {
            return prohibido();
        }
        return ResponseEntity.ok(recetaService.obtenerRecetaPorId(id, usuario.email()));
    }

    private static String profesional(HttpServletRequest request) {
        UsuarioJwt.Usuario usuario = (UsuarioJwt.Usuario) request.getAttribute(FiltroAutenticacionUsuario.ATRIBUTO_USUARIO);
        return usuario != null && ROL_PROFESIONAL.equals(usuario.rol()) ? usuario.email() : null;
    }

    private static UsuarioJwt.Usuario pacienteOProfesional(HttpServletRequest request) {
        UsuarioJwt.Usuario usuario = (UsuarioJwt.Usuario) request.getAttribute(FiltroAutenticacionUsuario.ATRIBUTO_USUARIO);
        if (usuario != null && (ROL_PACIENTE.equals(usuario.rol()) || ROL_PROFESIONAL.equals(usuario.rol()))) {
            return usuario;
        }
        return null;
    }

    private static <T> ResponseEntity<T> prohibido() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
}
