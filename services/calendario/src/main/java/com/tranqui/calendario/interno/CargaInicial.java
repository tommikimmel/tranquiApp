package com.tranqui.calendario.interno;

import com.tranqui.calendario.datos.CuentasGoogle;
import com.tranqui.calendario.datos.EventosExternos;
import com.tranqui.calendario.datos.Participantes;
import com.tranqui.calendario.datos.TurnosAgendados;
import com.tranqui.eventos.consumo.EsperaAntesDeConsumir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;

/**
 * Primer arranque del calendario (paso 1 de "Migración de datos en cada fase"): copia del monolito
 * los participantes, las cuentas de Google conectadas (tokens cifrados, sin descifrarlos: nadie tiene
 * que volver a autorizar), la caché de eventos externos y los turnos confirmados de hoy en adelante
 * con su evento y su Meet. Idempotente, reintenta con espera creciente si el monolito no está listo,
 * y solo corre con la base vacía.
 *
 * <p>El consumo de eventos espera a que termine ({@link EsperaAntesDeConsumir}): si el servicio vuelve
 * después de una vuelta atrás, la cola trae confirmaciones que ya atendió el monolito, y procesarlas
 * antes de copiar los turnos con su Meet crearía un Meet (o un evento de Google) nuevo para un turno
 * que ya lo tenía. Con los turnos copiados, esas confirmaciones viejas no hacen nada.
 */
@Component
public class CargaInicial implements EsperaAntesDeConsumir {

    private static final Logger log = LoggerFactory.getLogger(CargaInicial.class);
    private static final int PAGINA = 500;

    private final Participantes participantes;
    private final CuentasGoogle cuentas;
    private final EventosExternos externos;
    private final TurnosAgendados turnos;
    private final ClienteInterno interno;
    private final boolean habilitada;
    private volatile boolean terminada;

    public CargaInicial(Participantes participantes, CuentasGoogle cuentas, EventosExternos externos, TurnosAgendados turnos,
                        ClienteInterno interno, @Value("${calendario.carga-inicial.habilitada:true}") boolean habilitada) {
        this.participantes = participantes;
        this.cuentas = cuentas;
        this.externos = externos;
        this.turnos = turnos;
        this.interno = interno;
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
        Thread hilo = new Thread(this::cargarConReintentos, "carga-inicial-calendario");
        hilo.setDaemon(true);
        hilo.start();
    }

    void cargarConReintentos() {
        long esperaMs = 2_000;
        Boolean primerArranque = null;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (primerArranque == null) {
                    primerArranque = participantes.cantidad() == 0 && cuentas.cantidad() == 0;
                }
                if (!primerArranque) {
                    terminada = true;
                    return;
                }
                cargar();
                terminada = true;
                return;
            } catch (RuntimeException e) {
                log.warn("Carga inicial del calendario fallida; se reintenta en {} ms: {}", esperaMs, e.getMessage());
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
        int p = copiarParticipantes();
        int c = copiarCuentas();
        int e = copiarEventosExternos();
        int t = copiarTurnos();
        log.info("Carga inicial del calendario: {} participantes, {} cuentas de Google, {} eventos externos y {} turnos",
                p, c, e, t);
    }

    int copiarParticipantes() {
        int total = 0;
        for (int pagina = 0; ; pagina++) {
            List<ClienteInterno.Contacto> lote = interno.contactos(pagina, PAGINA);
            for (ClienteInterno.Contacto c : lote) {
                participantes.guardar(c.id(), c.email(), c.nombre(), c.apellido(), c.rol());
            }
            total += lote.size();
            if (lote.size() < PAGINA) {
                return total;
            }
        }
    }

    int copiarCuentas() {
        List<ClienteInterno.Cuenta> lista = interno.cuentas();
        for (ClienteInterno.Cuenta c : lista) {
            // Sin el syncToken del monolito: la primera pasada hace una sincronización completa y la caché
            // queda con todo lo que el monolito no guardaba (si el evento está marcado "disponible").
            cuentas.guardar(new CuentasGoogle.Cuenta(c.profesionalId(), c.accessTokenCifrado(), c.refreshTokenCifrado(),
                    c.tokenVenceEn(), c.calendarId(), null, c.watchChannelId(), c.watchResourceId(),
                    c.watchVenceEn(), c.watchActivo()));
        }
        return lista.size();
    }

    int copiarEventosExternos() {
        int total = 0;
        long desde = 0;
        while (true) {
            List<ClienteInterno.EventoExterno> lote = interno.eventosExternos(desde, PAGINA);
            for (ClienteInterno.EventoExterno e : lote) {
                externos.copiar(e.id(), e.profesionalId(), e.googleEventId(), e.titulo(), e.inicio(), e.fin(),
                        e.todoElDia(), e.estado());
            }
            total += lote.size();
            if (lote.size() < PAGINA) {
                break;
            }
            desde = lote.get(lote.size() - 1).id();
        }
        externos.ajustarSecuencia();
        return total;
    }

    int copiarTurnos() {
        int total = 0;
        for (ClienteInterno.TurnoConfirmado t : interno.turnosConfirmados()) {
            // Los documentos pedidos no ocupan agenda ni tienen evento en Google.
            if (Boolean.FALSE.equals(t.ocupaAgenda()) || t.horaInicio() == null) {
                continue;
            }
            turnos.guardar(t.turnoId(), t.profesionalId(), t.pacienteId(), t.fecha(), LocalTime.parse(t.horaInicio()),
                    t.horaFin() != null ? LocalTime.parse(t.horaFin()) : null, t.modalidad(), t.googleEventId(), t.meetUrl());
            total++;
        }
        return total;
    }
}
