package com.tranqui.calendario.agenda;

import com.tranqui.calendario.datos.CuentasGoogle;
import com.tranqui.calendario.datos.Participantes;
import com.tranqui.calendario.datos.TurnosAgendados;
import com.tranqui.calendario.google.GoogleCalendarApi;
import com.tranqui.calendario.google.TokensGoogle;
import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.MeetCreadoData;
import com.tranqui.eventos.outbox.OutboxPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Random;

/**
 * Los eventos de Google de los turnos confirmados (antes, las llamadas a GoogleCalendarService dentro
 * de TurnoService y PagoWebhookHandler del monolito). Cada evento de Google o Meet nuevo se avisa por
 * {@code calendario.meet.creado.v1}: el monolito guarda el link en el turno y notificaciones le manda
 * el mail al paciente ({@code linkNuevo}).
 *
 * <p>Un fallo de Google nunca rompe nada: el turno queda sin evento y se reintenta cada 5 minutos
 * ({@link #exportarPendientes}), como hacía el monolito.
 */
@Service
public class AgendaTurnos {

    private static final Logger log = LoggerFactory.getLogger(AgendaTurnos.class);
    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    private final TurnosAgendados turnos;
    private final CuentasGoogle cuentas;
    private final Participantes participantes;
    private final TokensGoogle tokens;
    private final GoogleCalendarApi google;
    private final OutboxPublisher outbox;
    private final boolean googleHabilitado;
    private final Random azar = new Random();

    public AgendaTurnos(TurnosAgendados turnos, CuentasGoogle cuentas, Participantes participantes, TokensGoogle tokens,
                        GoogleCalendarApi google, OutboxPublisher outbox,
                        @Value("${google.calendar.enabled:false}") boolean googleHabilitado) {
        this.turnos = turnos;
        this.cuentas = cuentas;
        this.participantes = participantes;
        this.tokens = tokens;
        this.google = google;
        this.outbox = outbox;
        this.googleHabilitado = googleHabilitado;
    }

    /** Un turno confirmado que ocupa agenda: se guarda y se le crea el evento (y el Meet si es online). */
    @Transactional
    public void confirmado(Long turnoId, Long profesionalId, Long pacienteId, LocalDate fecha, LocalTime horaInicio,
                           LocalTime horaFin, String modalidad) {
        turnos.guardar(turnoId, profesionalId, pacienteId, fecha, horaInicio, horaFin, modalidad, null, null);
        crearEvento(turnoId);
    }

    @Transactional
    public void crearEvento(Long turnoId) {
        TurnosAgendados.Turno turno = turnos.buscar(turnoId).orElse(null);
        if (turno == null || turno.googleEventId() != null) {
            return;
        }
        if (!googleHabilitado) {
            // Entorno local, como el monolito: sin Google, los turnos online llevan un Meet simulado.
            if (turno.esOnline() && turno.meetUrl() == null) {
                String meet = meetSimulado();
                int version = turnos.guardarEvento(turnoId, null, meet);
                publicarMeet(turno, meet, null, true, version);
            }
            return;
        }
        CuentasGoogle.Cuenta cuenta = cuentas.buscar(turno.profesionalId()).orElse(null);
        if (cuenta == null) {
            return;
        }
        String accessToken = tokens.accessToken(cuenta);
        if (accessToken == null) {
            log.warn("Sin access token de Google para el profesional {}: el turno {} se reintenta en la próxima pasada",
                    turno.profesionalId(), turnoId);
            return;
        }
        try {
            String paciente = participantes.porId(turno.pacienteId()).map(Participantes.Participante::nombre)
                    .filter(n -> n != null && !n.isBlank()).orElse("Paciente");
            boolean online = turno.esOnline();
            GoogleCalendarApi.EventoCreado creado = google.crear(accessToken, cuenta.calendario(),
                    "[Tranqui App] Consulta " + (online ? "Online" : "Presencial") + " - " + paciente,
                    "Consulta " + (online ? "de telemedicina" : "presencial") + " con Tranqui App",
                    inicio(turno), fin(turno), online);
            if (creado.eventId() == null) {
                return;
            }
            String meet = online ? creado.meetUrl() : null;
            int version = turnos.guardarEvento(turnoId, creado.eventId(), meet);
            boolean linkNuevo = meet != null && !meet.isBlank() && !meet.equals(turno.meetUrl());
            publicarMeet(turno, meet, creado.eventId(), linkNuevo, version);
            log.info("Evento de Google creado para el turno {} (evento {})", turnoId, creado.eventId());
        } catch (Exception e) {
            // No se propaga: un fallo de Google nunca rompe la confirmación; se reintenta en 5 minutos.
            log.error("No se pudo crear el evento de Google del turno {}: {}", turnoId, e.getMessage());
        }
    }

    @Transactional
    public void reprogramado(Long turnoId, LocalDate fecha, LocalTime horaInicio, LocalTime horaFinNueva) {
        TurnosAgendados.Turno turno = turnos.buscar(turnoId).orElse(null);
        if (turno == null) {
            return;
        }
        // Sin hora de fin nueva (productor viejo), se conserva la duración que tenía.
        LocalTime horaFin = horaFinNueva != null ? horaFinNueva
                : horaInicio.plus(java.time.Duration.between(turno.horaInicio(), turno.fin()));
        turnos.mover(turnoId, fecha, horaInicio, horaFin);
        if (!googleHabilitado || turno.googleEventId() == null) {
            return;
        }
        CuentasGoogle.Cuenta cuenta = cuentas.buscar(turno.profesionalId()).orElse(null);
        String accessToken = tokens.accessToken(cuenta);
        if (accessToken == null) {
            return;
        }
        try {
            google.mover(accessToken, cuenta.calendario(), turno.googleEventId(),
                    ZonedDateTime.of(fecha, horaInicio, ZONA), ZonedDateTime.of(fecha, horaFin, ZONA));
        } catch (Exception e) {
            log.error("No se pudo mover el evento de Google del turno {}: {}", turnoId, e.getMessage());
        }
    }

    @Transactional
    public void cancelado(Long turnoId) {
        TurnosAgendados.Turno turno = turnos.buscar(turnoId).orElse(null);
        if (turno == null) {
            return;
        }
        if (googleHabilitado && turno.googleEventId() != null) {
            borrarDeGoogle(turno);
        }
        turnos.borrar(turnoId);
    }

    /** Los turnos del profesional que quedaron sin evento (Google caído, cuenta recién conectada). */
    @Transactional
    public void exportarPendientes(Long profesionalId) {
        if (!googleHabilitado) {
            return;
        }
        for (TurnosAgendados.Turno turno : turnos.sinEvento(profesionalId)) {
            crearEvento(turno.turnoId());
        }
    }

    /**
     * Al desvincular: borra de Google los eventos que creó la app (nunca los propios del profesional).
     * Tiene que correr antes de borrar los tokens. Un fallo con uno no frena el resto.
     */
    @Transactional
    public void eliminarEventosDeLaApp(Long profesionalId) {
        for (TurnosAgendados.Turno turno : turnos.conEvento(profesionalId)) {
            if (googleHabilitado) {
                borrarDeGoogle(turno);
            }
            turnos.quitarEvento(turno.turnoId());
        }
    }

    private void borrarDeGoogle(TurnosAgendados.Turno turno) {
        CuentasGoogle.Cuenta cuenta = cuentas.buscar(turno.profesionalId()).orElse(null);
        String accessToken = tokens.accessToken(cuenta);
        if (accessToken == null) {
            return;
        }
        try {
            google.borrar(accessToken, cuenta.calendario(), turno.googleEventId());
        } catch (Exception e) {
            log.error("No se pudo borrar el evento de Google del turno {}: {}", turno.turnoId(), e.getMessage());
        }
    }

    private void publicarMeet(TurnosAgendados.Turno turno, String meet, String eventId, boolean linkNuevo, int version) {
        outbox.publicar(Topicos.CALENDARIO_MEET_CREADO_V1, "turno", String.valueOf(turno.turnoId()), version,
                new MeetCreadoData(turno.turnoId(), meet != null ? meet : "", eventId, turno.pacienteId(),
                        turno.profesionalId(), turno.fecha(), turno.horaInicio().toString(), linkNuevo));
    }

    private static ZonedDateTime inicio(TurnosAgendados.Turno turno) {
        return ZonedDateTime.of(turno.fecha(), turno.horaInicio(), ZONA);
    }

    private static ZonedDateTime fin(TurnosAgendados.Turno turno) {
        return ZonedDateTime.of(turno.fecha(), turno.fin(), ZONA);
    }

    private String meetSimulado() {
        StringBuilder sb = new StringBuilder("https://meet.google.com/");
        for (int grupo : new int[]{3, 4, 3}) {
            if (sb.charAt(sb.length() - 1) != '/') {
                sb.append('-');
            }
            for (int i = 0; i < grupo; i++) {
                sb.append((char) ('a' + azar.nextInt(26)));
            }
        }
        return sb.toString();
    }
}
