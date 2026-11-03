package com.tranqui.profesionales.web;

import com.tranqui.profesionales.perfil.Disponibilidades;
import com.tranqui.profesionales.perfil.MedicoDto;
import com.tranqui.profesionales.perfil.Perfiles;
import com.tranqui.profesionales.seguridad.FiltroAutenticacionUsuario;
import com.tranqui.profesionales.seguridad.UsuarioJwt;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * Las rutas de {@code MedicoController} del monolito que son de este servicio: el buscador público, "Mi
 * perfil" y la agenda. Las demás de {@code /api/medicos} (turnos, Mercado Pago, Google Calendar y las
 * estadísticas) siguen en sus dueños; el gateway reparte.
 */
@RestController
@RequestMapping("/api/medicos")
public class MedicoController {

    private static final String ROL_PROFESIONAL = "PSIQUIATRA";
    private static final Set<String> MODALIDADES = Set.of("ONLINE", "PRESENCIAL");

    private final Perfiles perfiles;
    private final Disponibilidades disponibilidades;

    public MedicoController(Perfiles perfiles, Disponibilidades disponibilidades) {
        this.perfiles = perfiles;
        this.disponibilidades = disponibilidades;
    }

    @GetMapping
    public List<MedicoDto> buscador() {
        return perfiles.buscador();
    }

    @GetMapping("/perfil")
    public ResponseEntity<MedicoDto> perfil(HttpServletRequest request) {
        String email = profesional(request);
        return email == null ? prohibido() : ResponseEntity.ok(perfiles.perfil(email));
    }

    @PutMapping("/perfil")
    public ResponseEntity<MedicoDto> actualizarPerfil(HttpServletRequest request, @RequestBody MedicoDto dto) {
        String email = profesional(request);
        return email == null ? prohibido() : ResponseEntity.ok(perfiles.actualizarPerfil(email, dto));
    }

    /** Solo la duración del turno y el intervalo ("Agenda"), sin el resto del perfil. */
    @PutMapping("/disponibilidad-config")
    public ResponseEntity<MedicoDto> actualizarConfigAgenda(HttpServletRequest request, @RequestBody MedicoDto dto) {
        String email = profesional(request);
        return email == null ? prohibido()
                : ResponseEntity.ok(perfiles.actualizarConfigAgenda(email, dto.duracionTurnoMinutos(), dto.intervaloEntreTurnosMinutos()));
    }

    @GetMapping("/disponibilidad")
    public ResponseEntity<List<MedicoDto.DisponibilidadDto>> disponibilidad(HttpServletRequest request,
                                                                           @RequestParam("modalidad") String modalidad) {
        String email = profesional(request);
        if (email == null) {
            return prohibido();
        }
        return ResponseEntity.ok(disponibilidades.obtener(email, modalidad(modalidad)));
    }

    @PutMapping("/disponibilidad")
    public ResponseEntity<List<MedicoDto.DisponibilidadDto>> guardarDisponibilidad(HttpServletRequest request,
                                                                                  @RequestParam("modalidad") String modalidad,
                                                                                  @RequestBody List<MedicoDto.DisponibilidadDto> dtos) {
        String email = profesional(request);
        if (email == null) {
            return prohibido();
        }
        return ResponseEntity.ok(disponibilidades.guardar(email, modalidad(modalidad), dtos));
    }

    /** El mail del profesional logueado, o null si el rol no es de profesional (el monolito respondía 403). */
    private static String profesional(HttpServletRequest request) {
        UsuarioJwt.Usuario usuario = (UsuarioJwt.Usuario) request.getAttribute(FiltroAutenticacionUsuario.ATRIBUTO_USUARIO);
        return usuario != null && ROL_PROFESIONAL.equals(usuario.rol()) ? usuario.email() : null;
    }

    private static String modalidad(String valor) {
        if (valor == null || !MODALIDADES.contains(valor)) {
            throw new IllegalArgumentException("Modalidad inválida: " + valor);
        }
        return valor;
    }

    private static <T> ResponseEntity<T> prohibido() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
}
