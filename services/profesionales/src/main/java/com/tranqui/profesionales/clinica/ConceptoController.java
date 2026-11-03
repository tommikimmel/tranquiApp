package com.tranqui.profesionales.clinica;

import com.tranqui.profesionales.seguridad.FiltroAutenticacionUsuario;
import com.tranqui.profesionales.seguridad.UsuarioJwt;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Rutas de la API de conceptos (solicitud y emisión de documentos clínicos pagos).
 * Misma interfaz que {@code ConceptoController} del monolito.
 */
@RestController
@RequestMapping("/api/conceptos")
public class ConceptoController {

    private static final String ROL_PROFESIONAL = "PSIQUIATRA";
    private static final String ROL_PACIENTE = "PACIENTE";

    private final ConceptoService conceptoService;

    public ConceptoController(ConceptoService conceptoService) {
        this.conceptoService = conceptoService;
    }

    @PostMapping("/solicitar")
    public ResponseEntity<Dtos.PagoResponseDto> solicitarDocumento(
            @RequestBody Dtos.SolicitarConceptoDto dto,
            HttpServletRequest request) {
        String email = paciente(request);
        if (email == null) {
            return prohibido();
        }
        return ResponseEntity.ok(conceptoService.crearSolicitud(dto, email));
    }

    @GetMapping("/pendientes")
    public ResponseEntity<List<Dtos.SolicitudResponse>> obtenerDocumentosPendientes(HttpServletRequest request) {
        String email = profesional(request);
        if (email == null) {
            return prohibido();
        }
        return ResponseEntity.ok(conceptoService.obtenerDocumentosPendientes(email));
    }

    @PostMapping("/{id}/emitir")
    public ResponseEntity<Dtos.SolicitudResponse> emitirDocumento(
            @PathVariable("id") Long id,
            @RequestParam("urlDescarga") String urlDescarga,
            HttpServletRequest request) {
        String email = profesional(request);
        if (email == null) {
            return prohibido();
        }
        return ResponseEntity.ok(conceptoService.emitirDocumento(id, urlDescarga, email));
    }

    private static String profesional(HttpServletRequest request) {
        UsuarioJwt.Usuario usuario = (UsuarioJwt.Usuario) request.getAttribute(FiltroAutenticacionUsuario.ATRIBUTO_USUARIO);
        return usuario != null && ROL_PROFESIONAL.equals(usuario.rol()) ? usuario.email() : null;
    }

    private static String paciente(HttpServletRequest request) {
        UsuarioJwt.Usuario usuario = (UsuarioJwt.Usuario) request.getAttribute(FiltroAutenticacionUsuario.ATRIBUTO_USUARIO);
        return usuario != null && ROL_PACIENTE.equals(usuario.rol()) ? usuario.email() : null;
    }

    private static <T> ResponseEntity<T> prohibido() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
}
