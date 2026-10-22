package com.tranqui.chat.interno;

import com.tranqui.chat.copias.Copias;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.List;

/**
 * Primer arranque del chat (paso 1 de "Migración de datos en cada fase"): copia del monolito los
 * participantes, los turnos confirmados de hoy en adelante y todos los mensajes, conservando los ids,
 * y deja la secuencia de mensajes después del último. Es idempotente y reintenta con espera
 * creciente si el monolito todavía no está listo. Solo corre si la copia de participantes está vacía.
 */
@Component
public class CargaInicial {

    private static final Logger log = LoggerFactory.getLogger(CargaInicial.class);
    private static final int PAGINA = 500;

    private final Copias copias;
    private final ClienteInterno interno;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final boolean habilitada;

    public CargaInicial(Copias copias, ClienteInterno interno, JdbcTemplate jdbc, TransactionTemplate tx,
                        @Value("${chat.carga-inicial.habilitada:true}") boolean habilitada) {
        this.copias = copias;
        this.interno = interno;
        this.jdbc = jdbc;
        this.tx = tx;
        this.habilitada = habilitada;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        if (!habilitada) {
            return;
        }
        Thread hilo = new Thread(this::cargarConReintentos, "carga-inicial-chat");
        hilo.setDaemon(true);
        hilo.start();
    }

    void cargarConReintentos() {
        long esperaMs = 2_000;
        Boolean primerArranque = null;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (primerArranque == null) {
                    primerArranque = copias.cantidadParticipantes() == 0;
                }
                if (!primerArranque) {
                    return;
                }
                int participantes = copiarParticipantes();
                int turnos = copiarTurnos();
                int mensajes = copiarMensajes();
                log.info("Carga inicial del chat: {} participantes, {} turnos confirmados y {} mensajes",
                        participantes, turnos, mensajes);
                return;
            } catch (RuntimeException e) {
                log.warn("Carga inicial del chat fallida; se reintenta en {} ms: {}", esperaMs, e.getMessage());
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

    int copiarParticipantes() {
        int total = 0;
        for (int pagina = 0; ; pagina++) {
            List<ClienteInterno.Contacto> lote = interno.contactos(pagina, PAGINA);
            for (ClienteInterno.Contacto c : lote) {
                copias.guardarParticipante(c.id(), c.email(), c.nombre(), c.apellido(), c.rol());
            }
            total += lote.size();
            if (lote.size() < PAGINA) {
                return total;
            }
        }
    }

    int copiarTurnos() {
        List<ClienteInterno.TurnoConfirmado> turnos = interno.turnosConfirmados();
        for (ClienteInterno.TurnoConfirmado t : turnos) {
            copias.guardarTurnoConfirmado(t.turnoId(), t.pacienteId(), t.profesionalId(), t.fecha(), t.horaInicio());
        }
        return turnos.size();
    }

    /** Copia por lotes en orden de id (retoma desde el último copiado si se cortó) y ajusta la secuencia. */
    int copiarMensajes() {
        int total = 0;
        Long ultimo = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM mensaje", Long.class);
        long desde = ultimo != null ? ultimo : 0;
        while (true) {
            List<ClienteInterno.MensajeMonolito> lote = interno.mensajes(desde, PAGINA);
            if (lote.isEmpty()) {
                break;
            }
            tx.executeWithoutResult(s -> {
                for (ClienteInterno.MensajeMonolito m : lote) {
                    jdbc.update("INSERT INTO mensaje (id, remitente_id, destinatario_id, contenido, leido, fecha_envio) "
                                    + "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING",
                            m.id(), m.remitenteId(), m.destinatarioId(), m.contenido(), m.leido(),
                            m.fechaEnvio() != null ? Timestamp.valueOf(m.fechaEnvio()) : new Timestamp(System.currentTimeMillis()));
                }
            });
            total += lote.size();
            desde = lote.get(lote.size() - 1).id();
            if (lote.size() < PAGINA) {
                break;
            }
        }
        // Los mensajes nuevos del servicio siguen después de los copiados (y no chocan con sus ids).
        jdbc.queryForObject("SELECT setval(pg_get_serial_sequence('mensaje', 'id'), GREATEST((SELECT COALESCE(MAX(id), 0) "
                + "FROM mensaje), 1), (SELECT COUNT(*) > 0 FROM mensaje))", Long.class);
        return total;
    }
}
