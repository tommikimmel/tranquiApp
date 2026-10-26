package com.tranqui.app.controller;

import com.tranqui.app.eventos.OutboxPublisher;
import com.tranqui.app.model.Disponibilidad;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Subscription;
import com.tranqui.app.model.TarifaMedico;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.DisponibilidadRepository;
import com.tranqui.app.repository.SubscriptionRepository;
import com.tranqui.app.repository.TarifaMedicoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.CopiaProfesionales;
import com.tranqui.app.service.SubscriptionService;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.DisponibilidadProfesionalData;
import com.tranqui.eventos.data.PerfilProfesionalData;
import com.tranqui.eventos.data.TarifasProfesionalData;
import com.tranqui.eventos.data.UsuarioActualizadoData;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Fase 5: lo que el monolito le responde al servicio de profesionales. Los datos de identidad, la
 * verificación del administrador, la suscripción y Mercado Pago siguen siendo del monolito; el servicio
 * los copia de acá (carga inicial, refresco de un profesional y sincronización de cada minuto) y manda
 * por acá la parte de identidad de {@code PUT /api/medicos/perfil}. Protegidas por
 * {@link com.tranqui.app.config.FiltroServicioInterno}, como el resto de {@code /internal/monolito}.
 */
@RestController
@RequestMapping("/internal/monolito/profesionales")
public class InternoProfesionalesController {

    /**
     * Un profesional visto desde el monolito. En {@code /estado} van sin la foto (solo su hash), sin el
     * perfil, la disponibilidad ni las tarifas.
     */
    // La fecha va en ISO (yyyy-MM-dd) aunque el monolito serializa LocalDate como dd/MM/yyyy para el
    // frontend (DateConfig): los servicios la leen con el Jackson de siempre.
    public record ProfesionalDto(Long id, int version, String email, String rol, String profession,
                                 String nombre, String apellido, String telefono, String sexo,
                                 @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer.class) LocalDate fechaNacimiento, Long cuil, String tipoDocumento,
                                 Integer numeroDocumento, String fotoUrl, String fotoHash,
                                 Boolean verificadoAdmin, boolean mpVinculada,
                                 String suscripcionEstado, LocalDateTime suscripcionPeriodoHasta, boolean puedeRecetar,
                                 PerfilProfesionalData perfil,
                                 List<DisponibilidadProfesionalData.Franja> disponibilidad,
                                 List<TarifasProfesionalData.Tarifa> tarifas) {}

    /** La parte de identidad del perfil, que el servicio no guarda como propia. */
    public record IdentidadDto(String nombre, String apellido, String telefono, String sexo,
                               LocalDate fechaNacimiento, Long cuil, String tipoDocumento, Integer numeroDocumento,
                               String fotoUrl) {}

    private static final int TAMANIO_MAXIMO = 200;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final UsuarioRepository usuarioRepository;
    private final DisponibilidadRepository disponibilidadRepository;
    private final TarifaMedicoRepository tarifaRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;
    private final OutboxPublisher outboxPublisher;

    public InternoProfesionalesController(UsuarioRepository usuarioRepository, DisponibilidadRepository disponibilidadRepository,
                                          TarifaMedicoRepository tarifaRepository, SubscriptionRepository subscriptionRepository,
                                          SubscriptionService subscriptionService, OutboxPublisher outboxPublisher) {
        this.usuarioRepository = usuarioRepository;
        this.disponibilidadRepository = disponibilidadRepository;
        this.tarifaRepository = tarifaRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionService = subscriptionService;
        this.outboxPublisher = outboxPublisher;
    }

    /** Carga inicial: los profesionales completos (con la foto), paginados por id. */
    @GetMapping
    @Transactional(readOnly = true)
    public List<ProfesionalDto> profesionales(@RequestParam(name = "pagina", defaultValue = "0") int pagina,
                                              @RequestParam(name = "tamanio", defaultValue = "50") int tamanio) {
        int tamanioSeguro = Math.max(1, Math.min(tamanio, TAMANIO_MAXIMO));
        return usuarioRepository.findByRolOrderByIdAsc(Rol.PSIQUIATRA, PageRequest.of(Math.max(0, pagina), tamanioSeguro)).stream()
                .map(this::completo)
                .toList();
    }

    /** Un usuario completo, para el alta o el refresco de un profesional. Puede no ser profesional (ver {@code rol}). */
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ProfesionalDto profesional(@PathVariable("id") Long id) {
        return completo(buscar(id));
    }

    /** La sincronización de cada minuto: identidad sin la foto, verificación, Mercado Pago y suscripción. */
    @GetMapping("/estado")
    @Transactional(readOnly = true)
    public List<ProfesionalDto> estado() {
        return usuarioRepository.findByRol(Rol.PSIQUIATRA).stream()
                .map(u -> armar(u, false))
                .toList();
    }

    /**
     * La parte de identidad de {@code PUT /api/medicos/perfil}. El servicio ya validó todo (mismas reglas que
     * {@code MedicoService#actualizarPerfil}); acá se guarda y se avisa como cualquier cambio de identidad.
     */
    @PutMapping("/{id}/identidad")
    @Transactional
    public ProfesionalDto identidad(@PathVariable("id") Long id, @RequestBody IdentidadDto dto) {
        Usuario u = buscar(id);
        u.setNombre(dto.nombre());
        u.setApellido(dto.apellido());
        u.setTelefono(dto.telefono());
        u.setSexo(dto.sexo());
        u.setFechaNacimiento(dto.fechaNacimiento());
        u.setCuil(dto.cuil());
        u.setTipoDocumento(dto.tipoDocumento());
        u.setNumeroDocumento(dto.numeroDocumento());
        u.setFotoUrl(dto.fotoUrl());
        u = usuarioRepository.saveAndFlush(u);
        outboxPublisher.publicar(
                Topicos.IDENTIDAD_USUARIO_ACTUALIZADO_V1,
                String.valueOf(u.getId()),
                u.getAggregateVersion(),
                new UsuarioActualizadoData(u.getId(), u.getEmail(), u.getNombre(), u.getApellido(), u.getTelefono(),
                        u.getRol() != null ? u.getRol().name() : null, u.isNotificacionesEmailHabilitadas()));
        return armar(u, false);
    }

    private Usuario buscar(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + id));
    }

    private ProfesionalDto completo(Usuario u) {
        return armar(u, true);
    }

    private ProfesionalDto armar(Usuario u, boolean completo) {
        Subscription sub = subscriptionRepository.findByProfessionalId(u.getId()).orElse(null);
        List<DisponibilidadProfesionalData.Franja> franjas = null;
        List<TarifasProfesionalData.Tarifa> tarifas = null;
        if (completo) {
            franjas = disponibilidadRepository.findByMedicoId(u.getId()).stream()
                    .map(InternoProfesionalesController::franja)
                    .toList();
            tarifas = tarifaRepository.findByMedicoId(u.getId()).stream()
                    .map(InternoProfesionalesController::tarifa)
                    .toList();
        }
        return new ProfesionalDto(u.getId(), u.getAggregateVersion(), u.getEmail(),
                u.getRol() != null ? u.getRol().name() : null, u.getProfession(),
                u.getNombre(), u.getApellido(), u.getTelefono(), u.getSexo(), u.getFechaNacimiento(), u.getCuil(),
                u.getTipoDocumento(), u.getNumeroDocumento(),
                completo ? u.getFotoUrl() : null, CopiaProfesionales.hashFoto(u.getFotoUrl()),
                u.getVerificadoAdmin(), u.tieneMercadoPagoVinculado(),
                sub != null && sub.getStatus() != null ? sub.getStatus().name() : null,
                sub != null ? sub.getCurrentPeriodEnd() : null,
                subscriptionService.hasFeature(u.getId(), "recetas_electronicas"),
                completo ? CopiaProfesionales.perfilDe(u) : null, franjas, tarifas);
    }

    private static DisponibilidadProfesionalData.Franja franja(Disponibilidad d) {
        return new DisponibilidadProfesionalData.Franja(d.getDiaSemana(), d.getHoraInicio().format(HORA),
                d.getHoraFin().format(HORA), d.getModalidad() != null ? d.getModalidad().name() : null);
    }

    private static TarifasProfesionalData.Tarifa tarifa(TarifaMedico t) {
        return new TarifasProfesionalData.Tarifa(t.getServicioId(), t.getLabel(), t.getPrecio(), t.isHabilitado(),
                t.isRequiereObraSocial(), t.getObraSocial(), t.getPrecioOnline(), t.getPrecioPresencial(),
                t.isRequiereAgenda(), t.isEsReceta());
    }
}
