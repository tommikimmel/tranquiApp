package com.tranqui.turnos.agenda;

import com.tranqui.eventos.data.DisponibilidadProfesionalData.Franja;
import com.tranqui.turnos.datos.Profesionales;
import com.tranqui.turnos.datos.Turno;
import com.tranqui.turnos.datos.Turnos;
import com.tranqui.turnos.interno.ClienteInterno;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Los horarios libres de un profesional: {@code TurnoService#obtenerHorariosDisponibles} y
 * {@code AgendaService#calcularBloquesDisponibles} del monolito, sin cambios de lógica.
 */
@Service
public class Horarios {

    private static final Logger log = LoggerFactory.getLogger(Horarios.class);
    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Cordoba");
    static final int DURACION_POR_DEFECTO = 45;
    // La misma caché corta que tenía el monolito para los horarios ocupados de Google (CacheConfig).
    private static final long CACHE_MS = 90_000;

    private record EnCache(long hasta, List<ClienteInterno.Intervalo> intervalos) {}

    private final Turnos turnos;
    private final Profesionales profesionales;
    private final ClienteInterno interno;
    private final Map<String, EnCache> ocupados = new ConcurrentHashMap<>();

    public Horarios(Turnos turnos, Profesionales profesionales, ClienteInterno interno) {
        this.turnos = turnos;
        this.profesionales = profesionales;
        this.interno = interno;
    }

    public List<LocalTime> disponibles(Long medicoId, LocalDate fecha, String modalidad) {
        List<Franja> disponibilidad = profesionales.franjas(medicoId, modalidad);
        // Los turnos del día sin filtrar por modalidad a propósito: un profesional no puede estar en dos
        // consultas a la vez, así que un turno presencial también bloquea ese horario online (y al revés).
        LocalDateTime limiteCincoMin = LocalDateTime.now().minusMinutes(5);
        List<Turno> existentes = turnos.delDia(medicoId, fecha).stream()
                .filter(t -> {
                    if (!t.ocupaAgenda) return false;
                    if (Turno.EXPIRADO.equals(t.estado)) return false;
                    if (Turno.PENDIENTE_PAGO.equals(t.estado)) {
                        return t.fechaCreacion != null && t.fechaCreacion.isAfter(limiteCincoMin);
                    }
                    return true;
                })
                .toList();

        Profesionales.Agenda agenda = profesionales.porId(medicoId).orElse(null);
        int duracion = agenda != null && agenda.duracionTurnoMinutos() != null ? agenda.duracionTurnoMinutos() : DURACION_POR_DEFECTO;
        int intervalo = agenda != null && agenda.intervaloEntreTurnosMinutos() != null ? agenda.intervaloEntreTurnosMinutos() : 0;
        List<LocalTime> locales = bloques(disponibilidad, existentes, fecha, duracion, intervalo);
        if (agenda == null) {
            return locales;
        }

        // Los horarios ocupados del Google Calendar del profesional (sus eventos propios, no turnos).
        List<ClienteInterno.Intervalo> ocupadosGoogle = ocupados(medicoId, fecha);
        if (ocupadosGoogle.isEmpty()) {
            return locales;
        }
        if (ocupadosGoogle.stream().anyMatch(ClienteInterno.Intervalo::todoElDia)) {
            return Collections.emptyList();
        }
        return locales.stream()
                .filter(hora -> {
                    LocalTime fin = hora.plusMinutes(duracion);
                    return ocupadosGoogle.stream().noneMatch(o -> hora.isBefore(o.fin()) && fin.isAfter(o.inicio()));
                })
                .toList();
    }

    /** El conteo de la home pública para varios profesionales; sin modalidad, la unión de las que ofrece. */
    public Map<Long, Integer> conteos(List<Long> medicoIds, LocalDate fecha, String modalidad) {
        Map<Long, Integer> conteos = new LinkedHashMap<>();
        for (Long medicoId : medicoIds) {
            try {
                if (modalidad != null) {
                    conteos.put(medicoId, disponibles(medicoId, fecha, modalidad).size());
                    continue;
                }
                Profesionales.Agenda agenda = profesionales.porId(medicoId).orElse(null);
                if (agenda == null) {
                    conteos.put(medicoId, 0);
                    continue;
                }
                Set<LocalTime> union = new LinkedHashSet<>();
                if (agenda.ofrecePresencial()) {
                    union.addAll(disponibles(medicoId, fecha, "PRESENCIAL"));
                }
                if (agenda.ofreceOnline()) {
                    union.addAll(disponibles(medicoId, fecha, "ONLINE"));
                }
                conteos.put(medicoId, union.size());
            } catch (Exception e) {
                conteos.put(medicoId, 0);
            }
        }
        return conteos;
    }

    /**
     * {@code AgendaService#calcularBloquesDisponibles}: recorre cada franja del día de la semana con paso
     * duración + intervalo; un bloque entra si cabe su duración (sin exigir el intervalo después del último),
     * no está en el pasado (hoy) y no choca con un turno.
     */
    static List<LocalTime> bloques(List<Franja> disponibilidad, List<Turno> existentes, LocalDate fecha, int duracion, int intervalo) {
        List<LocalTime> bloques = new ArrayList<>();
        int dia = fecha.getDayOfWeek().getValue();
        int paso = duracion + intervalo;
        LocalDate hoy = LocalDate.now(ZONA);
        LocalTime ahora = LocalTime.now(ZONA);
        for (Franja franja : disponibilidad) {
            if (franja.diaSemana() != dia) {
                continue;
            }
            LocalTime inicio = LocalTime.parse(franja.horaInicio());
            LocalTime fin = LocalTime.parse(franja.horaFin());
            while (!inicio.plusMinutes(duracion).isAfter(fin)) {
                boolean enElPasado = fecha.equals(hoy) && inicio.isBefore(ahora);
                LocalTime desde = inicio;
                LocalTime hasta = inicio.plusMinutes(duracion);
                boolean ocupado = existentes.stream().anyMatch(t -> desde.isBefore(t.horaFin) && hasta.isAfter(t.horaInicio));
                if (!ocupado && !enElPasado) {
                    bloques.add(inicio);
                }
                LocalTime siguiente = inicio.plusMinutes(paso);
                if (!siguiente.isAfter(inicio)) {
                    break; // pasó la medianoche
                }
                inicio = siguiente;
            }
        }
        bloques.sort(Comparator.naturalOrder());
        return bloques;
    }

    private List<ClienteInterno.Intervalo> ocupados(Long medicoId, LocalDate fecha) {
        String clave = medicoId + "-" + fecha;
        long ahora = System.currentTimeMillis();
        EnCache guardado = ocupados.get(clave);
        if (guardado != null && guardado.hasta() > ahora) {
            return guardado.intervalos();
        }
        try {
            List<ClienteInterno.Intervalo> intervalos = interno.ocupados(medicoId, fecha);
            ocupados.entrySet().removeIf(e -> e.getValue().hasta() <= ahora);
            ocupados.put(clave, new EnCache(ahora + CACHE_MS, intervalos));
            return intervalos;
        } catch (RuntimeException e) {
            // Como el monolito: si el calendario no responde, la agenda se ofrece sin ese filtro.
            log.warn("El calendario no respondió los horarios ocupados del profesional {} el {}: {}", medicoId, fecha, e.getMessage());
            return Collections.emptyList();
        }
    }
}
