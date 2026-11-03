package com.tranqui.turnos.interno;

import com.tranqui.turnos.datos.Personas;
import com.tranqui.turnos.datos.Profesionales;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

/**
 * Las copias de turnos de lo que no es suyo: los usuarios (identidad, en el monolito) y la agenda de los
 * profesionales. La agenda llega por eventos del servicio de profesionales y, además, cada
 * {@code turnos.resincronizacion-profesionales-minutos} se pide al monolito, que tiene la copia de siempre con
 * o sin ese servicio (así turnos también sigue al día en la vuelta atrás de la Fase 5).
 */
@Component
public class Copias {

    private static final Logger log = LoggerFactory.getLogger(Copias.class);
    static final int PAGINA = 200;

    private final ClienteInterno interno;
    private final Personas personas;
    private final Profesionales profesionales;
    private final TransactionTemplate tx;
    private final ObjectProvider<CargaInicial> cargaInicial;

    public Copias(ClienteInterno interno, Personas personas, Profesionales profesionales, TransactionTemplate tx,
                  ObjectProvider<CargaInicial> cargaInicial) {
        this.interno = interno;
        this.personas = personas;
        this.profesionales = profesionales;
        this.tx = tx;
        this.cargaInicial = cargaInicial;
    }

    /** Trae del monolito a un usuario (alta o cambio de identidad). Si el monolito ya no lo tiene, se deja como está. */
    public void refrescarPersona(Long id) {
        ClienteInterno.PacienteMonolito p;
        try {
            p = interno.paciente(id);
        } catch (HttpClientErrorException.NotFound e) {
            return; // los turnos viejos siguen mostrando sus datos
        }
        if (p != null) {
            boolean mp = personas.porId(id).map(Personas.Persona::mpVinculada).orElse(false);
            personas.guardar(p.persona(mp));
        }
    }

    /** Todos los usuarios, paginados (carga inicial). */
    public int copiarPersonas() {
        int total = 0;
        for (int pagina = 0; ; pagina++) {
            List<ClienteInterno.PacienteMonolito> lote = interno.pacientes(pagina, PAGINA);
            for (ClienteInterno.PacienteMonolito p : lote) {
                personas.guardar(p.persona(false));
            }
            total += lote.size();
            if (lote.size() < PAGINA) {
                return total;
            }
        }
    }

    /** La agenda, la disponibilidad, las tarifas y Mercado Pago de todos los profesionales del monolito. */
    public int copiarProfesionales() {
        int total = 0;
        for (int pagina = 0; ; pagina++) {
            List<ClienteInterno.ProfesionalMonolito> lote = interno.profesionales(pagina, PAGINA);
            for (ClienteInterno.ProfesionalMonolito p : lote) {
                if (p.perfil() != null) {
                    profesionales.guardarPerfil(p.id(), p.perfil());
                }
                profesionales.reemplazarDisponibilidad(p.id(), p.disponibilidad() != null ? p.disponibilidad() : List.of());
                profesionales.reemplazarTarifas(p.id(), p.tarifas() != null ? p.tarifas() : List.of());
                personas.mercadoPago(p.id(), p.mpVinculada());
            }
            total += lote.size();
            if (lote.size() < PAGINA) {
                return total;
            }
        }
    }

    @Scheduled(initialDelayString = "#{${turnos.resincronizacion-profesionales-minutos:15} * 60000}",
            fixedDelayString = "#{${turnos.resincronizacion-profesionales-minutos:15} * 60000}")
    public void resincronizarProfesionales() {
        CargaInicial carga = cargaInicial.getIfAvailable();
        if (carga == null || !carga.listo()) {
            return;
        }
        try {
            int n = tx.execute(estado -> copiarProfesionales());
            log.debug("Resincronización de profesionales desde el monolito: {}", n);
        } catch (RuntimeException e) {
            log.warn("El monolito no respondió la resincronización de profesionales; la agenda sigue con la copia: {}", e.getMessage());
        }
    }
}
