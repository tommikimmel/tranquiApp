package com.tranqui.identidad.novedades;

import com.tranqui.identidad.datos.NovedadesEnvio;
import com.tranqui.identidad.datos.NovedadesEnvios;
import com.tranqui.identidad.datos.Usuario;
import com.tranqui.identidad.datos.Usuarios;
import com.tranqui.identidad.novedades.NovedadesContenido.Publico;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mail de novedades al terminar un mantenimiento (ver docs/novedades.md), portado del monolito en la Fase 9 con la
 * misma lógica, sobre las tablas de identidad.
 *
 * <p>En producción usa los Broadcasts de Resend: antes de enviar sincroniza los contactos con los usuarios (crea los
 * que faltan y los pone en el segmento de su rol), trae las bajas hechas desde el link del mail y manda un broadcast
 * por segmento. La baja la gestiona Resend (Ley 25.326) y la sincronización nunca reactiva a nadie. En local
 * ({@code mail.transport=smtp}) o sin API key, manda un mail individual a cada destinatario.
 *
 * <p>Idempotente por id: un mismo envío nunca se repite; si falló a mitad, reintentarlo solo manda los segmentos que
 * faltaron.
 */
@Service
public class NovedadesService {

    private static final Logger log = LoggerFactory.getLogger(NovedadesService.class);

    static final String ENVIANDO = "ENVIANDO";
    static final String ENVIADO = "ENVIADO";
    static final String ERROR = "ERROR";

    private final Usuarios usuarios;
    private final NovedadesEnvios envios;
    private final ResendAudienciaClient resend;
    private final MailNovedades mail;
    private final ExecutorService ejecutor;

    @Value("${mail.transport:resend}")
    private String transporte;

    @Value("${novedades.segmento-pacientes:Tranqui App - Pacientes}")
    private String segmentoPacientes;

    @Value("${novedades.segmento-profesionales:Tranqui App - Profesionales}")
    private String segmentoProfesionales;

    @Autowired
    public NovedadesService(Usuarios usuarios, NovedadesEnvios envios, ResendAudienciaClient resend, MailNovedades mail) {
        this(usuarios, envios, resend, mail, Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "novedades");
            t.setDaemon(true);
            return t;
        }));
    }

    // Para tests: permite ejecutar el envío en el mismo hilo.
    NovedadesService(Usuarios usuarios, NovedadesEnvios envios, ResendAudienciaClient resend, MailNovedades mail,
                     ExecutorService ejecutor) {
        this.usuarios = usuarios;
        this.envios = envios;
        this.resend = resend;
        this.mail = mail;
        this.ejecutor = ejecutor;
    }

    public record Resultado(String id, String estado, String detalle, boolean yaExistia) {}

    // ================================================================================
    // Envío
    // ================================================================================

    /** Registra el envío y lo procesa en segundo plano. Si el id ya se envió, no hace nada. */
    public synchronized Resultado enviar(NovedadesContenido contenido) {
        validar(contenido);
        NovedadesEnvio envio = envios.buscarPorId(contenido.id()).orElse(null);
        if (envio != null && !ERROR.equals(envio.estado())) {
            return new Resultado(envio.id(), envio.estado(), envio.detalle(), true);
        }
        if (envio == null) {
            envios.insertar(new NovedadesEnvio(contenido.id(), contenido.titulo(), ENVIANDO, null, LocalDateTime.now(),
                    null, false, false));
        } else {
            envios.actualizar(new NovedadesEnvio(envio.id(), envio.titulo(), ENVIANDO, null, envio.creadoEn(),
                    envio.finalizadoEn(), envio.enviadoPacientes(), envio.enviadoProfesionales()));
        }
        ejecutor.submit(() -> procesar(contenido));
        return new Resultado(contenido.id(), ENVIANDO, null, false);
    }

    public Resultado estado(String id) {
        return envios.buscarPorId(id)
                .map(e -> new Resultado(e.id(), e.estado(), e.detalle(), true))
                .orElse(null);
    }

    /** Manda el mail solo a los administradores, una versión por público, para revisarlo antes. */
    public int enviarPrueba(NovedadesContenido contenido) {
        validar(contenido);
        List<Usuario> admins = usuarios.buscarPorRol("ADMIN").stream()
                .filter(u -> !u.isCuentaEliminada() && u.email() != null).toList();
        int enviados = 0;
        for (Publico publico : Publico.values()) {
            if (!contenido.tieneContenidoPara(publico)) continue;
            String html = mail.htmlNovedades(contenido.titulo(), contenido.itemsPara(publico), mail.urlMiCuenta());
            for (Usuario admin : admins) {
                mail.enviarHtml(admin.email(), "[Prueba " + etiqueta(publico) + "] " + contenido.titulo(), html);
                enviados++;
            }
        }
        return enviados;
    }

    void procesar(NovedadesContenido contenido) {
        Marcas marcas = new Marcas(envios.buscarPorId(contenido.id()).orElseThrow());
        String estado;
        String detalle;
        try {
            detalle = usarBroadcast() ? enviarPorBroadcast(contenido, marcas) : enviarIndividual(contenido, marcas);
            estado = ENVIADO;
            log.info("Novedades '{}' enviadas: {}", contenido.id(), detalle);
        } catch (Exception e) {
            log.error("Error enviando las novedades '{}'", contenido.id(), e);
            estado = ERROR;
            detalle = "Error: " + e.getMessage();
        }
        guardar(marcas, estado, detalle, LocalDateTime.now());
    }

    private String enviarPorBroadcast(NovedadesContenido contenido, Marcas marcas) {
        String segP = resend.asegurarSegmento(segmentoPacientes);
        String segR = resend.asegurarSegmento(segmentoProfesionales);
        String sincronizacion = sincronizarContactos(segP, segR);

        List<String> partes = new ArrayList<>(List.of(sincronizacion));
        for (Publico publico : Publico.values()) {
            boolean yaEnviado = publico == Publico.PACIENTES ? marcas.pacientes : marcas.profesionales;
            if (yaEnviado || !contenido.tieneContenidoPara(publico)) continue;
            String html = mail.htmlNovedades(contenido.titulo(), contenido.itemsPara(publico), "{{{RESEND_UNSUBSCRIBE_URL}}}");
            String id = resend.enviarBroadcast(publico == Publico.PACIENTES ? segP : segR,
                    mail.remitente(), contenido.titulo(), html, contenido.id() + " - " + etiqueta(publico));
            if (publico == Publico.PACIENTES) marcas.pacientes = true;
            else marcas.profesionales = true;
            // Se guarda cada segmento enviado: si el siguiente falla, el reintento no repite este.
            guardar(marcas, ENVIANDO, null, null);
            partes.add("broadcast " + etiqueta(publico) + ": " + id);
        }
        return String.join("; ", partes);
    }

    private String enviarIndividual(NovedadesContenido contenido, Marcas marcas) {
        int pacientes = 0, profesionales = 0;
        for (Usuario u : destinatarios()) {
            if (!u.isRecibirNovedades()) continue;
            Publico publico = publicoDe(u);
            boolean yaEnviado = publico == Publico.PACIENTES ? marcas.pacientes : marcas.profesionales;
            if (yaEnviado || !contenido.tieneContenidoPara(publico)) continue;
            mail.enviarHtml(u.email(), contenido.titulo(),
                    mail.htmlNovedades(contenido.titulo(), contenido.itemsPara(publico), mail.urlMiCuenta()));
            if (publico == Publico.PACIENTES) pacientes++; else profesionales++;
        }
        marcas.pacientes = true;
        marcas.profesionales = true;
        return "envío individual: " + pacientes + " pacientes, " + profesionales + " profesionales";
    }

    /**
     * Deja los contactos de Resend alineados con los usuarios: crea los que faltan (si aceptan novedades), los ubica en
     * el segmento de su rol y trae a la app las bajas hechas desde el mail. Nunca reactiva a quien se dio de baja.
     */
    String sincronizarContactos(String segP, String segR) {
        int creados = 0, bajas = 0, errores = 0;
        for (Usuario u : destinatarios()) {
            try {
                String propio = publicoDe(u) == Publico.PACIENTES ? segP : segR;
                String otro = propio.equals(segP) ? segR : segP;
                var contacto = resend.buscarContacto(u.email());
                if (contacto.isEmpty()) {
                    if (u.isRecibirNovedades()) {
                        resend.crearContacto(u.email(), u.nombre(), u.apellido(), propio);
                        creados++;
                    }
                    continue;
                }
                if (contacto.get().unsubscribed() && u.isRecibirNovedades()) {
                    usuarios.actualizar(u.toBuilder().recibirNovedades(false).build());
                    bajas++;
                }
                Set<String> segmentos = resend.segmentosDe(u.email());
                if (!segmentos.contains(propio)) resend.agregarASegmento(u.email(), propio);
                if (segmentos.contains(otro)) resend.quitarDeSegmento(u.email(), otro);
            } catch (Exception e) {
                errores++;
                log.warn("No se pudo sincronizar el contacto de novedades del usuario {}: {}", u.id(), e.getMessage());
            }
        }
        return "contactos: " + creados + " creados, " + bajas + " bajas traídas de Resend, " + errores + " errores";
    }

    // ================================================================================
    // Preferencia del usuario (Mi Cuenta)
    // ================================================================================

    /** Estado actual; en producción lo lee de Resend (incluye bajas hechas desde el mail). */
    public boolean recibeNovedades(Usuario u) {
        boolean recibe = u.isRecibirNovedades();
        if (usarBroadcast() && u.email() != null) {
            try {
                var contacto = resend.buscarContacto(u.email());
                if (contacto.isPresent() && contacto.get().unsubscribed() == recibe) {
                    recibe = !contacto.get().unsubscribed();
                    usuarios.actualizar(u.toBuilder().recibirNovedades(recibe).build());
                }
            } catch (Exception e) {
                log.warn("No se pudo leer de Resend la suscripción a novedades del usuario {}: {}", u.id(), e.getMessage());
            }
        }
        return recibe;
    }

    public void cambiarSuscripcion(Usuario u, boolean recibir) {
        Usuario actualizado = usuarios.actualizar(u.toBuilder().recibirNovedades(recibir).build());
        if (!usarBroadcast() || actualizado.email() == null) return;
        var contacto = resend.buscarContacto(actualizado.email());
        if (contacto.isPresent()) {
            resend.actualizarSuscripcion(actualizado.email(), recibir);
        } else if (recibir && esDestinatario(actualizado)) {
            String seg = resend.asegurarSegmento(publicoDe(actualizado) == Publico.PACIENTES ? segmentoPacientes : segmentoProfesionales);
            resend.crearContacto(actualizado.email(), actualizado.nombre(), actualizado.apellido(), seg);
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

    private void guardar(Marcas marcas, String estado, String detalle, LocalDateTime finalizadoEn) {
        NovedadesEnvio actual = marcas.envio;
        envios.actualizar(new NovedadesEnvio(actual.id(), actual.titulo(), estado, detalle, actual.creadoEn(),
                finalizadoEn, marcas.pacientes, marcas.profesionales));
    }

    private List<Usuario> destinatarios() {
        List<Usuario> todos = new ArrayList<>(usuarios.buscarPorRol("PACIENTE"));
        todos.addAll(usuarios.buscarPorRol("PSIQUIATRA"));
        return todos.stream().filter(this::esDestinatario).toList();
    }

    private boolean esDestinatario(Usuario u) {
        return ("PACIENTE".equals(u.rol()) || "PSIQUIATRA".equals(u.rol()))
                && u.email() != null && !u.email().isBlank()
                && Boolean.TRUE.equals(u.emailVerificado())
                && !u.isCuentaEliminada();
    }

    private static Publico publicoDe(Usuario u) {
        return "PSIQUIATRA".equals(u.rol()) ? Publico.PROFESIONALES : Publico.PACIENTES;
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

    /** Qué segmentos ya se mandaron en este envío (la fila es inmutable: se va guardando con cada avance). */
    private static final class Marcas {
        private final NovedadesEnvio envio;
        private boolean pacientes;
        private boolean profesionales;

        private Marcas(NovedadesEnvio envio) {
            this.envio = envio;
            this.pacientes = envio.enviadoPacientes();
            this.profesionales = envio.enviadoProfesionales();
        }
    }
}
