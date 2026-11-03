package com.tranqui.app.controller;

import com.tranqui.app.eventos.OutboxPublisher;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Turno;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.TurnoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.CopiaTurnos;
import com.tranqui.app.service.FichaPaciente;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.TurnoActualizadoData;
import com.tranqui.eventos.data.UsuarioRegistradoData;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Fase 6: lo que el monolito le responde al servicio de turnos. Los turnos para su carga inicial (con los
 * tokens de los links de los mails, que no viajan en eventos), los datos de los usuarios que muestra la
 * agenda, y "buscar o crear paciente" para la reserva: identidad sigue en el monolito hasta la Fase 9.
 * Protegidas por {@link com.tranqui.app.config.FiltroServicioInterno}, como el resto de {@code /internal/monolito}.
 */
@RestController
@RequestMapping("/internal/monolito")
public class InternoTurnosController {

    /** Un turno para la carga inicial: el dato del evento, su versión y los tokens de los links de los mails. */
    public record TurnoDto(int version, TurnoActualizadoData turno, String tokenReserva, String tokenConfirmacionAsistencia) {}

    /** Los datos de un usuario que muestran la agenda y los turnos (no hay datos de salud). */
    // La fecha va en ISO (yyyy-MM-dd) aunque el monolito serializa LocalDate como dd/MM/yyyy para el
    // frontend (DateConfig): los servicios la leen con el Jackson de siempre.
    public record PacienteDto(Long id, int version, String rol, String email, String nombre, String apellido,
                              String telefono, String dni, String direccion, String obraSocial, String numAfiliado,
                              String sexo, @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer.class) LocalDate fechaNacimiento, Long cuil, String tipoDocumento,
                              Integer numeroDocumento, String datosOfuscado, Integer credencialCodEntidad,
                              String credencialPan, String credencialPlan, String credencialToken,
                              boolean emailHabilitado,
                              String domicilioCalle, String domicilioNumero, String domicilioPiso,
                              String domicilioDpto, String domicilioCodigoPostal, String domicilioLocalidad,
                              String domicilioProvincia, String domicilioPais) {}

    /** Lo que manda la reserva para "buscar o crear paciente", como lo resolvía {@code TurnoService#reservarTurno}. */
    public record BuscarOCrearPaciente(String email, String nombre, String telefono, String obraSocial, String numAfiliado) {}

    private static final int TAMANIO_MAXIMO = 1000;

    private final TurnoRepository turnoRepository;
    private final UsuarioRepository usuarioRepository;
    private final OutboxPublisher outboxPublisher;
    private final FichaPaciente fichaPaciente;

    public InternoTurnosController(TurnoRepository turnoRepository, UsuarioRepository usuarioRepository,
                                   OutboxPublisher outboxPublisher, FichaPaciente fichaPaciente) {
        this.turnoRepository = turnoRepository;
        this.usuarioRepository = usuarioRepository;
        this.outboxPublisher = outboxPublisher;
        this.fichaPaciente = fichaPaciente;
    }

    /** Carga inicial: todos los turnos, paginados por id. */
    @GetMapping("/turnos")
    @Transactional(readOnly = true)
    public List<TurnoDto> turnos(@RequestParam(name = "pagina", defaultValue = "0") int pagina,
                                 @RequestParam(name = "tamanio", defaultValue = "500") int tamanio) {
        int tamanioSeguro = Math.max(1, Math.min(tamanio, TAMANIO_MAXIMO));
        return turnoRepository.findAll(PageRequest.of(Math.max(0, pagina), tamanioSeguro, Sort.by("id"))).stream()
                .map(InternoTurnosController::turno)
                .toList();
    }

    /** Todos los usuarios (los turnos tienen pacientes y profesionales), paginados por id. */
    @GetMapping("/pacientes")
    @Transactional(readOnly = true)
    public List<PacienteDto> pacientes(@RequestParam(name = "pagina", defaultValue = "0") int pagina,
                                       @RequestParam(name = "tamanio", defaultValue = "500") int tamanio) {
        int tamanioSeguro = Math.max(1, Math.min(tamanio, TAMANIO_MAXIMO));
        return usuarioRepository.findAll(PageRequest.of(Math.max(0, pagina), tamanioSeguro, Sort.by("id"))).stream()
                .map(InternoTurnosController::paciente)
                .toList();
    }

    @GetMapping("/pacientes/{id}")
    @Transactional(readOnly = true)
    public PacienteDto paciente(@PathVariable("id") Long id) {
        return paciente(usuarioRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + id)));
    }

    /**
     * Lo que hacía la reserva con el paciente: el mail normalizado; si no tiene cuenta se crea (paciente, con
     * el teléfono con +54); y si vienen, la obra social y el número de afiliado pisan los guardados. Avisa el
     * alta como el registro.
     */
    @PostMapping("/pacientes")
    @Transactional
    public PacienteDto buscarOCrear(@RequestBody BuscarOCrearPaciente pedido) {
        String email = pedido.email() != null ? pedido.email().trim().toLowerCase() : null;
        if (email == null || email.isEmpty()) {
            throw new IllegalArgumentException("Falta el mail del paciente.");
        }
        String telefono = pedido.telefono();
        if (telefono != null && !telefono.trim().isEmpty() && !telefono.trim().startsWith("+54")) {
            telefono = "+54 " + telefono.trim();
        }
        Usuario paciente = usuarioRepository.findByEmail(email).orElse(null);
        boolean nuevo = paciente == null;
        if (nuevo) {
            paciente = Usuario.builder().nombre(pedido.nombre()).email(email).telefono(telefono).rol(Rol.PACIENTE).build();
        }
        if (pedido.obraSocial() != null && !pedido.obraSocial().isBlank()) {
            paciente.setObraSocial(pedido.obraSocial());
        }
        if (pedido.numAfiliado() != null && !pedido.numAfiliado().isBlank()) {
            paciente.setNumAfiliado(pedido.numAfiliado());
        }
        paciente = usuarioRepository.saveAndFlush(paciente);
        if (nuevo) {
            outboxPublisher.publicar(Topicos.IDENTIDAD_USUARIO_REGISTRADO_V1, String.valueOf(paciente.getId()),
                    paciente.getAggregateVersion(), new UsuarioRegistradoData(paciente.getId(), paciente.getEmail(),
                            paciente.getNombre(), paciente.getApellido(), Rol.PACIENTE.name()));
        }
        return paciente(paciente);
    }

    /**
     * Fase 7: actualización de la ficha del paciente por el servicio de profesionales.
     * Guarda en el monolito (dueño de identidad hasta la Fase 9) y publica identidad.usuario.actualizado.
     */
    @PutMapping("/pacientes/{id}")
    @Transactional
    public PacienteDto actualizarPaciente(@PathVariable("id") Long id,
                                          @RequestBody com.tranqui.app.model.dto.PacienteDto dto) {
        return paciente(fichaPaciente.actualizar(id, dto));
    }

    private static TurnoDto turno(Turno t) {
        return new TurnoDto(t.getAggregateVersion(), CopiaTurnos.datos(t), t.getTokenReserva(), t.getTokenConfirmacionAsistencia());
    }

    public static PacienteDto paciente(Usuario u) {
        return new PacienteDto(u.getId(), u.getAggregateVersion(), u.getRol() != null ? u.getRol().name() : null, u.getEmail(),
                u.getNombre(), u.getApellido(), u.getTelefono(), u.getDni(), u.getDireccion(), u.getObraSocial(),
                u.getNumAfiliado(), u.getSexo(), u.getFechaNacimiento(), u.getCuil(), u.getTipoDocumento(),
                u.getNumeroDocumento(), u.getDatosOfuscado(), u.getCredencialCodEntidad(), u.getCredencialPan(),
                u.getCredencialPlan(), u.getCredencialToken(), u.isNotificacionesEmailHabilitadas(),
                u.getDomicilioCalle(), u.getDomicilioNumero(), u.getDomicilioPiso(), u.getDomicilioDpto(),
                u.getDomicilioCodigoPostal(), u.getDomicilioLocalidad(), u.getDomicilioProvincia(), u.getDomicilioPais());
    }
}
