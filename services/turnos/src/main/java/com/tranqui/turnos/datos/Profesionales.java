package com.tranqui.turnos.datos;

import com.tranqui.eventos.data.DisponibilidadProfesionalData.Franja;
import com.tranqui.eventos.data.PerfilProfesionalData;
import com.tranqui.eventos.data.TarifasProfesionalData.Tarifa;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Time;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Copia de la agenda de cada profesional: el perfil que usa la reserva (duración, intervalo, modalidades,
 * precio y domicilio), la disponibilidad y las tarifas. Viene del servicio de profesionales por eventos y,
 * cada tanto, del monolito (que tiene la copia de siempre, con o sin el servicio).
 */
@Repository
public class Profesionales {

    public record Agenda(Long id, Integer duracionTurnoMinutos, Integer intervaloEntreTurnosMinutos, boolean ofreceOnline,
                         boolean ofrecePresencial, BigDecimal precio, String domicilioAtencion, Double domicilioLat,
                         Double domicilioLng, String domicilioAtencionTorre, String domicilioAtencionPiso,
                         String domicilioAtencionDepto, String domicilioAtencionBarrio) {}

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final JdbcTemplate jdbc;
    private final RowMapper<Agenda> mapeo = (rs, i) -> new Agenda(rs.getLong("id"), (Integer) rs.getObject("duracion_turno_minutos"),
            (Integer) rs.getObject("intervalo_entre_turnos_minutos"), rs.getBoolean("ofrece_online"), rs.getBoolean("ofrece_presencial"),
            rs.getBigDecimal("precio"), rs.getString("domicilio_atencion"), (Double) rs.getObject("domicilio_lat"),
            (Double) rs.getObject("domicilio_lng"), rs.getString("domicilio_atencion_torre"), rs.getString("domicilio_atencion_piso"),
            rs.getString("domicilio_atencion_depto"), rs.getString("domicilio_atencion_barrio"));
    private final RowMapper<Franja> mapeoFranja = (rs, i) -> new Franja(rs.getInt("dia_semana"),
            rs.getObject("hora_inicio", LocalTime.class).format(HORA), rs.getObject("hora_fin", LocalTime.class).format(HORA),
            rs.getString("modalidad"));
    private final RowMapper<Tarifa> mapeoTarifa = (rs, i) -> new Tarifa(rs.getString("servicio_id"), rs.getString("label"),
            rs.getBigDecimal("precio"), rs.getBoolean("habilitado"), rs.getBoolean("requiere_obra_social"), rs.getString("obra_social"),
            rs.getBigDecimal("precio_online"), rs.getBigDecimal("precio_presencial"), rs.getBoolean("requiere_agenda"),
            rs.getBoolean("es_receta"));

    public Profesionales(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Agenda> porId(Long id) {
        return jdbc.query("SELECT * FROM profesional WHERE id = ?", mapeo, id).stream().findFirst();
    }

    @Transactional
    public void guardarPerfil(Long id, PerfilProfesionalData p) {
        Object[] datos = {p.duracionTurnoMinutos(), p.intervaloEntreTurnosMinutos(), p.ofreceOnline(), p.ofrecePresencial(), p.precio(),
                p.domicilioAtencion(), p.domicilioLat(), p.domicilioLng(), p.domicilioAtencionTorre(), p.domicilioAtencionPiso(),
                p.domicilioAtencionDepto(), p.domicilioAtencionBarrio(), id};
        int n = jdbc.update("UPDATE profesional SET duracion_turno_minutos = ?, intervalo_entre_turnos_minutos = ?, ofrece_online = ?, "
                + "ofrece_presencial = ?, precio = ?, domicilio_atencion = ?, domicilio_lat = ?, domicilio_lng = ?, "
                + "domicilio_atencion_torre = ?, domicilio_atencion_piso = ?, domicilio_atencion_depto = ?, "
                + "domicilio_atencion_barrio = ? WHERE id = ?", datos);
        if (n == 0) {
            jdbc.update("INSERT INTO profesional (duracion_turno_minutos, intervalo_entre_turnos_minutos, ofrece_online, ofrece_presencial, "
                    + "precio, domicilio_atencion, domicilio_lat, domicilio_lng, domicilio_atencion_torre, domicilio_atencion_piso, "
                    + "domicilio_atencion_depto, domicilio_atencion_barrio, id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", datos);
        }
    }

    /** Las franjas de una modalidad más las viejas sin modalidad, como {@code findByMedicoIdAndModalidadOLegacy}. */
    public List<Franja> franjas(Long id, String modalidad) {
        return jdbc.query("SELECT dia_semana, hora_inicio, hora_fin, modalidad FROM disponibilidad WHERE profesional_id = ? "
                + "AND (modalidad = ? OR modalidad IS NULL) ORDER BY id", mapeoFranja, id, modalidad);
    }

    @Transactional
    public void reemplazarDisponibilidad(Long id, List<Franja> franjas) {
        jdbc.update("DELETE FROM disponibilidad WHERE profesional_id = ?", id);
        for (Franja f : franjas) {
            jdbc.update("INSERT INTO disponibilidad (profesional_id, dia_semana, hora_inicio, hora_fin, modalidad) VALUES (?, ?, ?, ?, ?)",
                    id, f.diaSemana(), Time.valueOf(LocalTime.parse(f.horaInicio())), Time.valueOf(LocalTime.parse(f.horaFin())),
                    f.modalidad());
        }
    }

    public Optional<Tarifa> tarifa(Long id, String servicioId) {
        return jdbc.query("SELECT * FROM tarifa WHERE profesional_id = ? AND servicio_id = ?", mapeoTarifa, id, servicioId)
                .stream().findFirst();
    }

    @Transactional
    public void reemplazarTarifas(Long id, List<Tarifa> tarifas) {
        jdbc.update("DELETE FROM tarifa WHERE profesional_id = ?", id);
        for (Tarifa t : tarifas) {
            jdbc.update("INSERT INTO tarifa (profesional_id, servicio_id, label, precio, habilitado, requiere_obra_social, obra_social, "
                            + "precio_online, precio_presencial, requiere_agenda, es_receta) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, t.servicioId(), t.label(), t.precio(), t.habilitado(), t.requiereObraSocial(), t.obraSocial(),
                    t.precioOnline(), t.precioPresencial(), t.requiereAgenda(), t.esReceta());
        }
    }

    public long cantidad() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM profesional", Long.class);
        return n != null ? n : 0;
    }
}
