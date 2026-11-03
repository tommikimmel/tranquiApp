package com.tranqui.profesionales.interno;

import com.tranqui.eventos.data.TurnoActualizadoData;
import com.tranqui.profesionales.datos.Personas;
import com.tranqui.profesionales.datos.TurnosAtendidos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

/**
 * Las copias que no son del servicio: los usuarios (identidad, en el monolito) y los turnos atendidos.
 * Se copian en la carga inicial y se mantienen al día con los eventos.
 * Además, los turnos se resincronizan periódicamente cada 15 minutos por si el monolito no publica eventos
 * durante una vuelta atrás de la Fase 6.
 */
@Component
public class Copias {

    private static final Logger log = LoggerFactory.getLogger(Copias.class);
    static final int PAGINA = 200;

    private final ClienteInterno interno;
    private final Personas personas;
    private final TurnosAtendidos turnosAtendidos;
    private final TransactionTemplate tx;
    private final ObjectProvider<CargaInicial> cargaInicial;

    public Copias(ClienteInterno interno, Personas personas, TurnosAtendidos turnosAtendidos,
                  TransactionTemplate tx, ObjectProvider<CargaInicial> cargaInicial) {
        this.interno = interno;
        this.personas = personas;
        this.turnosAtendidos = turnosAtendidos;
        this.tx = tx;
        this.cargaInicial = cargaInicial;
    }

    /** Trae del monolito a un usuario (alta o cambio de identidad). Si el monolito ya no lo tiene, se deja como está. */
    public void refrescarPersona(Long id) {
        ClienteInterno.PacienteMonolito p;
        try {
            p = interno.paciente(id);
        } catch (HttpClientErrorException.NotFound e) {
            return;
        }
        if (p != null) {
            personas.guardar(p.persona());
        }
    }

    /** Todos los usuarios, paginados (carga inicial). */
    public int copiarPersonas() {
        int total = 0;
        for (int pagina = 0; ; pagina++) {
            List<ClienteInterno.PacienteMonolito> lote = interno.pacientes(pagina, PAGINA);
            for (ClienteInterno.PacienteMonolito p : lote) {
                personas.guardar(p.persona());
            }
            total += lote.size();
            if (lote.size() < PAGINA) {
                return total;
            }
        }
    }

    /** Todos los turnos, paginados (carga inicial y resincronización). */
    public int copiarTurnos() {
        int total = 0;
        for (int pagina = 0; ; pagina++) {
            List<ClienteInterno.TurnoMonolito> lote = interno.turnos(pagina, PAGINA);
            for (ClienteInterno.TurnoMonolito m : lote) {
                TurnoActualizadoData d = m.turno();
                turnosAtendidos.guardar(d.turnoId(), d.profesionalId(), d.pacienteId(), d.fecha(), d.estado(), m.version());
            }
            total += lote.size();
            if (lote.size() < PAGINA) {
                return total;
            }
        }
    }

    @Scheduled(initialDelayString = "#{${profesionales.clinica.resincronizacion-turnos-minutos:15} * 60000}",
            fixedDelayString = "#{${profesionales.clinica.resincronizacion-turnos-minutos:15} * 60000}")
    public void resincronizarTurnos() {
        CargaInicial carga = cargaInicial.getIfAvailable();
        if (carga == null || !carga.listo()) {
            return;
        }
        try {
            int n = tx.execute(estado -> copiarTurnos());
            log.debug("Resincronización de turnos desde el monolito: {}", n);
        } catch (RuntimeException e) {
            log.warn("El monolito no respondió la resincronización de turnos; la copia sigue como está: {}", e.getMessage());
        }
    }
}
