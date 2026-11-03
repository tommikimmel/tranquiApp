package com.tranqui.app.service;

import com.tranqui.app.model.EstadoAsistencia;
import com.tranqui.app.model.EstadoPago;
import com.tranqui.app.model.EstadoTurno;
import com.tranqui.app.model.Modalidad;
import com.tranqui.app.model.Pago;
import com.tranqui.app.model.TipoTurno;
import com.tranqui.app.model.Turno;
import com.tranqui.app.repository.PagoRepository;
import com.tranqui.app.repository.TurnoRepository;
import com.tranqui.app.repository.UsuarioRepository;
import com.tranqui.eventos.data.TurnoActualizadoData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Fase 6: la copia del monolito de los turnos que administra el servicio de turnos. Las estadísticas del
 * panel, la historia clínica y las recetas siguen leyendo {@code turno} (y el pago del turno); esto la
 * mantiene al día con {@code turnos.turno.actualizado} y con la resincronización. Conserva los ids del
 * servicio (que son los mismos que tenía el monolito). Aplicar la copia no publica nada.
 */
@Service
public class CopiaTurnos {

    private static final Logger log = LoggerFactory.getLogger(CopiaTurnos.class);

    private final TurnoRepository turnoRepository;
    private final PagoRepository pagoRepository;
    private final UsuarioRepository usuarioRepository;
    private final JdbcTemplate jdbc;

    public CopiaTurnos(TurnoRepository turnoRepository, PagoRepository pagoRepository, UsuarioRepository usuarioRepository,
                       JdbcTemplate jdbc) {
        this.turnoRepository = turnoRepository;
        this.pagoRepository = pagoRepository;
        this.usuarioRepository = usuarioRepository;
        this.jdbc = jdbc;
    }

    /**
     * Copia el turno y su pago. La versión del turno queda por encima de la del evento, así en una vuelta
     * atrás lo que vuelva a publicar el monolito no queda por debajo de lo que ya vieron los consumidores.
     */
    @Transactional
    public void aplicar(TurnoActualizadoData d, int version) {
        if (!usuarioRepository.existsById(d.pacienteId()) || !usuarioRepository.existsById(d.profesionalId())) {
            log.warn("turno.actualizado {} con un paciente o profesional que no existe en el monolito", d.turnoId());
            return;
        }
        if (!turnoRepository.existsById(d.turnoId())) {
            // Con el mismo id que en el servicio (JPA no deja elegirlo): la fila mínima, el resto abajo.
            jdbc.update("INSERT INTO turno (id, paciente_id, medico_id, fecha, hora_inicio, hora_fin, tipo, estado, asistencia, "
                            + "precio, fecha_creacion, version, ocupa_agenda, documento_enviado, es_receta, "
                            + "confirmacion_asistencia_email_enviado, asistencia_confirmada_paciente) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?)",
                    d.turnoId(), d.pacienteId(), d.profesionalId(), Date.valueOf(d.fecha()), Time.valueOf(d.horaInicio()),
                    Time.valueOf(d.horaFin()), d.tipo(), d.estado(), d.asistencia() != null ? d.asistencia() : "ESPERANDO",
                    d.precio(), Timestamp.valueOf(d.fechaCreacion() != null ? d.fechaCreacion() : LocalDateTime.now()),
                    d.ocupaAgenda(), d.documentoEnviado(), d.esReceta(), d.confirmacionAsistenciaEmailEnviado(),
                    d.asistenciaConfirmadaPaciente());
        }
        Turno t = turnoRepository.findById(d.turnoId()).orElseThrow();
        t.setPaciente(usuarioRepository.getReferenceById(d.pacienteId()));
        t.setMedico(usuarioRepository.getReferenceById(d.profesionalId()));
        t.setFecha(d.fecha());
        t.setHoraInicio(d.horaInicio());
        t.setHoraFin(d.horaFin());
        t.setTipo(TipoTurno.valueOf(d.tipo()));
        t.setModalidad(d.modalidad() != null ? Modalidad.valueOf(d.modalidad()) : null);
        t.setEstado(EstadoTurno.valueOf(d.estado()));
        t.setAsistencia(d.asistencia() != null ? EstadoAsistencia.valueOf(d.asistencia()) : EstadoAsistencia.ESPERANDO);
        t.setPrecio(d.precio());
        t.setMetadataAfiliado(d.metadataAfiliado());
        t.setServicioId(d.servicioId());
        t.setOcupaAgenda(d.ocupaAgenda());
        t.setIdFinanciador(d.idFinanciador());
        t.setTelemedicinaUrl(d.telemedicinaUrl());
        t.setGoogleEventId(d.googleEventId());
        t.setCheckoutUrl(d.checkoutUrl());
        t.setDocumentoEnviado(d.documentoEnviado());
        t.setEsReceta(d.esReceta());
        if (d.fechaCreacion() != null) {
            t.setFechaCreacion(d.fechaCreacion());
        }
        t.setConfirmacionAsistenciaEmailEnviado(d.confirmacionAsistenciaEmailEnviado());
        t.setAsistenciaConfirmadaPaciente(d.asistenciaConfirmadaPaciente());
        int actual = t.getVersion() != null ? t.getVersion() : 0;
        t.setVersion(Math.max(actual, version));
        t = turnoRepository.save(t);

        if (d.pagoEstado() != null) {
            Pago pago = t.getPago() != null ? t.getPago() : Pago.builder().turno(t).build();
            pago.setTransactionId(d.pagoTransactionId());
            pago.setEstado(EstadoPago.valueOf(d.pagoEstado()));
            pago.setMonto(d.pagoMonto() != null ? d.pagoMonto() : d.precio());
            pago.setFechaPago(d.pagoFecha());
            pagoRepository.save(pago);
        }
    }

    /** El turno del monolito como dato del evento (carga inicial del servicio). */
    public static TurnoActualizadoData datos(Turno t) {
        Pago p = t.getPago();
        return new TurnoActualizadoData(t.getId(), t.getPaciente().getId(), t.getMedico().getId(), t.getFecha(),
                t.getHoraInicio(), t.getHoraFin(), t.getTipo() != null ? t.getTipo().name() : null,
                t.getModalidad() != null ? t.getModalidad().name() : null, t.getEstado() != null ? t.getEstado().name() : null,
                t.getAsistencia() != null ? t.getAsistencia().name() : null, t.getPrecio(), t.getMetadataAfiliado(),
                t.getServicioId(), t.isOcupaAgenda(), t.getIdFinanciador(), t.getTelemedicinaUrl(), t.getGoogleEventId(),
                t.getCheckoutUrl(), t.isDocumentoEnviado(), t.isEsReceta(), t.getFechaCreacion(),
                t.isConfirmacionAsistenciaEmailEnviado(), t.isAsistenciaConfirmadaPaciente(),
                p != null ? p.getTransactionId() : null, p != null && p.getEstado() != null ? p.getEstado().name() : null,
                p != null ? p.getMonto() : null, p != null ? p.getFechaPago() : null);
    }
}
