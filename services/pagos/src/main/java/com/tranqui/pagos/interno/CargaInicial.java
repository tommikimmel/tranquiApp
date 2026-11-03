package com.tranqui.pagos.interno;

import com.tranqui.eventos.consumo.EsperaAntesDeConsumir;
import com.tranqui.pagos.datos.Cobros;
import com.tranqui.pagos.datos.CuentasMp;
import com.tranqui.pagos.datos.Pagos;
import com.tranqui.pagos.datos.Participantes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Primer arranque de pagos (paso 1 de "Migración de datos en cada fase"): copia del monolito los
 * participantes, las cuentas de Mercado Pago vinculadas (tokens cifrados, sin descifrarlos: nadie vuelve a
 * vincular), los pagos registrados, los documentos de la API de conceptos y los turnos esperando el pago.
 * Idempotente, reintenta con espera creciente si el monolito no está listo, y solo corre con la base vacía.
 *
 * <p>El consumo de eventos espera a que termine ({@link EsperaAntesDeConsumir}), como en el calendario: una
 * cancelación que quedó en la cola no puede procesarse antes de copiar el pago que hay que reembolsar.
 */
@Component
public class CargaInicial implements EsperaAntesDeConsumir {

    private static final Logger log = LoggerFactory.getLogger(CargaInicial.class);
    private static final int PAGINA = 500;

    private final Participantes participantes;
    private final CuentasMp cuentas;
    private final Pagos pagos;
    private final Cobros cobros;
    private final ClienteInterno interno;
    private final TransactionTemplate tx;
    private final boolean habilitada;
    private volatile boolean terminada;

    public CargaInicial(Participantes participantes, CuentasMp cuentas, Pagos pagos, Cobros cobros, ClienteInterno interno,
                        TransactionTemplate tx, @Value("${pagos.carga-inicial.habilitada:true}") boolean habilitada) {
        this.participantes = participantes;
        this.cuentas = cuentas;
        this.pagos = pagos;
        this.cobros = cobros;
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
        Thread hilo = new Thread(this::cargarConReintentos, "carga-inicial-pagos");
        hilo.setDaemon(true);
        hilo.start();
    }

    void cargarConReintentos() {
        long esperaMs = 2_000;
        Boolean primerArranque = null;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (primerArranque == null) {
                    primerArranque = participantes.cantidad() == 0 && cuentas.cantidad() == 0 && pagos.cantidad() == 0;
                }
                if (primerArranque) {
                    // Todo o nada: si se corta a la mitad, el próximo intento arranca de cero con la base vacía.
                    tx.executeWithoutResult(estado -> cargar());
                }
                terminada = true;
                return;
            } catch (RuntimeException e) {
                log.warn("Carga inicial de pagos fallida; se reintenta en {} ms: {}", esperaMs, e.getMessage());
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
        int pg = copiarPagos();
        int d = copiarDocumentos();
        int pe = copiarCobrosPendientes();
        log.info("Carga inicial de pagos: {} participantes, {} cuentas de Mercado Pago, {} pagos, {} documentos y {} cobros en curso",
                p, c, pg, d, pe);
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
            cuentas.guardar(new CuentasMp.Cuenta(c.profesionalId(), c.accessTokenCifrado(), c.refreshTokenCifrado(),
                    c.tokenVenceEn(), c.mpUserId()));
        }
        return lista.size();
    }

    int copiarPagos() {
        int total = 0;
        for (int pagina = 0; ; pagina++) {
            List<ClienteInterno.Pago> lote = interno.pagos(pagina, PAGINA);
            for (ClienteInterno.Pago p : lote) {
                String referencia = p.turnoId() != null ? Cobros.referenciaTurno(p.turnoId())
                        : Cobros.referenciaDocumento(p.solicitudId());
                pagos.registrar(new Pagos.Pago(null, referencia, p.turnoId(), p.solicitudId(), p.profesionalId(), p.pacienteId(),
                        p.transactionId(), p.estado(), p.monto(), p.fechaPago()), p.id());
            }
            total += lote.size();
            if (lote.size() < PAGINA) {
                return total;
            }
        }
    }

    /** Documentos de la API de conceptos: su cobro y, si están pagos, el pago (el monolito no los guarda en pago). */
    int copiarDocumentos() {
        List<ClienteInterno.Solicitud> lista = interno.solicitudes();
        for (ClienteInterno.Solicitud s : lista) {
            boolean pagado = "APROBADO".equals(s.estado());
            String referencia = Cobros.referenciaDocumento(s.solicitudId());
            cobros.crear(new Cobros.Cobro(referencia, null, s.solicitudId(), s.profesionalId(), s.pacienteId(), s.precio(),
                    s.titulo(), s.tipoConcepto(), null, pagado ? Cobros.PAGADO : Cobros.PENDIENTE, null));
            if (pagado && s.transactionId() != null) {
                pagos.registrar(new Pagos.Pago(null, referencia, null, s.solicitudId(), s.profesionalId(), s.pacienteId(),
                        s.transactionId(), Pagos.APROBADO, s.precio(), java.time.LocalDateTime.now()), null);
            }
        }
        return lista.size();
    }

    int copiarCobrosPendientes() {
        List<ClienteInterno.CobroPendiente> lista = interno.cobrosPendientes();
        for (ClienteInterno.CobroPendiente c : lista) {
            boolean conLink = c.checkoutUrl() != null && !c.checkoutUrl().isBlank();
            cobros.crear(new Cobros.Cobro(Cobros.referenciaTurno(c.turnoId()), c.turnoId(), null, c.profesionalId(),
                    c.pacienteId(), c.monto(), c.titulo(), null, conLink ? c.checkoutUrl() : null,
                    conLink ? Cobros.LISTO : Cobros.PENDIENTE, null));
        }
        return lista.size();
    }
}
