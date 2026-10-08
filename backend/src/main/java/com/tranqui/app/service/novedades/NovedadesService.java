package com.tranqui.app.service.novedades;

import com.tranqui.app.model.NovedadesEnvio;
import com.tranqui.app.model.Rol;
import com.tranqui.app.model.Usuario;
import com.tranqui.app.repository.NovedadesEnvioRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.app.service.ResendEmailService;
import com.tranqui.app.service.novedades.NovedadesContenido.Publico;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mail de novedades al terminar un mantenimiento (ver docs/novedades.md).
 *
 * En producción usa los Broadcasts de Resend: antes de enviar sincroniza los contactos con los
 * usuarios de la app (crea los que faltan y los pone en el segmento de su rol), trae las bajas
 * hechas desde el link del mail y manda un broadcast por segmento. La baja la gestiona Resend
 * (link {{{RESEND_UNSUBSCRIBE_URL}}}, Ley 25.326) y la sincronización nunca reactiva a nadie.
 *
 * En el entorno local (mail.transport=smtp) o sin API key, manda un mail individual a cada
 * destinatario por el canal común (Mailpit en local).
 *
 * Idempotente por id: un mismo envío nunca se repite; si falló a mitad, reintentarlo solo manda
 * los segmentos que faltaron.
 */
@Service
@Slf4j
public class NovedadesService {

    private final UsuarioRepository usuarioRepository;
    private final NovedadesEnvioRepository envioRepository;
    private final ResendAudienciaClient resend;
    private final ResendEmailService mail;
    private final ExecutorService ejecutor;

    @Value("${mail.transport:resend}")
    private String transporte;

    @Value("${resend.from-email:onboarding@resend.dev}")
    private String fromEmail;

    @Value("${novedades.segmento-pacientes:Tranqui App - Pacientes}")
    private String segmentoPacientes;

    @Value("${novedades.segmento-profesionales:Tranqui App - Profesionales}")
    private String segmentoProfesionales;

    @org.springframework.beans.factory.annotation.Autowired
    public NovedadesService(UsuarioRepository usuarioRepository, NovedadesEnvioRepository envioRepository,
                            ResendAudienciaClient resend, ResendEmailService mail) {
        this(usuarioRepository, envioRepository, resend, mail, Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "novedades");
            t.setDaemon(true);
            return t;
        }));
    }

    // Para tests: permite ejecutar el envío en el mismo hilo.
    NovedadesService(UsuarioRepository usuarioRepository, NovedadesEnvioRepository envioRepository,
                     ResendAudienciaClient resend, ResendEmailService mail, ExecutorService ejecutor) {
        this.usuarioRepository = usuarioRepository;
        this.envioRepository = envioRepository;
        this.resend = resend;
        this.mail = mail;
        this.ejecutor = ejecutor;
    }

    public record Resultado(String id, NovedadesEnvio.Estado estado, String detalle, boolean yaExistia) {}

    // ================================================================================
    // Envío
    // ================================================================================

    /** Registra el envío y lo procesa en segundo plano. Si el id ya se envió, no hace nada. */
    public synchronized Resultado enviar(NovedadesContenido contenido) {
        validar(contenido);
        NovedadesEnvio envio = envioRepository.findById(contenido.id()).orElse(null);
        if (envio != null && envio.getEstado() != NovedadesEnvio.Estado.ERROR) {
            return new Resultado(envio.getId(), envio.getEstado(), envio.getDetalle(), true);
        }
        if (envio == null) {
            envio = NovedadesEnvio.builder().id(contenido.id()).titulo(contenido.titulo()).build();
        }
        envio.setEstado(NovedadesEnvio.Estado.ENVIANDO);
        envio.setDetalle(null);
        envioRepository.save(envio);
        ejecutor.submit(() -> procesar(contenido));
        return new Resultado(envio.getId(), NovedadesEnvio.Estado.ENVIANDO, null, false);
    }

    public Resultado estado(String id) {
        return envioRepository.findById(id)
                .map(e -> new Resultado(e.getId(), e.getEstado(), e.getDetalle(), true))
                .orElse(null);
    }

    /** Manda el mail solo a los administradores, una versión por público, para revisarlo antes. */
    public int enviarPrueba(NovedadesContenido contenido) {
        validar(contenido);
        List<Usuario> admins = usuarioRepository.findByRol(Rol.ADMIN).stream()
                .filter(u -> !Boolean.TRUE.equals(u.getCuentaEliminada()) && u.getEmail() != null).toList();
        int enviados = 0;
        for (Publico publico : Publico.values()) {
            if (!contenido.tieneContenidoPara(publico)) continue;
            String html = mail.htmlNovedades(contenido.titulo(), contenido.itemsPara(publico), mail.urlMiCuenta());
            for (Usuario admin : admins) {
                mail.enviarHtml(admin.getEmail(), "[Prueba " + etiqueta(publico) + "] " + contenido.titulo(), html);
                enviados++;
            }
        }
        return enviados;
    }

    void procesar(NovedadesContenido contenido) {
        NovedadesEnvio envio = envioRepository.findById(contenido.id()).orElseThrow();
        try {
            String detalle = usarBroadcast() ? enviarPorBroadcast(contenido, envio) : enviarIndividual(contenido, envio);
            envio.setEstado(NovedadesEnvio.Estado.ENVIADO);
            envio.setDetalle(detalle);
            log.info("Novedades '{}' enviadas: {}", contenido.id(), detalle);
        } catch (Exception e) {
            log.error("Error enviando las novedades '{}'", contenido.id(), e);
            envio.setEstado(NovedadesEnvio.Estado.ERROR);
            envio.setDetalle("Error: " + e.getMessage());
        }
        envio.setFinalizadoEn(LocalDateTime.now());
        envioRepository.save(envio);
    }

    private String enviarPorBroadcast(NovedadesContenido contenido, NovedadesEnvio envio) {
        String segP = resend.asegurarSegmento(segmentoPacientes);
        String segR = resend.asegurarSegmento(segmentoProfesionales);
        String sincronizacion = sincronizarContactos(segP, segR);

        List<String> partes = new ArrayList<>(List.of(sincronizacion));
        for (Publico publico : Publico.values()) {
            boolean yaEnviado = publico == Publico.PACIENTES ? envio.isEnviadoPacientes() : envio.isEnviadoProfesionales();
            if (yaEnviado || !contenido.tieneContenidoPara(publico)) continue;
            String html = mail.htmlNovedades(contenido.titulo(), contenido.itemsPara(publico), "{{{RESEND_UNSUBSCRIBE_URL}}}");
            String id = resend.enviarBroadcast(publico == Publico.PACIENTES ? segP : segR,
                    "Tranqui App <" + fromEmail + ">", contenido.titulo(), html, contenido.id() + " - " + etiqueta(publico));
            if (publico == Publico.PACIENTES) envio.setEnviadoPacientes(true);
            else envio.setEnviadoProfesionales(true);
            envioRepository.save(envio);
            partes.add("broadcast " + etiqueta(publico) + ": " + id);
        }
        return String.join("; ", partes);
    }

    private String enviarIndividual(NovedadesContenido contenido, NovedadesEnvio envio) {
        int pacientes = 0, profesionales = 0;
        for (Usuario u : destinatarios()) {
            if (!u.isRecibirNovedades()) continue;
            Publico publico = publicoDe(u);
            boolean yaEnviado = publico == Publico.PACIENTES ? envio.isEnviadoPacientes() : envio.isEnviadoProfesionales();
            if (yaEnviado || !contenido.tieneContenidoPara(publico)) continue;
            mail.enviarHtml(u.getEmail(), contenido.titulo(),
                    mail.htmlNovedades(contenido.titulo(), contenido.itemsPara(publico), mail.urlMiCuenta()));
            if (publico == Publico.PACIENTES) pacientes++; else profesionales++;
        }
        envio.setEnviadoPacientes(true);
        envio.setEnviadoProfesionales(true);
        return "envío individual: " + pacientes + " pacientes, " + profesionales + " profesionales";
    }

    /**
     * Deja los contactos de Resend alineados con los usuarios: crea los que faltan (si aceptan
     * novedades), los ubica en el segmento de su rol y trae a la app las bajas hechas desde el mail.
     * Nunca reactiva a quien se dio de baja.
     */
    String sincronizarContactos(String segP, String segR) {
        int creados = 0, bajas = 0, errores = 0;
        for (Usuario u : destinatarios()) {
            try {
                String propio = publicoDe(u) == Publico.PACIENTES ? segP : segR;
                String otro = propio.equals(segP) ? segR : segP;
                var contacto = resend.buscarContacto(u.getEmail());
                if (contacto.isEmpty()) {
                    if (u.isRecibirNovedades()) {
                        resend.crearContacto(u.getEmail(), u.getNombre(), u.getApellido(), propio);
                        creados++;
                    }
                    continue;
                }
                if (contacto.get().unsubscribed() && u.isRecibirNovedades()) {
                    u.setRecibirNovedades(false);
                    usuarioRepository.save(u);
                    bajas++;
                }
                Set<String> segmentos = resend.segmentosDe(u.getEmail());
                if (!segmentos.contains(propio)) resend.agregarASegmento(u.getEmail(), propio);
                if (segmentos.contains(otro)) resend.quitarDeSegmento(u.getEmail(), otro);
            } catch (Exception e) {
                errores++;
                log.warn("No se pudo sincronizar el contacto de novedades del usuario {}: {}", u.getId(), e.getMessage());
            }
        }
        return "contactos: " + creados + " creados, " + bajas + " bajas traídas de Resend, " + errores + " errores";
    }

    // ================================================================================
    // Preferencia del usuario (Mi Cuenta / Configuración)
    // ================================================================================

    /** Estado actual; en producción lo lee de Resend (incluye bajas hechas desde el mail). */
    public boolean recibeNovedades(Usuario u) {
        if (usarBroadcast() && u.getEmail() != null) {
            try {
                var contacto = resend.buscarContacto(u.getEmail());
                if (contacto.isPresent() && contacto.get().unsubscribed() == u.isRecibirNovedades()) {
                    u.setRecibirNovedades(!contacto.get().unsubscribed());
                    usuarioRepository.save(u);
                }
            } catch (Exception e) {
                log.warn("No se pudo leer de Resend la suscripción a novedades del usuario {}: {}", u.getId(), e.getMessage());
            }
        }
        return u.isRecibirNovedades();
    }

    public void cambiarSuscripcion(Usuario u, boolean recibir) {
        u.setRecibirNovedades(recibir);
        usuarioRepository.save(u);
        if (!usarBroadcast() || u.getEmail() == null) return;
        var contacto = resend.buscarContacto(u.getEmail());
        if (contacto.isPresent()) {
            resend.actualizarSuscripcion(u.getEmail(), recibir);
        } else if (recibir && esDestinatario(u)) {
            String seg = resend.asegurarSegmento(publicoDe(u) == Publico.PACIENTES ? segmentoPacientes : segmentoProfesionales);
            resend.crearContacto(u.getEmail(), u.getNombre(), u.getApellido(), seg);
        }
    }

    /** Al eliminar una cuenta: borra el contacto en Resend (Ley 25.326). No falla si no puede. */
    public void eliminarContacto(String email) {
        if (!usarBroadcast() || email == null) return;
        try {
            resend.borrarContacto(email);
        } catch (Exception e) {
            log.warn("No se pudo borrar el contacto de novedades al eliminar una cuenta: {}", e.getMessage());
        }
    }

    // ================================================================================

    boolean usarBroadcast() {
        return !"smtp".equalsIgnoreCase(transporte) && resend.configurado();
    }

    private List<Usuario> destinatarios() {
        List<Usuario> todos = new ArrayList<>(usuarioRepository.findByRol(Rol.PACIENTE));
        todos.addAll(usuarioRepository.findByRol(Rol.PSIQUIATRA));
        return todos.stream().filter(this::esDestinatario).toList();
    }

    private boolean esDestinatario(Usuario u) {
        return (u.getRol() == Rol.PACIENTE || u.getRol() == Rol.PSIQUIATRA)
                && u.getEmail() != null && !u.getEmail().isBlank()
                && Boolean.TRUE.equals(u.getEmailVerificado())
                && !Boolean.TRUE.equals(u.getCuentaEliminada());
    }

    private static Publico publicoDe(Usuario u) {
        return u.getRol() == Rol.PSIQUIATRA ? Publico.PROFESIONALES : Publico.PACIENTES;
    }

    private static String etiqueta(Publico p) {
        return p == Publico.PACIENTES ? "pacientes" : "profesionales";
    }

    private static void validar(NovedadesContenido c) {
        if (c == null || c.id() == null || !c.id().matches("[A-Za-z0-9._-]{3,100}")) {
            throw new IllegalArgumentException("El id del envío es obligatorio (letras, números, punto, guion; 3 a 100 caracteres).");
        }
        if (c.titulo() == null || c.titulo().isBlank()) {
            throw new IllegalArgumentException("El título es obligatorio.");
        }
        if (!c.tieneContenidoPara(Publico.PACIENTES) && !c.tieneContenidoPara(Publico.PROFESIONALES)) {
            throw new IllegalArgumentException("Las novedades no tienen ninguna viñeta.");
        }
    }
}
