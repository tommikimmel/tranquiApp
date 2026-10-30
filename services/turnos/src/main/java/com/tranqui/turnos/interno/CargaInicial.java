package com.tranqui.turnos.interno;

import com.tranqui.eventos.ValidadorEventos;
import com.tranqui.eventos.consumo.EsperaAntesDeConsumir;
import com.tranqui.eventos.data.TurnoActualizadoData;
import com.tranqui.turnos.datos.Personas;
import com.tranqui.turnos.datos.Turno;
import com.tranqui.turnos.datos.Turnos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Primer arranque de turnos (paso 1 de "Migración de datos en cada fase"), con la app en mantenimiento
 * (decisión de la spec): copia del monolito los usuarios, la agenda de los profesionales y todos los turnos
 * con sus ids, sus versiones y los tokens de los links de los mails, y pone la secuencia después del último.
 * Todo o nada, reintenta con espera creciente y solo corre con la base vacía.
 *
 * <p>El consumo de eventos, el vencimiento del bloqueo y los recordatorios esperan a que termine.
 */
@Component
public class CargaInicial implements EsperaAntesDeConsumir {

    private static final Logger log = LoggerFactory.getLogger(CargaInicial.class);
    static final int PAGINA = 500;

    private final Copias copias;
    private final Turnos turnos;
    private final Personas personas;
    private final ClienteInterno interno;
    private final TransactionTemplate tx;
    private final boolean habilitada;
    private volatile boolean terminada;

    public CargaInicial(Copias copias, Turnos turnos, Personas personas, ClienteInterno interno, TransactionTemplate tx,
                        @Value("${turnos.carga-inicial.habilitada:true}") boolean habilitada) {
        this.copias = copias;
        this.turnos = turnos;
        this.personas = personas;
        this.interno = interno;
        this.tx = tx;
        this.habilitada = habilitada;
        // Sin carga inicial (tests) no hay nada que esperar.
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
        Thread hilo = new Thread(this::cargarConReintentos, "carga-inicial-turnos");
        hilo.setDaemon(true);
        hilo.start();
    }

    void cargarConReintentos() {
        long esperaMs = 2_000;
        Boolean primerArranque = null;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (primerArranque == null) {
                    primerArranque = turnos.cantidad() == 0 && personas.cantidad() == 0;
                }
                if (primerArranque) {
                    // Todo o nada: si se corta a la mitad, el próximo intento arranca de cero con la base vacía.
                    tx.executeWithoutResult(estado -> cargar());
                }
                terminada = true;
                return;
            } catch (RuntimeException e) {
                log.warn("Carga inicial de turnos fallida; se reintenta en {} ms: {}", esperaMs, e.getMessage());
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

    public void cargar() {
        int usuarios = copias.copiarPersonas();
        int profesionales = copias.copiarProfesionales();
        int total = 0;
        for (int pagina = 0; ; pagina++) {
            List<ClienteInterno.TurnoMonolito> lote = interno.turnos(pagina, PAGINA);
            for (ClienteInterno.TurnoMonolito m : lote) {
                turnos.copiar(turno(m));
            }
            total += lote.size();
            if (ValidadorEventos.recibidas(lote) < PAGINA) {
                break;
            }
        }
        turnos.ajustarSecuencia();
        log.info("Carga inicial de turnos: {} usuarios, {} profesionales y {} turnos", usuarios, profesionales, total);
    }

    static Turno turno(ClienteInterno.TurnoMonolito m) {
        TurnoActualizadoData d = m.turno();
        Turno t = new Turno();
        t.id = d.turnoId();
        t.version = m.version();
        t.pacienteId = d.pacienteId();
        t.medicoId = d.profesionalId();
        t.fecha = d.fecha();
        t.horaInicio = d.horaInicio();
        t.horaFin = d.horaFin();
        t.tipo = d.tipo();
        t.modalidad = d.modalidad();
        t.estado = d.estado();
        t.asistencia = d.asistencia() != null ? d.asistencia() : "ESPERANDO";
        t.precio = d.precio();
        t.metadataAfiliado = d.metadataAfiliado();
        t.servicioId = d.servicioId();
        t.ocupaAgenda = d.ocupaAgenda();
        t.idFinanciador = d.idFinanciador();
        t.telemedicinaUrl = d.telemedicinaUrl();
        t.googleEventId = d.googleEventId();
        t.checkoutUrl = d.checkoutUrl();
        t.documentoEnviado = d.documentoEnviado();
        t.esReceta = d.esReceta();
        if (d.fechaCreacion() != null) {
            t.fechaCreacion = d.fechaCreacion();
        }
        t.confirmacionAsistenciaEmailEnviado = d.confirmacionAsistenciaEmailEnviado();
        t.asistenciaConfirmadaPaciente = d.asistenciaConfirmadaPaciente();
        t.tokenConfirmacionAsistencia = m.tokenConfirmacionAsistencia();
        t.tokenReserva = m.tokenReserva();
        t.pagoTransactionId = d.pagoTransactionId();
        t.pagoEstado = d.pagoEstado();
        t.pagoMonto = d.pagoMonto();
        t.pagoFecha = d.pagoFecha();
        return t;
    }
}
