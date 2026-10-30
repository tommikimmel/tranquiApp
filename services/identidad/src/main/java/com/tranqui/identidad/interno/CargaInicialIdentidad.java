package com.tranqui.identidad.interno;

import com.tranqui.eventos.ValidadorEventos;
import com.tranqui.eventos.consumo.EsperaAntesDeConsumir;
import com.tranqui.identidad.datos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Carga inicial de identidad (Fase 9, I2a).
 * Copia del monolito usuarios, tickets, mensajes y novedades con sus ids, versiones,
 * hashes y fechas tal cual, validando exhaustivamente cada fila antes de insertar.
 *
 * <p>Corre únicamente con {@code identidad.carga.habilitada=true} y si las cuatro tablas
 * están vacías. Todo o nada en una sola transacción, y ajusta las secuencias arriba del máximo.
 */
@Component
public class CargaInicialIdentidad implements EsperaAntesDeConsumir {

    private static final Logger log = LoggerFactory.getLogger(CargaInicialIdentidad.class);
    static final int PAGINA = 500;

    public static final Set<String> CLAVES_USUARIO = Set.of(
            "id", "version", "email", "password", "rol", "fecha_registro", "email_verificado",
            "codigo_verificacion", "codigo_verificacion_expires_at", "reset_password_code",
            "reset_password_expires_at", "must_change_password", "terminos_aceptados_en",
            "perfil_completo", "cuenta_eliminada", "anonimizado_en", "notificaciones_email_habilitadas",
            "recibir_novedades", "nombre", "apellido", "telefono", "sexo", "fecha_nacimiento",
            "tipo_documento", "numero_documento", "dni", "cuil", "direccion", "foto_url",
            "domicilio_calle", "domicilio_numero", "domicilio_piso", "domicilio_dpto",
            "domicilio_codigo_postal", "domicilio_localidad", "domicilio_provincia", "domicilio_pais",
            "obra_social", "num_afiliado", "datos_ofuscado", "credencial_cod_entidad",
            "credencial_pan", "credencial_plan", "credencial_token", "verificado_admin",
            "profession", "license_type", "license_number", "license_jurisdiction",
            "license_document_url", "license_verified_at", "license_verified_by", "tax_id_type",
            "tax_id", "legal_name", "iva_condition_id", "fiscal_address"
    );

    public static final Set<String> CLAVES_TICKET = Set.of(
            "id", "version", "creador_id", "asunto", "estado", "fecha_creacion", "fecha_actualizacion"
    );

    public static final Set<String> CLAVES_TICKET_MENSAJE = Set.of(
            "id", "ticket_id", "autor_id", "contenido", "fecha_envio"
    );

    public static final Set<String> CLAVES_NOVEDADES_ENVIO = Set.of(
            "id", "titulo", "estado", "detalle", "creado_en", "finalizado_en",
            "enviado_pacientes", "enviado_profesionales"
    );

    private static final Set<String> ROLES_VALIDOS = Set.of("ADMIN", "PSIQUIATRA", "PACIENTE", "VISITADOR");
    private static final Set<String> ESTADOS_TICKET_VALIDOS = Set.of("PENDIENTE", "ACTIVO", "RESUELTO");
    private static final Set<String> ESTADOS_NOVEDADES_VALIDOS = Set.of("ENVIANDO", "ENVIADO", "ERROR");

    private final Usuarios usuarios;
    private final Tickets tickets;
    private final TicketMensajes ticketMensajes;
    private final NovedadesEnvios novedadesEnvios;
    private final ClienteInterno interno;
    private final TransactionTemplate tx;
    private final boolean habilitada;
    private volatile boolean terminada;

    public CargaInicialIdentidad(Usuarios usuarios,
                                 Tickets tickets,
                                 TicketMensajes ticketMensajes,
                                 NovedadesEnvios novedadesEnvios,
                                 ClienteInterno interno,
                                 TransactionTemplate tx,
                                 @Value("${identidad.carga.habilitada:false}") boolean habilitada) {
        this.usuarios = usuarios;
        this.tickets = tickets;
        this.ticketMensajes = ticketMensajes;
        this.novedadesEnvios = novedadesEnvios;
        this.interno = interno;
        this.tx = tx;
        this.habilitada = habilitada;
        this.terminada = !habilitada;
    }

    @Override
    public boolean listo() {
        return terminada;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        if (!habilitada) {
            return;
        }
        Thread hilo = new Thread(this::cargarConReintentos, "carga-inicial-identidad");
        hilo.setDaemon(true);
        hilo.start();
    }

    public void cargarConReintentos() {
        long esperaMs = 2_000;
        Boolean primerArranque = null;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (primerArranque == null) {
                    primerArranque = usuarios.cantidad() == 0
                            && tickets.cantidad() == 0
                            && ticketMensajes.cantidad() == 0
                            && novedadesEnvios.cantidad() == 0;
                }
                if (primerArranque) {
                    tx.executeWithoutResult(estado -> cargar());
                } else {
                    log.info("Carga inicial de identidad omitida: las tablas ya contienen datos");
                }
                terminada = true;
                return;
            } catch (RuntimeException e) {
                log.warn("Carga inicial de identidad fallida; se reintenta en {} ms: {}", esperaMs, e.getMessage());
                try {
                    Thread.sleep(esperaMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                esperaMs = Math.min(esperaMs * 2, 60_000);
            }
        }
    }

    public int cargar() {
        // 1. Obtener todos los datos del monolito
        List<ClienteInterno.UsuarioMonolito> rawUsuarios = new ArrayList<>();
        for (int pagina = 0; ; pagina++) {
            List<ClienteInterno.UsuarioMonolito> lote = interno.usuarios(pagina, PAGINA);
            if (lote == null || lote.isEmpty()) {
                break;
            }
            rawUsuarios.addAll(lote);
            if (ValidadorEventos.recibidas(lote) < PAGINA) {
                break;
            }
        }

        List<ClienteInterno.TicketMonolito> rawTickets = new ArrayList<>();
        for (int pagina = 0; ; pagina++) {
            List<ClienteInterno.TicketMonolito> lote = interno.tickets(pagina, PAGINA);
            if (lote == null || lote.isEmpty()) {
                break;
            }
            rawTickets.addAll(lote);
            if (ValidadorEventos.recibidas(lote) < PAGINA) {
                break;
            }
        }

        List<ClienteInterno.TicketMensajeMonolito> rawMensajes = new ArrayList<>();
        for (int pagina = 0; ; pagina++) {
            List<ClienteInterno.TicketMensajeMonolito> lote = interno.ticketMensajes(pagina, PAGINA);
            if (lote == null || lote.isEmpty()) {
                break;
            }
            rawMensajes.addAll(lote);
            if (ValidadorEventos.recibidas(lote) < PAGINA) {
                break;
            }
        }

        List<ClienteInterno.NovedadesEnvioMonolito> rawNovedades = new ArrayList<>();
        for (int pagina = 0; ; pagina++) {
            List<ClienteInterno.NovedadesEnvioMonolito> lote = interno.novedadesEnvios(pagina, PAGINA);
            if (lote == null || lote.isEmpty()) {
                break;
            }
            rawNovedades.addAll(lote);
            if (ValidadorEventos.recibidas(lote) < PAGINA) {
                break;
            }
        }

        // 2. Validar usuarios antes de insertar nada
        Set<Long> userIds = new HashSet<>();
        Map<String, Long> emailToId = new HashMap<>();
        List<Usuario> usuariosValidados = new ArrayList<>();

        for (ClienteInterno.UsuarioMonolito um : rawUsuarios) {
            Map<String, Object> map = um.valores();
            if (!CLAVES_USUARIO.equals(map.keySet())) {
                Object rawId = map.get("id");
                log.error("Contrato roto en usuario id {}: conjunto de claves recibido no coincide exactamente con el contrato", rawId);
                throw new IllegalStateException("Contrato roto en usuario id " + rawId + ": claves incorrectas");
            }

            Long id = aLong(map.get("id"));
            if (id == null || id <= 0) {
                log.error("Usuario con id inválido: {}", id);
                throw new IllegalStateException("Usuario con id inválido: " + id);
            }
            if (!userIds.add(id)) {
                log.error("Usuario con id duplicado: {}", id);
                throw new IllegalStateException("Usuario con id duplicado: " + id);
            }

            Integer ver = aInteger(map.get("version"));
            if (ver == null || ver < 0) {
                log.error("Usuario id {} con versión negativa o nula", id);
                throw new IllegalStateException("Usuario id " + id + " con versión inválida");
            }

            Object rawEmail = map.get("email");
            if (rawEmail == null || !(rawEmail instanceof String) || ((String) rawEmail).trim().isEmpty()) {
                log.error("Usuario id {} con email vacío", id);
                throw new IllegalStateException("Usuario id " + id + " con email vacío");
            }
            String emailNorm = ((String) rawEmail).trim().toLowerCase();
            Long idExistente = emailToId.putIfAbsent(emailNorm, id);
            if (idExistente != null) {
                log.error("Email duplicado detectado entre usuario id {} e id {}", idExistente, id);
                throw new IllegalStateException("Email duplicado entre usuario id " + idExistente + " e id " + id);
            }

            String rol = aString(map.get("rol"));
            if (rol == null || !ROLES_VALIDOS.contains(rol)) {
                log.error("Usuario id {} con rol inválido", id);
                throw new IllegalStateException("Usuario id " + id + " con rol inválido");
            }

            // Validar fechas
            LocalDateTime fechaRegistro;
            LocalDate fechaNacimiento;
            LocalDateTime codigoVerifExp;
            LocalDateTime resetPasswordExp;
            LocalDateTime terminosAceptados;
            LocalDateTime anonimizadoEn;
            LocalDateTime licenseVerifiedAt;
            try {
                fechaRegistro = aTimestamp(map.get("fecha_registro"));
                if (fechaRegistro == null) {
                    log.error("Usuario id {} con fecha_registro nula", id);
                    throw new IllegalStateException("Usuario id " + id + " con fecha_registro nula");
                }
                fechaNacimiento = aDate(map.get("fecha_nacimiento"));
                codigoVerifExp = aTimestamp(map.get("codigo_verificacion_expires_at"));
                resetPasswordExp = aTimestamp(map.get("reset_password_expires_at"));
                terminosAceptados = aTimestamp(map.get("terminos_aceptados_en"));
                anonimizadoEn = aTimestamp(map.get("anonimizado_en"));
                licenseVerifiedAt = aTimestamp(map.get("license_verified_at"));
            } catch (Exception e) {
                log.error("Usuario id {} con formato de fecha inválido", id);
                throw new IllegalStateException("Usuario id " + id + " con fecha inválida: " + e.getMessage());
            }

            Boolean emailVerificado = aBoolean(map.get("email_verificado"));
            Boolean mustChangePass = aBoolean(map.get("must_change_password"));
            Boolean perfilCompleto = aBoolean(map.get("perfil_completo"));
            Boolean cuentaEliminada = aBoolean(map.get("cuenta_eliminada"));
            Boolean notifEmailHab = aBoolean(map.get("notificaciones_email_habilitadas"));
            Boolean recibirNov = aBoolean(map.get("recibir_novedades"));
            Boolean verificadoAdmin = aBoolean(map.get("verificado_admin"));

            usuariosValidados.add(new Usuario(
                    id,
                    ver,
                    emailNorm,
                    aString(map.get("password")),
                    rol,
                    fechaRegistro,
                    emailVerificado != null ? emailVerificado : false,
                    aString(map.get("codigo_verificacion")),
                    codigoVerifExp,
                    aString(map.get("reset_password_code")),
                    resetPasswordExp,
                    mustChangePass != null && mustChangePass,
                    terminosAceptados,
                    perfilCompleto,
                    cuentaEliminada != null ? cuentaEliminada : false,
                    anonimizadoEn,
                    notifEmailHab != null ? notifEmailHab : true,
                    recibirNov != null ? recibirNov : true,
                    aString(map.get("nombre")),
                    aString(map.get("apellido")),
                    aString(map.get("telefono")),
                    aString(map.get("sexo")),
                    fechaNacimiento,
                    aString(map.get("tipo_documento")),
                    aInteger(map.get("numero_documento")),
                    aString(map.get("dni")),
                    aLong(map.get("cuil")),
                    aString(map.get("direccion")),
                    aString(map.get("foto_url")),
                    aString(map.get("domicilio_calle")),
                    aString(map.get("domicilio_numero")),
                    aString(map.get("domicilio_piso")),
                    aString(map.get("domicilio_dpto")),
                    aString(map.get("domicilio_codigo_postal")),
                    aString(map.get("domicilio_localidad")),
                    aString(map.get("domicilio_provincia")),
                    aString(map.get("domicilio_pais")),
                    aString(map.get("obra_social")),
                    aString(map.get("num_afiliado")),
                    aString(map.get("datos_ofuscado")),
                    aInteger(map.get("credencial_cod_entidad")),
                    aString(map.get("credencial_pan")),
                    aString(map.get("credencial_plan")),
                    aString(map.get("credencial_token")),
                    verificadoAdmin,
                    aString(map.get("profession")),
                    aString(map.get("license_type")),
                    aString(map.get("license_number")),
                    aString(map.get("license_jurisdiction")),
                    aString(map.get("license_document_url")),
                    licenseVerifiedAt,
                    aString(map.get("license_verified_by")),
                    aString(map.get("tax_id_type")),
                    aString(map.get("tax_id")),
                    aString(map.get("legal_name")),
                    aInteger(map.get("iva_condition_id")),
                    aString(map.get("fiscal_address"))
            ));
        }

        // 3. Validar tickets
        Set<Long> ticketIds = new HashSet<>();
        List<Ticket> ticketsValidados = new ArrayList<>();

        for (ClienteInterno.TicketMonolito tm : rawTickets) {
            Map<String, Object> map = tm.valores();
            if (!CLAVES_TICKET.equals(map.keySet())) {
                Object rawId = map.get("id");
                log.error("Contrato roto en ticket id {}: conjunto de claves recibido no coincide exactamente con el contrato", rawId);
                throw new IllegalStateException("Contrato roto en ticket id " + rawId + ": claves incorrectas");
            }

            Long id = aLong(map.get("id"));
            if (id == null || id <= 0) {
                log.error("Ticket con id inválido: {}", id);
                throw new IllegalStateException("Ticket con id inválido: " + id);
            }
            if (!ticketIds.add(id)) {
                log.error("Ticket con id duplicado: {}", id);
                throw new IllegalStateException("Ticket con id duplicado: " + id);
            }

            Integer ver = aInteger(map.get("version"));
            if (ver == null || ver < 0) {
                log.error("Ticket id {} con versión negativa o nula", id);
                throw new IllegalStateException("Ticket id " + id + " con versión inválida");
            }

            Long creadorId = aLong(map.get("creador_id"));
            if (creadorId == null || !userIds.contains(creadorId)) {
                log.error("Ticket id {} referencia a creador_id {} inexistente", id, creadorId);
                throw new IllegalStateException("Ticket id " + id + " referencia a creador_id inexistente: " + creadorId);
            }

            String asunto = aString(map.get("asunto"));
            if (asunto == null || asunto.isBlank()) {
                log.error("Ticket id {} con asunto vacío", id);
                throw new IllegalStateException("Ticket id " + id + " con asunto vacío");
            }

            String estado = aString(map.get("estado"));
            if (estado == null || !ESTADOS_TICKET_VALIDOS.contains(estado)) {
                log.error("Ticket id {} con estado inválido", id);
                throw new IllegalStateException("Ticket id " + id + " con estado inválido");
            }

            LocalDateTime fechaCreacion;
            LocalDateTime fechaActualizacion;
            try {
                fechaCreacion = aTimestamp(map.get("fecha_creacion"));
                fechaActualizacion = aTimestamp(map.get("fecha_actualizacion"));
                if (fechaCreacion == null || fechaActualizacion == null) {
                    throw new IllegalArgumentException("fechas requeridas");
                }
            } catch (Exception e) {
                log.error("Ticket id {} con fecha inválida", id);
                throw new IllegalStateException("Ticket id " + id + " con fecha inválida: " + e.getMessage());
            }

            ticketsValidados.add(new Ticket(id, ver, creadorId, asunto, estado, fechaCreacion, fechaActualizacion));
        }

        // 4. Validar ticket_mensajes
        Set<Long> mensajeIds = new HashSet<>();
        List<TicketMensaje> mensajesValidados = new ArrayList<>();

        for (ClienteInterno.TicketMensajeMonolito mm : rawMensajes) {
            Map<String, Object> map = mm.valores();
            if (!CLAVES_TICKET_MENSAJE.equals(map.keySet())) {
                Object rawId = map.get("id");
                log.error("Contrato roto en mensaje id {}: conjunto de claves recibido no coincide exactamente con el contrato", rawId);
                throw new IllegalStateException("Contrato roto en mensaje id " + rawId + ": claves incorrectas");
            }

            Long id = aLong(map.get("id"));
            if (id == null || id <= 0) {
                log.error("TicketMensaje con id inválido: {}", id);
                throw new IllegalStateException("TicketMensaje con id inválido: " + id);
            }
            if (!mensajeIds.add(id)) {
                log.error("TicketMensaje con id duplicado: {}", id);
                throw new IllegalStateException("TicketMensaje con id duplicado: " + id);
            }

            Long ticketId = aLong(map.get("ticket_id"));
            if (ticketId == null || !ticketIds.contains(ticketId)) {
                log.error("TicketMensaje id {} referencia a ticket_id {} inexistente", id, ticketId);
                throw new IllegalStateException("TicketMensaje id " + id + " referencia a ticket_id inexistente: " + ticketId);
            }

            Long autorId = aLong(map.get("autor_id"));
            if (autorId == null || !userIds.contains(autorId)) {
                log.error("TicketMensaje id {} referencia a autor_id {} inexistente", id, autorId);
                throw new IllegalStateException("TicketMensaje id " + id + " referencia a autor_id inexistente: " + autorId);
            }

            String contenido = aString(map.get("contenido"));
            if (contenido == null) {
                log.error("TicketMensaje id {} con contenido nulo", id);
                throw new IllegalStateException("TicketMensaje id " + id + " con contenido nulo");
            }

            LocalDateTime fechaEnvio;
            try {
                fechaEnvio = aTimestamp(map.get("fecha_envio"));
                if (fechaEnvio == null) {
                    throw new IllegalArgumentException("fecha_envio requerida");
                }
            } catch (Exception e) {
                log.error("TicketMensaje id {} con fecha inválida", id);
                throw new IllegalStateException("TicketMensaje id " + id + " con fecha inválida: " + e.getMessage());
            }

            mensajesValidados.add(new TicketMensaje(id, ticketId, autorId, contenido, fechaEnvio));
        }

        // 5. Validar novedades_envios
        Set<String> novedadesIds = new HashSet<>();
        List<NovedadesEnvio> novedadesValidadas = new ArrayList<>();

        for (ClienteInterno.NovedadesEnvioMonolito nem : rawNovedades) {
            Map<String, Object> map = nem.valores();
            if (!CLAVES_NOVEDADES_ENVIO.equals(map.keySet())) {
                Object rawId = map.get("id");
                log.error("Contrato roto en novedades_envio id {}: conjunto de claves recibido no coincide con el contrato", rawId);
                throw new IllegalStateException("Contrato roto en novedades_envio id " + rawId + ": claves incorrectas");
            }

            String id = aString(map.get("id"));
            if (id == null || id.isBlank()) {
                log.error("NovedadesEnvio con id vacío");
                throw new IllegalStateException("NovedadesEnvio con id vacío");
            }
            if (!novedadesIds.add(id)) {
                log.error("NovedadesEnvio con id duplicado: {}", id);
                throw new IllegalStateException("NovedadesEnvio con id duplicado: " + id);
            }

            String titulo = aString(map.get("titulo"));
            if (titulo == null || titulo.isBlank()) {
                log.error("NovedadesEnvio id {} con título vacío", id);
                throw new IllegalStateException("NovedadesEnvio id " + id + " con título vacío");
            }

            String estado = aString(map.get("estado"));
            if (estado == null || !ESTADOS_NOVEDADES_VALIDOS.contains(estado)) {
                log.error("NovedadesEnvio id {} con estado inválido", id);
                throw new IllegalStateException("NovedadesEnvio id " + id + " con estado inválido");
            }

            LocalDateTime creadoEn;
            LocalDateTime finalizadoEn;
            try {
                creadoEn = aTimestamp(map.get("creado_en"));
                if (creadoEn == null) {
                    throw new IllegalArgumentException("creado_en requerido");
                }
                finalizadoEn = aTimestamp(map.get("finalizado_en"));
            } catch (Exception e) {
                log.error("NovedadesEnvio id {} con fecha inválida", id);
                throw new IllegalStateException("NovedadesEnvio id " + id + " con fecha inválida: " + e.getMessage());
            }

            Boolean envPacientes = aBoolean(map.get("enviado_pacientes"));
            Boolean envProfesionales = aBoolean(map.get("enviado_profesionales"));

            novedadesValidadas.add(new NovedadesEnvio(
                    id,
                    titulo,
                    estado,
                    aString(map.get("detalle")),
                    creadoEn,
                    finalizadoEn,
                    envPacientes != null && envPacientes,
                    envProfesionales != null && envProfesionales
            ));
        }

        // 6. Si todo validó correctamente, insertar en orden
        for (Usuario u : usuariosValidados) {
            usuarios.insertar(u);
        }
        usuarios.ajustarSecuencia();

        for (Ticket t : ticketsValidados) {
            tickets.insertar(t);
        }
        tickets.ajustarSecuencia();

        for (TicketMensaje m : mensajesValidados) {
            ticketMensajes.insertar(m);
        }
        ticketMensajes.ajustarSecuencia();

        for (NovedadesEnvio n : novedadesValidadas) {
            novedadesEnvios.insertar(n);
        }

        log.info("Carga inicial de identidad completada: {} usuarios, {} tickets, {} mensajes y {} envíos de novedades",
                usuariosValidados.size(), ticketsValidados.size(), mensajesValidados.size(), novedadesValidadas.size());

        return usuariosValidados.size() + ticketsValidados.size() + mensajesValidados.size() + novedadesValidadas.size();
    }

    private static LocalDateTime aTimestamp(Object val) {
        if (val == null) return null;
        if (val instanceof LocalDateTime ldt) return ldt;
        if (val instanceof String s) {
            if (s.isBlank()) return null;
            return LocalDateTime.parse(s.trim());
        }
        throw new IllegalArgumentException("Tipo de timestamp no soportado: " + val.getClass());
    }

    private static LocalDate aDate(Object val) {
        if (val == null) return null;
        if (val instanceof LocalDate ld) return ld;
        if (val instanceof String s) {
            if (s.isBlank()) return null;
            return LocalDate.parse(s.trim());
        }
        throw new IllegalArgumentException("Tipo de fecha no soportado: " + val.getClass());
    }

    private static Long aLong(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.longValue();
        if (val instanceof String s) {
            if (s.isBlank()) return null;
            return Long.parseLong(s.trim());
        }
        throw new IllegalArgumentException("Tipo de Long no soportado: " + val.getClass());
    }

    private static Integer aInteger(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            if (s.isBlank()) return null;
            return Integer.parseInt(s.trim());
        }
        throw new IllegalArgumentException("Tipo de Integer no soportado: " + val.getClass());
    }

    private static Boolean aBoolean(Object val) {
        if (val == null) return null;
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) {
            if (s.isBlank()) return null;
            return Boolean.parseBoolean(s.trim());
        }
        throw new IllegalArgumentException("Tipo de Boolean no soportado: " + val.getClass());
    }

    private static String aString(Object val) {
        if (val == null) return null;
        return val.toString();
    }
}
