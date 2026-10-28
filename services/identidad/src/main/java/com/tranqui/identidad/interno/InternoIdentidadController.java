package com.tranqui.identidad.interno;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.DisponibilidadProfesionalData;
import com.tranqui.eventos.data.PerfilProfesionalData;
import com.tranqui.eventos.data.TarifasProfesionalData;
import com.tranqui.eventos.data.UsuarioRegistradoData;
import com.tranqui.eventos.outbox.OutboxPublisher;
import com.tranqui.identidad.datos.PerfilesRegistro;
import com.tranqui.identidad.datos.Usuario;
import com.tranqui.identidad.datos.Usuarios;
import com.tranqui.identidad.web.NoEncontradoException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Las rutas internas de identidad (Fase 9): las mismas que servía el monolito bajo {@code /internal/monolito/...} para
 * los datos de usuario, con el mismo JSON, así cada servicio solo cambia de dónde lee
 * ({@code TRANQUI_FUENTE_IDENTIDAD}). Las protege el JWT de servicio ({@link FiltroServicioInterno}).
 *
 * <p>Diferencias a propósito (identidad no es dueña de esos datos): en los profesionales, Mercado Pago, la suscripción,
 * la disponibilidad y las tarifas van en null (salen de pagos y de profesionales), y el perfil es el que se cargó al
 * registrarse ({@code perfil_registro}). Las escrituras validan todo lo que reciben, aunque lo mande otro servicio.
 */
@RestController
@RequestMapping("/internal/identidad")
public class InternoIdentidadController {

    private static final int TAMANIO_MAXIMO = 1000;
    private static final int TAMANIO_MAXIMO_PROFESIONALES = 200;
    private static final Set<String> TIPOS_DOCUMENTO_VALIDOS = Set.of("DNI", "LC", "LE", "Pasaporte");

    /** Lo mínimo para la copia de contactos de notificaciones (como {@code InternoMonolitoController}). */
    public record ContactoDto(Long id, String email, String nombre, String apellido, String rol, boolean emailHabilitado) {}

    /** Los datos de un usuario para la agenda, los turnos y la ficha (como {@code InternoTurnosController}). */
    public record PacienteDto(Long id, int version, String rol, String email, String nombre, String apellido,
                              String telefono, String dni, String direccion, String obraSocial, String numAfiliado,
                              String sexo, @JsonSerialize(using = LocalDateSerializer.class) LocalDate fechaNacimiento,
                              Long cuil, String tipoDocumento, Integer numeroDocumento, String datosOfuscado,
                              Integer credencialCodEntidad, String credencialPan, String credencialPlan,
                              String credencialToken, boolean emailHabilitado,
                              String domicilioCalle, String domicilioNumero, String domicilioPiso,
                              String domicilioDpto, String domicilioCodigoPostal, String domicilioLocalidad,
                              String domicilioProvincia, String domicilioPais) {}

    public record BuscarOCrearPaciente(String email, String nombre, String telefono, String obraSocial, String numAfiliado) {}

    /** Un profesional (como {@code InternoProfesionalesController}); lo que no es de identidad va en null. */
    public record ProfesionalDto(Long id, int version, String email, String rol, String profession,
                                 String nombre, String apellido, String telefono, String sexo,
                                 @JsonSerialize(using = LocalDateSerializer.class) LocalDate fechaNacimiento,
                                 Long cuil, String tipoDocumento, Integer numeroDocumento, String fotoUrl,
                                 String fotoHash, Boolean verificadoAdmin, Boolean mpVinculada,
                                 String suscripcionEstado, LocalDateTime suscripcionPeriodoHasta, Boolean puedeRecetar,
                                 PerfilProfesionalData perfil,
                                 List<DisponibilidadProfesionalData.Franja> disponibilidad,
                                 List<TarifasProfesionalData.Tarifa> tarifas) {}

    /** La parte de identidad de {@code PUT /api/medicos/perfil}, que manda profesionales. */
    public record IdentidadDto(String nombre, String apellido, String telefono, String sexo,
                               LocalDate fechaNacimiento, Long cuil, String tipoDocumento, Integer numeroDocumento,
                               String fotoUrl) {}

    /** Lo que necesitan las suscripciones y la factura (como {@code InternoSuscripcionesController}). */
    public record SuscriptorDto(Long id, String email, String nombre, String apellido, String rol, String profession,
                                Boolean verificadoAdmin, int version, String taxIdType, String taxId, String legalName,
                                Integer ivaConditionId, String fiscalAddress, String cuit, Long cuil, String dni,
                                Integer numeroDocumento) {}

    private final Usuarios usuarios;
    private final PerfilesRegistro perfiles;
    private final FichaPaciente fichaPaciente;
    private final OutboxPublisher outboxPublisher;

    public InternoIdentidadController(Usuarios usuarios, PerfilesRegistro perfiles, FichaPaciente fichaPaciente,
                                      OutboxPublisher outboxPublisher) {
        this.usuarios = usuarios;
        this.perfiles = perfiles;
        this.fichaPaciente = fichaPaciente;
        this.outboxPublisher = outboxPublisher;
    }

    // ================================================================================
    // Contactos y pacientes
    // ================================================================================

    @GetMapping("/contactos")
    public List<ContactoDto> contactos(@RequestParam(name = "pagina", defaultValue = "0") int pagina,
                                       @RequestParam(name = "tamanio", defaultValue = "500") int tamanio) {
        return usuarios.paginados(Math.max(0, pagina), tamanioSeguro(tamanio, TAMANIO_MAXIMO)).stream()
                .map(u -> new ContactoDto(u.id(), u.email(), u.nombre(), u.apellido(), u.rol(),
                        u.isNotificacionesEmailHabilitadas()))
                .toList();
    }

    /** Todos los usuarios (los turnos tienen pacientes y profesionales), paginados por id, como el monolito. */
    @GetMapping("/pacientes")
    public List<PacienteDto> pacientes(@RequestParam(name = "pagina", defaultValue = "0") int pagina,
                                       @RequestParam(name = "tamanio", defaultValue = "500") int tamanio) {
        return usuarios.paginados(Math.max(0, pagina), tamanioSeguro(tamanio, TAMANIO_MAXIMO)).stream()
                .map(InternoIdentidadController::paciente)
                .toList();
    }

    @GetMapping("/pacientes/{id}")
    public PacienteDto paciente(@PathVariable("id") Long id) {
        return paciente(buscar(id));
    }

    /**
     * Buscar o crear el paciente de una reserva, como hacía el monolito: el mail normalizado; si no tiene cuenta se
     * crea (paciente, sin contraseña, con el teléfono con +54); y si vienen, la obra social y el número de afiliado
     * pisan los guardados. Avisa el alta como el registro y, a diferencia del monolito, también avisa el cambio de un
     * usuario que ya existía (si no, las copias quedan viejas).
     */
    @PostMapping("/pacientes")
    @Transactional
    public PacienteDto buscarOCrear(@RequestBody(required = false) BuscarOCrearPaciente pedido) {
        if (pedido == null) {
            throw new IllegalArgumentException("Falta el cuerpo del pedido.");
        }
        String email = pedido.email() != null ? pedido.email().trim().toLowerCase() : null;
        if (email == null || email.isEmpty()) {
            throw new IllegalArgumentException("Falta el mail del paciente.");
        }
        List<String> errores = new ArrayList<>();
        if (!Validaciones.mailValido(email)) {
            errores.add("el mail del paciente no es válido");
        }
        if (pedido.telefono() != null && !pedido.telefono().trim().isEmpty() && !Validaciones.telefonoValido(pedido.telefono())) {
            errores.add("el teléfono debe contener solo números, entre 6 y 15 dígitos");
        }
        if (pedido.nombre() != null && pedido.nombre().length() > 100) {
            errores.add("el nombre supera los 100 caracteres");
        }
        if (!errores.isEmpty()) {
            throw new IllegalArgumentException("Datos del paciente inválidos: " + String.join("; ", errores) + ".");
        }

        boolean cambiaObraSocial = pedido.obraSocial() != null && !pedido.obraSocial().isBlank();
        boolean cambiaAfiliado = pedido.numAfiliado() != null && !pedido.numAfiliado().isBlank();
        Usuario existente = usuarios.buscarPorEmail(email).orElse(null);

        if (existente == null) {
            Usuario.Builder nuevo = Usuario.builder()
                    .email(email)
                    .nombre(pedido.nombre())
                    .telefono(Validaciones.conPrefijo(pedido.telefono()))
                    .rol("PACIENTE")
                    .version(0)
                    .fechaRegistro(LocalDateTime.now())
                    .emailVerificado(false);
            if (cambiaObraSocial) nuevo.obraSocial(pedido.obraSocial());
            if (cambiaAfiliado) nuevo.numAfiliado(pedido.numAfiliado());
            Long id = usuarios.insertarNuevo(nuevo.build());
            Usuario creado = buscar(id);
            outboxPublisher.publicar(Topicos.IDENTIDAD_USUARIO_REGISTRADO_V1, "usuario", String.valueOf(creado.id()),
                    creado.aggregateVersion(), new UsuarioRegistradoData(creado.id(), creado.email(), creado.nombre(),
                            creado.apellido(), "PACIENTE"));
            return paciente(creado);
        }

        if (!cambiaObraSocial && !cambiaAfiliado) {
            return paciente(existente);
        }
        Usuario.Builder cambios = existente.toBuilder();
        if (cambiaObraSocial) cambios.obraSocial(pedido.obraSocial());
        if (cambiaAfiliado) cambios.numAfiliado(pedido.numAfiliado());
        Usuario guardado = usuarios.actualizar(cambios.build());
        fichaPaciente.publicarActualizado(guardado);
        return paciente(guardado);
    }

    /** La ficha del paciente, que edita profesionales (mismas validaciones que el monolito). */
    @PutMapping("/pacientes/{id}")
    public PacienteDto actualizarPaciente(@PathVariable("id") Long id, @RequestBody(required = false) FichaPaciente.Ficha ficha) {
        return paciente(fichaPaciente.actualizar(id, ficha));
    }

    // ================================================================================
    // Profesionales
    // ================================================================================

    @GetMapping("/profesionales")
    public List<ProfesionalDto> profesionales(@RequestParam(name = "pagina", defaultValue = "0") int pagina,
                                              @RequestParam(name = "tamanio", defaultValue = "50") int tamanio,
                                              @RequestParam(name = "foto", defaultValue = "true") boolean foto) {
        return usuarios.buscarPorRolPaginados("PSIQUIATRA", Math.max(0, pagina),
                        tamanioSeguro(tamanio, TAMANIO_MAXIMO_PROFESIONALES)).stream()
                .map(u -> profesional(u, true, foto))
                .toList();
    }

    /** Un usuario completo, para el alta o el refresco de un profesional. Puede no ser profesional (ver {@code rol}). */
    @GetMapping("/profesionales/{id}")
    public ProfesionalDto profesional(@PathVariable("id") Long id) {
        return profesional(buscar(id), true, true);
    }

    /** La sincronización de cada minuto: identidad sin la foto (solo el hash) y la verificación. */
    @GetMapping("/profesionales/estado")
    public List<ProfesionalDto> estado() {
        return usuarios.buscarPorRol("PSIQUIATRA").stream()
                .map(u -> profesional(u, false, false))
                .toList();
    }

    /** La parte de identidad de {@code PUT /api/medicos/perfil}. Devuelve sin la foto, como el monolito. */
    @PutMapping("/profesionales/{id}/identidad")
    @Transactional
    public ProfesionalDto identidad(@PathVariable("id") Long id, @RequestBody(required = false) IdentidadDto dto) {
        validarIdentidad(dto);
        Usuario u = buscar(id);
        Usuario guardado = usuarios.actualizar(u.toBuilder()
                .nombre(dto.nombre())
                .apellido(dto.apellido())
                .telefono(dto.telefono())
                .sexo(dto.sexo())
                .fechaNacimiento(dto.fechaNacimiento())
                .cuil(dto.cuil())
                .tipoDocumento(dto.tipoDocumento())
                .numeroDocumento(dto.numeroDocumento())
                .fotoUrl(dto.fotoUrl())
                .build());
        fichaPaciente.publicarActualizado(guardado);
        return profesional(guardado, false, false);
    }

    // ================================================================================
    // Suscriptores
    // ================================================================================

    @GetMapping("/suscriptores")
    public List<SuscriptorDto> suscriptores(@RequestParam(name = "pagina", defaultValue = "0") int pagina,
                                            @RequestParam(name = "tamanio", defaultValue = "500") int tamanio) {
        return usuarios.buscarSuscriptores(Math.max(0, pagina), tamanioSeguro(tamanio, TAMANIO_MAXIMO)).stream()
                .map(this::suscriptor)
                .toList();
    }

    /** Un profesional (rol PSIQUIATRA o con profesión cargada); si no lo es, 404. */
    @GetMapping("/suscriptores/{id}")
    public ResponseEntity<SuscriptorDto> suscriptor(@PathVariable("id") Long id) {
        return usuarios.buscarPorId(id)
                .filter(InternoIdentidadController::esSuscriptor)
                .map(u -> ResponseEntity.ok(suscriptor(u)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ================================================================================

    private Usuario buscar(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("El id del usuario no es válido.");
        }
        return usuarios.buscarPorId(id).orElseThrow(() -> new NoEncontradoException("Usuario no encontrado: " + id));
    }

    private static int tamanioSeguro(int tamanio, int maximo) {
        return Math.max(1, Math.min(tamanio, maximo));
    }

    private static boolean esSuscriptor(Usuario u) {
        return "PSIQUIATRA".equals(u.rol()) || (u.profession() != null && !u.profession().isBlank());
    }

    private static void validarIdentidad(IdentidadDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Datos del profesional inválidos: falta el cuerpo.");
        }
        List<String> errores = new ArrayList<>();
        if (dto.nombre() == null || dto.nombre().trim().isEmpty()) {
            errores.add("el nombre es obligatorio");
        }
        if (dto.tipoDocumento() != null && !dto.tipoDocumento().trim().isEmpty()
                && !TIPOS_DOCUMENTO_VALIDOS.contains(dto.tipoDocumento().trim())) {
            errores.add("el tipo de documento debe ser DNI, LC, LE o Pasaporte");
        }
        if (dto.numeroDocumento() != null) {
            int digitos = String.valueOf(Math.abs(dto.numeroDocumento())).length();
            if (dto.numeroDocumento() <= 0 || digitos < 6 || digitos > 9) {
                errores.add("el número de documento debe tener entre 6 y 9 dígitos");
            }
        }
        if (dto.fechaNacimiento() != null
                && (dto.fechaNacimiento().isAfter(LocalDate.now()) || dto.fechaNacimiento().isBefore(LocalDate.of(1900, 1, 1)))) {
            errores.add("la fecha de nacimiento no es válida");
        }
        if (dto.telefono() != null && !dto.telefono().trim().isEmpty() && !Validaciones.telefonoValido(dto.telefono())) {
            errores.add("el teléfono debe contener solo números, entre 6 y 15 dígitos");
        }
        if (dto.cuil() != null && (dto.cuil() <= 0 || String.valueOf(dto.cuil()).length() != 11)) {
            errores.add("el CUIL debe tener 11 dígitos");
        }
        if (!errores.isEmpty()) {
            throw new IllegalArgumentException("Datos del profesional inválidos: " + String.join("; ", errores) + ".");
        }
    }

    static PacienteDto paciente(Usuario u) {
        return new PacienteDto(u.id(), u.aggregateVersion(), u.rol(), u.email(), u.nombre(), u.apellido(),
                u.telefono(), u.dni(), u.direccion(), u.obraSocial(), u.numAfiliado(), u.sexo(), u.fechaNacimiento(),
                u.cuil(), u.tipoDocumento(), u.numeroDocumento(), u.datosOfuscado(), u.credencialCodEntidad(),
                u.credencialPan(), u.credencialPlan(), u.credencialToken(), u.isNotificacionesEmailHabilitadas(),
                u.domicilioCalle(), u.domicilioNumero(), u.domicilioPiso(), u.domicilioDpto(),
                u.domicilioCodigoPostal(), u.domicilioLocalidad(), u.domicilioProvincia(), u.domicilioPais());
    }

    private ProfesionalDto profesional(Usuario u, boolean completo, boolean conFoto) {
        PerfilProfesionalData perfil = completo ? perfiles.buscarPorUsuarioId(u.id()).orElse(null) : null;
        return new ProfesionalDto(u.id(), u.aggregateVersion(), u.email(), u.rol(), u.profession(),
                u.nombre(), u.apellido(), u.telefono(), u.sexo(), u.fechaNacimiento(), u.cuil(),
                u.tipoDocumento(), u.numeroDocumento(), conFoto ? u.fotoUrl() : null, hashFoto(u.fotoUrl()),
                u.verificadoAdmin(), null, null, null, null, perfil, null, null);
    }

    private SuscriptorDto suscriptor(Usuario u) {
        // El cuit es del perfil (lo carga el registro): sale de perfil_registro.
        String cuit = perfiles.buscarPorUsuarioId(u.id()).map(PerfilProfesionalData::cuit).orElse(null);
        return new SuscriptorDto(u.id(), u.email(), u.nombre(), u.apellido(), u.rol(), u.profession(),
                u.verificadoAdmin(), u.aggregateVersion(), u.taxIdType(), u.taxId(), u.legalName(),
                u.ivaConditionId(), u.fiscalAddress(), cuit, u.cuil(), u.dni(), u.numeroDocumento());
    }

    /** El mismo hash que el monolito (SHA-256 de la URL): profesionales lo usa para saber si cambió la foto. */
    static String hashFoto(String fotoUrl) {
        if (fotoUrl == null) {
            return null;
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(fotoUrl.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
