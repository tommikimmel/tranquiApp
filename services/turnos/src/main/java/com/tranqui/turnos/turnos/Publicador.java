package com.tranqui.turnos.turnos;

import com.tranqui.eventos.Topicos;
import com.tranqui.eventos.data.AsistenciaConfirmadaData;
import com.tranqui.eventos.data.DocumentoEnviadoData;
import com.tranqui.eventos.data.DocumentoSolicitadoData;
import com.tranqui.eventos.data.TurnoActualizadoData;
import com.tranqui.eventos.data.TurnoCanceladoData;
import com.tranqui.eventos.data.TurnoConfirmadoData;
import com.tranqui.eventos.data.TurnoExpiradoData;
import com.tranqui.eventos.data.TurnoRecordatorioData;
import com.tranqui.eventos.data.TurnoReprogramadoData;
import com.tranqui.eventos.data.TurnoReservadoData;
import com.tranqui.eventos.outbox.OutboxPublisher;
import com.tranqui.turnos.datos.Turno;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * Los eventos de turnos, por la outbox y dentro de la transacción de cada cambio. Los mismos tópicos, datos y
 * tipos de agregado que publicaba el monolito (los consumidores descartan versiones viejas por tipo e id), con
 * la versión del turno. Cada cambio publica además {@code turno.actualizado} para la copia del monolito.
 */
@Component
public class Publicador {

    private final OutboxPublisher outbox;

    public Publicador(OutboxPublisher outbox) {
        this.outbox = outbox;
    }

    private static String id(Turno t) {
        return String.valueOf(t.id);
    }

    private static String hora(LocalTime h, String siNula) {
        return h != null ? h.toString() : siNula;
    }

    public void actualizado(Turno t) {
        outbox.publicar(Topicos.TURNOS_TURNO_ACTUALIZADO_V1, id(t), t.version, datos(t));
    }

    public void reservado(Turno t, String tituloCobro) {
        outbox.publicar(Topicos.TURNOS_TURNO_RESERVADO_V1, id(t), t.version, new TurnoReservadoData(t.id, t.pacienteId, t.medicoId,
                t.fecha, hora(t.horaInicio, "00:00"), t.precio, OffsetDateTime.now().plusMinutes(5), tituloCobro));
    }

    public void documentoSolicitado(Turno t, String tipoDocumento, String descripcion) {
        outbox.publicar(Topicos.TURNOS_DOCUMENTO_SOLICITADO_V1, id(t), t.version,
                new DocumentoSolicitadoData(t.id, t.pacienteId, t.medicoId, tipoDocumento, descripcion));
    }

    public void confirmado(Turno t, String tipoLabel, String tipoDocumentoLabel, String direccion) {
        outbox.publicar(Topicos.TURNOS_TURNO_CONFIRMADO_V1, id(t), t.version, new TurnoConfirmadoData(t.id, t.pacienteId, t.medicoId,
                t.fecha, hora(t.horaInicio, "00:00"), t.modalidad != null ? t.modalidad : "ONLINE", t.precio, t.ocupaAgenda, tipoLabel,
                tipoDocumentoLabel, direccion, t.telemedicinaUrl, TurnoConfirmadoData.ORIGEN_PAGO, hora(t.horaFin, null)));
    }

    public void cancelado(Turno t, String canceladoPor, String motivo, String resultadoReembolso) {
        boolean corresponde = "REEMBOLSADO".equals(resultadoReembolso) || "EN_PROCESO".equals(resultadoReembolso);
        outbox.publicar(Topicos.TURNOS_TURNO_CANCELADO_V1, id(t), t.version, new TurnoCanceladoData(t.id, t.pacienteId, t.medicoId,
                canceladoPor, motivo, corresponde, t.fecha, hora(t.horaInicio, null), t.ocupaAgenda, resultadoReembolso));
    }

    public void expirado(Turno t) {
        outbox.publicar(Topicos.TURNOS_TURNO_EXPIRADO_V1, id(t), t.version, new TurnoExpiradoData(t.id, t.pacienteId, t.medicoId));
    }

    public void reprogramado(Turno t, LocalDate fechaAnterior, LocalTime horaAnterior, String direccion) {
        outbox.publicar(Topicos.TURNOS_TURNO_REPROGRAMADO_V1, id(t), t.version, new TurnoReprogramadoData(t.id, t.pacienteId, t.medicoId,
                fechaAnterior, hora(horaAnterior, ""), t.fecha, t.horaInicio.toString(), t.modalidad, direccion, t.telemedicinaUrl,
                t.horaFin.toString()));
    }

    public void recordatorio(Turno t, String modalidadLabel, String tipoLabel, String direccion) {
        outbox.publicar(Topicos.TURNOS_RECORDATORIO_PROGRAMADO_V1, id(t), t.version, new TurnoRecordatorioData(t.id, t.pacienteId,
                t.medicoId, t.fecha, hora(t.horaInicio, "00:00"), modalidadLabel, tipoLabel, direccion));
    }

    public void documentoEnviado(Turno t, String tipoLabel, String origen) {
        // Como el monolito: el tipo de agregado es "turno", no el que sale del tópico.
        outbox.publicar(Topicos.TURNOS_DOCUMENTO_ENVIADO_V1, "turno", id(t), t.version,
                new DocumentoEnviadoData(t.id, t.pacienteId, t.medicoId, tipoLabel, origen));
    }

    public void asistenciaConfirmada(Turno t) {
        outbox.publicar(Topicos.TURNOS_ASISTENCIA_CONFIRMADA_V1, "turno", id(t), t.version,
                new AsistenciaConfirmadaData(t.id, t.pacienteId, t.medicoId, t.fecha, hora(t.horaInicio, null)));
    }

    /** El turno como dato de {@code turno.actualizado} y de la resincronización del monolito (sin los tokens). */
    public static TurnoActualizadoData datos(Turno t) {
        return new TurnoActualizadoData(t.id, t.pacienteId, t.medicoId, t.fecha, t.horaInicio, t.horaFin, t.tipo, t.modalidad,
                t.estado, t.asistencia, t.precio, t.metadataAfiliado, t.servicioId, t.ocupaAgenda, t.idFinanciador, t.telemedicinaUrl,
                t.googleEventId, t.checkoutUrl, t.documentoEnviado, t.esReceta, t.fechaCreacion, t.confirmacionAsistenciaEmailEnviado,
                t.asistenciaConfirmadaPaciente, t.pagoTransactionId, t.pagoEstado, t.pagoMonto, t.pagoFecha);
    }
}
